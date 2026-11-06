package com.harsh.gymmanager.ui.screens.signin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalDemoMode
import com.harsh.gymmanager.domain.repository.AuthRepository
import com.harsh.gymmanager.presentation.signin.CODE_DIGITS
import com.harsh.gymmanager.presentation.signin.PHONE_COUNTRY_CODE
import com.harsh.gymmanager.presentation.signin.SignInUiState
import com.harsh.gymmanager.presentation.signin.SignInViewModel
import com.harsh.gymmanager.ui.components.GhostButton
import com.harsh.gymmanager.ui.components.MyGymButton
import com.harsh.gymmanager.ui.components.MyGymIconButton
import com.harsh.gymmanager.ui.format.asString
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.motion.MyGymMotion
import com.harsh.gymmanager.ui.screens.settings.DemoPasswordSheet
import com.harsh.gymmanager.ui.theme.MyGymRadius
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.MyGymTypography
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The first screen until someone signs in: their mobile number, then the code texted to it. The code
 * step slides in like a pushed screen and back goes to the number. A demo is a tap away for anyone
 * just looking.
 */
@Composable
fun SignInScreen(auth: AuthRepository) {
    val vm = viewModel { SignInViewModel(auth) }
    val state by vm.state.collectAsStateWithLifecycle()
    val demoMode = LocalDemoMode.current
    var askingDemoPassword by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = state.awaitingCode,
        modifier = Modifier.fillMaxSize().imePadding(),
        transitionSpec = {
            val forward = targetState
            (slideInHorizontally(MyGymMotion.push()) { if (forward) it else -it / 3 } + fadeIn(MyGymMotion.push())) togetherWith
                (slideOutHorizontally(MyGymMotion.push()) { if (forward) -it / 3 else it } + fadeOut(MyGymMotion.push()))
        },
    ) { awaitingCode ->
        if (awaitingCode) {
            CodeStep(state, vm)
        } else {
            PhoneStep(state, vm, onTryDemo = demoMode?.let { { askingDemoPassword = true } })
        }
    }
    if (askingDemoPassword && demoMode != null) DemoPasswordSheet(demoMode, onDismiss = { askingDemoPassword = false })
}

@Composable
private fun PhoneStep(state: SignInUiState, vm: SignInViewModel, onTryDemo: (() -> Unit)?) {
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).statusBarsPadding().screenGutter()
                .padding(top = MyGymSpace.xl, bottom = MyGymSpace.lg),
        ) {
            BrandMark()
            Spacer(Modifier.height(MyGymSpace.xxl + MyGymSpace.lg))
            Text(stringResource(Res.string.signin_title), style = typography.headline)
            Spacer(Modifier.height(MyGymSpace.sm))
            Text(stringResource(Res.string.signin_subtitle), style = typography.body, color = colors.inkMuted)
            Spacer(Modifier.height(MyGymSpace.xxl))
            Text(stringResource(Res.string.signin_phone), style = typography.bodyStrong)
            Spacer(Modifier.height(MyGymSpace.sm))
            PhoneField(
                phone = state.phone,
                onPhoneChange = vm::onPhoneChange,
                complete = state.isPhoneComplete,
                error = state.phoneError != null,
                onDone = vm::sendCode,
            )
            Spacer(Modifier.height(MyGymSpace.sm))
            val error = state.phoneError?.asString()
            Text(
                error ?: stringResource(Res.string.signin_sms_note),
                style = typography.caption,
                color = if (error != null) colors.statusDue else colors.inkMuted,
            )
        }
        Column(
            Modifier.fillMaxWidth().screenGutter().navigationBarsPadding().padding(vertical = MyGymSpace.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MyGymButton(
                stringResource(if (state.isBusy) Res.string.signin_sending else Res.string.signin_send_code),
                vm::sendCode,
                Modifier.fillMaxWidth(),
                enabled = state.isPhoneComplete && !state.isBusy,
            )
            // While typing, the keyboard needs the room more than the demo does.
            if (onTryDemo != null && WindowInsets.ime.getBottom(LocalDensity.current) == 0) {
                Row(Modifier.padding(top = MyGymSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(Res.string.signin_just_looking), style = typography.body, color = colors.inkMuted)
                    GhostButton(stringResource(Res.string.signin_try_demo), onTryDemo)
                }
            }
        }
    }
}

