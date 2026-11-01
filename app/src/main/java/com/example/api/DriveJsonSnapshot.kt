package com.example.api

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** A freshly read, owned JSON source and its HTTP concurrency precondition; never persisted. */
internal data class DriveOwnedMetadata(val json: JSONObject,val etag: String?)

internal data class DriveJsonSnapshot(val fileId: String,val read: DriveRestoreJsonRead,val etag: String?, val version: String = "",
    val rawFingerprint: DriveOriginalFingerprint? = null)

internal val driveReferenceFields=setOf("rootFolderId","systemFolderId","receiptsFolderId","paymentsFolderId","exportsFolderId",
    "transactionsFolderId","backupsFolderId","driveFileId","driveFolderId","driveMetadataFileId","metadataFileId",
    "mainDriveFileId","previousMainDriveFileId","previousMetadataFileId","legacyDriveFolderId")

/** Stable entity identity, never list position, when an array carries business IDs. */
internal fun visitDriveReferences(value: Any?,path: String="",visit: (String,String,JSONObject,String)->Unit) {
    when(value) {
        is JSONObject -> value.keys().forEach { key ->
            val item=value.opt(key);val location="$path/${JSONObject.quote(key)}"
            if(key in driveReferenceFields && item is String && item.isNotBlank()) visit(key,item,value,location)
            visitDriveReferences(item,location,visit)
        }
        is JSONArray -> {
            for(i in 0 until value.length()) {
                val item=value.opt(i)
                val identity=(item as? JSONObject)?.let { o -> listOf("internalId","documentId","id").firstNotNullOfOrNull { key ->
                    o.optString(key).takeIf { it.isNotBlank() && it!="null" }?.let { "$key=${JSONObject.quote(it)}" } } } ?: "index=$i"
                visitDriveReferences(item,"$path/[$identity]",visit)
            }
        }
    }
}

/** Object order is irrelevant; array order and exact string values (including raw approval JSON) stay authoritative. */
internal fun canonicalDriveJson(value: Any?): String = when(value) {
    is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",","{","}") { JSONObject.quote(it)+":"+canonicalDriveJson(value.opt(it)) }
    is JSONArray -> (0 until value.length()).joinToString(",","[","]") { canonicalDriveJson(value.opt(it)) }
    null,JSONObject.NULL -> "null"
    is String -> JSONObject.quote(value)
    else -> value.toString()
}
internal fun canonicalDriveJsonText(value: String): String = canonicalDriveJson(JSONTokener(value).nextValue())
