"""Install only the persistent keystore capable of updating original version 102."""
import base64
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from apk_signing import certificate_fingerprints, require_product_update_identity, PRODUCT_PACKAGE, INSTALLED_PRODUCT_SHA1


def prepare():
    encoded = os.environ.get('PRODUCTION_DEBUG_KEYSTORE_BASE64', '')
    approved = os.environ.get('PRODUCTION_DEBUG_SHA1', '').replace(':', '').lower()
    if not encoded or approved != INSTALLED_PRODUCT_SHA1:
        raise ValueError('Persistent original signing key and matching approved SHA1 required')
    data = base64.b64decode(''.join(encoded.split()), validate=True)
    os.umask(0o077)
    with tempfile.TemporaryDirectory() as directory:
        candidate = Path(directory) / 'candidate.keystore'
        candidate.write_bytes(data)
        candidate.chmod(0o600)
        result = subprocess.run(['keytool', '-list', '-rfc', '-keystore', str(candidate),
                                 '-storepass', 'android', '-alias', 'androiddebugkey'],
                                capture_output=True, text=True, check=True)
        fingerprints = certificate_fingerprints(result.stdout)
        require_product_update_identity(dict(package=PRODUCT_PACKAGE, signatureVerified=True,
                                             signingSha1=fingerprints['signingSha1']))
        shutil.copyfile(candidate, 'debug.keystore')
        Path('debug.keystore').chmod(0o600)
    print('Persistent signing certificate matches installed original version 102')


if __name__ == '__main__':
    try:
        prepare()
    except (ValueError, OSError, subprocess.SubprocessError):
        raise SystemExit('Product publication blocked: missing, invalid or incompatible signing material')