@Composable
private fun CodeStep(state: SignInUiState, vm: SignInViewModel) {
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(horizontal = MyGymSpace.gutter, vertical = MyGymSpace.md)) {
            MyGymIconButton(MyGymIcons.ChevronLeft, stringResource(Res.string.signin_change_number), vm::changeNumber)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).screenGutter()
                .padding(top = MyGymSpace.lg, bottom = MyGymSpace.lg),
        ) {
            Text(stringResource(Res.string.signin_code_title), style = typography.headline)
            Spacer(Modifier.height(MyGymSpace.sm))
            Text(
                stringResource(Res.string.signin_code_body, displayPhone(state.phone)),
                style = typography.body,
                color = colors.inkMuted,
            )
            Spacer(Modifier.height(MyGymSpace.xxl))
            CodeCells(
                code = state.code,
                onCodeChange = vm::onCodeChange,
                error = state.codeError != null,
                shakeKey = state.failedAttempts,
                onDone = vm::verifyCode,
            )
            Spacer(Modifier.height(MyGymSpace.sm))
            val error = state.codeError?.asString()
            Text(
                when {
                    error != null -> error
                    state.isBusy -> stringResource(Res.string.signin_verifying)
                    else -> ""
                },
                style = typography.caption,
                color = if (error != null) colors.statusDue else colors.inkMuted,
            )
            Spacer(Modifier.height(MyGymSpace.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.signin_no_code), style = typography.body, color = colors.inkMuted)
                if (state.resendInSeconds > 0) {
                    Text(
                        stringResource(Res.string.signin_resend_in, countdown(state.resendInSeconds)),
                        Modifier.padding(horizontal = MyGymSpace.sm, vertical = MyGymSpace.sm),
                        style = typography.bodyStrong.copy(fontFeatureSettings = "tnum"),
                        color = colors.inkMuted,
                    )
                } else {
                    GhostButton(stringResource(Res.string.signin_resend), vm::resendCode)
                }
            }
        }
        MyGymButton(
            stringResource(if (state.isBusy) Res.string.signin_verifying else Res.string.signin_verify),
            vm::verifyCode,
            Modifier.fillMaxWidth().screenGutter().navigationBarsPadding().padding(vertical = MyGymSpace.md),
            enabled = state.isCodeComplete && !state.isBusy,
        )
    }
}

/** The launch screen's clay tile and dumbbell, so the splash hands over to a screen that's clearly the same app. */
@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
        Box(Modifier.size(48.dp).background(BrandClay, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(MyGymIcons.Dumbbell, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Text(stringResource(Res.string.app_name), style = MyGymTheme.typography.screenTitle)
    }
}

/** The number in the display face, grouped as it's read aloud: 98765 43210. Ticks once all ten digits are in. */
@Composable
private fun PhoneField(phone: String, onPhoneChange: (String) -> Unit, complete: Boolean, error: Boolean, onDone: () -> Unit) {
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val digits = typography.stat.copy(fontSize = typography.stat.fontSize * 0.93f)
    BasicTextField(
        value = phone,
        onValueChange = onPhoneChange,
        modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.PhoneNumberNational },
        singleLine = true,
        textStyle = digits.copy(color = colors.ink),
        cursorBrush = SolidColor(colors.ink),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        visualTransformation = PhoneGrouping,
        interactionSource = interaction,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(colors.surfaceSunken, MyGymRadius.fieldShape)
                    .ring(error = error, focused = focused),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.padding(start = MyGymSpace.lg, end = MyGymSpace.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("🇮🇳", style = typography.title)
                    Text(PHONE_COUNTRY_CODE, style = digits, color = colors.inkMuted)
                }
                Box(Modifier.width(1.dp).height(28.dp).background(colors.line))
                Box(Modifier.weight(1f).padding(horizontal = MyGymSpace.md)) {
                    if (phone.isEmpty()) Text(PHONE_PLACEHOLDER, style = digits, color = colors.inkMuted.copy(alpha = 0.45f))
                    inner()
                }
                AnimatedVisibility(complete, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                    Icon(
                        MyGymIcons.Check,
                        contentDescription = null,
                        tint = colors.statusActive,
                        modifier = Modifier.padding(end = MyGymSpace.lg).size(22.dp),
                    )
                }
            }
        },
    )
}

