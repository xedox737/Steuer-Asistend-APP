from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(text, old, new, label):
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"{label}: anchor not found")
    return text.replace(old, new, 1)


# 1) Google Drive: Zahlungsart vollständig persistieren
rel = "app/src/main/java/com/example/data/DrivePersistenceRepository.kt"
s = read(rel)

s = replace_once(
    s,
    '''    val zahlungsstatus: String?,
    val zahlungsdatum: String?,
    val zahlungsreferenz: String? = null,
    val notizSteuerberater: String? = null,''',
    '''    val zahlungsstatus: String?,
    val zahlungsdatum: String?,
    val zahlungsreferenz: String? = null,
    val zahlungsart: String = "Unbekannt",
    val zahlungsartQuelle: String = "UNBEKANNT",
    val zahlungsartConfidence: Double = 0.0,
    val notizSteuerberater: String? = null,''',
    "PersistedReceipt payment fields",
)

s = replace_once(
    s,
    '''            put("zahlungsstatus", zahlungsstatus ?: JSONObject.NULL)
            put("zahlungsdatum", zahlungsdatum ?: JSONObject.NULL)
            put("zahlungsreferenz", zahlungsreferenz ?: JSONObject.NULL)
            
            put("notizSteuerberater", notizSteuerberater ?: JSONObject.NULL)''',
    '''            put("zahlungsstatus", zahlungsstatus ?: JSONObject.NULL)
            put("zahlungsdatum", zahlungsdatum ?: JSONObject.NULL)
            put("zahlungsreferenz", zahlungsreferenz ?: JSONObject.NULL)
            put("zahlungsart", zahlungsart)
            put("zahlungsartQuelle", zahlungsartQuelle)
            put("zahlungsartConfidence", zahlungsartConfidence)
            
            put("notizSteuerberater", notizSteuerberater ?: JSONObject.NULL)''',
    "Drive payment JSON write",
)

s = replace_once(
    s,
    '''            zahlungsstatus = if (json.isNull("zahlungsstatus")) null else json.getString("zahlungsstatus"),
            zahlungsdatum = if (json.isNull("zahlungsdatum")) null else json.getString("zahlungsdatum"),
            zahlungsreferenz = if (json.isNull("zahlungsreferenz")) null else json.getString("zahlungsreferenz"),
            notizSteuerberater = if (json.isNull("notizSteuerberater")) null else json.optString("notizSteuerberater"),''',
    '''            zahlungsstatus = if (json.isNull("zahlungsstatus")) null else json.getString("zahlungsstatus"),
            zahlungsdatum = if (json.isNull("zahlungsdatum")) null else json.getString("zahlungsdatum"),
            zahlungsreferenz = if (json.isNull("zahlungsreferenz")) null else json.getString("zahlungsreferenz"),
            zahlungsart = json.optString("zahlungsart", "Unbekannt"),
            zahlungsartQuelle = json.optString("zahlungsartQuelle", "UNBEKANNT"),
            zahlungsartConfidence = json.optDouble("zahlungsartConfidence", 0.0),
            notizSteuerberater = if (json.isNull("notizSteuerberater")) null else json.optString("notizSteuerberater"),''',
    "Drive payment JSON parse",
)

s = replace_once(
    s,
    '''            wohneinheit = wohneinheit ?: "",
            mieter = "",
            isArchivedToDrive = true,''',
    '''            wohneinheit = wohneinheit ?: "",
            mieter = "",
            zahlungsart = zahlungsart,
            zahlungsartQuelle = zahlungsartQuelle,
            zahlungsartConfidence = zahlungsartConfidence,
            isArchivedToDrive = true,''',
    "Drive restore payment",
)

ctor_old = '''                zahlungsstatus = "BEZAHLT",
                zahlungsdatum = currentReceipt.datum,
                zahlungsreferenz = "",
                pruefstatus = currentReceipt.pruefstatus,'''
ctor_new = '''                zahlungsstatus = "BEZAHLT",
                zahlungsdatum = currentReceipt.datum,
                zahlungsreferenz = "",
                zahlungsart = currentReceipt.zahlungsart,
                zahlungsartQuelle = currentReceipt.zahlungsartQuelle,
                zahlungsartConfidence = currentReceipt.zahlungsartConfidence,
                pruefstatus = currentReceipt.pruefstatus,'''
