import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("apk_signing", Path(__file__).resolve().parents[1] / "apk_signing.py")
parser = importlib.util.module_from_spec(spec)
spec.loader.exec_module(parser)


class SigningCertificateTests(unittest.TestCase):
    def output(self, prefix="Signer #1", sha1="a" * 40, sha256="b" * 64):
        return f"{prefix} certificate SHA-1 digest: {sha1}\n{prefix} certificate SHA-256 digest: {sha256}\n"

    def test_classic_verified_signer_and_source_stamp_are_distinguished(self):
        text = self.output() + self.output("Source Stamp Signer", "c" * 40, "d" * 64)
        self.assertEqual(dict(signingSha1="a" * 40, signingSha256="b" * 64), parser.certificate_fingerprints(text))

    def test_same_verified_certificate_in_multiple_sdk_ranges_is_supported(self):
        text = self.output("Signer (minSdkVersion=24, maxSdkVersion=32)")
        text += self.output("Signer (minSdkVersion=33, maxSdkVersion=2147483647)", "A" * 40, "B" * 64)
        self.assertEqual(dict(signingSha1="a" * 40, signingSha256="b" * 64), parser.certificate_fingerprints(text))

    def test_missing_malformed_or_conflicting_certificates_fail_closed(self):
        for text in ("", self.output(sha1="a" * 39), self.output() + self.output("Signer #2", "c" * 40)):
            with self.subTest(text=text), self.assertRaises(ValueError):
                parser.certificate_fingerprints(text)


if __name__ == "__main__":
    unittest.main()
