package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

@JsonClass(generateAdapter = true)
data class ReceiptItem(
    @Json(name = "bezeichnung") val bezeichnung: String = "",
    @Json(name = "menge") val menge: Double = 1.0,
    @Json(name = "einzelpreis") val einzelpreis: Double = 0.0,
    @Json(name = "gesamtpreis") val gesamtpreis: Double = 0.0,
    @Json(name = "hauptkategorie") val hauptkategorie: String = "",
    @Json(name = "unterkategorie") val unterkategorie: String = "",
    @Json(name = "wohneinheit") val wohneinheit: String = "",
    @Json(name = "massnahme") val massnahme: String = "",
    @Json(name = "kontoNr") val kontoNr: String = "",
    @Json(name = "buSchluessel") val buSchluessel: String = "",
    @Json(name = "steuersatz") val steuersatz: Double = 0.0,
    @Json(name = "privatanteilProzent") val privatanteilProzent: Double = 0.0
)

object ReceiptItemConverter {
    private val moshi by lazy {
        Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    fun parseJson(json: String?): List<ReceiptItem> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, ReceiptItem::class.java)
            val adapter = moshi.adapter<List<ReceiptItem>>(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun toJson(items: List<ReceiptItem>?): String {
        if (items.isNullOrEmpty()) return ""
        return try {
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, ReceiptItem::class.java)
            val adapter = moshi.adapter<List<ReceiptItem>>(type)
            adapter.toJson(items)
        } catch (e: Exception) {
            ""
        }
    }
}

@Entity(
    tableName = "receipts",
    indices = [
        Index(value = ["internalId"], name = "index_receipts_internalId"),
        Index(value = ["driveFileId"], name = "index_receipts_driveFileId"),
        Index(value = ["driveMetadataFileId"], name = "index_receipts_driveMetadataFileId")
    ]
)
data class Receipt(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val aussteller: String,
    val datum: String, // YYYY-MM-DD
    val uhrzeit: String, // HH:MM or empty
    val bruttobetrag: Double,
    val hauptkategorie: String,
    val unterkategorie: String,
    val kontoNr: String,
    val beschreibung: String,
    val isEigenleistungSanierung: Boolean = false,
    val imageUrl: String = "", // Optional simulation reference
    val wohneinheit: String = "", // Associated housing unit
    val mieter: String = "", // Mieter name
    val isArchivedToDrive: Boolean = false,
    val positionenJson: String = "", // Serialized JSON list of ReceiptItem
    val exportStatus: String = "EXPORTBEREIT", // ENTWURF, KI_VORSCHLAG, ZU_PRUEFEN, GEPRUEFT, EXPORTBEREIT, EXPORTIERT, AUSGESCHLOSSEN
    val pruefstatus: String = "GEPRUEFT",      // UNGEPRUEFT, GEPRUEFT, KORRIGIERT
    val exportlaufId: String = "",
    
    // New Google Drive Persistence fields
    val internalId: String = "",
    val displayId: String? = null,
    val driveFileId: String? = null,
    val driveFolderId: String? = null,
    val driveMetadataFileId: String? = null,
    val storedFilename: String? = null,
    val originalMimeType: String? = null,
    val fileSizeBytes: Long? = null,
    val syncStatus: String = "PENDING", // PENDING, SYNCED, ERROR, REVIEW_REQUIRED
    val syncError: String? = null,
    val lastSyncedAt: String? = null,
    val driveRevision: Long? = null,
    val deletionStatus: String = "ACTIVE", // ACTIVE, DELETED, RESTORED, DELETE_PENDING
    val deletedAt: String? = null,
    val deletedBy: String? = null,
    val deletionId: String? = null,
    val deletionReason: String? = null
) {
    fun getPositionenList(): List<ReceiptItem> = ReceiptItemConverter.parseJson(positionenJson)

    fun getEffectiveDisplayId(): String {
        val raw = displayId?.takeIf { it.isNotBlank() }
            ?: internalId.takeIf { it.isNotBlank() }
            ?: id.toString()
        return if (raw.startsWith("BLG-")) raw else "BLG-$raw"
    }
}

