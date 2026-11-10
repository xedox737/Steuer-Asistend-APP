package com.example.data

import android.content.ContentValues
import android.content.SharedPreferences
import android.database.Cursor
import android.database.CursorWrapper
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import java.util.Locale
import java.util.WeakHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Optimistic capture boundary. Not yet used by the exporter: preference, file and logical
 * operation writers must also be connected before this can prove a complete snapshot.
 */
internal class OfflineBackupCoordinator {
    private val monitor = Any()
    private var generation = 0L
    private var activeWriters = 0L
    private var blockedReason: String? = null
    private val initializations = mutableSetOf<CompletableDeferred<Unit>>()
    private val preferences = IdentityHashMap<SharedPreferences, SharedPreferences>()

    internal class Capture internal constructor(internal val owner: OfflineBackupCoordinator, internal val generation: Long)

    private fun advance() {
        if (generation == Long.MAX_VALUE) blockedReason = "Die Snapshot-Koordination muss neu gestartet werden."
        else generation++
    }

    fun beginWrite(): Closeable = synchronized(monitor) {
        advance()
        activeWriters++
        object : Closeable {
            private var closed = false
            override fun close() = synchronized(monitor) {
                if (!closed) {
                    closed = true
                    activeWriters--
                    advance()
                }
            }
        }
    }

    fun <T> writing(action: () -> T): T = beginWrite().use { action() }

    /** Registers the entire mutation, including queued entry and structured child jobs. */
    fun launch(
        scope: CoroutineScope,
        context: CoroutineContext = EmptyCoroutineContext,
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        val writer = beginWrite()
        try {
            return scope.launch(context, start, block).also { job ->
                // Runs even if cancellation prevents the coroutine body from entering.
                job.invokeOnCompletion { writer.close() }
            }
        } catch (error: Throwable) {
            writer.close()
            throw error
        }
    }

    /** Registers before enqueue and through actual I/O completion, independently of callbacks. */
    fun executor(delegate: java.util.concurrent.Executor): java.util.concurrent.Executor =
        java.util.concurrent.Executor { command ->
            val writer = beginWrite()
            try {
                delegate.execute {
                    try { command.run() } finally { writer.close() }
                }
            } catch (error: Throwable) {
                // Also safe for synchronous delegates: the registration closes only once.
                writer.close()
                throw error
            }
        }

    fun trackPreferences(preferences: SharedPreferences): SharedPreferences = synchronized(monitor) {
        this.preferences.getOrPut(preferences) { TrackedPreferences(preferences, this) }
    }

    fun blockSnapshots(reason: String) = synchronized(monitor) {
        blockedReason = blockedReason ?: reason
        advance()
    }

    fun initializationFailed() = blockSnapshots("Die Datenbankinitialisierung wurde nicht vollständig abgeschlossen.")

    internal class Initialization internal constructor(private val finishAction: (Boolean) -> Unit) {
        fun finish(failed: Boolean) = finishAction(failed)
    }

    fun beginInitialization(): Initialization = synchronized(monitor) {
        val completed = CompletableDeferred<Unit>()
        val writer = beginWrite()
        initializations.add(completed)
        Initialization { failed ->
            val finished = synchronized(monitor) {
                if (initializations.remove(completed)) {
                    if (failed) initializationFailed()
                    writer.close()
                    true
                } else false
            }
            // Resume callers outside the monitor, after failure state and writer release.
            if (finished) completed.complete(Unit)
        }
    }

    /** Await known startup jobs once; later registrations invalidate the capture as usual. */
    suspend fun awaitInitialization() {
        val pending = synchronized(monitor) { initializations.toList() }
        pending.forEach { it.await() }
    }

    fun beginCapture(): Capture = synchronized(monitor) {
        blockedReason?.let { throw OfflineBackupValidationException(it) }
        if (activeWriters != 0L) throw OfflineBackupValidationException("Daten werden gerade geändert. Bitte die Sicherung später erneut starten.")
        Capture(this, generation)
    }

    /** Call only after all captured metadata and original bytes are fixed in private staging. */
    fun completeCapture(capture: Capture) = synchronized(monitor) {
        blockedReason?.let { throw OfflineBackupValidationException(it) }
        if (capture.owner !== this || activeWriters != 0L || capture.generation != generation) {
            throw OfflineBackupValidationException("Daten wurden während der Erfassung geändert. Die Sicherung wurde abgebrochen.")
        }
    }

