package com.example.data

import android.content.Context
import android.util.Log
import com.example.ui.WohneinheitStatus
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

object FirestoreService {
    private const val TAG = "FirestoreService"
    private var isInitialized = false
    private var firestoreInstance: FirebaseFirestore? = null
    private var authInstance: FirebaseAuth? = null

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                firestoreInstance = FirebaseFirestore.getInstance()
                authInstance = FirebaseAuth.getInstance()
                isInitialized = true
                Log.i(TAG, "Firebase initialized via auto-configuration.")
                return
            }

            // Fallback config so the app runs and compiles anywhere
            val options = FirebaseOptions.Builder()
                .setApplicationId("1:417852369012:android:9d365bc7e89ab01")
                .setApiKey("mock-key-ai-studio-firestore-integration")
                .setProjectId("ai-studio-firestore-project")
                .build()

            FirebaseApp.initializeApp(context, options)
            firestoreInstance = FirebaseFirestore.getInstance()
            authInstance = FirebaseAuth.getInstance()
            isInitialized = true
            Log.i(TAG, "Firebase initialized programmatically.")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init error: ${e.localizedMessage}", e)
            isInitialized = false
            firestoreInstance = null
            authInstance = null
        }
    }

    fun isCloudActive(): Boolean {
        return isInitialized && firestoreInstance != null
    }

    fun getCurrentUser(): FirebaseUser? {
        return authInstance?.currentUser
    }

    suspend fun signIn(email: String, password: String): FirebaseUser? {
        val auth = authInstance ?: return null
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user
        } catch (e: Exception) {
            Log.e(TAG, "SignIn error: ${e.localizedMessage}")
            throw e
        }
    }

    suspend fun signUp(email: String, password: String): FirebaseUser? {
        val auth = authInstance ?: return null
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user
        } catch (e: Exception) {
            Log.e(TAG, "SignUp error: ${e.localizedMessage}")
            throw e
        }
    }

    fun signOut() {
        authInstance?.signOut()
    }

    private fun getReceiptsCollection(uid: String): CollectionReference? {
        val db = firestoreInstance ?: return null
        return db.collection("users").document(uid).collection("receipts")
    }

    private fun getWohneinheitenCollection(uid: String): CollectionReference? {
        val db = firestoreInstance ?: return null
        return db.collection("users").document(uid).collection("wohneinheiten")
    }

    suspend fun saveReceipt(receipt: Receipt): Boolean {
        if (!isCloudActive()) return false
        val uid = getCurrentUser()?.uid ?: return false
        return try {
            val col = getReceiptsCollection(uid) ?: return false
            val data = receiptToMap(receipt)
            col.document(receipt.id.toString())
                .set(data)
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving receipt to Firestore: ${e.localizedMessage}")
            false
        }
    }

    suspend fun deleteReceipt(receiptId: Int): Boolean {
        if (!isCloudActive()) return false
        val uid = getCurrentUser()?.uid ?: return false
        return try {
            val col = getReceiptsCollection(uid) ?: return false
            col.document(receiptId.toString())
                .delete()
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting receipt from Firestore: ${e.localizedMessage}")
            false
        }
    }

    suspend fun saveWohneinheit(unit: WohneinheitStatus): Boolean {
        if (!isCloudActive()) return false
        val uid = getCurrentUser()?.uid ?: return false
        return try {
            val col = getWohneinheitenCollection(uid) ?: return false
            val data = wohneinheitToMap(unit)
            col.document(unit.name)
                .set(data)
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving wohneinheit to Firestore: ${e.localizedMessage}")
            false
        }
    }

    suspend fun getWohneinheiten(): List<WohneinheitStatus>? {
        if (!isCloudActive()) return null
        val uid = getCurrentUser()?.uid ?: return null
        return try {
            val col = getWohneinheitenCollection(uid) ?: return null
            val snapshot = col.get().await()
            snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                mapToWohneinheit(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching wohneinheiten: ${e.localizedMessage}")
            null
        }
    }

    suspend fun getReceipts(): List<Receipt>? {
        if (!isCloudActive()) return null
        val uid = getCurrentUser()?.uid ?: return null
        return try {
            val col = getReceiptsCollection(uid) ?: return null
            val snapshot = col.get().await()
            snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                mapToReceipt(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching receipts: ${e.localizedMessage}")
            null
        }
    }

    private fun receiptToMap(receipt: Receipt): Map<String, Any> {
        return mapOf(
            "id" to receipt.id,
            "aussteller" to receipt.aussteller,
            "datum" to receipt.datum,
            "uhrzeit" to receipt.uhrzeit,
            "bruttobetrag" to receipt.bruttobetrag,
            "hauptkategorie" to receipt.hauptkategorie,
            "unterkategorie" to receipt.unterkategorie,
            "kontoNr" to receipt.kontoNr,
            "beschreibung" to receipt.beschreibung,
            "isEigenleistungSanierung" to receipt.isEigenleistungSanierung,
            "imageUrl" to receipt.imageUrl,
            "wohneinheit" to receipt.wohneinheit,
            "mieter" to receipt.mieter,
            "isArchivedToDrive" to receipt.isArchivedToDrive
        )
    }

    private fun mapToReceipt(map: Map<String, Any>): Receipt {
        return Receipt(
            id = (map["id"] as? Long)?.toInt() ?: (map["id"] as? Double)?.toInt() ?: 0,
            aussteller = map["aussteller"] as? String ?: "",
            datum = map["datum"] as? String ?: "",
            uhrzeit = map["uhrzeit"] as? String ?: "",
            bruttobetrag = (map["bruttobetrag"] as? Double) ?: (map["bruttobetrag"] as? Long)?.toDouble() ?: 0.0,
            hauptkategorie = map["hauptkategorie"] as? String ?: "",
            unterkategorie = map["unterkategorie"] as? String ?: "",
            kontoNr = map["kontoNr"] as? String ?: "",
            beschreibung = map["beschreibung"] as? String ?: "",
            isEigenleistungSanierung = map["isEigenleistungSanierung"] as? Boolean ?: false,
            imageUrl = map["imageUrl"] as? String ?: "",
            wohneinheit = map["wohneinheit"] as? String ?: "",
            mieter = map["mieter"] as? String ?: "",
            isArchivedToDrive = map["isArchivedToDrive"] as? Boolean ?: false
        )
    }

    private fun wohneinheitToMap(unit: WohneinheitStatus): Map<String, Any> {
        return mapOf(
            "name" to unit.name,
            "label" to unit.label,
            "status" to unit.status,
            "mieter" to unit.mieter,
            "kaltmiete" to unit.kaltmiete,
            "wohnflaeche" to unit.wohnflaeche
        )
    }

    private fun mapToWohneinheit(map: Map<String, Any>): WohneinheitStatus {
        return WohneinheitStatus(
            name = map["name"] as? String ?: "",
            label = map["label"] as? String ?: "",
            status = map["status"] as? String ?: "",
            mieter = map["mieter"] as? String ?: "",
            kaltmiete = (map["kaltmiete"] as? Double) ?: (map["kaltmiete"] as? Long)?.toDouble() ?: 0.0,
            wohnflaeche = (map["wohnflaeche"] as? Double) ?: (map["wohnflaeche"] as? Long)?.toDouble() ?: 0.0
        )
    }
}
