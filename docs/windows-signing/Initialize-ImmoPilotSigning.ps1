[CmdletBinding()]
param([switch]$CopyForGitHub)

$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'Dieses Skript ist fuer Windows vorgesehen.' }
if (-not [Environment]::Is64BitOperatingSystem) { throw 'Ein 64-Bit-Windows wird benoetigt.' }

$signingDirectory = Join-Path $env:LOCALAPPDATA 'ImmoPilot\Signing'
$keystorePath = Join-Path $signingDirectory 'immopilot-debug.keystore'
$toolsDirectory = Join-Path $env:LOCALAPPDATA 'ImmoPilot\SigningTools'

function Find-Keytool {
    $command = Get-Command keytool.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\keytool.exe') }
    if ($env:ProgramFiles) {
        $candidates += (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr\bin\keytool.exe')
    }
    foreach ($candidate in $candidates) { if (Test-Path -LiteralPath $candidate) { return $candidate } }
    if (Test-Path -LiteralPath $toolsDirectory) {
        $cached = Get-ChildItem -LiteralPath $toolsDirectory -Filter keytool.exe -File -Recurse | Select-Object -First 1
        if ($cached) { return $cached.FullName }
    }
    Write-Host 'Java fehlt. Lade das portable Eclipse-Temurin-JDK 17 (ca. 190 MB).'
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    $assets = Invoke-RestMethod -Uri 'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $asset = $assets | Select-Object -First 1
    $package = $asset.binary.package
    if ($package.checksum -notmatch '^[a-fA-F0-9]{64}$' -or $package.link -notmatch '^https://github\.com/adoptium/') {
        throw 'Unerwartete JDK-Metadaten. Es wurde kein Schluessel erzeugt.'
    }
    New-Item -ItemType Directory -Path $toolsDirectory -Force | Out-Null
    $archivePath = Join-Path $toolsDirectory 'temurin17.zip'
    Invoke-WebRequest -UseBasicParsing -Uri $package.link -OutFile $archivePath
    if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash -ne $package.checksum) {
        throw 'JDK-Pruefsumme stimmt nicht. Das heruntergeladene Programm wird nicht ausgefuehrt.'
    }
    Expand-Archive -LiteralPath $archivePath -DestinationPath (Join-Path $toolsDirectory 'jdk17') -Force
    $downloaded = Get-ChildItem -LiteralPath (Join-Path $toolsDirectory 'jdk17') -Filter keytool.exe -File -Recurse | Select-Object -First 1
    if (-not $downloaded) { throw 'keytool.exe fehlt nach dem Entpacken.' }
    return $downloaded.FullName
}

$bootstrapMutex = [Threading.Mutex]::new($false, 'Local\ImmoPilotStableSigningBootstrap')
try { $lockAcquired = $bootstrapMutex.WaitOne(0) }
catch [Threading.AbandonedMutexException] { $lockAcquired = $true }
if (-not $lockAcquired) {
    $bootstrapMutex.Dispose()
    throw 'Die Einrichtung laeuft bereits in einem anderen Fenster.'
}
try {
$keytoolPath = Find-Keytool
function Invoke-Keytool([string[]]$ToolArguments) {
    $previousPreference = $ErrorActionPreference
    try {
        # Java writes normal informational messages to stderr too. Check its actual exit code.
        $ErrorActionPreference = 'Continue'
        $output = & $keytoolPath @ToolArguments 2>&1
        $toolExitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($toolExitCode -ne 0) { throw "Keytool fehlgeschlagen (Exit $toolExitCode). Vorhandene Schluessel bleiben unveraendert." }
    return (($output | ForEach-Object { [string]$_ }) -join "`n")
}

if (-not (Test-Path -LiteralPath $signingDirectory)) {
    New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetOwner($identity.User)
    $acl.SetAccessRuleProtection($true, $false)
    $rule = New-Object Security.AccessControl.FileSystemAccessRule($identity.User, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $signingDirectory -AclObject $acl
}

if (-not (Test-Path -LiteralPath $keystorePath)) {
    Invoke-Keytool -ToolArguments @('-genkeypair', '-storetype', 'JKS', '-keystore', $keystorePath,
        '-storepass', 'android', '-alias', 'androiddebugkey', '-keypass', 'android',
        '-dname', 'CN=ImmoPilot Android,O=ImmoPilot,C=DE', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000') | Out-Null
    Write-Host 'Ein dauerhafter ImmoPilot-Debug-Schluessel wurde lokal erzeugt.'
} else {
    Write-Host 'Vorhandener Schluessel wird wiederverwendet; kein neuer Schluessel erzeugt.'
}

$publicOutput = Invoke-Keytool -ToolArguments @('-list', '-rfc', '-keystore', $keystorePath, '-storepass', 'android', '-alias', 'androiddebugkey')
$match = [regex]::Match($publicOutput, '(?s)-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----')
if (-not $match.Success) { throw 'Oeffentliches Zertifikat konnte nicht gelesen werden.' }
$certificateBytes = [Convert]::FromBase64String(($match.Groups[1].Value -replace '\s', ''))
$sha1 = [Security.Cryptography.SHA1]::Create()
$sha256 = [Security.Cryptography.SHA256]::Create()
try {
    $sha1Text = [BitConverter]::ToString($sha1.ComputeHash($certificateBytes)).Replace('-', ':')
    $sha256Text = [BitConverter]::ToString($sha256.ComputeHash($certificateBytes)).Replace('-', ':')
} finally { $sha1.Dispose(); $sha256.Dispose() }

@{ package = 'com.aistudio.steuerassistent.vnhqkp'; certificateSha1 = $sha1Text; certificateSha256 = $sha256Text } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $signingDirectory 'public-identity.json') -Encoding UTF8
Write-Host ''
Write-Host "OEFFENTLICHE SHA1: $sha1Text"
Write-Host "OEFFENTLICHE SHA256: $sha256Text"
Write-Host "Schluesseldatei: $keystorePath"
Write-Host 'Diese Schluesseldatei dauerhaft separat sichern; niemals oeffentlich hochladen.'
Write-Host 'GitHub-Variable PRODUCTION_DEBUG_SHA1 erhaelt die oeffentliche SHA1 oben.'

if ($CopyForGitHub) {
    # Never print private key bytes or their Base64 representation.
    Set-Clipboard -Value ([Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath)))
    Write-Host ''
    Write-Host 'Die Zwischenablage enthaelt jetzt den PRIVATEN Schluessel fuer GitHub.'
    Write-Host 'Nur in das GitHub-Secret PRODUCTION_DEBUG_KEYSTORE_BASE64 einfuegen, NICHT in Chat, Dateien oder Kommentare.'
    Start-Process 'https://github.com/xedox737/Steuer-Asistend-APP/settings/secrets/actions/new'
}

Write-Host ''
Write-Host 'Dies ist ein neuer Signierstand fuer eine NEUINSTALLATION, kein Update von Version 103.'
Write-Host 'Die App noch nicht deinstallieren. Zuerst muessen SHA1-Freigabe, OAuth und APK-Pruefung abgeschlossen sein.'

} finally {
    $bootstrapMutex.ReleaseMutex()
    $bootstrapMutex.Dispose()
}
