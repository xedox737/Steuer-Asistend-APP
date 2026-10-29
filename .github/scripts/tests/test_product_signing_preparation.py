import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import base64

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location('prepare_product', SCRIPTS/'prepare-product-signing.py')
preparation = importlib.util.module_from_spec(spec)
spec.loader.exec_module(preparation)


class ProductSigningPreparationTests(unittest.TestCase):
    def test_missing_material_is_rejected_without_creating_key(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaises(ValueError):
            preparation.prepare()

    def test_wrong_approved_identity_is_rejected_before_loading_key(self):
        with patch.dict(os.environ, dict(PRODUCTION_DEBUG_KEYSTORE_BASE64='not-a-key', PRODUCTION_DEBUG_SHA1='00'*20)), self.assertRaises(ValueError):
            preparation.prepare()

    def test_valid_but_incompatible_private_key_is_never_installed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            subprocess.run(['keytool', '-genkeypair', '-keystore', str(root/'synthetic.jks'),
                            '-storepass', 'android', '-keypass', 'android', '-alias', 'androiddebugkey',
                            '-dname', 'CN=Synthetic Test Only', '-keyalg', 'RSA', '-keysize', '2048',
                            '-validity', '1'], check=True, capture_output=True)
            env = dict(os.environ, PRODUCTION_DEBUG_KEYSTORE_BASE64=base64.b64encode((root/'synthetic.jks').read_bytes()).decode(),
                       PRODUCTION_DEBUG_SHA1=preparation.INSTALLED_PRODUCT_SHA1)
            result = subprocess.run([sys.executable, str(SCRIPTS/'prepare-product-signing.py')],
                                    env=env, cwd=root, capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertFalse((root/'debug.keystore').exists())
            self.assertNotIn(env['PRODUCTION_DEBUG_KEYSTORE_BASE64'], result.stdout+result.stderr)
