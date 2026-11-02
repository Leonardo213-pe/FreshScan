package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.local.LocalVisualForecastStore
import com.example.domain.model.FoodAnalysis
import com.example.domain.model.VisualFeatureVector
import com.example.domain.model.VisualForecastRule
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class VisualForecastRepository(
    private val db: FirebaseFirestore,
    private val localStore: LocalVisualForecastStore = LocalVisualForecastStore()
) {
    constructor(
        context: Context,
        localStore: LocalVisualForecastStore = LocalVisualForecastStore(context)
    ) : this(
        db = FirebaseFirestore.getInstance(context.getString(R.string.firestore_database_id)),
        localStore = localStore
    )

    private val auth: FirebaseAuth? = try {
        FirebaseAuth.getInstance()
    } catch (_: Exception) {
        null
    }

    private fun requireUserId(): String {
        return auth?.currentUser?.uid
            ?: throw SecurityException("User must be authenticated to write collective forecasts")
    }

    fun observeCollectiveForecasts(): Flow<List<VisualForecastRule>> {
        val path = COLLECTION_NAME
        return db.collection(path)
            .whereEqualTo("catalogScope", VisualForecastRule.CATALOG_SCOPE)
            .limit(150)
            .snapshots()
            .map { snapshot ->
                val rules = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(VisualForecastRule::class.java)?.copy(id = doc.id)
                }
                localStore.replaceAllFromCloud(rules)
                rules
            }
            .catch { e ->
                if (e is Exception) {
                    handleFirestoreError(e, OperationType.LIST, path)
                }
                emit(localStore.getAllRules())
            }
    }

    suspend fun syncCollectiveForecasts(): Result<Unit> = runCatching {
        val path = COLLECTION_NAME
        try {
            val snapshot = db.collection(path)
                .whereEqualTo("catalogScope", VisualForecastRule.CATALOG_SCOPE)
                .limit(150)
                .get()
                .await()
            val rules = snapshot.documents.mapNotNull { doc ->
                doc.toObject(VisualForecastRule::class.java)?.copy(id = doc.id)
            }
            if (rules.isNotEmpty()) {
                localStore.replaceAllFromCloud(rules)
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    suspend fun createForecastRule(rule: VisualForecastRule): Result<String> = runCatching {
        val uid = requireUserId()
        val docRef = if (rule.id.isNotBlank()) {
            db.collection(COLLECTION_NAME).document(rule.id)
        } else {
            db.collection(COLLECTION_NAME).document()
        }
        val path = "$COLLECTION_NAME/${docRef.id}"
        val now = Timestamp.now()
        val payload = rule.copy(
            id = docRef.id,
            catalogScope = VisualForecastRule.CATALOG_SCOPE,
            userId = uid,
            createdAt = rule.createdAt ?: now,
            updatedAt = now
        )
        localStore.upsertLocalRule(payload)
        try {
            docRef.set(payload, SetOptions.merge()).await()
            docRef.id
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            throw e
        }
    }

    suspend fun publishOrConfirmValidatedForecast(
        features: VisualFeatureVector,
        analysis: FoodAnalysis
    ): Result<VisualForecastRule> = runCatching {
        val uid = auth?.currentUser?.uid ?: "local_device"
        val rule = VisualForecastRule.fromValidatedAnalysis(
            userId = uid,
            featureVector = features,
            analysis = analysis
        )
        localStore.upsertLocalRule(rule)

        if (auth?.currentUser != null) {
            val docRef = db.collection(COLLECTION_NAME).document(rule.id)
            val path = "$COLLECTION_NAME/${rule.id}"
            try {
                docRef.set(rule, SetOptions.merge()).await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, path)
                throw e
            }
        }
        rule
    }

    suspend fun deleteForecastRule(ruleId: String): Result<Unit> = runCatching {
        requireUserId()
        val path = "$COLLECTION_NAME/$ruleId"
        try {
            db.collection(COLLECTION_NAME).document(ruleId).delete().await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            throw e
        }
    }

    companion object {
        const val COLLECTION_NAME = "collective_forecasts"
    }
}
