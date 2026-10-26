"""Parse public apksigner fingerprints without assuming a specific signer label."""
import re


def certificate_fingerprints(output):
    result = {}
    for algorithm, length, key in (("SHA-?1", 40, "signingSha1"), ("SHA-?256", 64, "signingSha256")):
        # Newer build-tools label SDK-specific signers rather than using 'Signer #1'.
        # The same certificate can appear for multiple SDK ranges. A different
        # certificate or a missing digest must fail closed, not select one arbitrarily.
        values = set(value.lower() for value in re.findall(
            r"^\s*Signer\b[^\n]*?certificate\s+" + algorithm +
            r"\s+digest:\s*([0-9a-f]{" + str(length) + r"})\s*$",
            output, re.I | re.M))
        if len(values) != 1:
            raise ValueError("Expected exactly one verified signing certificate for " + key)
        result[key] = values.pop()
    return result
