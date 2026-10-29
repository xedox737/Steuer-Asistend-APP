# Phase 11D: Original-App, native Google-Anmeldung

Stand: 29.10.2026. **NO-GO für eine praktische Veröffentlichung.** Fehlercode 10 auf dem echten POCO ist noch nicht ursächlich aufgelöst. Kein echter Google-/Android16-Test wurde ausgeführt. Eine grüne Simulation ersetzt ihn nicht.

Erster Auth-Codecommit vor der CI-Retry-Korrektur: `f732ee5b21a6cfb35c2a1b449aab41335639b452`. Lokal geprüft: `b929f3fbca6e1a690ba01572c2f940c1c1ab0763`, identischer Tree `7d2faf8dec46100782bfd2019a3f1e8fd02ae385`. Commit-Zuordnung: `phase11d-evidence/github-publication.json`.

## Ausgangspunkt und Grenzen

Base: `bdc17be4abc1398dabddb28eda2d9074d455a031`, gemergter PR #150. Installierte Version: Vorabversion 102, Quellstand `8ba68339e3a7e13cd139c12e94303edc8d7aee0f`, Workflow `40799163931`. Package bleibt `com.aistudio.steuerassistent.vnhqkp`, installiertes Zertifikat laut Auftrag SHA1 `EA:42:88:6D:5B:6E:AF:FB:AD:49:26:86:D9:65:B7:47:62:A9:76:49`.

Vor Änderungen wurde Fetch versucht; der Sandbox-Proxy war nicht erreichbar. Der aktuelle Main wurde über GitHub geprüft. Seine Commit- und Tree-Objekte wurden über die GitHub-Schnittstelle importiert und gegen die tatsächlichen Git-SHAs validiert, einschließlich signierter Merge-Commits. Der Arbeitsbranch `fix/phase11d-original-drive-auth` stammt exakt von dieser Base. Die bereitgestellten aktualisierten Work-Arbeitsanweisungen und AGENTS.md wurden vollständig gelesen. Die isolierte Test-App und ihr Branch werden nicht geändert.

## Belegter Fehler und ungeklärter Gerätefehler

Die vom Nutzer gemeldete `ApiException` 10 stammt aus dem SDK-Ergebnis der nativen Anmeldung vor `connectDrive()`. Die bisherige UI ordnet diesen Code einer Konfigurationsmeldung zu. Sie erzeugt den Code nicht selbst. Ein korrekt aussehender Android-Client mit passender Package-ID und SHA1 ist kein Nachweis der gesamten Projekt-/Consent-/Berechtigungskonfiguration. Ohne Console-Zugriff und Geräteprotokoll lässt sich keine bestimmte externe Einstellung als tatsächliche Ursache belegen.

Ein zusätzlicher Codefehler ist reproduziert: `GoogleAuthUtil.getToken()` kann `UserRecoverableAuthException` mit einer Google-Freigabe-Intent liefern. Bisher fing die App diese als allgemeine `GoogleAuthException`, markierte die Anmeldung als verloren und verwarf die Freigabeoberfläche. Roter lokaler Lauf: **24 echte Methoden, 1 Fehler**, ausschließlich `recoverableConsentDoesNotBecomeLogout`. Dafür wurden die betroffenen Base-Quellen frisch kompiliert. Dies belegt den Consent-Fehler, nicht die Ursache von Code 10 auf dem Gerät.

Der direkte ergänzende Lauf verwendet SDK35 und vorhandene Build-Ressourcen mit absoluten Pfaden. Ein erster Lauf mit relativen Ressourcen war infrastrukturell ungültig; er wird nicht als Reproduktion gezählt. Die eingecheckten Tests bleiben SDK36. Reguläres Gradle scheitert hier vor Kompilierung am `FileLockContentionHandler`/fehlenden nutzbaren Wildcard-IP. Die vollständige CI ist maßgeblich für reguläre SDK36-, Lint-, Build- und Emulatorergebnisse.

## Historie des Anmeldewegs