@Dao
interface ReceiptDao {
    @Query("SELECT * FROM receipts WHERE deletionStatus = 'ACTIVE' OR deletionStatus IS NULL OR deletionStatus = '' ORDER BY datum DESC, uhrzeit DESC")
    fun getAllReceipts(): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE deletionStatus = 'ACTIVE' OR deletionStatus IS NULL OR deletionStatus = ''")
    suspend fun getAllReceiptsList(): List<Receipt>

    @Query("SELECT * FROM receipts WHERE deletionStatus IN ('DELETED', 'DELETE_PENDING') ORDER BY datum DESC, uhrzeit DESC")
    fun getDeletedReceipts(): Flow<List<Receipt>>

    @Query("SELECT * FROM receipts WHERE deletionStatus IN ('DELETED', 'DELETE_PENDING')")
    suspend fun getDeletedReceiptsList(): List<Receipt>

    @Query("SELECT * FROM receipts WHERE deletionStatus = 'DELETE_PENDING'")
    suspend fun getPendingDeletionReceipts(): List<Receipt>

    @Query("SELECT * FROM receipts")
    suspend fun getAllReceiptsIncludingDeletedList(): List<Receipt>

    @Query("SELECT * FROM receipts WHERE id = :id")
    suspend fun getReceiptById(id: Int): Receipt?

    @Query("SELECT * FROM receipts WHERE internalId = :internalId ORDER BY id LIMIT 1")
    suspend fun getReceiptByInternalId(internalId: String): Receipt?

    @Query("SELECT * FROM receipts WHERE driveFileId = :mainDriveFileId ORDER BY id LIMIT 1")
    suspend fun getReceiptByMainDriveFileId(mainDriveFileId: String): Receipt?

    @Query("SELECT * FROM receipts WHERE driveMetadataFileId = :metadataFileId ORDER BY id")
    suspend fun getReceiptsByMetadataFileId(metadataFileId: String): List<Receipt>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: Receipt): Long

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun deleteReceiptById(id: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(receipts: List<Receipt>)

    @Query("DELETE FROM receipts")
    suspend fun deleteAll()
}

@Entity(tableName = "property_metadata")
data class PropertyMetadata(
    @PrimaryKey val id: Int = 1,
    val name: String = "7-Familienhaus (Anlage V)",
    val adresse: String = "Musterstraße 42, 12345 Musterstadt",
    val wohnort: String = "Hauptstraße 1, 12345 Wohnstadt",
    val baujahr: Int = 1985,
    val wohnflaeche: Double = 420.0,
    val grundstuecksgroesse: Double = 650.0,
    val notariellesKaufdatum: String = "2025-10-01",
    val uebergangNutzenLasten: String = "2026-01-01",
    val wohneinheiten: String = "WE 1, WE 2, WE 3, WE 4, WE 5, WE 6, WE 7",
    val gesamtKaufpreis: Double = 250000.0,
    val gebaeudewert: Double = 200000.0
)

@Dao
interface PropertyDao {
    @Query("SELECT * FROM property_metadata WHERE id = 1")
    fun getPropertyMetadataFlow(): Flow<PropertyMetadata?>

    @Query("SELECT * FROM property_metadata WHERE id = 1")
    suspend fun getPropertyMetadata(): PropertyMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPropertyMetadata(metadata: PropertyMetadata)
}

@Dao
interface ReceiptEntityDao {
    @Query("SELECT * FROM receipt_entities ORDER BY datum DESC, id DESC")
    fun getAllEntities(): Flow<List<ReceiptEntity>>

    @Query("SELECT * FROM receipt_entities WHERE id = :id")
    suspend fun getEntityById(id: Int): ReceiptEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(receipt: ReceiptEntity): Long

    @Query("DELETE FROM receipt_entities WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM receipt_entities")
    suspend fun deleteAll()
}

