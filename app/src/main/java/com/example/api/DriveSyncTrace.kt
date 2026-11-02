package com.example.api

import kotlinx.coroutines.Job
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.ForwardingSource
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject

/** Diagnostics describe operations, never accounts, tokens, URLs, filenames or document contents. */
enum class DriveSyncPhase(val label: String) {
    INITIALIZATION("Drive-Initialisierung"), INVENTORY("Bestandsprüfung"), LEDGER("Hauptbuch-CSV"),
    AUTH_RENEWAL("Google-Zugang nach Unterbrechung prüfen"),
    FOLDERS("Immobilien- und Dokumentordner"), UNITS("Wohneinheiten-CSV"), MASTER_DATA("Stammdaten"),
    PROPERTY_METADATA("Immobilien-Stammdaten"), UNIT_DATA("Wohneinheiten-JSON"), DATEV_PROFILES("DATEV-Profile"),
    LEARNING_RULES("KI-Lernregeln"), CATEGORIES("Kategorien"), CATEGORY_MAPPINGS("Ordnerzuordnungen"),
    RECEIPTS("Belege"), ORIGINAL("Beleg-Originaldatei"), METADATA("Beleg-Metadaten"),
    RECEIPT_INDEX("Belegindex"), DOCUMENTS("Eigenständige Dokumente"),
    DOCUMENT_INDEX("Dokumentindex"), SUPPLEMENTAL("Zusatzdatensicherung"), COMPLETION("Abschlussprüfung")
}
enum class DriveSyncOutcome(val label: String) {
    RUNNING("Läuft"), SUCCESS("Erfolgreich"), UNCHANGED("Unverändert"), FAILED("Fehlgeschlagen"),
    BLOCKED("Durch Sicherheitsprüfung blockiert"), NOT_ATTEMPTED("Nicht versucht"), CANCELLED("Abgebrochen")
}
enum class DriveReceiptSyncOutcome { UPLOADED, UNCHANGED, METADATA_ONLY, FAILED, BLOCKED, NOT_ATTEMPTED, INTERRUPTED }

data class DriveDocumentSyncCounts(val total: Int = 0, val processed: Int = 0, val uploaded: Int = 0,
    val unchanged: Int = 0, val metadataOnly: Int = 0, val failed: Int = 0, val blocked: Int = 0, val interrupted: Int = 0) {
    val notAttempted: Int get() = (total - processed).coerceAtLeast(0)
}

/** Stable diagnostic codes, never document values, paths, filenames or credentials. */
enum class DriveSyncCheck(val label: String) {
    LOCAL_PRECONDITION("Lokaler Prüfnachweis widersprüchlich; Schreiben gesperrt"),
    RECEIPT_REVISION_MISMATCH("Lokaler Beleg und Drive-Metadaten unterscheiden sich; gemeinsamer Revisionsstand fehlt"),
    ORIGINAL_BASELINE_MISSING("Original ohne Vergleichs-Hash und ohne unabhängige lokale Kopie; historischer Inhalt nicht nachweisbar. Keine Datei verändert"),
    ORIGINAL_BASELINE_CONTENT_MISMATCH("Lokales Original und Drive-Inhalt widersprechen sich; fehlende Baseline wird nicht ersetzt"),
    ORIGINAL_BASELINE_REFERENCE_MISMATCH("Gespeicherte Originalreferenzen oder gemeinsamer Belegstand passen nicht; Baseline-Aufbau gesperrt"),
    ORIGINAL_BASELINE_CHANGED_DURING_READ("Original wurde während des Baseline-Nachweises geändert; erneut sicher prüfen"),
    ORIGINAL_CHECKSUM_MISMATCH("Original-Hash passt nicht zum gespeicherten Vergleichsstand"),
    ORIGINAL_SIZE_INVALID("Für das Original wurde keine gültige Dateigröße nachgewiesen"),
    INDEX_BINDING_MISMATCH("Lokaler Beleg und Drive-Index haben unterschiedliche Dateibindungen"),
    INDEX_DELETED_LOCAL_ACTIVE("Drive-Index kennzeichnet einen lokal aktiven Beleg als gelöscht"),
    INDEX_RECEIPT_NOT_LOCAL("Ein aktiver Indexbeleg fehlt im lokalen Bestand"),
    JSON_ENCODING_INVALID("JSON-Datei hat keine gültige Zeichenkodierung; Lesen gesperrt"),
    JSON_CHECKSUM_MISMATCH("JSON-Datei und gelesener Prüfnachweis haben unterschiedliche Hashes"),
    JSON_SIZE_MISMATCH("JSON-Datei und gelesener Prüfnachweis haben unterschiedliche Byte-Größen"),
    JSON_VERSION_MISSING("Für die JSON-Datei fehlt ein erforderlicher Versionsnachweis"),
    JSON_VERSION_MISMATCH("Versionsnachweise vor und nach dem JSON-Lesen stimmen nicht überein"),
    BACKUP_PREIMAGE_CONFLICT("Sicherung würde einen unbekannten oder neueren Datenstand verändern"),
    LOCAL_RECEIPT_CHANGED("Lokaler Beleg wurde während des Abgleichs geändert oder entfernt")
}