Im alten Stand `72aa308fb60b3a9d57809baebdcdc2299940374a` war der grüne Hauptbutton der eingebettete Web-Flow. Der native GoogleSignIn-Pfad existierte bereits als Alternative. Der genaue Wechsel des Hauptbuttons zu nativ erfolgte im Phase11C-Codecommit `6ed31a292abdea4fd06834d52e494012d38e29b9` (inhaltlich lokaler Fix `2b58e6a`, gleicher Tree `7252c88f004c7788aedd39a476e716527378db03`), übernommen durch PR #149 / Merge `8ba6833`. PR #150 ändert den Signierungs-/Veröffentlichungsworkflow, nicht den Login-Callback. Die Phase11C-Reparaturen für 401, Netzwerk, TTL und Namespace bleiben erhalten.

## Gezielte Änderungen

- `NativeDriveSignIn`: kleine SDK-Grenze für die bestehenden Options und Activity-Ergebnisse. Weiterhin ausschließlich E-Mail und `drive.file`, kein ID-Token, kein Server-Auth-Code, keine neue Client-ID. SDK-Code 10 bleibt auch bei Android `RESULT_CANCELED` erhalten; eine echte Abmeldung wird daraus nicht abgeleitet. Kontoübernahme verlangt `RESULT_OK`, eine nutzbare E-Mail und tatsächlich gewährte Drive-Berechtigung. Fehlertexte enthalten nur Phase und numerische Statuscodes, keine Intent-Extras, Tokens oder Kontoangaben.
- `ReceiptAppUi`: vorhandener ActivityResult-Launcher bleibt Hauptweg. Doppelte laufende Loginstarts werden unterdrückt. Ergebnisübergabe trägt die bestehende Drive-Sitzung; späte Ergebnisse nach Logout oder Kontowechsel werden verworfen. Ein eigener Launcher übernimmt ausschließlich die von Google gelieferte recoverable Intent. Der Web-Flow wird nicht zum Hauptweg zurückgebaut.
- `ReceiptViewModel`: recoverable Consent wird vom echten Auth-Fehler getrennt. Konto und lokale Daten bleiben erhalten. Eine Freigabe kann nur einmal übernommen werden; beim Ergebnis wird die ursprüngliche Sitzung geprüft. Zustimmung ist kein Drive-Erfolg: Erst nach Abschluss des bisherigen Initialisierungs-/Syncjobs wird ein Token erneut angefordert und der vorhandene sichere Drive-Zugriff geprüft. Teiltransfers werden nicht automatisch wiederholt. Bleibt Consent nach Zustimmung weiter erforderlich, entsteht keine automatische Dialogschleife. Abbruch bleibt wiederholbar. Logout/Kontowechsel verwerfen alte Requests. Der echte native Fehlercode bleibt auch in der Drive-Diagnose sichtbar, statt durch „Kein Konto“ verdeckt zu werden.
- `DriveConnectionFailure`: zusätzliche Kategorie CONSENT vor dem allgemeinen Auth-Fehler; sonstige 401/403/404-/Netzwerk-/Timeout-/Sicherheitsregeln bleiben bestehen.
- Signierungswächter: Nach Prüfung der gebauten APK muss zusätzlich deren tatsächlicher, verifizierter Zertifikathash zum installierten Produkt passen, bevor der Workflow ein stabiles Produktupdate veröffentlicht. PR #150 bleibt aktiv; ein korrekter Keystore allein ersetzt die Prüfung des gebauten Artefakts nicht.

## Zustandsvertrag

Kontoauswahl ist nicht OAuth-Erfolg. Ein gültiger SDK-Callback mit Drive-Scope verknüpft das Konto, anschließend werden Token und Drive-Struktur geprüft. Eine recoverable Freigabe ist eine ausstehende Google-Aktion; sie löscht die Kontoverknüpfung nicht. Netzwerk-/Timeoutfehler behalten das Konto. HTTP403 und 404 bleiben Zugriffs-/Strukturfehler. HTTP401 invalidiert den nativen Cache und erneuert einmal; wiederholte Ablehnung verlangt gezielte Autorisierung. Der vorhandene automatische Neustart mit gespeichertem Konto und expliziter Logout bleiben erhalten. Es werden keine neuen Access- oder Refresh-Tokens gespeichert.

## Zusätzlicher belegter Veröffentlichungsfehler