@Dao
interface ExportAuditDao {
    @Query("SELECT * FROM export_audit_runs ORDER BY timestamp DESC")
    fun getAllRunsFlow(): Flow<List<ExportAuditRun>>

    @Query("SELECT * FROM export_audit_runs WHERE exportlaufId = :id")
    suspend fun getRunById(id: String): ExportAuditRun?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: ExportAuditRun)

    @Query("DELETE FROM export_audit_runs")
    suspend fun deleteAll()
}

enum class ReceiptDocumentRole {
    MAIN_RECEIPT,
    ATTACHMENT,
    DELIVERY_NOTE,
    PAYMENT_PROOF,
    EMAIL,
    E_INVOICE_XML,
    OTHER
}

@Entity(tableName = "receipt_documents")
data class ReceiptDocumentReference(
    @PrimaryKey val id: String,
    val receiptInternalId: String,
    val driveFileId: String,
    val driveFolderId: String?,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long?,
    val role: String,
    val createdAt: String
)

@Dao
interface ReceiptDocumentDao {
    @Query("SELECT * FROM receipt_documents WHERE receiptInternalId = :internalId")
    suspend fun getDocumentsForReceipt(internalId: String): List<ReceiptDocumentReference>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: ReceiptDocumentReference)

    @Query("DELETE FROM receipt_documents WHERE id = :id")
    suspend fun deleteDocumentById(id: String)

    @Query("DELETE FROM receipt_documents")
    suspend fun deleteAll()
}

val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE receipts ADD COLUMN internalId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE receipts ADD COLUMN displayId TEXT")
        db.execSQL("ALTER TABLE receipts ADD COLUMN driveFileId TEXT")
        db.execSQL("ALTER TABLE receipts ADD COLUMN driveFolderId TEXT")
        db.execSQL("ALTER TABLE receipts ADD COLUMN driveMetadataFileId TEXT")
        db.execSQL("ALTER TABLE receipts ADD COLUMN storedFilename TEXT")
        db.execSQL("ALTER TABLE receipts ADD COLUMN originalMimeType TEXT")
        db.execSQL("ALTER TABLE receipts ADD COLUMN fileSizeBytes INTEGER")
        db.execSQL("ALTER TABLE receipts ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING'")
        db.execSQL("ALTER TABLE receipts ADD COLUMN syncError TEXT")
        db.execSQL("ALTER TABLE receipts ADD COLUMN lastSyncedAt TEXT")
        db.execSQL("ALTER TABLE receipts ADD COLUMN driveRevision INTEGER")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `receipt_documents` (
                `id` TEXT NOT NULL, 
                `receiptInternalId` TEXT NOT NULL, 
                `driveFileId` TEXT NOT NULL, 
                `driveFolderId` TEXT, 
                `filename` TEXT NOT NULL, 
                `mimeType` TEXT NOT NULL, 
                `sizeBytes` INTEGER, 
                `role` TEXT NOT NULL, 
                `createdAt` TEXT NOT NULL, 
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
    }
}

val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE receipts ADD COLUMN deletionStatus TEXT NOT NULL DEFAULT 'ACTIVE'")
        db.execSQL("ALTER TABLE receipts ADD COLUMN deletedAt TEXT")
        db.execSQL("ALTER TABLE receipts ADD COLUMN deletedBy TEXT")
        db.execSQL("ALTER TABLE receipts ADD COLUMN deletionId TEXT")
        db.execSQL("ALTER TABLE receipts ADD COLUMN deletionReason TEXT")
    }
}

val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        // Intentionally non-unique: existing duplicate identities must not break app startup.
        // Restore writes are guarded by DAO-based upsert until explicit cleanup makes a UNIQUE
        // index safe for every installation.
        db.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_internalId ON receipts(internalId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_driveFileId ON receipts(driveFileId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_receipts_driveMetadataFileId ON receipts(driveMetadataFileId)")
    }
}

