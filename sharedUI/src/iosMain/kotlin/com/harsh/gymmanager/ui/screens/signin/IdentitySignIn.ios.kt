package com.harsh.gymmanager.ui.screens.signin

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.harsh.gymmanager.data.remote.GoogleSignInConfig
import com.harsh.gymmanager.domain.repository.IdentityProvider
import com.harsh.gymmanager.domain.repository.IdentityToken
import com.harsh.gymmanager.domain.repository.SignInException
import com.harsh.gymmanager.domain.repository.SignInProblem
import com.harsh.gymmanager.ui.theme.MyGymTheme
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASAuthorizationAppleIDButton
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonStyle
import platform.AuthenticationServices.ASAuthorizationAppleIDButtonTypeContinue
import platform.UIKit.UIAction
import platform.UIKit.UIControlEventPrimaryActionTriggered
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Swift's side of Apple and Google sign-in, where their kits are. Each calls back once, on the main
 * thread, with the ID token and the nonce it was asked for with; with neither if the person backed
 * out; or with an [error] description.
 */
interface NativeIdentitySignIn {
    fun signInWithApple(onResult: (idToken: String?, nonce: String?, error: String?) -> Unit)

    fun signInWithGoogle(clientId: String, onResult: (idToken: String?, nonce: String?, error: String?) -> Unit)
}

private var native: NativeIdentitySignIn? = null

/** Swift hands over its sign-in at launch. */
@Suppress("unused")
fun installIdentitySignIn(signIn: NativeIdentitySignIn) {
    native = signIn
}

@Composable
actual fun rememberIdentitySignIn(): IdentitySignIn = remember { NativeSignIn }

/** Apple first, as its guidelines ask when both are offered. */
private object NativeSignIn : IdentitySignIn {
    override val providers: List<IdentityProvider>
        get() = when {
            native == null -> emptyList()
            GoogleSignInConfig.IOS_CLIENT_ID.isBlank() -> listOf(IdentityProvider.APPLE)
            else -> listOf(IdentityProvider.APPLE, IdentityProvider.GOOGLE)
        }

    override suspend fun ask(provider: IdentityProvider): IdentityToken? {
        val signIn = native ?: return null
        return suspendCancellableCoroutine { continuation ->
            val onResult = { idToken: String?, nonce: String?, error: String? ->
                when {
                    error != null ->
                        continuation.resumeWithException(SignInException(SignInProblem.IDENTITY_REJECTED, Exception(error)))
                    idToken == null || nonce == null -> continuation.resume(null)
                    else -> continuation.resume(IdentityToken(provider, idToken, nonce))
                }
            }
            when (provider) {
                IdentityProvider.APPLE -> signIn.signInWithApple(onResult)
                IdentityProvider.GOOGLE -> signIn.signInWithGoogle(GoogleSignInConfig.IOS_CLIENT_ID, onResult)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun AppleSignInButton(onClick: () -> Unit, modifier: Modifier) {
    val click by rememberUpdatedState(onClick)
    val dark = MyGymTheme.colors.isDark
    // Its style is fixed when it's made, so a theme change makes a new one.
    key(dark) {
        UIKitView(
            factory = {
                ASAuthorizationAppleIDButton(
                    authorizationButtonType = ASAuthorizationAppleIDButtonTypeContinue,
                    authorizationButtonStyle = if (dark) ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleWhite else ASAuthorizationAppleIDButtonStyle.ASAuthorizationAppleIDButtonStyleBlack,
                ).apply {
                    cornerRadius = BUTTON_HEIGHT / 2
                    addAction(UIAction.actionWithHandler { click() }, forControlEvents = UIControlEventPrimaryActionTriggered)
                }
            },
            modifier = modifier.fillMaxWidth().height(BUTTON_HEIGHT.dp),
            properties = UIKitInteropProperties(
                interactionMode = UIKitInteropInteractionMode.NonCooperative,
                placedAsOverlay = true,
            ),
        )
    }
}

/** The same height as the app's own buttons. */
private const val BUTTON_HEIGHT = 52.0
