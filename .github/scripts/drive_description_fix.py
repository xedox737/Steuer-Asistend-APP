from pathlib import Path

PATH = Path("app/src/main/java/com/example/data/DrivePersistenceRepository.kt")
s = PATH.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global s
    if new in s:
        return
    if old not in s:
        raise SystemExit(f"{label}: anchor not found")
    s = s.replace(old, new, 1)


# PersistedReceipt: description is optional for backward compatibility with old Drive backups.
replace_once(
    '''    val aussteller: String?,
    val rechnungsnummer: String?,
    val datum: String?,''',
    '''    val aussteller: String?,
    val rechnungsnummer: String?,
    val beschreibung: String? = null,
    val datum: String?,''',
    "PersistedReceipt description field",
)

# JSON serialization.
replace_once(
    '''            put("aussteller", aussteller ?: JSONObject.NULL)
            put("rechnungsnummer", rechnungsnummer ?: JSONObject.NULL)
            put("datum", datum ?: JSONObject.NULL)''',
    '''            put("aussteller", aussteller ?: JSONObject.NULL)
            put("rechnungsnummer", rechnungsnummer ?: JSONObject.NULL)
            put("beschreibung", beschreibung ?: JSONObject.NULL)
            put("datum", datum ?: JSONObject.NULL)''',
    "description JSON write",
)

# JSON parsing; optString + has/isNull keeps old metadata compatible.
replace_once(
    '''            aussteller = if (json.isNull("aussteller")) null else json.getString("aussteller"),
            rechnungsnummer = if (json.isNull("rechnungsnummer")) null else json.getString("rechnungsnummer"),
            datum = if (json.isNull("datum")) null else json.getString("datum"),''',
    '''            aussteller = if (json.isNull("aussteller")) null else json.getString("aussteller"),
            rechnungsnummer = if (json.isNull("rechnungsnummer")) null else json.getString("rechnungsnummer"),
            beschreibung = if (!json.has("beschreibung") || json.isNull("beschreibung")) null else json.optString("beschreibung"),
            datum = if (json.isNull("datum")) null else json.getString("datum"),''',
    "description JSON parse",
)

# Restore must never fabricate the description from vendor/address.
replace_once(
    '            beschreibung = aussteller ?: "",',
    '            beschreibung = beschreibung ?: "",',
    "restore description mapping",
)

# Add the local description to every currentReceipt -> PersistedReceipt construction path.
old_ctor = '''                aussteller = currentReceipt.aussteller,
                rechnungsnummer = "",
                datum = currentReceipt.datum,'''
new_ctor = '''                aussteller = currentReceipt.aussteller,
                rechnungsnummer = "",
                beschreibung = currentReceipt.beschreibung,
                datum = currentReceipt.datum,'''
if new_ctor not in s:
    count = s.count(old_ctor)
    if count < 1:
        raise SystemExit("PersistedReceipt currentReceipt constructors: anchor not found")
    s = s.replace(old_ctor, new_ctor)
else:
    s = s.replace(old_ctor, new_ctor)

# Strengthen the existing Drive E2E test: description must survive metadata round-trip.
replace_once(
    '''            val internalIdMatch = downloadedPersisted.internalId == updatedLocal.internalId
            val displayIdMatch = downloadedPersisted.displayId == (updatedLocal.displayId ?: testDisplayId)
            val amountMatch = downloadedPersisted.bruttobetragCent == 11900L

            if (internalIdMatch && displayIdMatch && amountMatch) {''',
    '''            val internalIdMatch = downloadedPersisted.internalId == updatedLocal.internalId
            val displayIdMatch = downloadedPersisted.displayId == (updatedLocal.displayId ?: testDisplayId)
            val amountMatch = downloadedPersisted.bruttobetragCent == 11900L
            val descriptionMatch = downloadedPersisted.beschreibung == updatedLocal.beschreibung

            if (internalIdMatch && displayIdMatch && amountMatch && descriptionMatch) {''',
    "Drive E2E description assertion",
)

PATH.write_text(s, encoding="utf-8")
print("Drive description sync/restore fix applied.")