if ctor_new not in s:
    if ctor_old not in s:
        raise SystemExit("Drive persisted receipt constructors: anchor not found")
    s = s.replace(ctor_old, ctor_new)

write(rel, s)


# 2) PDF-Nacherkennung für bestehende Belege
rel = "app/src/main/java/com/example/ui/ReceiptViewModel.kt"
s = read(rel)
if "import android.graphics.pdf.PdfRenderer" not in s:
    s = s.replace(
        "import android.graphics.BitmapFactory\n",
        "import android.graphics.BitmapFactory\nimport android.graphics.pdf.PdfRenderer\nimport android.os.ParcelFileDescriptor\n",
        1,
    )

old_decode = '''        val bitmaps = kotlinx.coroutines.withContext(Dispatchers.IO) {
            paths.mapNotNull { path -> runCatching {
                val file = File(path)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }.getOrNull() }
        }'''
new_decode = '''        val bitmaps = kotlinx.coroutines.withContext(Dispatchers.IO) {
            paths.flatMap { path ->
                runCatching {
                    val file = File(path)
                    if (!file.exists() || !file.isFile) return@runCatching emptyList<Bitmap>()
                    if (file.extension.equals("pdf", ignoreCase = true)) {
                        val rendered = mutableListOf<Bitmap>()
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                            PdfRenderer(descriptor).use { renderer ->
                                val pagesToRead = minOf(renderer.pageCount, 3)
                                for (index in 0 until pagesToRead) {
                                    renderer.openPage(index).use { page ->
                                        val scale = minOf(2.0f, 1600.0f / page.width.coerceAtLeast(1))
                                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        rendered.add(bitmap)
                                    }
                                }
                            }
                        }
                        rendered
                    } else {
                        listOfNotNull(BitmapFactory.decodeFile(file.absolutePath))
                    }
                }.getOrElse {
                    Log.w("ReceiptViewModel", "Original für Zahlungsart konnte nicht gerendert werden: $path", it)
                    emptyList()
                }
            }
        }'''
s = replace_once(s, old_decode, new_decode, "Existing receipt PDF/image decoder")

# Speichern nach Review = Nutzer bestätigt
s = s.replace(
    'zahlungsartQuelle = if (normalizePaymentMethod(zahlungsart) == "Unbekannt") "UNBEKANNT" else "KI_SCAN",',
    'zahlungsartQuelle = if (normalizePaymentMethod(zahlungsart) == "Unbekannt") "UNBEKANNT" else "NUTZER_BESTAETIGT",',
    1,
)
s = s.replace(
    'zahlungsartConfidence = if (normalizePaymentMethod(zahlungsart) == "Unbekannt") 0.0 else 0.95,',
    'zahlungsartConfidence = if (normalizePaymentMethod(zahlungsart) == "Unbekannt") 0.0 else 1.0,',
    1,
)
write(rel, s)


# 3) Manuelle Änderung sauber kennzeichnen
rel = "app/src/main/java/com/example/ui/ReceiptAppUi.kt"
s = read(rel)
old_ui = '''                                mieter = editMieter,
                                zahlungsart = editZahlungsart,
                                positionenJson ='''
new_ui = '''                                mieter = editMieter,
                                zahlungsart = editZahlungsart,
                                zahlungsartQuelle = if (editZahlungsart.trim().equals(receipt.zahlungsart.trim(), ignoreCase = true)) receipt.zahlungsartQuelle else if (editZahlungsart.trim().equals("Unbekannt", ignoreCase = true) || editZahlungsart.isBlank()) "UNBEKANNT" else "MANUELL",
                                zahlungsartConfidence = if (editZahlungsart.trim().equals("Unbekannt", ignoreCase = true) || editZahlungsart.isBlank()) 0.0 else if (editZahlungsart.trim().equals(receipt.zahlungsart.trim(), ignoreCase = true)) receipt.zahlungsartConfidence else 1.0,
                                positionenJson ='''
s = replace_once(s, old_ui, new_ui, "Manual payment edit")
write(rel, s)

print("Payment method hardening patch applied.")