data class DriveSyncFailure(val kind: String, val httpStatus: Int? = null,
    val check: DriveSyncCheck? = null, val localRevision: Long? = null, val driveRevision: Long? = null,
    val differentFields: List<String> = emptyList(), val expectedBytes: Long? = null, val actualBytes: Long? = null) {
    val description: String get() = check?.label ?: when (kind) {
        "DNS" -> "DNS-Auflösung fehlgeschlagen"
        "TIMEOUT" -> "Zeitüberschreitung bei der Netzwerkübertragung"
        "NETWORK" -> "Netzwerkverbindung unterbrochen"
        "SAFETY" -> "Durch Sicherheitsprüfung blockiert"
        "CONFLICT" -> "Versionskonflikt; Überschreiben gesperrt"
        "PRECONDITION" -> "Sichere Versionsvorbedingung von Google Drive fehlt; Update gesperrt"
        "LOCAL_SOURCE" -> "Lokale Originaldatei nicht lesbar und kein unverändertes Drive-Original nachgewiesen"
        "CANCELLED" -> "Vom Nutzer oder durch Sitzungswechsel abgebrochen"
        "PROCESS_RESTART" -> "Beim App-Neustart unterbrochener Lauf; Messwerte zeigen den letzten gespeicherten Zwischenstand"
        "HTTP" -> when (httpStatus) {
            401 -> "Google-Zugangstoken abgelehnt"
            403 -> "Google Drive verweigert den Zugriff"
            404 -> "Datei oder Verweis in diesem Schritt nicht erreichbar"
            408 -> "Google Drive meldet eine Zeitüberschreitung"
            429 -> "Google Drive begrenzt die Anfragerate"
            in 500..599 -> "Google-Drive-Server vorübergehend nicht verfügbar"
            else -> "Google-Drive-Anfrage fehlgeschlagen"
        }
        else -> "Dieser Sicherungsschritt konnte nicht abgeschlossen werden"
    }
    companion object {
        internal fun from(error: Throwable): DriveSyncFailure {
            val causes = generateSequence(error) { it.cause }.take(8).toList()
            return when {
                causes.any { it is kotlinx.coroutines.CancellationException } -> DriveSyncFailure("CANCELLED")
                causes.any { it is java.net.UnknownHostException } -> DriveSyncFailure("DNS")
                causes.any { it is java.net.SocketTimeoutException } -> DriveSyncFailure("TIMEOUT")
                error is DriveSyncConflict -> DriveSyncFailure("CONFLICT", check = error.check,
                    localRevision = error.localRevision, driveRevision = error.driveRevision,
                    differentFields = error.differentFields, expectedBytes = error.expectedBytes, actualBytes = error.actualBytes)
                error is DriveAccessFailure -> when (error.reason) {
                    DriveAccessFailure.Reason.HTTP -> DriveSyncFailure(when (error.httpStatus) { 412 -> "CONFLICT"; 428 -> "PRECONDITION"; else -> "HTTP" },error.httpStatus)
                    DriveAccessFailure.Reason.TIMEOUT -> DriveSyncFailure("TIMEOUT")
                    DriveAccessFailure.Reason.NETWORK -> DriveSyncFailure("NETWORK")
                    else -> DriveSyncFailure("SAFETY")
                }
                error is java.io.IOException -> DriveSyncFailure("NETWORK")
                else -> DriveSyncFailure("OTHER")
            }
        }
    }
}

