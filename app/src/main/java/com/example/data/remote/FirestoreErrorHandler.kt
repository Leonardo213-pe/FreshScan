package com.example.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    error: Exception,
    operationType: OperationType,
    path: String?
): String {
    val currentUser = try {
        FirebaseAuth.getInstance().currentUser
    } catch (_: Exception) {
        null
    }

    val errorInfo = JSONObject().apply {
        put("error", error.message ?: "Unknown Firestore error")
        put("operationType", operationType.value)
        put("path", path ?: "unknown")
        put(
            "authInfo",
            JSONObject().apply {
                put("userId", currentUser?.uid ?: "unauthenticated")
                put("email", currentUser?.email ?: "")
                put("emailVerified", currentUser?.isEmailVerified ?: false)
                put("isAnonymous", currentUser?.isAnonymous ?: false)
            }
        )
    }.toString()

    Log.e("FirestoreError", errorInfo, error)
    return errorInfo
}