Die unabhängige Prüfung des angegebenen Workflows `40799163931` zeigt: Version 102 stammt aus `.github/workflows/publish-verified-apk.yml`, nicht aus `android-ci.yml`. Dieser zweite Publisher erzeugte auch nach PR #150 einen zufälligen Schlüssel und veröffentlichte ohne dessen Schutz. Drei ältere Einmal-Workflows (`compact-quick-access-height`, `fix-finanzamt-pdf-crash`, `fix-legacy-description-runtime`) enthielten ebenfalls direkte Veröffentlichungen mit zufälligen Schlüsseln. Alle fünf direkten GitHub-Release-Publisher sind jetzt auf einen Keystore- und tatsächlichen APK-Zertifikatnachweis gebunden.

Der gemeinsame strenge Vorbereitungsschritt benötigt das vorhandene persistente Secret und den exakt passenden genehmigten SHA1. Er prüft den tatsächlichen öffentlichen Zertifikathash aus dem geladenen Keystore, bevor er diesen installiert; temporäre Kandidaten werden geschützt und bereinigt. Fehlende/ungültige/fremde Schlüssel blockieren die Veröffentlichung, statt durch neu erzeugte Schlüssel ersetzt zu werden. Ein lokaler Negativtest erzeugt einen synthetischen gültigen Fremdschlüssel und weist dessen Ablehnung ohne Installation und ohne Schlüsselausgabe nach. Der positive Pfad mit dem privaten Originalschlüssel ist mangels Verfügbarkeit offen.

Die drei älteren Codeänderungs-Workflows dürfen nur noch bewusst per `workflow_dispatch` starten. Sonst würde schon die Sicherheitskorrektur ihrer Workflow-Dateien beim späteren Merge deren alte automatischen Codeänderungen/Git-Pushes auslösen. Ihre manuellen Funktionen bleiben vorhanden; sie verlangen vor Änderungen gültiges dauerhaftes Signaturmaterial und vor Commit/Release die Prüfung des gebauten Artefakts. Kein Workflow wird durch diesen Auftrag manuell gestartet, kein Main-Commit oder Release ausgelöst.

Der erste PR-CI-Lauf `40913257040` prüft den Zwischenstand vor diesem nachgewiesenen Publisher-Fix. Er wird durch die notwendige Sicherheitskorrektur ersetzt und nicht als vollständige finale Abnahme gezählt. Die Android-Produktquellen bleiben gegenüber dem bereits lokal mit 45 Methoden geprüften Stand identisch; für die neue Signing-/Workflow-Korrektur wurden acht Python-Tests, YAML-Parsing und Diff-Prüfung ausgeführt. Keine Tests wurden deaktiviert.

## Tests und Nachweise

`NativeDriveSignInTest` prüft echte lokale SDK-Intent-/Options-Verträge: Erfolg, abgesagte Auswahl, Android-Ergebnis gegen SDK-Code 10, fehlenden Drive-Scope, Netzwerk/Timeout und Vermeidung von Konto-/Token-Lecks. Die Google-Dienste selbst werden dadurch nicht geprüft.

Die vorhandene SDK36-Klasse `DriveConnectionRegressionTest` erhält Consent-Verbrauch, erneute Tokenanforderung nach Freigabe, Abbruch, Logout, Kontowechsel, Dialogschleifenschutz, Kontoübergabe und Ablehnung verspäteter/mehrfacher Callbacks. Bestehende Fälle für Neustart, Tokenablauf, Netzunterbrechung, 401/403/404, doppelte Initialisierung, Cancellation, Web-TTL und Präferenzerhalt bleiben aktiv. Der SDK36-Ausführungswächter prüft alle tatsächlichen Methoden beider Klassen, Fehler und Skips. Die bestehende vollständige JVM-/Emulator-CI wird nicht eingeschränkt.

Lokaler Zwischenstand: 47 JVM-Methoden unter ergänzendem SDK35 bestanden; acht Python-Signaturtests bestanden. Die vollständigen regulären CI-Ergebnisse werden mit tatsächlichen Run-IDs im PR nachgetragen, nicht vorweggenommen. Evidence liegt unter `docs/phase11d-evidence/`. Kompilier-/Laufgrenzen und der rote Ausgangsfall bleiben sichtbar.

## Belegter Fehler bei sofortiger Wiederverbindung und finale Korrektur