@Database(entities = [Receipt::class, PropertyMetadata::class, ReceiptEntity::class, Beleg::class, ExportAuditRun::class, ReceiptDocumentReference::class], version = 13, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    abstract fun propertyDao(): PropertyDao
    abstract fun receiptEntityDao(): ReceiptEntityDao
    abstract fun belegDao(): BelegDao
    abstract fun exportAuditDao(): ExportAuditDao
    abstract fun receiptDocumentDao(): ReceiptDocumentDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "receipt_database"
                )
                .addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .fallbackToDestructiveMigration()
                .addCallback(AppDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun populateDatabase(
            receiptDao: ReceiptDao,
            propertyDao: PropertyDao,
            receiptEntityDao: ReceiptEntityDao? = null,
            belegDao: BelegDao? = null
        ) {
            receiptDao.deleteAll()
            receiptEntityDao?.deleteAll()
            belegDao?.deleteAllBelege()

            val initialReceipts = listOf(
                    Receipt(
                        aussteller = "Notar Dr. Joachim Müller",
                        datum = "2025-10-05",
                        uhrzeit = "11:30",
                        bruttobetrag = 3450.00,
                        hauptkategorie = "Anschaffungskosten",
                        unterkategorie = "Notarkosten",
                        kontoNr = "0050",
                        beschreibung = "Notargebühren für Kaufvertrag (Kaufpreis 250.000 €)",
                        isEigenleistungSanierung = false
                    ),
                    Receipt(
                        aussteller = "Finanzamt Musterstadt",
                        datum = "2025-11-15",
                        uhrzeit = "",
                        bruttobetrag = 12500.00,
                        hauptkategorie = "Anschaffungskosten",
                        unterkategorie = "Grunderwerbsteuer",
                        kontoNr = "0050",
                        beschreibung = "Grunderwerbsteuerbescheid (5% von 250.000 €)",
                        isEigenleistungSanierung = false
                    ),
                    Receipt(
                        aussteller = "Amtsgericht Musterstadt",
                        datum = "2025-12-10",
                        uhrzeit = "",
                        bruttobetrag = 1120.00,
                        hauptkategorie = "Anschaffungskosten",
                        unterkategorie = "Grundbuchgebühren",
                        kontoNr = "0050",
                        beschreibung = "Eintragungsgebühr Eigentümerwechsel Kaufvertrag",
                        isEigenleistungSanierung = false
                    ),
                    Receipt(
                        aussteller = "Notar Dr. Joachim Müller",
                        datum = "2025-10-12",
                        uhrzeit = "14:15",
                        bruttobetrag = 850.00,
                        hauptkategorie = "Finanzierung, Kredite & Versicherungen",
                        unterkategorie = "Geldbeschaffungskosten",
                        kontoNr = "2120",
                        beschreibung = "Notargebühren für Grundschuldbestellung über 200.000 €",
                        isEigenleistungSanierung = false
                    ),
                    Receipt(
                        aussteller = "Sparkasse Musterstadt",
                        datum = "2025-12-30",
                        uhrzeit = "09:00",
                        bruttobetrag = 450.00,
                        hauptkategorie = "Finanzierung, Kredite & Versicherungen",
                        unterkategorie = "Geldbeschaffungskosten",
                        kontoNr = "2120",
                        beschreibung = "Bearbeitungsentgelt Darlehensvertrag",
                        isEigenleistungSanierung = false
                    ),
                    Receipt(
                        aussteller = "Sparkasse Musterstadt",
                        datum = "2026-01-30",
                        uhrzeit = "08:30",
                        bruttobetrag = 580.00,
                        hauptkategorie = "Finanzierung, Kredite & Versicherungen",
                        unterkategorie = "Kreditzinsen",
                        kontoNr = "2110",
                        beschreibung = "Sollzinsen für Immobilienkredit Jan 2026",
                        isEigenleistungSanierung = false
                    ),
                    Receipt(
                        aussteller = "OBI Baumarkt",
                        datum = "2025-10-15",
                        uhrzeit = "14:22",
                        bruttobetrag = 384.50,
                        hauptkategorie = "Renovierungs- / Reparaturkosten & Investitionen",
                        unterkategorie = "Streichen, Tapezieren",
                        kontoNr = "4830",
                        beschreibung = "Wandfarbe Alpina, Pinsel, Abdeckfolie, Farbwalzen",
                        isEigenleistungSanierung = true,
                        positionenJson = """[{"bezeichnung":"Alpina Wandfarbe 10L","menge":2.0,"einzelpreis":49.99,"gesamtpreis":99.98},{"bezeichnung":"Malertape Set","menge":5.0,"einzelpreis":4.50,"gesamtpreis":22.50},{"bezeichnung":"Profi Farbroller Set","menge":2.0,"einzelpreis":18.90,"gesamtpreis":37.80},{"bezeichnung":"Abdeckfolie 50qm","menge":4.0,"einzelpreis":6.05,"gesamtpreis":24.20}]"""
                    ),
                    Receipt(
                        aussteller = "Hornbach",
                        datum = "2025-11-08",
                        uhrzeit = "10:15",
                        bruttobetrag = 1145.20,
                        hauptkategorie = "Renovierungs- / Reparaturkosten & Investitionen",
                        unterkategorie = "Fenster, Tür & Boden",
                        kontoNr = "4830",
                        beschreibung = "Laminatboden Eiche, Trittschalldämmung, Übergangsleisten",
                        isEigenleistungSanierung = true,
                        positionenJson = """[{"bezeichnung":"Laminat Eiche Natur 2.5qm/Pck","menge":20.0,"einzelpreis":45.00,"gesamtpreis":900.00},{"bezeichnung":"Trittschalldämmung 10qm","menge":5.0,"einzelpreis":29.90,"gesamtpreis":149.50},{"bezeichnung":"Sockelleiste Eiche 2.4m","menge":12.0,"einzelpreis":7.97,"gesamtpreis":95.70}]"""
                    ),
                    Receipt(
                        aussteller = "Bauhaus",
                        datum = "2025-12-05",
                        uhrzeit = "16:45",
                        bruttobetrag = 890.00,
                        hauptkategorie = "Renovierungs- / Reparaturkosten & Investitionen",
                        unterkategorie = "Sanitär",
                        kontoNr = "4830",
                        beschreibung = "Duschkabine, Thermostatbatterie, Silikon, Fliesenkleber",
                        isEigenleistungSanierung = true,
                        positionenJson = """[{"bezeichnung":"Duschkabine Glas 90x90","menge":1.0,"einzelpreis":649.00,"gesamtpreis":649.00},{"bezeichnung":"Thermostatbatterie Dusche","menge":1.0,"einzelpreis":189.00,"gesamtpreis":189.00},{"bezeichnung":"Sanitär-Silikon 310ml","menge":2.0,"einzelpreis":13.00,"gesamtpreis":26.00},{"bezeichnung":"Fliesenkleber 25kg","menge":2.0,"einzelpreis":13.00,"gesamtpreis":26.00}]"""
                    ),
                    Receipt(
                        aussteller = "Aral Tankstelle",
                        datum = "2025-10-15",
                        uhrzeit = "13:50",
                        bruttobetrag = 65.00,
                        hauptkategorie = "Sonstige Ausgaben",
                        unterkategorie = "Fahrtkosten",
                        kontoNr = "4670",
                        beschreibung = "Kraftstoff Super E10 (Fahrt OBI & Objekt)",
                        isEigenleistungSanierung = false
                    ),
                    Receipt(
                        aussteller = "Aral Tankstelle",
                        datum = "2025-11-08",
                        uhrzeit = "09:30",
                        bruttobetrag = 70.00,
                        hauptkategorie = "Sonstige Ausgaben",
                        unterkategorie = "Fahrtkosten",
                        kontoNr = "4670",
                        beschreibung = "Kraftstoff Super E10 (Fahrt Hornbach & Objekt)",
                        isEigenleistungSanierung = false
                    ),
                    // Mietzahlungen Jan 2026
                    Receipt(
                        aussteller = "Mieter Hans Peter",
                        datum = "2026-01-02",
                        uhrzeit = "10:00",
                        bruttobetrag = 600.00,
                        hauptkategorie = "Miete, Nebenkosten & Kaution",
                        unterkategorie = "Warmmiete",
                        kontoNr = "4970",
                        beschreibung = "Miete WE 1 (Januar 2026)",
                        isEigenleistungSanierung = false,
                        wohneinheit = "WE 1"
                    ),
                    Receipt(
                        aussteller = "Mieterin Erika Mustermann",
                        datum = "2026-01-03",
                        uhrzeit = "11:15",
                        bruttobetrag = 550.00,
                        hauptkategorie = "Miete, Nebenkosten & Kaution",
                        unterkategorie = "Warmmiete",
                        kontoNr = "4970",
                        beschreibung = "Miete WE 2 (Januar 2026)",
                        isEigenleistungSanierung = false,
                        wohneinheit = "WE 2"
                    ),
                    Receipt(
                        aussteller = "Familie Schmidt",
                        datum = "2026-01-02",
                        uhrzeit = "09:45",
                        bruttobetrag = 650.00,
                        hauptkategorie = "Miete, Nebenkosten & Kaution",
                        unterkategorie = "Warmmiete",
                        kontoNr = "4970",
                        beschreibung = "Miete WE 3 (Januar 2026)",
                        isEigenleistungSanierung = false,
                        wohneinheit = "WE 3"
                    ),
                    Receipt(
                        aussteller = "Mieter Klaus & Sabine",
                        datum = "2026-01-04",
                        uhrzeit = "14:20",
                        bruttobetrag = 650.00,
                        hauptkategorie = "Miete, Nebenkosten & Kaution",
                        unterkategorie = "Warmmiete",
                        kontoNr = "4970",
                        beschreibung = "Miete WE 5 (Januar 2026)",
                        isEigenleistungSanierung = false,
                        wohneinheit = "WE 5"
                    ),
                    Receipt(
                        aussteller = "Dr. Julia Wagner",
                        datum = "2026-01-02",
                        uhrzeit = "08:10",
                        bruttobetrag = 720.00,
                        hauptkategorie = "Miete, Nebenkosten & Kaution",
                        unterkategorie = "Warmmiete",
                        kontoNr = "4970",
                        beschreibung = "Miete WE 7 (Januar 2026)",
                        isEigenleistungSanierung = false,
                        wohneinheit = "WE 7"
                    )
                )
            receiptDao.insertAll(initialReceipts)
            propertyDao.insertPropertyMetadata(PropertyMetadata())
            initialReceipts.forEachIndexed { index, receipt ->
                receiptEntityDao?.insert(
                    ReceiptEntity(
                        id = index + 1,
                        datum = receipt.datum,
                        kreditor = receipt.aussteller,
                        betrag = receipt.bruttobetrag,
                        pdfPath = receipt.imageUrl,
                        positionenJson = receipt.positionenJson
                    )
                )
                belegDao?.insertBeleg(
                    Beleg(
                        id = (index + 1).toLong(),
                        betrag = receipt.bruttobetrag,
                        datum = receipt.datum,
                        kategorie = receipt.hauptkategorie,
                        anbieter = receipt.aussteller,
                        bildPfad = receipt.imageUrl
                    )
                )
            }
        }

            private class AppDatabaseCallback(
                private val scope: CoroutineScope
            ) : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    scope.launch(Dispatchers.IO) {
                        try {
                            db.query("SELECT id, displayId, datum FROM receipts WHERE internalId IS NULL OR internalId = ''").use { cursor ->
                                while (cursor.moveToNext()) {
                                    val id = cursor.getInt(0)
                                    val existingDisplayId = if (!cursor.isNull(1)) cursor.getString(1) else null
                                    val datum = if (!cursor.isNull(2)) cursor.getString(2) else "2026-01-01"
                                    val year = try { datum.split("-").firstOrNull()?.trim() ?: "2026" } catch (e: Exception) { "2026" }
                                    val newInternalId = java.util.UUID.randomUUID().toString()
                                    val newDisplayId = existingDisplayId ?: "BLG-$year-${String.format("%06d", id)}"
                                    db.execSQL(
                                        "UPDATE receipts SET internalId = ?, displayId = ? WHERE id = ?",
                                        arrayOf(newInternalId, newDisplayId, id)
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AppDatabase", "Error migrating internalIds on open", e)
                        }
                    }
                }

                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    INSTANCE?.let { database ->
                        scope.launch(Dispatchers.IO) {
                            populateDatabase(
                                database.receiptDao(),
                                database.propertyDao(),
                                database.receiptEntityDao(),
                                database.belegDao()
                            )
                        }
                    }
                }
            }
        }
    }

