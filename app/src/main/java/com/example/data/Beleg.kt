package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room-Datenbank-Entität für Belege mit allen erforderlichen Feldern
 * für Anlagenspeicherung und Finanzzuordnung.
 */
@Entity(tableName = "belege")
data class Beleg(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val betrag: Double,
    val datum: String, // e.g. YYYY-MM-DD
    val kategorie: String, // z.B. Instandhaltung, Verwaltung, etc.
    val anbieter: String, // Anbieter / Aussteller / Handwerker
    val bildPfad: String = "" // Pfad zum Bild / Scan für Anlagenspeicherung
)

@Dao
interface BelegDao {
    @Query("SELECT * FROM belege ORDER BY datum DESC")
    fun getAllBelege(): Flow<List<Beleg>>

    @Query("SELECT * FROM belege WHERE id = :id")
    suspend fun getBelegById(id: Long): Beleg?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeleg(beleg: Beleg): Long

    @Query("DELETE FROM belege WHERE id = :id")
    suspend fun deleteBelegById(id: Long)

    @Query("DELETE FROM belege")
    suspend fun deleteAllBelege()
}