    // Room 2.7.0 Builder.openHelperFactory retains its Framework default delegate.
    // Sources and exact versions: docs/backup-persistence-inventory.md (SQLite boundary).
    fun sqliteFactory(): SupportSQLiteOpenHelper.Factory = SupportSQLiteOpenHelper.Factory { configuration ->
        val databases = IdentityHashMap<SupportSQLiteDatabase, TrackedDatabase>()
        fun wrap(database: SupportSQLiteDatabase): TrackedDatabase = synchronized(databases) {
            databases.getOrPut(database) { TrackedDatabase(database, this) }
        }
        val original = configuration.callback
        val callback = object : SupportSQLiteOpenHelper.Callback(original.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = writing { original.onConfigure(wrap(db)) }
            override fun onCreate(db: SupportSQLiteDatabase) = writing { original.onCreate(wrap(db)) }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                writing { original.onUpgrade(wrap(db), oldVersion, newVersion) }
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
                writing { original.onDowngrade(wrap(db), oldVersion, newVersion) }
            override fun onOpen(db: SupportSQLiteDatabase) = writing {
                val wrapped = wrap(db)
                wrapped.finishNativeTransaction()
                original.onOpen(wrapped)
            }
            override fun onCorruption(db: SupportSQLiteDatabase) = writing { original.onCorruption(wrap(db)) }
        }
        val forwarded = SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
            .name(configuration.name).callback(callback)
            .noBackupDirectory(configuration.useNoBackupDirectory)
            .allowDataLossOnRecovery(configuration.allowDataLossOnRecovery).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(forwarded)
        fun opened(action: () -> SupportSQLiteDatabase): SupportSQLiteDatabase {
            try { return wrap(action()) } finally {
                // SQLiteOpenHelper commits/rolls back its creation/migration transaction
                // internally, outside SupportSQLiteDatabase.endTransaction (also on error).
                val existing = synchronized(databases) { databases.values.toList() }
                existing.forEach { it.finishNativeTransaction() }
            }
        }
        object : SupportSQLiteOpenHelper by helper {
            override val writableDatabase: SupportSQLiteDatabase get() = opened { helper.writableDatabase }
            override val readableDatabase: SupportSQLiteDatabase get() = opened { helper.readableDatabase }
            override fun setWriteAheadLoggingEnabled(enabled: Boolean) = writing { helper.setWriteAheadLoggingEnabled(enabled) }
        }
    }

    companion object {
        val shared = OfflineBackupCoordinator()
    }

    private class TrackedPreferences(
        private val preferences: SharedPreferences,
        private val coordinator: OfflineBackupCoordinator
    ) : SharedPreferences by preferences {
        private val listeners = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun edit(): SharedPreferences.Editor {
            val editor = preferences.edit()
            return object : SharedPreferences.Editor by editor {
                // Each chained call must return this adapter, never the raw Editor.
                override fun putString(key: String?, value: String?): SharedPreferences.Editor { editor.putString(key, value); return this }
                override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { editor.putStringSet(key, values); return this }
                override fun putInt(key: String?, value: Int): SharedPreferences.Editor { editor.putInt(key, value); return this }
                override fun putLong(key: String?, value: Long): SharedPreferences.Editor { editor.putLong(key, value); return this }
                override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { editor.putFloat(key, value); return this }
                override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { editor.putBoolean(key, value); return this }
                override fun remove(key: String?): SharedPreferences.Editor { editor.remove(key); return this }
                override fun clear(): SharedPreferences.Editor { editor.clear(); return this }
                override fun commit(): Boolean = coordinator.writing { editor.commit() }
                override fun apply() = coordinator.writing { editor.apply() }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            if (listener == null) { preferences.registerOnSharedPreferenceChangeListener(null); return }
            synchronized(listeners) {
                val proxy = listeners.getOrPut(listener) {
                    val reference = WeakReference(listener)
                    SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        reference.get()?.onSharedPreferenceChanged(this@TrackedPreferences, key)
                    }
                }
                preferences.registerOnSharedPreferenceChangeListener(proxy)
            }
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            synchronized(listeners) {
                preferences.unregisterOnSharedPreferenceChangeListener(listeners.remove(listener) ?: listener)
            }
        }
    }