/**
 * One box per digit, filled by a single hidden field so paste and the phone's SMS code autofill both
 * work. The box being typed into shows a caret; a rejected code shakes the row and rings it red.
 */
@Composable
private fun CodeCells(code: String, onCodeChange: (String) -> Unit, error: Boolean, shakeKey: Int, onDone: () -> Unit) {
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
    val focus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shake = remember { Animatable(0f) }
    val caret by rememberInfiniteTransition().animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(CARET_BLINK_MS, easing = LinearEasing), RepeatMode.Reverse),
    )
    val description = stringResource(Res.string.signin_code_cells)

    // Open the keyboard as the step arrives, so the code can go straight in.
    LaunchedEffect(Unit) { focus.requestFocus() }
    // After a rejected code the keyboard stays up for the next try.
    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) {
            focus.requestFocus()
            for (x in SHAKE_STEPS) shake.animateTo(x, tween(SHAKE_STEP_MS))
        }
    }

    BasicTextField(
        value = code,
        onValueChange = onCodeChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .graphicsLayer { translationX = shake.value.dp.toPx() }
            .semantics {
                contentDescription = description
                contentType = ContentType.SmsOtpCode
            },
        singleLine = true,
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        interactionSource = interaction,
        decorationBox = { inner ->
            Box {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
                    repeat(CODE_DIGITS) { i ->
                        val active = focused && i == code.length
                        Box(
                            Modifier
                                .weight(1f)
                                .height(64.dp)
                                .background(colors.surfaceSunken, MyGymRadius.fieldShape)
                                .ring(error = error, focused = active),
                            contentAlignment = Alignment.Center,
                        ) {
                            val digit = code.getOrNull(i)
                            if (digit != null) {
                                Text(digit.toString(), style = typography.stat, color = colors.ink, textAlign = TextAlign.Center)
                            } else if (active) {
                                Box(Modifier.width(2.dp).height(26.dp).alpha(caret).background(colors.ink))
                            }
                        }
                    }
                }
                // The real field sits invisibly over the boxes, so a tap anywhere on them focuses it.
                Box(Modifier.matchParentSize().alpha(0f)) { inner() }
            }
        },
    )
}

@Composable
private fun Modifier.ring(error: Boolean, focused: Boolean): Modifier {
    val color = when {
        error -> MyGymTheme.colors.statusDue
        focused -> MyGymTheme.colors.ink
        else -> return this
    }
    return border(2.dp, color, MyGymRadius.fieldShape)
}

/** Shows "98765 43210" while the field holds "9876543210". */
private object PhoneGrouping : VisualTransformation {
    private const val GROUP = 5

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val grouped = if (raw.length > GROUP) raw.take(GROUP) + " " + raw.drop(GROUP) else raw
        return TransformedText(
            AnnotatedString(grouped),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = if (offset > GROUP) offset + 1 else offset
                override fun transformedToOriginal(offset: Int) = if (offset > GROUP) offset - 1 else offset
            },
        )
    }
}

/** The display face between the hero figure and a screen title, for the one line that leads each step. */
private val MyGymTypography.headline: TextStyle
    get() = displayXl.copy(fontSize = 38.sp, lineHeight = 40.sp, fontFeatureSettings = null)

/** "+91 90000 00001" for "9000000001". */
private fun displayPhone(digits: String): String =
    "$PHONE_COUNTRY_CODE ${digits.take(5)} ${digits.drop(5)}".trimEnd()

/** "0:27" for 27. */
private fun countdown(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private const val PHONE_PLACEHOLDER = "98765 43210"

/** The launch screen's tile colour, kept the same in dark mode as the launch screen does. */
private val BrandClay = Color(0xFFB0441B)

private const val CARET_BLINK_MS = 530
private const val SHAKE_STEP_MS = 45
private val SHAKE_STEPS = listOf(-14f, 12f, -9f, 6f, -3f, 0f)
