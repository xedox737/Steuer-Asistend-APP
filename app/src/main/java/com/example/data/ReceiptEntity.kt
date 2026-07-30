package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipt_entities")
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val datum: String,
    val kreditor: String,
    val betrag: Double,
    val pdfPath: String,
    val positionenJson: String = ""
)