data class DriveSyncStep(
    val phase: DriveSyncPhase, val internalReceiptId: String? = null,
    val startMs: Long = 0, val endMs: Long? = null, val outcome: DriveSyncOutcome = DriveSyncOutcome.RUNNING,
    val calls: Int = 0, val listCalls: Int = 0, val getCalls: Int = 0, val createCalls: Int = 0, val updateCalls: Int = 0,
    val sentBytes: Long = 0, val receivedBytes: Long = 0, val retries: Int = 0, val failure: DriveSyncFailure? = null,
    val generateIdCalls: Int = 0
) {
    val durationMs: Long get() = ((endMs ?: startMs)-startMs).coerceAtLeast(0)
}

data class DriveSyncReport(
    val outcome: DriveSyncOutcome = DriveSyncOutcome.RUNNING, val elapsedMs: Long = 0,
    val startedAt: String = "", val steps: List<DriveSyncStep> = emptyList(),
    val receiptsTotal: Int = 0, val receiptsProcessed: Int = 0,
    val uploaded: Int = 0, val unchanged: Int = 0, val metadataOnly: Int = 0, val failed: Int = 0, val blocked: Int = 0,
    val apiCalls: Int = 0, val sentBytes: Long = 0, val receivedBytes: Long = 0,
    val lastSuccessfulPhase: DriveSyncPhase? = null,
    val documents: DriveDocumentSyncCounts = DriveDocumentSyncCounts(), val interrupted: Int = 0,
    val appVersionCode: Int? = null
) {
    val currentPhase: DriveSyncPhase? get() = steps.lastOrNull { it.outcome==DriveSyncOutcome.RUNNING }?.phase
    val notAttempted: Int get() = (receiptsTotal-receiptsProcessed).coerceAtLeast(0)
    val isCheckpoint: Boolean get() = steps.any { it.failure?.kind == "PROCESS_RESTART" }
    fun germanText(): String = buildString {
        appendLine("ImmoPilot – Google-Drive-Synchronisierung")
        appendLine("App-Versionscode beim Lauf: ${appVersionCode ?: "im alten Bericht nicht erfasst"}")
        appendLine("Ergebnis: ${outcome.label}")
        appendLine("Beginn: $startedAt")
        if (isCheckpoint) appendLine("Zwischenstand vor App-Neustart: Alle Zähler beziehen sich auf den letzten Speicherpunkt; spätere Vorgänge sind unbekannt.")
        appendLine("Dauer: ${String.format(java.util.Locale.GERMANY,"%.1f",elapsedMs/1000.0)} Sekunden")
        val failures=steps.filter { it.outcome in setOf(DriveSyncOutcome.FAILED,DriveSyncOutcome.BLOCKED) }
        if(failures.isNotEmpty()) appendLine("Fehlgeschlagen bei: ${failures.map { it.phase.label }.distinct().joinToString()}")
        val primary = failures.firstOrNull { it.internalReceiptId != null && it.failure?.check != null }
        if (primary != null) {
            appendLine("Ursprünglicher Sicherheitskonflikt: ${primary.phase.label}; interne Beleg-ID=${primary.internalReceiptId}; Prüfcode=${primary.failure?.check?.name}")
            val dependent = steps.count { it.phase == primary.phase && it.internalReceiptId != null && it.outcome == DriveSyncOutcome.CANCELLED }
            appendLine("Dadurch abgebrochene parallele Prüfungen: $dependent (kein zusätzlicher Originalkonflikt)")
        }
        appendLine("Belege: $uploaded übertragen · $unchanged unverändert · $metadataOnly nur Metadaten · $failed fehlgeschlagen · $blocked gesperrt · $notAttempted nicht versucht")
        appendLine("Belegabgleich bearbeitet: $receiptsProcessed von $receiptsTotal")
        appendLine("Unterbrochene Belege: $interrupted")
        appendLine("Dokumente: ${documents.uploaded} übertragen · ${documents.unchanged} unverändert · ${documents.metadataOnly} nur Metadaten · ${documents.failed} fehlgeschlagen · ${documents.blocked} gesperrt · ${documents.interrupted} unterbrochen · ${documents.notAttempted} nicht versucht (${documents.processed}/${documents.total})")
        appendLine("Letzte erfolgreiche Phase: ${lastSuccessfulPhase?.label ?: "Keine"}")
        appendLine("API-Aufrufe: $apiCalls; HTTP-Nutzdaten tatsächlich geschrieben/gelesen: $sentBytes/$receivedBytes Bytes (ohne Protokolloverhead)")
        steps.forEach { step ->
            append("${step.phase.label}: ${step.outcome.label}; Start=${step.startMs} ms; Ende=${step.endMs ?: "offen"} ms; ${step.durationMs} ms; API=${step.calls} (list=${step.listCalls}, get=${step.getCalls}, create=${step.createCalls}, update=${step.updateCalls}, generateIds=${step.generateIdCalls}); Bytes=${step.sentBytes}/${step.receivedBytes}; Retries=${step.retries}")
            step.internalReceiptId?.let { append("; interne Beleg-ID=$it") }
            step.failure?.let {
                append("; ${it.description}; Klasse=${it.kind}${it.httpStatus?.let { status -> "; HTTP=$status" }.orEmpty()}")
                it.check?.let { check ->
                    append("; Quelle=lokale Sicherheitsprüfung; Prüfcode=${check.name}")
                    if (check == DriveSyncCheck.RECEIPT_REVISION_MISMATCH)
                        append("; lokale Revision=${it.localRevision ?: "unbekannt"}; Drive-Revision=${it.driveRevision ?: "unbekannt"}")
                }
                if (it.differentFields.isNotEmpty()) append("; abweichende Feldnamen=${it.differentFields.joinToString(",")}")
                it.expectedBytes?.let { size -> append("; nachgewiesene Bytes=$size") }
                it.actualBytes?.let { size -> append("; gelesene Bytes=$size") }
            }
            appendLine()
        }
    }
    fun toJson(): String = JSONObject().apply {
        put("outcome",outcome.name);put("elapsedMs",elapsedMs);put("startedAt",startedAt)
        put("appVersionCode",appVersionCode ?: JSONObject.NULL)
        put("receiptsTotal",receiptsTotal);put("receiptsProcessed",receiptsProcessed)
        put("uploaded",uploaded);put("unchanged",unchanged);put("metadataOnly",metadataOnly);put("failed",failed);put("blocked",blocked)
        put("apiCalls",apiCalls);put("sentBytes",sentBytes);put("receivedBytes",receivedBytes)
        put("lastSuccessfulPhase",lastSuccessfulPhase?.name ?: JSONObject.NULL)
        put("interrupted", interrupted)
        put("documents", JSONObject().put("total", documents.total).put("processed", documents.processed)
            .put("uploaded", documents.uploaded).put("unchanged", documents.unchanged).put("metadataOnly", documents.metadataOnly)
            .put("failed", documents.failed).put("blocked", documents.blocked).put("interrupted", documents.interrupted))
        put("steps",JSONArray().apply { steps.forEach { s -> put(JSONObject().apply {
            put("phase",s.phase.name);put("internalReceiptId",s.internalReceiptId ?: JSONObject.NULL)
            put("startMs",s.startMs);put("endMs",s.endMs ?: JSONObject.NULL);put("outcome",s.outcome.name)
            put("calls",s.calls);put("list",s.listCalls);put("get",s.getCalls);put("create",s.createCalls);put("update",s.updateCalls);put("generateIds",s.generateIdCalls)
            put("sent",s.sentBytes);put("received",s.receivedBytes);put("retries",s.retries)
            put("failure",s.failure?.kind ?: JSONObject.NULL);put("http",s.failure?.httpStatus ?: JSONObject.NULL)
            put("check",s.failure?.check?.name ?: JSONObject.NULL)
            put("localRevision",s.failure?.localRevision ?: JSONObject.NULL);put("driveRevision",s.failure?.driveRevision ?: JSONObject.NULL)
            put("differentFields",JSONArray(s.failure?.differentFields.orEmpty()))
            put("expectedBytes",s.failure?.expectedBytes ?: JSONObject.NULL);put("actualBytes",s.failure?.actualBytes ?: JSONObject.NULL)
        }) } })
    }.toString()
    companion object {
        fun fromJson(raw: String?): DriveSyncReport? = try {
            if(raw.isNullOrBlank()) null else {
                val o=JSONObject(raw);val a=o.getJSONArray("steps")
                val steps=(0 until a.length()).map { i -> val s=a.getJSONObject(i)
                    DriveSyncStep(DriveSyncPhase.valueOf(s.getString("phase")),
                        s.optString("internalReceiptId").takeIf { !s.isNull("internalReceiptId") && it.matches(Regex("[A-Za-z0-9_-]{1,128}")) },
                        s.getLong("startMs"),if(s.isNull("endMs"))null else s.getLong("endMs"),DriveSyncOutcome.valueOf(s.getString("outcome")),
                        s.optInt("calls"),s.optInt("list"),s.optInt("get"),s.optInt("create"),s.optInt("update"),s.optLong("sent"),s.optLong("received"),s.optInt("retries"),
                        if(s.isNull("failure"))null else DriveSyncFailure(s.getString("failure"),if(s.isNull("http"))null else s.getInt("http"),
                            s.optString("check").takeIf { it.isNotBlank() && it != "null" }?.let { name -> runCatching { DriveSyncCheck.valueOf(name) }.getOrNull() },
                            if(s.isNull("localRevision"))null else s.getLong("localRevision"), if(s.isNull("driveRevision"))null else s.getLong("driveRevision"),
                            s.optJSONArray("differentFields")?.let { fields -> (0 until fields.length()).map { fields.getString(it) }
                                .filter { it.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}")) }.distinct().sorted().take(64) }.orEmpty(),
                            if(s.isNull("expectedBytes"))null else s.getLong("expectedBytes"),if(s.isNull("actualBytes"))null else s.getLong("actualBytes")), s.optInt("generateIds")) }
                val d = o.optJSONObject("documents") ?: JSONObject()
                DriveSyncReport(DriveSyncOutcome.valueOf(o.getString("outcome")),o.getLong("elapsedMs"),o.getString("startedAt"),steps,
                    o.optInt("receiptsTotal"),o.optInt("receiptsProcessed"),o.optInt("uploaded"),o.optInt("unchanged"),o.optInt("metadataOnly"),o.optInt("failed"),o.optInt("blocked"),
                    o.optInt("apiCalls"),o.optLong("sentBytes"),o.optLong("receivedBytes"),o.optString("lastSuccessfulPhase").takeIf { it!="null" && it.isNotBlank() }?.let(DriveSyncPhase::valueOf),
                    DriveDocumentSyncCounts(d.optInt("total"), d.optInt("processed"), d.optInt("uploaded"), d.optInt("unchanged"),
                        d.optInt("metadataOnly"), d.optInt("failed"), d.optInt("blocked"), d.optInt("interrupted")), o.optInt("interrupted"),
                    if (o.isNull("appVersionCode")) null else o.getInt("appVersionCode"))
            }
        } catch (_: org.json.JSONException) { null } catch (_: IllegalArgumentException) { null }
    }
}

internal class DriveSyncRecorder(
    private val onUpdate: (DriveSyncReport)->Unit,
    private val nanos: ()->Long = System::nanoTime
) {
    private val originalWrites=hashSetOf<String>()
    @Synchronized fun originalWritten(id: String) { originalWrites += id }
    @Synchronized fun wasOriginalWritten(id: String): Boolean = id in originalWrites
    private val start=nanos()
    private var report=DriveSyncReport(startedAt=java.time.Instant.now().toString(), appVersionCode=com.example.BuildConfig.VERSION_CODE)
    private fun elapsed()=((nanos()-start)/1_000_000).coerceAtLeast(0)
    @Synchronized fun snapshot(): DriveSyncReport = report.copy(elapsedMs=elapsed())
    private fun publish() { report=report.copy(elapsedMs=elapsed());onUpdate(report) }
    @Synchronized fun tick() { publish() }
    @Synchronized fun begin(phase: DriveSyncPhase,id: String?): Int {
        val key=report.steps.size
        report=report.copy(steps=report.steps+DriveSyncStep(phase,id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) },elapsed()))
        publish();return key
    }
    @Synchronized fun end(key: Int,result: DriveSyncOutcome,error: DriveSyncFailure?=null) {
        val steps=report.steps.toMutableList();val old=steps[key]
        val finalResult = if (result == DriveSyncOutcome.SUCCESS && old.createCalls == 0 && old.updateCalls == 0 &&
            old.phase !in setOf(DriveSyncPhase.INITIALIZATION, DriveSyncPhase.INVENTORY, DriveSyncPhase.AUTH_RENEWAL, DriveSyncPhase.COMPLETION)) DriveSyncOutcome.UNCHANGED else result
        val failure = if (error?.kind in setOf("OTHER", "NETWORK") && old.failure != null) old.failure else error ?: old.failure
        val classified = if (finalResult == DriveSyncOutcome.FAILED && failure?.kind in setOf("SAFETY", "CONFLICT", "PRECONDITION"))
            DriveSyncOutcome.BLOCKED else finalResult
        steps[key]=old.copy(endMs=elapsed(),outcome=classified,failure=failure)
        report=report.copy(steps=steps,lastSuccessfulPhase=if(result in setOf(DriveSyncOutcome.SUCCESS,DriveSyncOutcome.UNCHANGED))old.phase else report.lastSuccessfulPhase)
        publish()
    }
    @Synchronized fun http(keys: List<Int>,request: Request) {
        val list=request.method=="GET" && request.url.encodedPath.endsWith("/files")
        val generateIds=request.method=="GET" && request.url.encodedPath.endsWith("/files/generateIds")
        val steps=report.steps.toMutableList()
        keys.forEach { k -> val s=steps[k];steps[k]=s.copy(calls=s.calls+1,listCalls=s.listCalls+if(list)1 else 0,
            getCalls=s.getCalls+if(request.method=="GET" && !list && !generateIds)1 else 0,
            generateIdCalls=s.generateIdCalls+if(generateIds)1 else 0,createCalls=s.createCalls+if(request.method=="POST")1 else 0,
            updateCalls=s.updateCalls+if(request.method=="PATCH" || request.method=="PUT")1 else 0) }
        report=report.copy(apiCalls=report.apiCalls+1,steps=steps)
    }
    @Synchronized fun bytes(keys: List<Int>,sent: Long=0,received: Long=0) {
        val steps=report.steps.toMutableList();keys.forEach { k -> val s=steps[k];steps[k]=s.copy(sentBytes=s.sentBytes+sent,receivedBytes=s.receivedBytes+received) }
        report=report.copy(steps=steps,sentBytes=report.sentBytes+sent,receivedBytes=report.receivedBytes+received)
    }
    @Synchronized fun fault(keys: List<Int>,failure: DriveSyncFailure?) {
        val steps=report.steps.toMutableList();keys.forEach { k -> steps[k]=steps[k].copy(failure=failure) };report=report.copy(steps=steps)
    }
    @Synchronized fun retry(keys: List<Int>) {
        val steps=report.steps.toMutableList();keys.forEach { k -> steps[k]=steps[k].copy(retries=steps[k].retries+1) };report=report.copy(steps=steps);publish()
    }
    @Synchronized fun total(count: Int) { report=report.copy(receiptsTotal=count);publish() }
    @Synchronized fun documentsTotal(count: Int) { report=report.copy(documents=report.documents.copy(total=count));publish() }
    @Synchronized fun document(outcome: DriveReceiptSyncOutcome) {
        if (outcome == DriveReceiptSyncOutcome.NOT_ATTEMPTED) return
        val d = report.documents
        report = report.copy(documents = d.copy(processed = d.processed + 1,
            uploaded = d.uploaded + if (outcome == DriveReceiptSyncOutcome.UPLOADED) 1 else 0,
            unchanged = d.unchanged + if (outcome == DriveReceiptSyncOutcome.UNCHANGED) 1 else 0,
            metadataOnly = d.metadataOnly + if (outcome == DriveReceiptSyncOutcome.METADATA_ONLY) 1 else 0,
            failed = d.failed + if (outcome == DriveReceiptSyncOutcome.FAILED) 1 else 0,
            blocked = d.blocked + if (outcome == DriveReceiptSyncOutcome.BLOCKED) 1 else 0,
            interrupted = d.interrupted + if (outcome == DriveReceiptSyncOutcome.INTERRUPTED) 1 else 0))
        publish()
    }
    @Synchronized fun receipt(outcome: DriveReceiptSyncOutcome) {
        if (outcome == DriveReceiptSyncOutcome.NOT_ATTEMPTED) return
        report=report.copy(receiptsProcessed=report.receiptsProcessed+1,
            uploaded=report.uploaded+if(outcome==DriveReceiptSyncOutcome.UPLOADED)1 else 0,
            unchanged=report.unchanged+if(outcome==DriveReceiptSyncOutcome.UNCHANGED)1 else 0,
            metadataOnly=report.metadataOnly+if(outcome==DriveReceiptSyncOutcome.METADATA_ONLY)1 else 0,
            failed=report.failed+if(outcome==DriveReceiptSyncOutcome.FAILED)1 else 0,
            blocked=report.blocked+if(outcome==DriveReceiptSyncOutcome.BLOCKED)1 else 0,
            interrupted=report.interrupted+if(outcome==DriveReceiptSyncOutcome.INTERRUPTED)1 else 0);publish()
    }
    @Synchronized fun finish(outcome: DriveSyncOutcome) { report=report.copy(outcome=outcome);publish() }
}