    private class TrackedDatabase(
        private val database: SupportSQLiteDatabase,
        private val coordinator: OfflineBackupCoordinator
    ) : SupportSQLiteDatabase by database {
        private val transactionWriter = ThreadLocal<Closeable?>()

        fun finishNativeTransaction() {
            if (transactionWriter.get() != null) settleTransaction(create = false)
        }

        // SQLite 2.5.0: inTransaction is thread-bound; nested work commits/rolls back
        // at the outer endTransaction. Keep the lease across gaps between statements.
        private fun settleTransaction(create: Boolean) {
            // Framework 2.5.0 closes/reopens the native connection after migration.
            // A closed connection has already ended its transaction; querying it throws.
            val inTransaction = try { database.isOpen && database.inTransaction() } catch (_: Exception) {
                coordinator.blockSnapshots("Der Datenbankzustand konnte nicht vollständig geprüft werden.")
                false
            }
            if (inTransaction) {
                if (create && transactionWriter.get() == null) transactionWriter.set(coordinator.beginWrite())
            } else {
                val writer = transactionWriter.get()
                transactionWriter.remove()
                writer?.close()
            }
        }

        private fun <T> mutate(action: () -> T): T = coordinator.beginWrite().use {
            try { action() } finally { settleTransaction(create = true) }
        }

        override fun endTransaction() {
            try { database.endTransaction() } finally { settleTransaction(create = false) }
        }
        override fun yieldIfContendedSafely(): Boolean = try { database.yieldIfContendedSafely() }
            finally { settleTransaction(create = false) }
        override fun yieldIfContendedSafely(sleepAfterYieldDelayMillis: Long): Boolean =
            try { database.yieldIfContendedSafely(sleepAfterYieldDelayMillis) }
            finally { settleTransaction(create = false) }

        override fun compileStatement(sql: String): SupportSQLiteStatement {
            val statement = database.compileStatement(sql)
            return object : SupportSQLiteStatement by statement {
                override fun execute() = mutate { statement.execute() }
                override fun executeInsert(): Long = mutate { statement.executeInsert() }
                override fun executeUpdateDelete(): Int = mutate { statement.executeUpdateDelete() }
                override fun simpleQueryForLong(): Long = if (readOnly(sql)) statement.simpleQueryForLong()
                    else mutate { statement.simpleQueryForLong() }
                override fun simpleQueryForString(): String? = if (readOnly(sql)) statement.simpleQueryForString()
                    else mutate { statement.simpleQueryForString() }
            }
        }

        // Unknown row-returning SQL can mutate (e.g. WITH ... UPDATE ... RETURNING).
        // Keep it registered through cursor consumption, not just query construction.
        override fun query(query: String): Cursor = query(SimpleSQLiteQuery(query))
        override fun query(query: String, bindArgs: Array<out Any?>): Cursor = query(SimpleSQLiteQuery(query, bindArgs))
        override fun query(query: SupportSQLiteQuery): Cursor = query(query, null)
        private fun delegateQuery(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor =
            // SQLite Framework 2.5.0 dereferences cancellationSignal!! in this overload.
            if (cancellationSignal == null) database.query(query) else database.query(query, cancellationSignal)

        override fun query(query: SupportSQLiteQuery, cancellationSignal: CancellationSignal?): Cursor {
            if (readOnly(query.sql)) return delegateQuery(query, cancellationSignal)
            val writer = coordinator.beginWrite()
            try {
                val cursor = delegateQuery(query, cancellationSignal)
                settleTransaction(create = true)
                return object : CursorWrapper(cursor) {
                    override fun close() {
                        try { super.close() } finally {
                            try { settleTransaction(create = false) } finally { writer.close() }
                        }
                    }
                }
            } catch (error: Throwable) {
                try { settleTransaction(create = true) } finally { writer.close() }
                throw error
            }
        }

        override fun execSQL(sql: String) = mutate { database.execSQL(sql) }
        override fun execSQL(sql: String, bindArgs: Array<out Any?>) = mutate { database.execSQL(sql, bindArgs) }
        override fun insert(table: String, conflictAlgorithm: Int, values: ContentValues): Long =
            mutate { database.insert(table, conflictAlgorithm, values) }
        override fun update(table: String, conflictAlgorithm: Int, values: ContentValues, whereClause: String?, whereArgs: Array<out Any?>?): Int =
            mutate { database.update(table, conflictAlgorithm, values, whereClause, whereArgs) }
        override fun delete(table: String, whereClause: String?, whereArgs: Array<out Any?>?): Int =
            mutate { database.delete(table, whereClause, whereArgs) }
        override var version: Int
            get() = database.version
            set(value) { mutate { database.version = value } }
        override var pageSize: Long
            get() = database.pageSize
            set(value) { mutate { database.pageSize = value } }
        override fun setMaximumSize(numBytes: Long): Long = mutate { database.setMaximumSize(numBytes) }
        override fun setLocale(locale: Locale) = mutate { database.setLocale(locale) }
        override fun setForeignKeyConstraintsEnabled(enabled: Boolean) = mutate { database.setForeignKeyConstraintsEnabled(enabled) }
        override fun enableWriteAheadLogging(): Boolean = mutate { database.enableWriteAheadLogging() }
        override fun disableWriteAheadLogging() = mutate { database.disableWriteAheadLogging() }
        override fun execPerConnectionSQL(sql: String, bindArgs: Array<out Any?>?) {
            // This API can run SQL later on newly opened connections. Current app has no
            // caller; preserve its behavior but refuse a completeness claim if introduced.
            coordinator.blockSnapshots("Verzögerte SQL-Schreibzugriffe sind für die Sicherung nicht freigegeben.")
            mutate { database.execPerConnectionSQL(sql, bindArgs) }
        }

        private fun readOnly(sql: String): Boolean {
            val normalized = sql.trim().uppercase(Locale.ROOT)
            return normalized.startsWith("SELECT ") || normalized.startsWith("SELECT\n") ||
                Regex("PRAGMA\\s+(TABLE_INFO|TABLE_XINFO|FOREIGN_KEY_LIST|FOREIGN_KEY_CHECK|INTEGRITY_CHECK|QUICK_CHECK)\\b.*", RegexOption.DOT_MATCHES_ALL).matches(normalized)
        }
    }
}
