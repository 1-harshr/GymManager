package com.harsh.gymmanager.ui.screens.signin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.harsh.gymmanager.domain.repository.IdentityProvider
import com.harsh.gymmanager.domain.repository.IdentityToken

/** Google's and Apple's own sign-in sheets, as this phone offers them. */
interface IdentitySignIn {
    /** What's offered here, in the order shown; empty where neither is set up. */
    val providers: List<IdentityProvider>

    /**
     * Shows the provider's sheet and returns what it vouched for, or null if the person backed out.
     * Failures are SignInExceptions.
     */
    suspend fun ask(provider: IdentityProvider): IdentityToken?
}

@Composable
expect fun rememberIdentitySignIn(): IdentitySignIn

/** Apple's own "Continue with Apple" button, as its guidelines ask. Only called where Apple is offered. */
@Composable
expect fun AppleSignInButton(onClick: () -> Unit, modifier: Modifier = Modifier)
