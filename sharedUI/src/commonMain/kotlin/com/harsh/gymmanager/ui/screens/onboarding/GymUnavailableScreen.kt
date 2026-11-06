package com.harsh.gymmanager.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.harsh.gymmanager.ui.components.GhostButton
import com.harsh.gymmanager.ui.components.MyGymButton
import com.harsh.gymmanager.ui.screens.signin.BrandMark
import com.harsh.gymmanager.ui.screens.signin.headline
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Signed in, but the gym couldn't be checked, usually for want of a connection. */
@Composable
fun GymUnavailableScreen(onRetry: () -> Unit, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).statusBarsPadding().screenGutter().padding(top = MyGymSpace.xl)) {
            BrandMark()
            Spacer(Modifier.height(MyGymSpace.xxl + MyGymSpace.lg))
            Text(stringResource(Res.string.gym_unavailable_title), style = MyGymTheme.typography.headline)
            Spacer(Modifier.height(MyGymSpace.sm))
            Text(stringResource(Res.string.gym_unavailable_body), style = MyGymTheme.typography.body, color = MyGymTheme.colors.inkMuted)
        }
        Column(
            Modifier.fillMaxWidth().screenGutter().navigationBarsPadding().padding(vertical = MyGymSpace.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MyGymSpace.sm),
        ) {
            MyGymButton(stringResource(Res.string.try_again), onRetry, Modifier.fillMaxWidth())
            GhostButton(stringResource(Res.string.sign_out), onSignOut)
        }
    }
}
