"""Derive public fingerprints from PEM certificates emitted after apksigner verification."""
import base64
import binascii
import hashlib
import re

PRODUCT_PACKAGE = "com.aistudio.steuerassistent.vnhqkp"
INSTALLED_PRODUCT_SHA1 = "cf438d2ed21d0fd5f8b0ab81bf518a5178a77385"


def require_product_update_identity(report):
    """A verified APK, not merely the input keystore, must match installed version 103."""
    if report.get("package") != PRODUCT_PACKAGE or report.get("signatureVerified") is not True:
        raise ValueError("Unverified or wrong product APK identity")
    actual = report.get("signingSha1")
    if not isinstance(actual, str) or actual.lower() != INSTALLED_PRODUCT_SHA1:
        raise ValueError("APK cannot update installed product version 103: signing certificate differs")


def certificate_fingerprints(output):
    certificates = set()
    for value in re.findall(r"-----BEGIN CERTIFICATE-----([^-]+)-----END CERTIFICATE-----", output):
        try:
            certificate = base64.b64decode(re.sub(r"\s+", "", value), validate=True)
        except (ValueError, binascii.Error) as error:
            raise ValueError("Malformed public signing certificate") from error
        if not certificate:
            raise ValueError("Empty public signing certificate")
        certificates.add(certificate)
    if len(certificates) != 1:
        raise ValueError("Expected one unambiguous verified public signing certificate; found " + str(len(certificates)))
    certificate = certificates.pop()
    # SHA1 is an Android OAuth certificate identifier, not the APK signature algorithm.
    return dict(signingSha1=hashlib.sha1(certificate, usedforsecurity=False).hexdigest(),
                signingSha256=hashlib.sha256(certificate).hexdigest())
