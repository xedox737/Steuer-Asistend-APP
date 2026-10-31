package com.example.api

import com.example.util.DiagnosticLog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import com.example.data.Receipt
import com.example.ui.WohneinheitStatus
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock

/** Original IDs retained for recovery; unavailable files are not fetched or replaced. */
data class DriveRestoreJsonRead(val json: String, val unavailableOriginalIds: Set<String> = emptySet())

object GoogleDriveClient {
    private const val TAG = "GoogleDriveClient"
    // Private transport; QA replaces it reflectively with an in-memory HTTP interceptor.
    private var client = OkHttpClient()
    private fun executeDriveRequest(request: Request): okhttp3.Response {
        val response = DriveEnvironment.execute(request) { client.newCall(it).execute() }
        if (response.code == 401) {
            response.close()
            throw DriveAccessFailure(DriveAccessFailure.Reason.HTTP, 401)
        }
        return response
    }

    internal fun validateDataReferences(accessToken: String, json: String) =
        DriveEnvironment.validateJsonReferences(json, accessToken) { client.newCall(it).execute() }

    fun validateStorageConfig(accessToken: String, config: com.example.data.DriveAppConfig, rootId: String? = null, systemId: String? = null) =
        DriveEnvironment.validateConfig(config, accessToken, rootId, systemId) { client.newCall(it).execute() }

    private val folderCache = ConcurrentHashMap<String, String>()

    fun clearFolderCache() {
        folderCache.clear()
    }

    private val folderMutexMap = ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

    private fun getFolderMutex(key: String): kotlinx.coroutines.sync.Mutex {
        return folderMutexMap.getOrPut(key) { kotlinx.coroutines.sync.Mutex() }
    }

    private suspend fun saveCategoryMapping(
        accessToken: String,
        systemFolderId: String,
        canonicalPathKey: String,
        folderId: String,
        context: Context
    ) {
        try {
            // 1. Save locally in SharedPreferences
            val prefs = context.getSharedPreferences("category_folder_mappings_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString(canonicalPathKey, folderId).apply()

            // 2. Save in category-folder-mappings.json on Drive
            val mappingsFile = findFileByAppProperty(accessToken, systemFolderId, "categoryFolderMappings")
            val mappingsJson = if (mappingsFile != null) {
                try {
                    downloadJson(accessToken, mappingsFile.id)
                } catch (e: Exception) {
                    e.rethrowDriveControlFailure()
                    JSONObject().apply { put("mappings", JSONArray()) }.toString()
                }
            } else {
                JSONObject().apply { put("mappings", JSONArray()) }.toString()
            }

            val rootObj = JSONObject(mappingsJson)
            val array = rootObj.optJSONArray("mappings") ?: JSONArray()
            
            // Check if mapping already exists
            var exists = false
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("categoryKey") == canonicalPathKey) {
                    obj.put("driveFolderId", folderId)
                    exists = true
                    break
                }
            }
            
            if (!exists) {
                val yearStr = canonicalPathKey.split("/").firstOrNull()
                val yearInt = yearStr?.toIntOrNull()
                val newMap = JSONObject().apply {
                    put("id", folderId)
                    put("categoryKey", canonicalPathKey)
                    if (yearInt != null) {
                        put("year", yearInt)
                    } else {
                        put("year", JSONObject.NULL)
                    }
                    put("driveFolderId", folderId)
                    put("active", true)
                }
                array.put(newMap)
            }

            rootObj.put("mappings", array)
            val newJsonStr = rootObj.toString(4)

            if (mappingsFile != null) {
                updateJson(accessToken, mappingsFile.id, newJsonStr)
            } else {
                uploadJson(
                    accessToken = accessToken,
                    folderId = systemFolderId,
                    filename = "category-folder-mappings.json",
                    json = newJsonStr,
                    appProperties = mapOf(
                        "appName" to DriveEnvironment.appIdentifier,
                        "entityType" to "categoryFolderMappings",
                        "schemaVersion" to "1"
                    )
                )
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.e(TAG, "Error saving category mapping")
        }
    }

