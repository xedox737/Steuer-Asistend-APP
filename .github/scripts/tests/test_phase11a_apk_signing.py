import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("apk_signing", Path(__file__).resolve().parents[1] / "apk_signing.py")
parser = importlib.util.module_from_spec(spec)
spec.loader.exec_module(parser)


class SigningCertificateTests(unittest.TestCase):
    # Public certificate only, exported from a verified local Android-debug APK.
    # Expected fingerprints were independently printed by build-tools 36 apksigner.
    pem = (Path(__file__).parent / "fixtures/android-debug-public-cert.pem").read_text()
    expected = dict(signingSha1="7b30129dc53f6b2adb4c188b03bd2b39feb52e19",
                    signingSha256="ee446593dea9492fa2eaa7fd9602206eb22a38e107de2d3a4e2ecfcc92b4005d")

    def test_exact_installed_product_certificate_is_required_for_updates(self):
        parser.require_product_update_identity(dict(package=parser.PRODUCT_PACKAGE,
            signatureVerified=True, signingSha1=parser.INSTALLED_PRODUCT_SHA1.upper()))

    def test_ci_test_other_and_unverified_apks_cannot_be_product_updates(self):
        valid = dict(package=parser.PRODUCT_PACKAGE, signatureVerified=True, signingSha1=parser.INSTALLED_PRODUCT_SHA1)
        for change in [dict(package=parser.PRODUCT_PACKAGE+".phase11a"), dict(signatureVerified=False),
                       dict(signatureVerified="true"), dict(signingSha1=self.expected["signingSha1"]),
                       dict(signingSha1=None), dict(signingSha1=""), dict(signingSha1=" " + parser.INSTALLED_PRODUCT_SHA1)]:
            with self.subTest(change=change), self.assertRaises(ValueError):
                parser.require_product_update_identity(valid | change)

    def test_verified_certificate_is_independent_of_human_digest_labels(self):
        self.assertEqual(self.expected, parser.certificate_fingerprints("New SDK signer heading, no SHA1 text\n" + self.pem))
        self.assertEqual(self.expected, parser.certificate_fingerprints("Signer #1 certificate SHA-1 digest: ignored-label\n" + self.pem))

    def test_same_verified_certificate_in_multiple_sdk_ranges_is_supported(self):
        text = "Signer (minSdkVersion=24, maxSdkVersion=32)\n" + self.pem
        text += "Signer (minSdkVersion=33, maxSdkVersion=2147483647)\n" + self.pem
        self.assertEqual(self.expected, parser.certificate_fingerprints(text))

    def test_missing_malformed_or_ambiguous_certificates_fail_closed(self):
        other = "-----BEGIN CERTIFICATE-----\nb3RoZXItY2VydGlmaWNhdGU=\n-----END CERTIFICATE-----\n"
        for text in ("", self.pem.replace("MI", "@!", 1), self.pem + other):
            with self.subTest(text=text), self.assertRaises(ValueError):
                parser.certificate_fingerprints(text)


if __name__ == "__main__":
    unittest.main()
