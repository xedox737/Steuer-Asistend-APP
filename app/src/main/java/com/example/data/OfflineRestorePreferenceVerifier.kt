package com.example.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Strictly parses the journal-bound business preference snapshot without writing live preferences. */
internal object OfflineRestorePreferenceVerifier {
    data class Checked(val index: OfflineRestoreRollbackVerifier.Index, val payload: JSONObject)
    private const val MAX_BYTES = 64L * 1024 * 1024
    private val integerPattern = Regex("0|-?[1-9][0-9]{0,18}")

    suspend fun verify(context: Context, journal: OfflineRestoreJournal.Entry): Checked = withContext(Dispatchers.IO) {
        try {
            val index = OfflineRestoreRollbackVerifier.verifyIndex(context, journal)
            val file = index.preferences
            val runtime = Runtime.getRuntime()
            val free = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()
            require(file.length() in 1..MAX_BYTES && file.length() <= (free - 32L * 1024 * 1024) / 6)
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            val cancellation = currentCoroutineContext()
            val stores = PersistentPreferenceInventory.stores.filter { it.kind == PersistentPreferenceInventory.Kind.BACKUP }
                .associateBy { requireNotNull(it.payloadKey) }
            val root = JSONObject(); val seenStores = hashSetOf<String>()
            JsonReader(InputStreamReader(FileInputStream(file), decoder)).use { reader ->
                reader.isLenient = false; reader.beginObject()
                while (reader.hasNext()) {
                    cancellation.ensureActive(); val name = reader.nextName()
                    val store = requireNotNull(stores[name]); require(seenStores.add(name))
                    val values = JSONObject(); val keys = hashSetOf<String>(); reader.beginObject()
                    while (reader.hasNext()) {
                        cancellation.ensureActive(); val key = reader.nextName()
                        if (keys.size % 256 == 0) {
                            val available = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()
                            require(available > 32L * 1024 * 1024)
                        }
                        require(keys.add(key) && PersistentPreferenceInventory.permits(store, key))
                        reader.beginObject()
                        require(reader.hasNext() && reader.nextName() == "type" && reader.peek() == JsonToken.STRING)
                        val type = reader.nextString()
                        require(reader.hasNext() && reader.nextName() == "value")
                        val value: Any = when (type) {
                            "string" -> { require(reader.peek() == JsonToken.STRING); reader.nextString() }
                            "int" -> integer(reader).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) }.toInt()
                            "long" -> integer(reader)
                            "float" -> {
                                require(reader.peek() == JsonToken.NUMBER)
                                val number = reader.nextString().toDouble(); val float = number.toFloat()
                                require(number.isFinite() && float.isFinite() && float.toDouble() == number); number
                            }
                            "boolean" -> { require(reader.peek() == JsonToken.BOOLEAN); reader.nextBoolean() }
                            "stringSet" -> {
                                val array = JSONArray(); var previous: String? = null
                                reader.beginArray()
                                while (reader.hasNext()) {
                                    cancellation.ensureActive(); require(reader.peek() == JsonToken.STRING)
                                    val item = reader.nextString(); require(previous == null || requireNotNull(previous) < item)
                                    array.put(item); previous = item
                                }
                                reader.endArray(); array
                            }
                            else -> throw IllegalArgumentException()
                        }
                        require(!reader.hasNext()); reader.endObject()
                        values.put(key, JSONObject().put("type", type).put("value", value))
                    }
                    reader.endObject(); root.put(name, values)
                }
                reader.endObject(); require(reader.peek() == JsonToken.END_DOCUMENT && seenStores == stores.keys)
            }
            // Detect any change of the journal, index or preference file during parsing.
            require(OfflineRestoreRollbackVerifier.verifyIndex(context, journal) == index)
            Checked(index, root)
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
        catch (_: JSONException) { invalid() }
    }
    private fun integer(reader: JsonReader): Long {
        require(reader.peek() == JsonToken.NUMBER)
        val value = reader.nextString(); require(integerPattern.matches(value))
        return requireNotNull(value.toLongOrNull())
    }
    private fun invalid(): Nothing = throw OfflineBackupValidationException("Die Einstellungen des Rückfallstands sind unvollständig oder ungültig. Der Bestand bleibt gesperrt.")
}