    suspend fun findFileByReceiptProperties(
        accessToken: String,
        folderId: String,
        receiptInternalId: String,
        documentRole: String = "ORIGINAL"
    ): DriveFile? {
        val q = "'$folderId' in parents and appProperties has { key='appName' and value='${DriveEnvironment.appIdentifier}' } and appProperties has { key='receiptInternalId' and value='$receiptInternalId' } and appProperties has { key='documentRole' and value='$documentRole' } and trashed = false"
        try {
            val encodedQ = URLEncoder.encode(q, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id,name)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            executeDriveRequest(request).use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        val fileObj = files.getJSONObject(0)
                        return DriveFile(fileObj.getString("id"), fileObj.getString("name"))
                    }
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.e(TAG, "Error finding file by receipt properties")
        }
        return null
    }

    suspend fun findFileByReceiptPropertiesGlobally(
        accessToken: String,
        receiptInternalId: String,
        documentRole: String = "ORIGINAL"
    ): DriveFile? {
        if (receiptInternalId.isBlank()) return null
        val q = "appProperties has { key='appName' and value='${DriveEnvironment.appIdentifier}' } and appProperties has { key='receiptInternalId' and value='$receiptInternalId' } and appProperties has { key='documentRole' and value='$documentRole' } and trashed = false"
        return searchFiles(accessToken, q).singleOrNull()
    }

    /** Errors must not be mistaken for an empty search: that would create duplicate backup structures. */
    private inline fun <T> initializationCall(block: () -> T): T = try { block() }
        catch (e: DriveAccessFailure) { throw e }
        catch (_: java.net.SocketTimeoutException) { throw DriveAccessFailure(DriveAccessFailure.Reason.TIMEOUT) }
        catch (_: IOException) { throw DriveAccessFailure(DriveAccessFailure.Reason.NETWORK) }
        catch (_: org.json.JSONException) { throw DriveAccessFailure(DriveAccessFailure.Reason.INVALID_METADATA) }

    private fun initializationSearch(accessToken: String, query: String, orderBy: String? = null): List<JSONObject> = initializationCall {
        val found = mutableListOf<JSONObject>()
        var page: String? = null
        repeat(100) {
            val url = okhttp3.HttpUrl.Builder().scheme("https").host("www.googleapis.com")
                .addPathSegments("drive/v3/files").addQueryParameter("q", query)
                .addQueryParameter("fields", "nextPageToken,incompleteSearch,files(id,name)").addQueryParameter("pageSize", "1000")
                .apply { page?.let { addQueryParameter("pageToken", it) }; orderBy?.let { addQueryParameter("orderBy", it) } }.build()
            val request = Request.Builder().url(url).header("Authorization", "Bearer $accessToken").build()
            val json = executeDriveRequest(request).use {
                if (!it.isSuccessful) throw DriveAccessFailure(DriveAccessFailure.Reason.HTTP, it.code)
                JSONObject(it.body?.string().orEmpty())
            }
            if (json.optBoolean("incompleteSearch")) throw DriveAccessFailure(DriveAccessFailure.Reason.INCOMPLETE_SEARCH)
            val files = json.driveFileListItems()
            repeat(files.length()) { found += files.getJSONObject(it) }
            page = json.optString("nextPageToken").takeIf(String::isNotBlank)
            if (page == null) return@initializationCall found
        }
        throw DriveAccessFailure(DriveAccessFailure.Reason.INCOMPLETE_SEARCH)
    }

    suspend fun getOrCreateFolder(
        accessToken: String,
        folderName: String = DriveEnvironment.rootFolderName,
        parentId: String? = null,
        canonicalPathKey: String? = null,
        systemFolderId: String? = null,
        context: Context? = null
    ): String? {
        val name = folderName.trim().ifEmpty { "Sonstiges" }
        val accountKey = java.security.MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val requestKey = if (canonicalPathKey != null) "path#$canonicalPathKey" else "name#$name"
        val cacheKey = "$accountKey#${DriveEnvironment.rootFolderName}#${parentId ?: "root"}#$requestKey"
        val prefs = if (canonicalPathKey != null && context != null)
            context.getSharedPreferences("category_folder_mappings_prefs", Context.MODE_PRIVATE) else null
        suspend fun checked(id: String): String {
            val meta = getFileMetadataChecked(accessToken, id)
            if (meta.mimeType != "application/vnd.google-apps.folder") throw DriveAccessFailure(DriveAccessFailure.Reason.WRONG_MIME)
            if ((parentId != null && meta.parentIds != listOf(parentId)) || (canonicalPathKey == null && meta.name != name))
                throw DriveAccessFailure(DriveAccessFailure.Reason.WRONG_LOCATION)
            return id
        }
        fun invalidLocalMapping(e: DriveAccessFailure) = e.reason in setOf(
            DriveAccessFailure.Reason.WRONG_MIME, DriveAccessFailure.Reason.WRONG_LOCATION, DriveAccessFailure.Reason.INVALID_ID)
        // Keep the original logical name/path lock shared across cache entry kinds.
        val lockKey = "$accountKey#${DriveEnvironment.rootFolderName}#${parentId ?: "root"}#${canonicalPathKey ?: name}"
        return getFolderMutex(lockKey).withLock {
            folderCache[cacheKey]?.let { id ->
                try { return@withLock checked(id) }
                catch (e: DriveAccessFailure) {
                    // Only confirmed invalid cached identity/type/location; not HTTP404 (possibly permissions),
                    // HTTP401/403, network errors, foreign roots or ownership failures.
                    if (!invalidLocalMapping(e)) throw e
                    folderCache.remove(cacheKey, id)
                }
            }
            prefs?.getString(canonicalPathKey, null)?.takeIf(String::isNotBlank)?.let { id ->
                try { return@withLock checked(id).also { folderCache[cacheKey] = it } }
                catch (e: DriveAccessFailure) {
                    if (!DriveEnvironment.isIsolatedTest || !invalidLocalMapping(e)) throw e
                    // One verified invalid test mapping only; never clear stores or delete/move Drive data.
                    prefs.edit().remove(canonicalPathKey).commit()
                }
            }
            val escapedName = name.replace("\\", "\\\\").replace("'", "\\'")
            val parentQuery = "'${parentId ?: "root"}' in parents"
            val byName = "name = '$escapedName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false and $parentQuery"
            val byPath = canonicalPathKey?.let {
                val escaped = it.replace("\\", "\\\\").replace("'", "\\'")
                "mimeType = 'application/vnd.google-apps.folder' and appProperties has { key='appName' and value='${DriveEnvironment.appIdentifier}' } and appProperties has { key='folderRole' and value='CATEGORY' } and appProperties has { key='canonicalPathKey' and value='$escaped' } and trashed = false and $parentQuery"
            }
            val found = byPath?.let { initializationSearch(accessToken, it) }.orEmpty().ifEmpty {
                initializationSearch(accessToken, byName)
            }
            if (found.isNotEmpty()) return@withLock checked(found.first().getString("id")).also { folderCache[cacheKey] = it }
            val body = JSONObject().put("name", name).put("mimeType", "application/vnd.google-apps.folder").apply {
                parentId?.let { put("parents", JSONArray().put(it)) }
                if (canonicalPathKey != null) put("appProperties", JSONObject()
                    .put("appName", DriveEnvironment.appIdentifier).put("folderRole", "CATEGORY")
                    .put("canonicalPathKey", canonicalPathKey).put("parentFolderId", parentId.orEmpty()).put("schemaVersion", "1"))
            }
            val request = Request.Builder().url("https://www.googleapis.com/drive/v3/files")
                .header("Authorization", "Bearer $accessToken")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
            val created = initializationCall {
                executeDriveRequest(request).use {
                    if (!it.isSuccessful) throw DriveAccessFailure(DriveAccessFailure.Reason.HTTP, it.code)
                    JSONObject(it.body?.string().orEmpty()).getString("id")
                }
            }
            checked(created)
            // Preserve cross-client category race handling: reuse the oldest valid match,
            // without deleting, moving or renaming any concurrently created folder.
            val selected = byPath?.let { initializationSearch(accessToken, it, "createdTime asc").firstOrNull() }
                ?.getString("id")?.let { checked(it) } ?: created
            folderCache[cacheKey] = selected
            if (canonicalPathKey != null && systemFolderId != null && context != null)
                saveCategoryMapping(accessToken, systemFolderId, canonicalPathKey, selected, context)
            selected
        }
    }

    private fun extractYear(datum: String): String {
        val trimmed = datum.trim()
        val regex = Regex("""\b(19\d\d|20\d\d)\b""")
        val match = regex.find(trimmed)
        if (match != null) {
            return match.value
        }
        return SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())
    }

    suspend fun uploadReceipt(accessToken: String, mainFolderId: String, receipt: Receipt): Boolean {
        // Hierarchy: active app root -> [Jahr] -> [Hauptkategorie] -> [Unterkategorie]
        val yearName = extractYear(receipt.datum)
        val hauptkategorieName = receipt.hauptkategorie.trim().ifEmpty { "Sonstiges" }
        val unterkategorieName = receipt.unterkategorie.trim().ifEmpty { "Allgemein" }

        val yearFolderId = getOrCreateFolder(accessToken, yearName, mainFolderId) ?: mainFolderId
        val hauptFolderId = getOrCreateFolder(accessToken, hauptkategorieName, yearFolderId) ?: yearFolderId
        val targetFolderId = getOrCreateFolder(accessToken, unterkategorieName, hauptFolderId) ?: hauptFolderId

        // Format date as YYYY-MM-DD (jjjj-mm-dd)
        val formattedDate = receipt.datum.trim().replace(".", "-").replace("/", "-")

        // Upload as text file summary
        val sanitizedAussteller = receipt.aussteller.replace("[^a-zA-Z0-9]".toRegex(), "_").ifEmpty { "Beleg" }
        val filename = "Beleg_${formattedDate}_${sanitizedAussteller}_${receipt.bruttobetrag}.txt"
        val itemsList = receipt.getPositionenList()
        val itemsText = if (itemsList.isNotEmpty()) {
            "\nEinzelne Positionen:\n" + itemsList.joinToString("\n") { item ->
                " - ${item.bezeichnung} | Menge: ${item.menge} | Einzelpreis: ${item.einzelpreis} € | Gesamt: ${item.gesamtpreis} €"
            } + "\n"
        } else ""

        val content = """
            =========================================
            STEUERASSISTENT - BELEGS-EXPORT
            =========================================
            Aussteller:       ${receipt.aussteller}
            Datum:            ${com.example.util.DateFormatUtils.safeFormat(receipt.datum)} ${receipt.uhrzeit}
            Bruttobetrag:     ${receipt.bruttobetrag} EUR
            Hauptkategorie:   ${receipt.hauptkategorie}
            Unterkategorie:   ${receipt.unterkategorie}
            Konto-Nr:         ${receipt.kontoNr}
            Eigenleistung:    ${if (receipt.isEigenleistungSanierung) "Ja" else "Nein"}
            Beschreibung:     ${receipt.beschreibung}
            $itemsText=========================================
            Exportiert am:    ${SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date())}
            =========================================
        """.trimIndent()
        
        val successText = uploadFileToDrive(accessToken, targetFolderId, filename, "text/plain", content.toByteArray())
        
        // Optionally upload as PDF if image exists
        var successPdf = true
        if (receipt.imageUrl.isNotEmpty()) {
            val pdfFilename = "Beleg_${formattedDate}_${sanitizedAussteller}_${receipt.bruttobetrag}.pdf"
            val pdfContent = generatePdfFromReceiptImages(com.example.data.ReceiptOriginalChain.paths(receipt.imageUrl))
            if (pdfContent != null) {
                successPdf = uploadFileToDrive(accessToken, targetFolderId, pdfFilename, "application/pdf", pdfContent)
            }
        }
        
        return successText && successPdf
    }
    
    fun generatePdfFromReceiptImages(imagePaths: List<String>): ByteArray? {
        val validPaths = imagePaths.map { it.trim() }.filter { it.isNotEmpty() && File(it).exists() }
        if (validPaths.isEmpty()) return null

        val maxFileSizeBytes = 500 * 1024 // Strict 500 KB limit
        var maxDimension = 1000
        var jpegQuality = 80
        var pdfBytes: ByteArray? = null

        for (attempt in 0..4) {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Standard A4 (595x842 pt)

            for (path in validPaths) {
                val originalBitmap = decodeSampledBitmapFromFile(path, maxDimension, maxDimension) ?: continue

                // Compress bitmap to JPEG byte array first to optimize stream size
                val stream = ByteArrayOutputStream()
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
                val compressedBytes = stream.toByteArray()
                originalBitmap.recycle()

                val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size) ?: continue

                val page = document.startPage(pageInfo)
                val canvas = page.canvas

                // Fill canvas with white background
                canvas.drawColor(android.graphics.Color.WHITE)

                // Calculate scaling to fit image within A4 page (595 x 842) with 20pt margin
                val margin = 20f
                val targetW = 595f - 2 * margin
                val targetH = 842f - 2 * margin

                val scale = Math.min(targetW / compressedBitmap.width, targetH / compressedBitmap.height)
                val drawW = compressedBitmap.width * scale
                val drawH = compressedBitmap.height * scale

                val left = margin + (targetW - drawW) / 2f
                val top = margin + (targetH - drawH) / 2f

                val destRect = android.graphics.RectF(left, top, left + drawW, top + drawH)
                val paint = android.graphics.Paint().apply {
                    isFilterBitmap = true
                }

                canvas.drawBitmap(compressedBitmap, null, destRect, paint)
                document.finishPage(page)
                compressedBitmap.recycle()
            }

            val outputStream = ByteArrayOutputStream()
            try {
                document.writeTo(outputStream)
                document.close()
                val result = outputStream.toByteArray()

                if (result.size <= maxFileSizeBytes || attempt == 4) {
                    pdfBytes = result
                    DiagnosticLog.d(TAG, "PDF generated successfully. Attempt")
                    break
                } else {
                    DiagnosticLog.d(TAG, "PDF size (")
                    maxDimension = (maxDimension * 0.7).toInt().coerceAtLeast(350)
                    jpegQuality = (jpegQuality * 0.65).toInt().coerceAtLeast(25)
                }
            } catch (e: Exception) {
                DiagnosticLog.e(TAG, "Error generating PDF")
                try { document.close() } catch (_: Exception) {}
                break
            }
        }

        return pdfBytes
    }

    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false

        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    internal fun ledgerCsv(receipts: List<Receipt>): String = buildString {
        append("ID;Aussteller;Datum;Uhrzeit;Bruttobetrag;Hauptkategorie;Unterkategorie;KontoNr;Eigenleistung;Beschreibung\n")
        receipts.forEach { receipt ->
            append(listOf(receipt.id.toString(), receipt.aussteller, receipt.datum, receipt.uhrzeit,
                receipt.bruttobetrag.toString(), receipt.hauptkategorie, receipt.unterkategorie, receipt.kontoNr,
                if (receipt.isEigenleistungSanierung) "Ja" else "Nein", receipt.beschreibung)
                .joinToString(";") { com.example.util.csvField(it) }).append('\n')
        }
    }

    fun uploadLedgerCsv(accessToken: String, folderId: String, receipts: List<Receipt>): Boolean =
        uploadFileToDrive(accessToken, folderId, "Steuerassistent_Belege_Hauptbuch.csv", "text/csv; charset=UTF-8",
            ledgerCsv(receipts).toByteArray(Charsets.UTF_8))

    internal fun unitsCsv(units: List<WohneinheitStatus>): String = buildString {
        append("Name;Label;Status;Mieter;Kaltmiete;Wohnflaeche;Mietvertragsstart\n")
        units.forEach { unit ->
            append(listOf(unit.name, unit.label, unit.status, unit.mieter, unit.kaltmiete.toString(),
                unit.wohnflaeche.toString(), unit.mietvertragsstart)
                .joinToString(";") { com.example.util.csvField(it) }).append('\n')
        }
    }

    fun uploadWohneinheitenCsv(accessToken: String, folderId: String, units: List<WohneinheitStatus>): Boolean =
        uploadFileToDrive(accessToken, folderId, "Steuerassistent_Wohneinheiten.csv", "text/csv; charset=UTF-8",
            unitsCsv(units).toByteArray(Charsets.UTF_8))

    private fun uploadFileToDrive(
        accessToken: String,
        folderId: String,
        filename: String,
        mimeType: String,
        fileContent: ByteArray
    ): Boolean {
        // 1. Search for existing file
        val escapedFilename = filename.replace("\\", "\\\\").replace("'", "\\'")
        val fileQ = "name = '$escapedFilename' and '$folderId' in parents and trashed = false"
        var existingFileId: String? = null
        try {
            val encodedFileQ = URLEncoder.encode(fileQ, "UTF-8")
            val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$encodedFileQ&fields=files(id)"
            
            val searchRequest = Request.Builder()
                .url(searchUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            
            executeDriveRequest(searchRequest).use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val json = JSONObject(responseBody)
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        existingFileId = files.getJSONObject(0).getString("id")
                        DiagnosticLog.d(TAG, "Found existing file")
                    }
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.e(TAG, "Error searching file")
        }

        // 2. Upload or Update
        val url = if (existingFileId != null) {
            "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media"
        } else {
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        }

        val request = if (existingFileId != null) {
            val requestBody = fileContent.toRequestBody(mimeType.toMediaType())
            Request.Builder()
                .url(url)
                .patch(requestBody)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
        } else {
            val boundary = "foo_bar_boundary"

            val metadata = JSONObject().apply {
                put("name", filename)
                put("parents", JSONArray().put(folderId))
            }

            val delimiter = "\r\n--$boundary\r\n"
            val closeDelimiter = "\r\n--$boundary--"

            val requestBodyBuilder = StringBuilder()
            requestBodyBuilder.append(delimiter)
            requestBodyBuilder.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            requestBodyBuilder.append(metadata.toString())
            requestBodyBuilder.append(delimiter)
            requestBodyBuilder.append("Content-Type: $mimeType\r\n\r\n")

            val requestBodyHeaderBytes = requestBodyBuilder.toString().toByteArray(Charsets.UTF_8)
            val requestBodyFooterBytes = closeDelimiter.toByteArray(Charsets.UTF_8)

            val totalBodyBytes = requestBodyHeaderBytes.size + fileContent.size + requestBodyFooterBytes.size
            val fullBody = ByteArray(totalBodyBytes)

            System.arraycopy(requestBodyHeaderBytes, 0, fullBody, 0, requestBodyHeaderBytes.size)
            System.arraycopy(fileContent, 0, fullBody, requestBodyHeaderBytes.size, fileContent.size)
            System.arraycopy(requestBodyFooterBytes, 0, fullBody, requestBodyHeaderBytes.size + fileContent.size, requestBodyFooterBytes.size)

            val requestBody = fullBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType())

            Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
        }

        return try {
            executeDriveRequest(request).use { response ->
                if (response.isSuccessful) {
                    DiagnosticLog.d(TAG, "File")
                    true
                } else {
                    DiagnosticLog.e(TAG, "Upload failed")
                    false
                }
            }
        } catch (e: IOException) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.e(TAG, "Error uploading file")
            false
        }
    }

    suspend fun findAppDataFolder(accessToken: String): DriveFolder? {
        val main = getOrCreateFolder(accessToken, DriveEnvironment.rootFolderName) ?: return null
        val q = "name = '_BelegApp-Daten' and mimeType = 'application/vnd.google-apps.folder' and '$main' in parents and trashed = false"
        val found = initializationSearch(accessToken, q).firstOrNull() ?: return null
        val meta = getFileMetadataChecked(accessToken, found.getString("id"))
        if (meta.mimeType != "application/vnd.google-apps.folder") throw DriveAccessFailure(DriveAccessFailure.Reason.WRONG_MIME)
        if (meta.parentIds != listOf(main) || meta.name != "_BelegApp-Daten") throw DriveAccessFailure(DriveAccessFailure.Reason.WRONG_LOCATION)
        return DriveFolder(meta.id, meta.name)
    }

    suspend fun createAppDataFolder(accessToken: String): DriveFolder {
        val mainFolderId = getOrCreateFolder(accessToken, DriveEnvironment.rootFolderName) ?: throw IOException("Could not find or create main folder")
        val folderId = getOrCreateFolder(accessToken, "_BelegApp-Daten", mainFolderId) ?: throw IOException("Could not find or create system folder _BelegApp-Daten")
        return DriveFolder(folderId, "_BelegApp-Daten")
    }

    suspend fun uploadJson(
        accessToken: String,
        folderId: String,
        filename: String,
        json: String,
        appProperties: Map<String, String>? = null
    ): DriveFileResult {
        val boundary = "json_upload_boundary"
        val metadata = JSONObject().apply {
            put("name", filename)
            put("parents", JSONArray().put(folderId))
            if (appProperties != null) {
                val appPropsJson = JSONObject()
                for ((k, v) in appProperties) {
                    appPropsJson.put(k, v)
                }
                put("appProperties", appPropsJson)
            }
        }

        val delimiter = "\r\n--$boundary\r\n"
        val closeDelimiter = "\r\n--$boundary--"

        val requestBodyBuilder = StringBuilder()
        requestBodyBuilder.append(delimiter)
        requestBodyBuilder.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        requestBodyBuilder.append(metadata.toString())
        requestBodyBuilder.append(delimiter)
        requestBodyBuilder.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")

        val fileBytes = json.toByteArray(Charsets.UTF_8)
        val requestBodyHeaderBytes = requestBodyBuilder.toString().toByteArray(Charsets.UTF_8)
        val requestBodyFooterBytes = closeDelimiter.toByteArray(Charsets.UTF_8)

        val totalBodyBytes = requestBodyHeaderBytes.size + fileBytes.size + requestBodyFooterBytes.size
        val fullBody = ByteArray(totalBodyBytes)

        System.arraycopy(requestBodyHeaderBytes, 0, fullBody, 0, requestBodyHeaderBytes.size)
        System.arraycopy(fileBytes, 0, fullBody, requestBodyHeaderBytes.size, fileBytes.size)
        System.arraycopy(requestBodyFooterBytes, 0, fullBody, requestBodyHeaderBytes.size + fileBytes.size, requestBodyFooterBytes.size)

        val requestBody = fullBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType())
        val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return try {
            validateDataReferences(accessToken, json)
            executeDriveRequest(request).use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val resJson = JSONObject(responseBody)
                    val fileId = resJson.getString("id")
                    DriveFileResult(true, fileId, null)
                } else {
                    val errMsg = response.body?.string() ?: response.message
                    DriveFileResult(false, null, "Upload failed with status ${response.code}: $errMsg")
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DriveFileResult(false, null, e.message ?: e.toString())
        }
    }

    suspend fun updateJson(
        accessToken: String,
        fileId: String,
        json: String
    ): DriveFileResult {
        val requestBody = json.toByteArray(Charsets.UTF_8).toRequestBody("application/json; charset=UTF-8".toMediaType())
        val url = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
        val request = Request.Builder()
            .url(url)
            .patch(requestBody)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return try {
            validateDataReferences(accessToken, json)
            executeDriveRequest(request).use { response ->
                if (response.isSuccessful) {
                    DriveFileResult(true, fileId, null)
                } else {
                    val errMsg = response.body?.string() ?: response.message
                    DriveFileResult(false, null, "Update failed with status ${response.code}: $errMsg")
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DriveFileResult(false, null, e.message ?: e.toString())
        }
    }

    suspend fun deleteFile(
        accessToken: String,
        fileId: String
    ): Boolean {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId"
        val request = Request.Builder()
            .url(url)
            .delete()
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return try {
            executeDriveRequest(request).use { response ->
                response.isSuccessful || response.code == 404
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.e(TAG, "Error deleting file")
            false
        }
    }

    suspend fun downloadJson(
        accessToken: String,
        fileId: String
    ): String = readJson(accessToken, fileId, restore = false).json

    internal suspend fun downloadJsonForRestore(accessToken: String, fileId: String): DriveRestoreJsonRead =
        readJson(accessToken, fileId, restore = true)

    private suspend fun readJson(accessToken: String, fileId: String, restore: Boolean): DriveRestoreJsonRead {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        executeDriveRequest(request).use { response ->
            if (response.isSuccessful) {
                val json = response.body?.string() ?: ""
                val missing = if (restore) {
                    DriveEnvironment.validateRestoreJsonReferences(json, accessToken) { client.newCall(it).execute() }
                } else {
                    validateDataReferences(accessToken, json)
                    emptySet()
                }
                return DriveRestoreJsonRead(json, missing)
            } else {
                throw DriveAccessFailure(DriveAccessFailure.Reason.HTTP, response.code)
            }
        }
    }

    suspend fun searchFiles(accessToken: String, q: String): List<DriveFile> {
        if (DriveEnvironment.namespaceGuardEnabled && !Regex("'[^']+' in parents").containsMatchIn(q)) {
            val root = getOrCreateFolder(accessToken) ?: return emptyList()
            val folders = listOf(root) + listAppTreeFiles(accessToken, root)
                .filter { it.mimeType == "application/vnd.google-apps.folder" }.map { it.id }
            return folders.flatMap { searchFiles(accessToken, "($q) and '$it' in parents") }.distinctBy { it.id }
        }
        return withContext(Dispatchers.IO) {
            val list = mutableListOf<DriveFile>()
            try {
                val encodedQ = URLEncoder.encode(q, "UTF-8")
                val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id,name,mimeType)"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                executeDriveRequest(request).use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            val files = json.optJSONArray("files")
                            if (files != null) {
                                for (i in 0 until files.length()) {
                                    val f = files.getJSONObject(i)
                                    list.add(DriveFile(f.optString("id"), f.optString("name")))
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.rethrowDriveControlFailure()
                DiagnosticLog.e(TAG, "Error in searchFiles")
            }
            list
        }
    }

    suspend fun downloadFileBytes(
        accessToken: String,
        fileId: String
    ): ByteArray? {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        try {
            executeDriveRequest(request).use { response ->
                if (response.isSuccessful) {
                    return response.body?.bytes()
                } else {
                    DiagnosticLog.e(TAG, "Download file bytes failed")
                    return null
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.e(TAG, "Error downloading file bytes for fileId")
            return null
        }
    }

    /** Metadata used by the non-destructive document-layout migration. */
    suspend fun getFileMetadata(accessToken: String, fileId: String): DriveManagedFile? = try {
        getFileMetadataChecked(accessToken, fileId)
    } catch (controlFailure: Exception) {
        controlFailure.rethrowDriveControlFailure()
        DiagnosticLog.e(TAG, "Could not read Drive metadata for document migration")
        null
    }

    /** Strict variant for identity checks: HTTP, permission, MIME and namespace failures stay distinct. */
    internal suspend fun getFileMetadataChecked(accessToken: String, fileId: String): DriveManagedFile = initializationCall {
        if (!Regex("[A-Za-z0-9_-]+").matches(fileId)) throw DriveAccessFailure(DriveAccessFailure.Reason.INVALID_ID)
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?fields=id,name,mimeType,size,parents,trashed,appProperties")
            .header("Authorization", "Bearer $accessToken").build()
        executeDriveRequest(request).use {
            if (!it.isSuccessful) throw DriveAccessFailure(DriveAccessFailure.Reason.HTTP, it.code)
            val json = JSONObject(it.body?.string().orEmpty())
            if (json.optBoolean("trashed")) throw DriveAccessFailure(DriveAccessFailure.Reason.NAMESPACE)
            if (json.optString("id") != fileId || json.optString("name").isBlank() || json.optString("mimeType").isBlank())
                throw DriveAccessFailure(DriveAccessFailure.Reason.INVALID_METADATA)
            DriveManagedFile(fileId, json.getString("name"), json.getString("mimeType"), json.optLong("size", 0L),
                json.optJSONArray("parents")?.let { array -> (0 until array.length()).map { i -> array.getString(i) } }.orEmpty(),
                json.optJSONObject("appProperties")?.let(::jsonStringMap).orEmpty())
        }
    }

    /** Bounded inventory below the app root. It never scans unrelated Drive areas. */
    suspend fun listAppTreeFiles(
        accessToken: String,
        appRootFolderId: String,
        maxEntries: Int = 10_000
    ): List<DriveManagedFile> {
        if (appRootFolderId.isBlank() || maxEntries <= 0) return emptyList()
        val result = mutableListOf<DriveManagedFile>()
        val folders = ArrayDeque<String>().apply { add(appRootFolderId) }
        val visited = mutableSetOf<String>()
        while (folders.isNotEmpty() && result.size < maxEntries) {
            val parentId = folders.removeFirst()
            if (!visited.add(parentId)) continue
            val children = listManagedChildren(accessToken, parentId, maxEntries - result.size)
            result += children
            children.filter { it.mimeType == "application/vnd.google-apps.folder" }.forEach { folders.add(it.id) }
        }
        return result
    }

    private suspend fun listManagedChildren(accessToken: String, parentId: String, limit: Int): List<DriveManagedFile> {
        val result = mutableListOf<DriveManagedFile>()
        var pageToken = ""
        do {
            val q = "'$parentId' in parents and trashed = false"
            val url = buildString {
                append("https://www.googleapis.com/drive/v3/files?q=").append(URLEncoder.encode(q, "UTF-8"))
                append("&pageSize=1000&fields=nextPageToken,files(id,name,mimeType,size,parents,trashed,appProperties)")
                if (pageToken.isNotBlank()) append("&pageToken=").append(URLEncoder.encode(pageToken, "UTF-8"))
            }
            val request = Request.Builder().url(url).addHeader("Authorization", "Bearer $accessToken").build()
            val responseJson = executeDriveRequest(request).use { response ->
                if (!response.isSuccessful) throw IOException("Drive-Inventur fehlgeschlagen (${response.code})")
                JSONObject(response.body?.string().orEmpty())
            }
            val files = responseJson.optJSONArray("files") ?: JSONArray()
            for (index in 0 until files.length()) {
                if (result.size >= limit) break
                val file = files.getJSONObject(index)
                result += DriveManagedFile(
                    id = file.optString("id"), name = file.optString("name"), mimeType = file.optString("mimeType"),
                    sizeBytes = file.optLong("size", 0L),
                    parentIds = file.optJSONArray("parents")?.let { parents ->
                        (0 until parents.length()).map { parents.optString(it) }.filter(String::isNotBlank)
                    }.orEmpty(),
                    appProperties = file.optJSONObject("appProperties")?.let(::jsonStringMap).orEmpty()
                )
            }
            pageToken = responseJson.optString("nextPageToken", "")
        } while (pageToken.isNotBlank() && result.size < limit)
        return result
    }

    private fun jsonStringMap(json: JSONObject): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = json.optString(key, "")
        }
        return result
    }

    /** Moves and/or renames the same Drive file. It never copies, replaces or deletes the original. */
    suspend fun moveAndRenameFile(
        accessToken: String,
        fileId: String,
        targetParentId: String,
        oldParentIds: List<String>,
        targetFilename: String
    ): Boolean {
        val removeParents = oldParentIds.filter { it != targetParentId }.joinToString(",")
        val query = buildString {
            if (targetParentId !in oldParentIds) append("addParents=").append(URLEncoder.encode(targetParentId, "UTF-8")).append('&')
            if (removeParents.isNotBlank()) append("removeParents=").append(URLEncoder.encode(removeParents, "UTF-8")).append('&')
            append("fields=id,parents,name")
        }
        val request = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?$query")
            .patch(JSONObject().put("name", targetFilename).toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addHeader("Authorization", "Bearer $accessToken")
            .build()
        return try {
            executeDriveRequest(request).use { it.isSuccessful }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.e(TAG, "Could not move Drive file during document migration")
            false
        }
    }

    suspend fun fetchDriveFileInfoAndHeader(
        accessToken: String,
        fileId: String,
        maxHeaderBytes: Int = 2048
    ): DriveFileInfoAndHeader {
        if (fileId.isBlank()) {
            return DriveFileInfoAndHeader(fileId = fileId, isReachable = false, errorMessage = "Keine File-ID angegeben")
        }

        var name: String? = null
        var mimeType: String? = null
        var sizeBytes = 0L
        var reachable = false

        try {
            val metaUrl = "https://www.googleapis.com/drive/v3/files/$fileId?fields=id,name,mimeType,size,trashed"
            val metaReq = Request.Builder()
                .url(metaUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            executeDriveRequest(metaReq).use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string() ?: ""
                    val json = JSONObject(bodyStr)
                    if (!json.optBoolean("trashed", false)) {
                        name = json.optString("name", null)
                        mimeType = json.optString("mimeType", null)
                        sizeBytes = json.optLong("size", 0L)
                        reachable = true
                    }
                } else {
                    DiagnosticLog.w(TAG, "File metadata fetch failed for")
                    return DriveFileInfoAndHeader(
                        fileId = fileId,
                        isReachable = false,
                        errorMessage = "Datei in Google Drive nicht gefunden (Status ${response.code})"
                    )
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.e(TAG, "Exception getting file metadata for")
            return DriveFileInfoAndHeader(
                fileId = fileId,
                isReachable = false,
                errorMessage = "Netzwerkfehler: ${e.message}"
            )
        }

        if (!reachable) {
            return DriveFileInfoAndHeader(
                fileId = fileId,
                isReachable = false,
                errorMessage = "Datei ist im Papierkorb oder nicht erreichbar"
            )
        }

        var headerBytes = byteArrayOf()
        try {
            val mediaUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val mediaReq = Request.Builder()
                .url(mediaUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Range", "bytes=0-${maxHeaderBytes - 1}")
                .build()

            executeDriveRequest(mediaReq).use { response ->
                if (response.isSuccessful || response.code == 206) {
                    val bytes = response.body?.bytes()
                    if (bytes != null) {
                        headerBytes = bytes
                        if (sizeBytes == 0L) {
                            sizeBytes = bytes.size.toLong()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.w(TAG, "Error fetching partial header bytes for")
        }

        return DriveFileInfoAndHeader(
            fileId = fileId,
            name = name,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            isReachable = true,
            headerBytes = headerBytes
        )
    }

    suspend fun findFileByAppProperty(
        accessToken: String,
        folderId: String,
        entityType: String,
        failOnError: Boolean = false
    ): DriveFile? {
        if (failOnError) {
            val query = "'$folderId' in parents and appProperties has { key='appName' and value='${DriveEnvironment.appIdentifier}' } and appProperties has { key='entityType' and value='$entityType' } and trashed = false"
            val found = initializationSearch(accessToken, query).ifEmpty {
                initializationSearch(accessToken, "name = '${getFilenameForEntityType(entityType)}' and '$folderId' in parents and trashed = false")
            }.firstOrNull() ?: return null
            return initializationCall { DriveFile(found.getString("id"), found.getString("name")) }
        }
        val q = "'$folderId' in parents and appProperties has { key='appName' and value='${DriveEnvironment.appIdentifier}' } and appProperties has { key='entityType' and value='$entityType' } and trashed = false"
        try {
            val encodedQ = URLEncoder.encode(q, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id,name)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            executeDriveRequest(request).use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        val fileObj = files.getJSONObject(0)
                        return DriveFile(fileObj.getString("id"), fileObj.getString("name"))
                    }
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.e(TAG, "Error finding file by app property")
        }

        // Fallback search by name if not found or if query failed
        val filename = getFilenameForEntityType(entityType)
        val fileQ = "name = '$filename' and '$folderId' in parents and trashed = false"
        try {
            val encodedQ = URLEncoder.encode(fileQ, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id,name)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            executeDriveRequest(request).use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        val fileObj = files.getJSONObject(0)
                        return DriveFile(fileObj.getString("id"), fileObj.getString("name"))
                    }
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.e(TAG, "Error finding file by name fallback")
        }

        return null
    }

    fun findFileByName(accessToken: String, folderId: String, filename: String): String? {
        val escapedFilename = filename.replace("\\", "\\\\").replace("'", "\\'")
        val q = "name = '$escapedFilename' and '$folderId' in parents and trashed = false"
        try {
            val encodedQ = URLEncoder.encode(q, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            executeDriveRequest(request).use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        return files.getJSONObject(0).getString("id")
                    }
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.e("GoogleDriveClient", "Error finding file by name")
        }
        return null
    }

    fun uploadFile(
        accessToken: String,
        folderId: String,
        filename: String,
        mimeType: String,
        fileContent: ByteArray,
        existingFileId: String? = null,
        appProperties: Map<String, String>? = null
    ): String? {
        if (!existingFileId.isNullOrEmpty()) {
            val mediaRequestBody = fileContent.toRequestBody(mimeType.toMediaType())
            val mediaRequest = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media")
                .patch(mediaRequestBody)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            
            try {
                executeDriveRequest(mediaRequest).use { response ->
                    if (!response.isSuccessful) {
                        DiagnosticLog.e(TAG, "Update file media content failed")
                        return null
                    }
                }
                
                // Now, update metadata if name or appProperties is provided
                val metaJson = JSONObject().apply {
                    put("name", filename)
                    if (!appProperties.isNullOrEmpty()) {
                        val appPropsJson = JSONObject()
                        for ((k, v) in appProperties) {
                            appPropsJson.put(k, v)
                        }
                        put("appProperties", appPropsJson)
                    }
                }
                val metaRequestBody = metaJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val metaRequest = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$existingFileId")
                    .patch(metaRequestBody)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                
                executeDriveRequest(metaRequest).use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        return json.getString("id")
                    } else {
                        DiagnosticLog.e(TAG, "Update file metadata failed")
                        return existingFileId
                    }
                }
            } catch (e: Exception) {
                e.rethrowDriveControlFailure()
                DiagnosticLog.e(TAG, "Error updating file")
                return null
            }
        }

        val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        val request = run {
            val boundary = "foo_bar_boundary"

            val metadata = JSONObject().apply {
                put("name", filename)
                put("parents", JSONArray().put(folderId))
                if (!appProperties.isNullOrEmpty()) {
                    val appPropsJson = JSONObject()
                    for ((k, v) in appProperties) {
                        appPropsJson.put(k, v)
                    }
                    put("appProperties", appPropsJson)
                }
            }

            val delimiter = "\r\n--$boundary\r\n"
            val closeDelimiter = "\r\n--$boundary--"

            val requestBodyBuilder = StringBuilder()
            requestBodyBuilder.append(delimiter)
            requestBodyBuilder.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            requestBodyBuilder.append(metadata.toString())
            requestBodyBuilder.append(delimiter)
            requestBodyBuilder.append("Content-Type: $mimeType\r\n\r\n")

            val requestBodyHeaderBytes = requestBodyBuilder.toString().toByteArray(Charsets.UTF_8)
            val requestBodyFooterBytes = closeDelimiter.toByteArray(Charsets.UTF_8)

            val totalBodyBytes = requestBodyHeaderBytes.size + fileContent.size + requestBodyFooterBytes.size
            val fullBody = ByteArray(totalBodyBytes)

            System.arraycopy(requestBodyHeaderBytes, 0, fullBody, 0, requestBodyHeaderBytes.size)
            System.arraycopy(fileContent, 0, fullBody, requestBodyHeaderBytes.size, fileContent.size)
            System.arraycopy(requestBodyFooterBytes, 0, fullBody, requestBodyHeaderBytes.size + fileContent.size, requestBodyFooterBytes.size)

            val requestBody = fullBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType())

            Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
        }

        return try {
            executeDriveRequest(request).use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    DiagnosticLog.d(TAG, "File uploaded successfully")
                    if (!existingFileId.isNullOrEmpty()) {
                        existingFileId
                    } else {
                        val resJson = JSONObject(body)
                        resJson.getString("id")
                    }
                } else {
                    DiagnosticLog.e(TAG, "Upload failed")
                    null
                }
            }
        } catch (e: IOException) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.e(TAG, "Error uploading file")
            null
        }
    }

    private fun getFilenameForEntityType(entityType: String): String {
        return when (entityType) {
            "appConfig" -> "app-config.json"
            "propertyMetadata" -> "property-metadata.json"
            "units" -> "wohneinheiten.json"
            "categories" -> "categories.json"
            "categoryFolderMappings" -> "category-folder-mappings.json"
            "datevProfiles" -> "datev-profiles.json"
            "aiLearnedRules" -> "ai-learned-rules.json"
            "receiptIndex" -> "receipt-index.json"
            "documentIndex" -> "document-index.json"
            else -> "$entityType.json"
        }
    }

    fun runDriveTest(accessToken: String): DriveTestApiResult {
        if (accessToken.isBlank()) {
            return DriveTestApiResult(
                affectedAction = "Authentifizierung",
                errorMessage = "Kein Zugriffstoken (Access-Token) verfügbar. Bitte über den Google-Login anmelden.",
                isReAuthRequired = true
            )
        }

        var mainFolderId = ""
        var systemFolderId = ""
        var testFileId = ""

        // 1. Search the active build root in visible Drive ("Meine Ablage").
        val mainFolderName = DriveEnvironment.rootFolderName
        val escapedMainName = mainFolderName.replace("\\", "\\\\").replace("'", "\\'")
        val mainQ = "name = '$escapedMainName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false" +
            if (DriveEnvironment.namespaceGuardEnabled) " and 'root' in parents" else ""

        try {
            val encodedQ = URLEncoder.encode(mainQ, "UTF-8")
            val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id)"
            val request = Request.Builder()
                .url(searchUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            executeDriveRequest(request).use { response ->
                if (response.code == 401 || response.code == 403) {
                    return DriveTestApiResult(
                        affectedAction = "Suche Hauptordner",
                        errorMessage = "Zugriff verweigert (HTTP ${response.code}). Bitte erneut anmelden.",
                        httpStatusCode = response.code,
                        isReAuthRequired = true
                    )
                }
                if (!response.isSuccessful) {
                    return DriveTestApiResult(
                        affectedAction = "Suche Hauptordner",
                        errorMessage = "Fehler beim Suchen des Hauptordners: HTTP ${response.code} ${response.message}",
                        httpStatusCode = response.code
                    )
                }
                val json = JSONObject(response.body?.string() ?: "")
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    mainFolderId = files.getJSONObject(0).getString("id")
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            return DriveTestApiResult(
                affectedAction = "Suche Hauptordner",
                errorMessage = "Netzwerk- oder API-Fehler bei der Hauptordner-Suche: ${e.message}"
            )
        }

        // Create main folder if missing
        if (mainFolderId.isBlank()) {
            try {
                val createUrl = "https://www.googleapis.com/drive/v3/files"
                val bodyJson = JSONObject().apply {
                    put("name", mainFolderName)
                    put("mimeType", "application/vnd.google-apps.folder")
                }
                val requestBody = bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(createUrl)
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                executeDriveRequest(request).use { response ->
                    if (response.code == 401 || response.code == 403) {
                        return DriveTestApiResult(
                            affectedAction = "Erstellung Hauptordner",
                            errorMessage = "Zugriff verweigert (HTTP ${response.code}). Bitte erneut anmelden.",
                            httpStatusCode = response.code,
                            isReAuthRequired = true
                        )
                    }
                    if (!response.isSuccessful) {
                        return DriveTestApiResult(
                            affectedAction = "Erstellung Hauptordner",
                            errorMessage = "Fehler beim Erstellen des Hauptordners: HTTP ${response.code} ${response.message}",
                            httpStatusCode = response.code
                        )
                    }
                    val json = JSONObject(response.body?.string() ?: "")
                    mainFolderId = json.getString("id")
                }
            } catch (e: Exception) {
                e.rethrowDriveControlFailure()
                return DriveTestApiResult(
                    affectedAction = "Erstellung Hauptordner",
                    errorMessage = "Fehler bei Erstellung des Hauptordners: ${e.message}"
                )
            }
        }

        // 2. Search for system folder "_BelegApp-Daten" inside mainFolderId
        val systemFolderName = "_BelegApp-Daten"
        val escapedSysName = systemFolderName.replace("\\", "\\\\").replace("'", "\\'")
        val sysQ = "name = '$escapedSysName' and mimeType = 'application/vnd.google-apps.folder' and '$mainFolderId' in parents and trashed = false"

        try {
            val encodedQ = URLEncoder.encode(sysQ, "UTF-8")
            val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id)"
            val request = Request.Builder()
                .url(searchUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            executeDriveRequest(request).use { response ->
                if (response.code == 401 || response.code == 403) {
                    return DriveTestApiResult(
                        mainFolderId = mainFolderId,
                        affectedAction = "Suche Systemordner",
                        errorMessage = "Zugriff verweigert (HTTP ${response.code}). Bitte erneut anmelden.",
                        httpStatusCode = response.code,
                        isReAuthRequired = true
                    )
                }
                if (!response.isSuccessful) {
                    return DriveTestApiResult(
                        mainFolderId = mainFolderId,
                        affectedAction = "Suche Systemordner",
                        errorMessage = "Fehler beim Suchen des Systemordners: HTTP ${response.code} ${response.message}",
                        httpStatusCode = response.code
                    )
                }
                val json = JSONObject(response.body?.string() ?: "")
                val files = json.optJSONArray("files")
                if (files != null && files.length() > 0) {
                    systemFolderId = files.getJSONObject(0).getString("id")
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            return DriveTestApiResult(
                mainFolderId = mainFolderId,
                affectedAction = "Suche Systemordner",
                errorMessage = "Fehler bei der Systemordner-Suche: ${e.message}"
            )
        }

        // Create system folder if missing
        if (systemFolderId.isBlank()) {
            try {
                val createUrl = "https://www.googleapis.com/drive/v3/files"
                val bodyJson = JSONObject().apply {
                    put("name", systemFolderName)
                    put("mimeType", "application/vnd.google-apps.folder")
                    put("parents", JSONArray().put(mainFolderId))
                }
                val requestBody = bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(createUrl)
                    .post(requestBody)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()

                executeDriveRequest(request).use { response ->
                    if (response.code == 401 || response.code == 403) {
                        return DriveTestApiResult(
                            mainFolderId = mainFolderId,
                            affectedAction = "Erstellung Systemordner",
                            errorMessage = "Zugriff verweigert (HTTP ${response.code}). Bitte erneut anmelden.",
                            httpStatusCode = response.code,
                            isReAuthRequired = true
                        )
                    }
                    if (!response.isSuccessful) {
                        return DriveTestApiResult(
                            mainFolderId = mainFolderId,
                            affectedAction = "Erstellung Systemordner",
                            errorMessage = "Fehler beim Erstellen des Systemordners: HTTP ${response.code} ${response.message}",
                            httpStatusCode = response.code
                        )
                    }
                    val json = JSONObject(response.body?.string() ?: "")
                    systemFolderId = json.getString("id")
                }
            } catch (e: Exception) {
                e.rethrowDriveControlFailure()
                return DriveTestApiResult(
                    mainFolderId = mainFolderId,
                    affectedAction = "Erstellung Systemordner",
                    errorMessage = "Fehler bei Erstellung des Systemordners: ${e.message}"
                )
            }
        }

        // 3. Create/Upload "drive-verbindungstest.json" inside systemFolderId
        val testFileName = "drive-verbindungstest.json"
        val timestampStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())
        val uploadJsonObj = JSONObject().apply {
            put("test", true)
            put("app", DriveEnvironment.appIdentifier)
            put("message", "Echter Google-Drive-Upload erfolgreich")
            put("createdAt", timestampStr)
        }
        val uploadJsonContent = uploadJsonObj.toString(2)
        val uploadBytes = uploadJsonContent.toByteArray(Charsets.UTF_8)

        var existingTestFileId: String? = null
        try {
            val escapedFileName = testFileName.replace("\\", "\\\\").replace("'", "\\'")
            val testFileQ = "name = '$escapedFileName' and '$systemFolderId' in parents and trashed = false"
            val encodedQ = URLEncoder.encode(testFileQ, "UTF-8")
            val searchUrl = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id)"
            val request = Request.Builder()
                .url(searchUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            executeDriveRequest(request).use { response ->
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "")
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        existingTestFileId = files.getJSONObject(0).getString("id")
                    }
                }
            }
        } catch (_: Exception) {}

        val uploadUrl = if (existingTestFileId != null) {
            "https://www.googleapis.com/upload/drive/v3/files/$existingTestFileId?uploadType=media"
        } else {
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        }

        val uploadRequest = if (existingTestFileId != null) {
            val requestBody = uploadBytes.toRequestBody("application/json; charset=utf-8".toMediaType())
            Request.Builder()
                .url(uploadUrl)
                .patch(requestBody)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
        } else {
            val boundary = "test_boundary_${System.currentTimeMillis()}"
            val metadata = JSONObject().apply {
                put("name", testFileName)
                put("parents", JSONArray().put(systemFolderId))
            }
            val delimiter = "\r\n--$boundary\r\n"
            val closeDelimiter = "\r\n--$boundary--"

            val requestBodyBuilder = StringBuilder()
            requestBodyBuilder.append(delimiter)
            requestBodyBuilder.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            requestBodyBuilder.append(metadata.toString())
            requestBodyBuilder.append(delimiter)
            requestBodyBuilder.append("Content-Type: application/json; charset=UTF-8\r\n\r\n")

            val headerBytes = requestBodyBuilder.toString().toByteArray(Charsets.UTF_8)
            val footerBytes = closeDelimiter.toByteArray(Charsets.UTF_8)

            val fullBody = ByteArray(headerBytes.size + uploadBytes.size + footerBytes.size)
            System.arraycopy(headerBytes, 0, fullBody, 0, headerBytes.size)
            System.arraycopy(uploadBytes, 0, fullBody, headerBytes.size, uploadBytes.size)
            System.arraycopy(footerBytes, 0, fullBody, headerBytes.size + uploadBytes.size, footerBytes.size)

            val requestBody = fullBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType())
            Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
        }

        try {
            executeDriveRequest(uploadRequest).use { response ->
                if (response.code == 401 || response.code == 403) {
                    return DriveTestApiResult(
                        mainFolderId = mainFolderId,
                        systemFolderId = systemFolderId,
                        affectedAction = "Upload der Testdatei",
                        errorMessage = "Zugriff verweigert (HTTP ${response.code}). Bitte erneut anmelden.",
                        httpStatusCode = response.code,
                        isReAuthRequired = true
                    )
                }
                if (!response.isSuccessful) {
                    return DriveTestApiResult(
                        mainFolderId = mainFolderId,
                        systemFolderId = systemFolderId,
                        affectedAction = "Upload der Testdatei",
                        errorMessage = "Upload fehlgeschlagen: HTTP ${response.code} ${response.message}",
                        httpStatusCode = response.code
                    )
                }
                if (existingTestFileId != null) {
                    testFileId = existingTestFileId!!
                } else {
                    val json = JSONObject(response.body?.string() ?: "")
                    testFileId = json.optString("id", "")
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            return DriveTestApiResult(
                mainFolderId = mainFolderId,
                systemFolderId = systemFolderId,
                affectedAction = "Upload der Testdatei",
                errorMessage = "Fehler beim Upload: ${e.message}"
            )
        }

        if (testFileId.isBlank()) {
            return DriveTestApiResult(
                mainFolderId = mainFolderId,
                systemFolderId = systemFolderId,
                affectedAction = "Upload der Testdatei",
                errorMessage = "Google Drive hat keine gültige Datei-ID zurückgegeben."
            )
        }

        // 4. Download file using exact testFileId
        var downloadedContent = ""
        try {
            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$testFileId?alt=media"
            val downloadRequest = Request.Builder()
                .url(downloadUrl)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()

            executeDriveRequest(downloadRequest).use { response ->
                if (response.code == 401 || response.code == 403) {
                    return DriveTestApiResult(
                        mainFolderId = mainFolderId,
                        systemFolderId = systemFolderId,
                        testFileId = testFileId,
                        uploadSuccess = true,
                        affectedAction = "Download der Testdatei",
                        errorMessage = "Zugriff verweigert beim Download (HTTP ${response.code}). Bitte erneut anmelden.",
                        httpStatusCode = response.code,
                        isReAuthRequired = true
                    )
                }
                if (!response.isSuccessful) {
                    return DriveTestApiResult(
                        mainFolderId = mainFolderId,
                        systemFolderId = systemFolderId,
                        testFileId = testFileId,
                        uploadSuccess = true,
                        affectedAction = "Download der Testdatei",
                        errorMessage = "Download fehlgeschlagen: HTTP ${response.code} ${response.message}",
                        httpStatusCode = response.code
                    )
                }
                downloadedContent = response.body?.string() ?: ""
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            return DriveTestApiResult(
                mainFolderId = mainFolderId,
                systemFolderId = systemFolderId,
                testFileId = testFileId,
                uploadSuccess = true,
                affectedAction = "Download der Testdatei",
                errorMessage = "Fehler beim Download: ${e.message}"
            )
        }

        // 5. Compare downloaded content with uploaded content
        val isContentIdentical = try {
            val uploadedObj = JSONObject(uploadJsonContent)
            val downloadedObj = JSONObject(downloadedContent)
            uploadedObj.optBoolean("test") == downloadedObj.optBoolean("test") &&
            uploadedObj.optString("app") == downloadedObj.optString("app") &&
            uploadedObj.optString("message") == downloadedObj.optString("message") &&
            uploadedObj.optString("createdAt") == downloadedObj.optString("createdAt")
        } catch (e: Exception) {
            uploadJsonContent.trim() == downloadedContent.trim()
        }

        if (!isContentIdentical) {
            return DriveTestApiResult(
                mainFolderId = mainFolderId,
                systemFolderId = systemFolderId,
                testFileId = testFileId,
                uploadSuccess = true,
                downloadSuccess = true,
                contentIdentical = false,
                affectedAction = "Inhaltsprüfung",
                errorMessage = "Der heruntergeladene Datei-Inhalt weicht vom hochgeladenen Inhalt ab."
            )
        }

        // Everything succeeded!
        return DriveTestApiResult(
            mainFolderId = mainFolderId,
            systemFolderId = systemFolderId,
            testFileId = testFileId,
            uploadSuccess = true,
            downloadSuccess = true,
            contentIdentical = true
        )
    }

    suspend fun findMetadataFileByProperties(
        accessToken: String,
        folderId: String,
        receiptInternalId: String
    ): DriveFile? {
        val q = "'$folderId' in parents and appProperties has { key='appName' and value='${DriveEnvironment.appIdentifier}' } and appProperties has { key='entityType' and value='receiptMetadata' } and appProperties has { key='receiptInternalId' and value='$receiptInternalId' } and trashed = false"
        try {
            val encodedQ = URLEncoder.encode(q, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id,name)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            executeDriveRequest(request).use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val files = json.optJSONArray("files")
                    if (files != null && files.length() > 0) {
                        val fileObj = files.getJSONObject(0)
                        return DriveFile(fileObj.getString("id"), fileObj.getString("name"))
                    }
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.e("GoogleDriveClient", "Error finding metadata file by properties")
        }
        return null
    }

    suspend fun listAllReceiptMetadataFiles(
        accessToken: String,
        folderId: String
    ): List<DriveMetadataFileInfo> {
        val q = "'$folderId' in parents and trashed = false"
        val list = mutableListOf<DriveMetadataFileInfo>()
        try {
            val encodedQ = URLEncoder.encode(q, "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&fields=files(id,name,createdTime,modifiedTime,appProperties)"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .build()
            executeDriveRequest(request).use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val files = json.optJSONArray("files")
                    if (files != null) {
                        for (i in 0 until files.length()) {
                            val fileObj = files.getJSONObject(i)
                            val name = fileObj.getString("name")
                            // We are only interested in JSON files representing receipt metadata
                            if (!name.lowercase().endsWith(".json") || name.lowercase() == "receipt-index.json") {
                                continue
                            }
                            val id = fileObj.getString("id")
                            val createdTime = fileObj.optString("createdTime", "")
                            val modifiedTime = fileObj.optString("modifiedTime", "")
                            val appPropertiesObj = fileObj.optJSONObject("appProperties")
                            
                            // Get internalId from appProperties, or extract from filename
                            var receiptInternalId = appPropertiesObj?.optString("receiptInternalId", "") ?: ""
                            if (receiptInternalId.isBlank()) {
                                receiptInternalId = name.removeSuffix(".json").removeSuffix(".JSON")
                            }
                            
                            list.add(
                                DriveMetadataFileInfo(
                                    id = id,
                                    name = name,
                                    createdTime = createdTime,
                                    modifiedTime = modifiedTime,
                                    receiptInternalId = receiptInternalId
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.rethrowDriveControlFailure()
            DiagnosticLog.e("GoogleDriveClient", "Error listing all metadata files")
        }
        return list
    }
}

data class DriveMetadataFileInfo(
    val id: String,
    val name: String,
    val createdTime: String,
    val modifiedTime: String,
    val receiptInternalId: String
)

data class DriveTestApiResult(
    val mainFolderId: String = "",
    val systemFolderId: String = "",
    val testFileId: String = "",
    val uploadSuccess: Boolean = false,
    val downloadSuccess: Boolean = false,
    val contentIdentical: Boolean = false,
    val affectedAction: String? = null,
    val errorMessage: String? = null,
    val httpStatusCode: Int? = null,
    val isReAuthRequired: Boolean = false
)

data class DriveFolder(val id: String, val name: String)
data class DriveFile(val id: String, val name: String)
data class DriveManagedFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val parentIds: List<String>,
    val appProperties: Map<String, String> = emptyMap()
)
data class DriveFileResult(val success: Boolean, val fileId: String?, val errorMessage: String? = null)

data class DriveFileInfoAndHeader(
    val fileId: String,
    val name: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val isReachable: Boolean = false,
    val headerBytes: ByteArray = byteArrayOf(),
    val errorMessage: String? = null
)

data class MagicBytesDetection(
    val formatName: String,
    val standardMimeType: String,
    val standardExtension: String,
    val isValidReceiptFormat: Boolean
)

fun detectMagicBytesFormat(header: ByteArray): MagicBytesDetection {
    if (header.isEmpty()) {
        return MagicBytesDetection("Unbekannt", "", "", false)
    }

    val utf8String = try {
        val str = String(header, Charsets.UTF_8)
        str.replace("\uFEFF", "").trimStart()
    } catch (e: Exception) {
        e.rethrowDriveControlFailure()
        ""
    }

    if (utf8String.startsWith("{") || utf8String.startsWith("[")) {
        return MagicBytesDetection("JSON / Text", "application/json", "json", isValidReceiptFormat = false)
    }

    val hasPdfSignature = (header.size >= 5 && header.copyOfRange(0, 5).contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D))) ||
            utf8String.startsWith("%PDF-")
    if (hasPdfSignature) {
        return MagicBytesDetection("PDF", "application/pdf", "pdf", isValidReceiptFormat = true)
    }

    if (header.size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
        return MagicBytesDetection("JPEG", "image/jpeg", "jpg", isValidReceiptFormat = true)
    }

    val pngPattern = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    if (header.size >= 8 && header.copyOfRange(0, 8).contentEquals(pngPattern)) {
        return MagicBytesDetection("PNG", "image/png", "png", isValidReceiptFormat = true)
    }

    if (header.size >= 12 &&
        header.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII)) &&
        header.copyOfRange(8, 12).contentEquals("WEBP".toByteArray(Charsets.US_ASCII))
    ) {
        return MagicBytesDetection("WEBP", "image/webp", "webp", isValidReceiptFormat = true)
    }

    val zipPattern = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    if (header.size >= 4 && header.copyOfRange(0, 4).contentEquals(zipPattern)) {
        return MagicBytesDetection("ZIP Archiv", "application/zip", "zip", isValidReceiptFormat = false)
    }

    return MagicBytesDetection("Unbekannt", "", "", isValidReceiptFormat = false)
}
