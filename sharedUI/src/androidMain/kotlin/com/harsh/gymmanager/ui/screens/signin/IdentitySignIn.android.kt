package com.harsh.gymmanager.ui.screens.signin

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.harsh.gymmanager.data.remote.GoogleSignInConfig
import com.harsh.gymmanager.domain.repository.IdentityProvider
import com.harsh.gymmanager.domain.repository.IdentityToken
import com.harsh.gymmanager.domain.repository.SignInException
import com.harsh.gymmanager.domain.repository.SignInProblem
import java.security.MessageDigest
import java.security.SecureRandom

@Composable
actual fun rememberIdentitySignIn(): IdentitySignIn {
    // The sheet needs the activity, which is what the screen's context is.
    val context = LocalContext.current
    return remember(context) { GoogleOnAndroid(context) }
}

/** Android offers Google only: Apple sign-in there would be a web page, for few people. */
private class GoogleOnAndroid(private val context: Context) : IdentitySignIn {
    override val providers =
        if (GoogleSignInConfig.WEB_CLIENT_ID.isBlank()) emptyList() else listOf(IdentityProvider.GOOGLE)

    override suspend fun ask(provider: IdentityProvider): IdentityToken? {
        val nonce = randomNonce()
        // The button's own flow: every account on the phone, and a way to add one.
        val option = GetSignInWithGoogleOption.Builder(GoogleSignInConfig.WEB_CLIENT_ID)
            .setNonce(sha256(nonce))
            .build()
        val credential = try {
            CredentialManager.create(context).getCredential(context, GetCredentialRequest(listOf(option))).credential
        } catch (e: GetCredentialCancellationException) {
            return null
        } catch (e: GetCredentialInterruptedException) {
            throw SignInException(SignInProblem.NO_CONNECTION, e)
        } catch (e: GetCredentialException) {
            throw SignInException(SignInProblem.IDENTITY_REJECTED, e)
        }
        if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw SignInException(SignInProblem.IDENTITY_REJECTED)
        }
        return IdentityToken(IdentityProvider.GOOGLE, GoogleIdTokenCredential.createFrom(credential.data).idToken, nonce)
    }
}

@Composable
actual fun AppleSignInButton(onClick: () -> Unit, modifier: Modifier) = Unit

private fun randomNonce(): String = ByteArray(32).also(SecureRandom()::nextBytes).toHex()

/** Lowercase hex, which is how Supabase hashes the nonce to compare it. */
private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).toHex()

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