CI `40914708522` führte 1.201 eindeutige SDK36-JVM-Methoden aus: 1.200 bestanden, eine scheiterte. Der vorhandene Test `networkDuringNativeTokenAcquisitionKeepsLoginAndAllowsRetry` wartete nach einer sofortigen Wiederholung vergeblich auf erfolgreiche Initialisierung. Lint bestand; Emulator und APK-Build wurden wegen des fehlgeschlagenen Verify-Jobs nicht ausgeführt. Die vorhandenen Portfolio-Ausführungswächter blieben aktiv und meldeten den Unitfehler korrekt weiter; die 36 Drive-Reparaturmethoden mit 60/60 Originalhashes bestanden. Es gab keinen zusätzlichen Backup-/Restorefehler.

Ursache: `driveSyncError` wird vor dem endgültigen Abschluss des produzierenden Jobs veröffentlicht. Ein unmittelbarer Wiederholungsaufruf wurde vom laufenden Job als Doppelaufruf verworfen. Ein neuer deterministischer Test wiederholt unmittelbar im Fehler-Collector und reproduzierte den Fehler lokal: 46 Methoden, genau dieser neue Fall rot. Die bisherigen Timeouts und Assertions bleiben unverändert.

Die Produktkorrektur merkt nach einem bereits veröffentlichten Fehler höchstens eine explizit angeforderte Initialisierung pro Sitzung vor. Ein Completion-Handler wartet den laufenden Initialisierungs-/Syncjob ab; normale Doppelaufrufe und inzwischen erfolgreiche Vorgänge werden verworfen. Vor dem Nachholen werden Sitzung, Kontoverknüpfung und verbleibender Fehler erneut geprüft. Logout/Kontowechsel verwerfen die Vormerkung. Es entsteht keine automatische Retry-Schleife und keine automatische Wiederholung teilweise ausgeführter Übertragungen. Lokaler Abschluss: 47 Methoden bestanden, einschließlich fünf unmittelbarer Retry-Aufrufe und Logout mit vorgemerkt ausstehendem Retry. Die finale vollständige CI muss diesen Stand erneut prüfen.

## OAuth- und Update-Voraussetzungen: offen

1. Android-Client muss für exakt Package und tatsächliches installierbares APK-Zertifikat im vorgesehenen Google-Cloud-Projekt liegen. Keine Web-Client-ID in `requestIdToken()` einsetzen: der native Drive-Pfad fordert keinen ID-Token an. Die bisherige Web-Client-ID und Firebase-Redirect gehören nur zum bisherigen alternativen Web-Flow. Eine optionale fehlende `google-services.json` beweist bei diesem API-Aufruf allein keinen Fehler.
2. Console prüfen: Projekt-ID/-Nummer des Android-Clients, Drive-API aktiv, OAuth-Branding/Audience/Veröffentlichungsstatus, erlaubte Nutzer, erforderlicher Scope und Organisationseinschränkungen. Datum/Uhrzeit der Clientanlage und Test nach Wirksamwerden protokollieren. Diese Einstellungen wurden hier nicht eingesehen. Eine bloße Wartezeit ist kein Erfolgstest.
3. Der native und bisherige Web-Client müssen hinsichtlich Projekt und `drive.file`-Dateizugriff konsistent geprüft werden. Fehlender Zugriff auf ältere Backups kann einen separaten 403-/404-/Namespacefehler verursachen; er beweist keine Ursache für den vorherigen Sign-In-Code 10.
4. Das private Original-Signaturmaterial muss verfügbar und als vorhandenes GitHub-Secret eingerichtet sein; der freigegebene öffentliche SHA1 muss passen. Secret-Inhalte werden weder gelesen noch ausgegeben. Es gibt hier keine konfigurierte GCP-Identität und keinen Zugriff auf Console oder Gerät. Verfügbarkeit des alten Schlüssels bleibt offen. Aus der APK kann der Privatkey nicht wiederhergestellt werden.
5. Ohne alten Schlüssel: keine Deinstallation, kein Überschreiben der Installation, keine Behauptung eines kompatiblen Updates. Alte Installation unverändert nutzbar lassen. Erst unabhängigen lokalen Datenexport und Originaldateien sichern, Backup/Restore auf einem separaten leeren Profil prüfen und IDs/Beziehungen/Dateihashes vergleichen. Eine spätere Migration oder Signaturschlüsselrotation benötigt einen eigenen freigegebenen und praktisch geprüften Plan; für eine Rotation wird ebenfalls das alte Signaturmaterial benötigt. Dieses Dokument autorisiert keine Migration oder Änderung der Produktidentität.