class ReceiptRepository(
    private val receiptDao: ReceiptDao,
    private val propertyDao: PropertyDao,
    private val receiptEntityDao: ReceiptEntityDao? = null,
    private val belegDao: BelegDao? = null,
    private val exportAuditDao: ExportAuditDao? = null,
    private val receiptDocumentDao: ReceiptDocumentDao? = null
) {
    val allReceipts: Flow<List<Receipt>> = receiptDao.getAllReceipts()
    val deletedReceipts: Flow<List<Receipt>> = receiptDao.getDeletedReceipts()
    val propertyMetadata: Flow<PropertyMetadata?> = propertyDao.getPropertyMetadataFlow()
    val allReceiptEntities: Flow<List<ReceiptEntity>> = receiptEntityDao?.getAllEntities() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    val allBelege: Flow<List<Beleg>> = belegDao?.getAllBelege() ?: kotlinx.coroutines.flow.flowOf(emptyList())
    val allAuditRuns: Flow<List<ExportAuditRun>> = exportAuditDao?.getAllRunsFlow() ?: kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun getAllReceiptsList(): List<Receipt> {
        return receiptDao.getAllReceiptsList()
    }

    suspend fun getDeletedReceiptsList(): List<Receipt> {
        return receiptDao.getDeletedReceiptsList()
    }

    suspend fun getPendingDeletionReceipts(): List<Receipt> {
        return receiptDao.getPendingDeletionReceipts()
    }

    suspend fun getAllReceiptsIncludingDeletedList(): List<Receipt> {
        return receiptDao.getAllReceiptsIncludingDeletedList()
    }

    suspend fun softDelete(id: Int, status: String, deletedAt: String, deletedBy: String, deletionId: String, reason: String) {
        val existing = receiptDao.getReceiptById(id)
        if (existing != null) {
            val updated = existing.copy(
                deletionStatus = status,
                deletedAt = deletedAt,
                deletedBy = deletedBy,
                deletionId = deletionId,
                deletionReason = reason
            )
            receiptDao.insertReceipt(updated)
        }
    }

    suspend fun getDocumentsForReceipt(internalId: String): List<ReceiptDocumentReference> {
        return receiptDocumentDao?.getDocumentsForReceipt(internalId) ?: emptyList()
    }

    suspend fun insertDocument(doc: ReceiptDocumentReference) {
        receiptDocumentDao?.insertDocument(doc)
    }

    suspend fun deleteDocumentById(id: String) {
        receiptDocumentDao?.deleteDocumentById(id)
    }

    suspend fun insertAuditRun(run: ExportAuditRun) {
        exportAuditDao?.insertRun(run)
    }

    suspend fun getAuditRunById(id: String): ExportAuditRun? {
        return exportAuditDao?.getRunById(id)
    }

    suspend fun getReceiptById(id: Int): Receipt? {
        return receiptDao.getReceiptById(id)
    }

    suspend fun getReceiptByInternalId(internalId: String): Receipt? =
        internalId.takeIf(String::isNotBlank)?.let { receiptDao.getReceiptByInternalId(it) }

    suspend fun getReceiptByMainDriveFileId(mainDriveFileId: String?): Receipt? =
        mainDriveFileId?.takeIf(String::isNotBlank)?.let { receiptDao.getReceiptByMainDriveFileId(it) }

    suspend fun getReceiptsByMetadataFileId(metadataFileId: String): List<Receipt> =
        metadataFileId.takeIf(String::isNotBlank)?.let { receiptDao.getReceiptsByMetadataFileId(it) }.orEmpty()

    suspend fun insert(receipt: Receipt): Long {
        val preparedInternalId = if (receipt.internalId.isBlank()) java.util.UUID.randomUUID().toString() else receipt.internalId
        val year = try { receipt.datum.split("-").firstOrNull()?.trim() ?: "2026" } catch (e: Exception) { "2026" }
        val preparedDisplayId = receipt.displayId ?: "BLG-$year-${String.format("%06d", if (receipt.id != 0) receipt.id else 0)}"
        
        val preparedReceipt = receipt.copy(internalId = preparedInternalId, displayId = preparedDisplayId)
        val id = receiptDao.insertReceipt(preparedReceipt)
        val finalId = if (preparedReceipt.id != 0) preparedReceipt.id else id.toInt()
        
        val finalReceipt = if (preparedReceipt.displayId?.contains("000000") == true) {
            val correctDisplayId = "BLG-$year-${String.format("%06d", finalId)}"
            val updated = preparedReceipt.copy(id = finalId, displayId = correctDisplayId)
            receiptDao.insertReceipt(updated)
            updated
        } else {
            preparedReceipt.copy(id = finalId)
        }

        receiptEntityDao?.insert(
            ReceiptEntity(
                id = finalId,
                datum = finalReceipt.datum,
                kreditor = finalReceipt.aussteller,
                betrag = finalReceipt.bruttobetrag,
                pdfPath = finalReceipt.imageUrl,
                positionenJson = finalReceipt.positionenJson
            )
        )
        belegDao?.insertBeleg(
            Beleg(
                id = finalId.toLong(),
                betrag = finalReceipt.bruttobetrag,
                datum = finalReceipt.datum,
                kategorie = finalReceipt.hauptkategorie,
                anbieter = finalReceipt.aussteller,
                bildPfad = finalReceipt.imageUrl
            )
        )
        return id
    }

    suspend fun insertAll(receipts: List<Receipt>) {
        receiptDao.insertAll(receipts)
        receipts.forEach { receipt ->
            receiptEntityDao?.insert(
                ReceiptEntity(
                    id = receipt.id,
                    datum = receipt.datum,
                    kreditor = receipt.aussteller,
                    betrag = receipt.bruttobetrag,
                    pdfPath = receipt.imageUrl,
                    positionenJson = receipt.positionenJson
                )
            )
            belegDao?.insertBeleg(
                Beleg(
                    id = receipt.id.toLong(),
                    betrag = receipt.bruttobetrag,
                    datum = receipt.datum,
                    kategorie = receipt.hauptkategorie,
                    anbieter = receipt.aussteller,
                    bildPfad = receipt.imageUrl
                )
            )
        }
    }

    suspend fun deleteById(id: Int) {
        receiptDao.deleteReceiptById(id)
        receiptEntityDao?.deleteById(id)
        belegDao?.deleteBelegById(id.toLong())
    }

    suspend fun clearAllData() {
        clearRestoreRelevantTables()
    }

    suspend fun clearRestoreRelevantTables() {
        receiptDao.deleteAll()
        receiptEntityDao?.deleteAll()
        belegDao?.deleteAllBelege()
        receiptDocumentDao?.deleteAll()
        exportAuditDao?.deleteAll()
    }

    suspend fun getPropertyMetadata(): PropertyMetadata? {
        return propertyDao.getPropertyMetadata()
    }

    suspend fun updatePropertyMetadata(metadata: PropertyMetadata) {
        propertyDao.insertPropertyMetadata(metadata)
    }

    suspend fun resetDefaults() {
        AppDatabase.populateDatabase(receiptDao, propertyDao, receiptEntityDao)
    }
}