internal object DriveSyncTrace {
    private data class Context(val recorder: DriveSyncRecorder,val keys: List<Int>,val job: Job?,val receiptId: String? = null)
    private val context=ThreadLocal<Context?>()
    suspend fun <T> run(recorder: DriveSyncRecorder,block: suspend ()->T): T =
        withContext(context.asContextElement(Context(recorder,emptyList(),currentCoroutineContext()[Job]))) { block() }
    suspend fun <T> inCurrentJob(block: suspend () -> T): T {
        val parent = context.get() ?: return block()
        return withContext(context.asContextElement(parent.copy(job = currentCoroutineContext()[Job]))) { block() }
    }
    suspend fun <T> step(phase: DriveSyncPhase,id: String?=null,outcome: (T)->DriveSyncOutcome={DriveSyncOutcome.SUCCESS},block: suspend ()->T): T {
        val parent=context.get() ?: return block()
        val key=parent.recorder.begin(phase,id)
        return withContext(context.asContextElement(parent.copy(keys=parent.keys+key, job=currentCoroutineContext()[Job], receiptId=id ?: parent.receiptId))) {
            try { val result=block();currentCoroutineContext().ensureActive();parent.recorder.end(key,outcome(result));result }
            catch(e: Throwable) { val failure=DriveSyncFailure.from(e)
                parent.recorder.end(key,if(failure.kind=="CANCELLED")DriveSyncOutcome.CANCELLED else if(failure.kind in setOf("SAFETY", "CONFLICT"))DriveSyncOutcome.BLOCKED else DriveSyncOutcome.FAILED,failure);throw e }
        }
    }
    fun originalTransferred() { context.get()?.let { c -> c.receiptId?.let(c.recorder::originalWritten) } }
    fun currentFailure(): DriveSyncFailure? = context.get()?.let { c ->
        c.keys.lastOrNull()?.let { key -> c.recorder.snapshot().steps[key].failure }
    }
    fun failure(error: Throwable) { context.get()?.let { it.recorder.fault(it.keys, DriveSyncFailure.from(error)) } }
    fun <T> stepBlocking(phase: DriveSyncPhase, outcome: (T)->DriveSyncOutcome, block: ()->T): T {
        val parent=context.get() ?: return block()
        val key=parent.recorder.begin(phase,parent.receiptId)
        context.set(parent.copy(keys=parent.keys+key))
        return try { val result=block();parent.recorder.end(key,outcome(result));result }
        catch(e: Throwable) { val f=DriveSyncFailure.from(e);parent.recorder.end(key,
            if(f.kind=="CANCELLED")DriveSyncOutcome.CANCELLED else if(f.kind=="SAFETY")DriveSyncOutcome.BLOCKED else DriveSyncOutcome.FAILED,f);throw e }
        finally { context.set(parent) }
    }
    fun retry() { context.get()?.let { it.recorder.retry(it.keys) } }
    fun ensureActive() { context.get()?.job?.ensureActive() }
    fun raw(request: Request,send: (Request)->Response): Response {
        val c=context.get() ?: return send(request)
        c.job?.ensureActive();c.recorder.http(c.keys,request)
        val body=request.body
        val counted=if(body==null)request else request.newBuilder().method(request.method,object: RequestBody() {
            override fun contentType()=body.contentType()
            override fun contentLength()=body.contentLength()
            override fun isOneShot()=body.isOneShot()
            override fun writeTo(sink: BufferedSink) {
                val counter=object: ForwardingSink(sink) {
                    override fun write(source: Buffer,byteCount: Long) { super.write(source,byteCount);c.recorder.bytes(c.keys,sent=byteCount) }
                }.buffer()
                body.writeTo(counter);counter.flush()
            }
        }).build()
        try {
            val response=send(counted)
            c.recorder.fault(c.keys,if(response.isSuccessful)null else DriveSyncFailure("HTTP",response.code))
            val responseBody=response.body ?: return response
            val source=object: ForwardingSource(responseBody.source()) {
                override fun read(sink: Buffer,byteCount: Long): Long = try {
                    super.read(sink,byteCount).also { if(it>0)c.recorder.bytes(c.keys,received=it) }
                } catch(e: java.io.IOException) { c.recorder.fault(c.keys,DriveSyncFailure.from(e));throw e }
            }.buffer()
            return response.newBuilder().body(object: ResponseBody() {
                override fun contentType()=responseBody.contentType()
                override fun contentLength()=responseBody.contentLength()
                override fun source()=source
            }).build()
        } catch(e: Exception) { c.recorder.fault(c.keys,DriveSyncFailure.from(e));throw e }
    }
}