Für spätere allgemeine Kunden: OAuth-Projekt, Consent-Veröffentlichung, Datenschutzinformationen und benötigte App-Verifikation werden einmal vom Anbieter eingerichtet; Kunden erteilen Zustimmung, legen aber keine eigenen OAuth-Clients an. Bei Google Play App Signing ist das tatsächlich ausgelieferte App-Signaturzertifikat maßgeblich, nicht allein der Upload-Key. Bestehende Package-ID und Release-Strategie werden hier nicht geändert.

## Unterstützte Identity-Lösung

Google kennzeichnet GoogleSignIn inzwischen als veraltet/nicht mehr unterstützt. Für Authentifizierung ist Credential Manager, für Drive-Autorisierung AuthorizationClient zu evaluieren. Credential Manager allein liefert keine Drive-Autorisierung. Eine gezielte Migration muss die bestehende Kontobindung, Scope-Freigabe, stille Tokenanforderung, Resolution-Ergebnisse und Namespace-Sicherheit vollständig testen. Sie umgeht keine fehlerhafte externe OAuth-/Signaturkonfiguration. Hier erfolgt deshalb keine spekulative komplette Migration als behaupteter Code10-Fix; die Abkündigung bleibt ein dokumentiertes Freigaberisiko. Ein langfristiges kommerzielles GO verlangt diese Bewertung und reale Validierung.

Primärquellen (29.10.2026):
- https://developers.google.com/android/reference/com/google/android/gms/common/api/CommonStatusCodes
- https://developers.google.com/identity/sign-in/android/migration-guide
- https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationResult

## Anleitung: echte Android16-Abnahme ohne Datenverlust

1. Installierte Package-ID, APK-Zertifikat, Version und gesicherte lokale Bestandszahlen unabhängig erfassen. Backup und 60 Originalhashes sichern. Keine Deinstallation oder Datensatzbereinigung.
2. Test-APK nur dann als Update verwenden, wenn die gebaute Original-Package-ID und verifizierte Signatur zur installierten Version passen. Sonst STOP/NO-GO; ausschließlich separates leeres Profil für technische Tests.
3. Auf POCO/Android16 mit aktuellen Google Play-Diensten nativen Hauptbutton öffnen, gewünschtes Konto auswählen und Drive-Zustimmung erteilen. Phase, Android-Ergebnis und tatsächlichen Google-Statuscode festhalten; keine Tokens oder personenbezogenen Protokolle veröffentlichen.
4. Nachweisen: Konto verknüpft, Autorisierung und Scope vorhanden, Token erhalten, Drive-API erreichbar, produktiver Root `Steuerassistent Belege/_BelegApp-Daten` sicher erkannt. Keine fremden oder isolierten Testordner verwenden. Erst danach separaten Sync-Erfolg dokumentieren.
5. App schließen/Prozess stoppen und neu starten: dasselbe Konto, keine neue Auswahl; Struktur erneut prüfen. Nach Tokenablauf Token-Erneuerung prüfen. Kurz Netz aus/an: Konto bleibt, erneuter Zugriff gelingt. Kontoauswahl abbrechen und Freigabe widerrufen: korrekte gezielte Aktion statt Datenverlust. Logout und bewussten Kontowechsel separat prüfen.
6. Datenerhalt und Update mit identischer Signatur auf separatem gesicherten Bestand prüfen; Immobilien, Einheiten, Mietverhältnisse, Belege/Dokumente, Bank, DATEV-Historie, AfA, Fahrtenbuch und Einstellungen vergleichen. Keine echte Produktionsinstallation als ungesicherten Versuch verwenden.

**GO erst nach erfolgreicher vollständiger CI, geprüfter Update-Signatur, geklärter OAuth-Konfiguration und erfolgreichem echtem Android16-Login/Neustart/Erneuerungstest. Aktuell NO-GO.** Kein automatischer Merge. Keine neue Produkt-APK wird als funktionierend oder problemlos updatefähig bezeichnet.

Weitere verbleibende Sicherheitsgrenze: Der unveränderte optionale Web-/manuelle Altpfad speichert sein zeitlich begrenztes manuelles Token noch in den bisherigen Preferences. Der native Hauptpfad legt keine Tokens dort ab. Diese Altlast ist kein dauerhafter Produktionsweg und bleibt vor kommerzieller Freigabe zu entfernen oder sicher zu migrieren; es werden hier keine bestehenden Credentials pauschal gelöscht.
