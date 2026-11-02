package com.example.security

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "GoogleAuthManager"

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit = {},
    onUnauthenticated: () -> Unit = {},
    scope: CoroutineScope
) {
    val webClientId = context.getString(R.string.default_web_client_id)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(webClientId)
        .setAutoSelectEnabled(true)
        .build()

    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential =
                    GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: NoCredentialException) {
            onUnauthenticated()
        } catch (e: Exception) {
            Log.d(TAG, "Auto sign-in skipped: ${e.message}")
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit,
    scope: CoroutineScope,
    onFinally: () -> Unit = {}
) {
    val webClientId = context.getString(R.string.default_web_client_id)
    val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val firebaseCredential =
                    GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
                onSuccess()
            } else {
                onFailure("No se pudo verificar la credencial de Google.")
            }
        } catch (_: GetCredentialCancellationException) {
            onFailure("Inicio de sesión cancelado.")
        } catch (e: GetCredentialException) {
            Log.w(TAG, "CredentialManager sign-in error", e)
            onFailure("Agregue una cuenta de Google en el dispositivo o continúe sin cuenta.")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Google sign-in error", e)
            onFailure(e.localizedMessage ?: "Error al iniciar sesión con Google.")
        } finally {
            onFinally()
        }
    }
}

@Composable
fun GoogleSignInButton(
    onSignInSuccess: () -> Unit,
    onSignInError: (String) -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Continuar con Google"
) {
    val context = LocalContext.current
    val credentialManager = remember { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    Button(
        onClick = {
            if (!isLoading) {
                isLoading = true
                onGoogleSignInClicked(
                    context = context,
                    credentialManager = credentialManager,
                    onSuccess = {
                        isLoading = false
                        onSignInSuccess()
                    },
                    onFailure = { err ->
                        isLoading = false
                        onSignInError(err)
                    },
                    scope = scope,
                    onFinally = { isLoading = false }
                )
            }
        },
        enabled = !isLoading,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("google_sign_in_button")
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Conectando...", fontWeight = FontWeight.SemiBold)
        } else {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = text, fontWeight = FontWeight.SemiBold)
        }
    }
}
