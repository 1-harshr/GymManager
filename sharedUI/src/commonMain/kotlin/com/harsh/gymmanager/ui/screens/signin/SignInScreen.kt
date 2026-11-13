package com.harsh.gymmanager.ui.screens.signin

import androidx.compose.ui.platform.LocalUriHandler
import com.harsh.gymmanager.ui.PRIVACY_URL
import com.harsh.gymmanager.ui.WEB_APP_URL
import com.harsh.gymmanager.data.remote.SignInConfig
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import com.harsh.gymmanager.ui.theme.BrandClay
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.LinkAnnotation
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
import com.harsh.gymmanager.domain.repository.IdentityProvider
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
import com.harsh.gymmanager.ui.theme.MyGymLightColors
import com.harsh.gymmanager.ui.theme.MyGymRadius
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.MyGymTypography
import com.harsh.gymmanager.ui.theme.screenGutter
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The first screen until someone signs in: Google or Apple where they're set up, or a mobile number and
 * then the code texted to it. The code step slides in like a pushed screen and back goes to the number.
 * A demo is a tap away for anyone just looking.
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
    val identity = rememberIdentitySignIn()
    // Phone sign-in is off for this release, but a build with no other way in keeps it.
    val offersPhone = SignInConfig.PHONE || identity.providers.isEmpty()
    Column(Modifier.fillMaxSize().background(BrandClay)) {
        // The hero takes whatever the sheet doesn't need, and scrolls if the keyboard squeezes it.
        BoxWithConstraints(Modifier.weight(1f)) {
            val room = maxHeight
            Column(Modifier.verticalScroll(rememberScrollState())) { Hero(onTryDemo, Modifier.heightIn(min = room)) }
        }
        Column(
            Modifier.fillMaxWidth().background(colors.surface, MyGymRadius.sheetShape).screenGutter()
                .navigationBarsPadding().padding(top = MyGymSpace.xl, bottom = MyGymSpace.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (identity.providers.isNotEmpty()) {
                    IdentityButtons(identity, state, vm)
                    if (offersPhone) OrPhone()
                }
                if (offersPhone) {
                    PhoneEntry(state, vm)
                    Spacer(Modifier.height(MyGymSpace.lg))
                    MyGymButton(
                        stringResource(if (state.isBusy) Res.string.signin_sending else Res.string.signin_send_code),
                        vm::sendCode,
                        Modifier.fillMaxWidth(),
                        enabled = state.isPhoneComplete && !state.isBusy,
                    )
                }
            }
            Spacer(Modifier.height(MyGymSpace.md))
            FinePrint()
        }
    }
}

/** "By continuing, you agree to the privacy policy and terms. You can also use it on the web." with both links inline. */
@Composable
private fun FinePrint() {
    val colors = MyGymTheme.colors
    val links = TextLinkStyles(SpanStyle(color = colors.ink, textDecoration = TextDecoration.Underline))
    val lead = stringResource(Res.string.signin_consent_lead)
    val privacy = stringResource(Res.string.signin_consent_link)
    val webLead = stringResource(Res.string.signin_web_lead)
    val web = stringResource(Res.string.signin_web_link)
    val text = buildAnnotatedString {
        append("$lead ")
        withLink(LinkAnnotation.Url(PRIVACY_URL, links)) { append(privacy) }
        append(". $webLead ")
        withLink(LinkAnnotation.Url(WEB_APP_URL, links)) { append(web) }
        append(".")
    }
    Text(text, Modifier.fillMaxWidth(), style = MyGymTheme.typography.caption, color = colors.inkMuted, textAlign = TextAlign.Center)
}

/**
 * The clay top half: the brand, a glimpse of the app at work (a payment in, a renewal coming up, the
 * member count) and the promise. It's clay in both themes, like the launcher icon it follows on from.
 */
@Composable
private fun Hero(onTryDemo: (() -> Unit)?, modifier: Modifier = Modifier) {
    val typography = MyGymTheme.typography
    Column(
        modifier.fillMaxWidth().statusBarsPadding().screenGutter().padding(top = MyGymSpace.lg, bottom = MyGymSpace.xl),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(onClay = true)
            Spacer(Modifier.weight(1f))
            // Tucked in the corner for anyone just looking; the sheet below is for gyms signing in.
            if (onTryDemo != null) {
                Surface(onClick = onTryDemo, shape = MyGymRadius.pillShape, color = Color.Transparent, contentColor = Color.White.copy(alpha = 0.85f)) {
                    Text(
                        stringResource(Res.string.signin_try_demo),
                        Modifier.padding(horizontal = MyGymSpace.sm, vertical = MyGymSpace.sm),
                        style = MyGymTheme.typography.caption,
                    )
                }
            }
        }
        HeroCards(Modifier.fillMaxWidth().padding(vertical = MyGymSpace.xl))
        Column {
            Text(stringResource(Res.string.signin_title), style = typography.headline, color = Color.White)
            Spacer(Modifier.height(MyGymSpace.sm))
            Text(stringResource(Res.string.signin_subtitle), style = typography.body, color = Color.White.copy(alpha = 0.82f))
        }
    }
}

/**
 * Two cards and a pill, set at slight angles like they've been dealt onto the table. They're a picture,
 * not data, so screen readers skip them; their colours stay light-theme because they sit on clay.
 */
@Composable
private fun HeroCards(modifier: Modifier = Modifier) {
    val light = MyGymLightColors
    val typography = MyGymTheme.typography
    Box(modifier.height(176.dp).clearAndSetSemantics {}) {
        HeroCard(
            Modifier.align(Alignment.TopStart).padding(top = MyGymSpace.sm).rotate(-4f),
        ) {
            StatusLine(stringResource(Res.string.signin_hero_paid), light.statusActive, light.statusActiveSoft)
            Spacer(Modifier.height(MyGymSpace.sm))
            Text(stringResource(Res.string.signin_hero_paid_amount), style = typography.stat, color = light.ink)
            Text(stringResource(Res.string.signin_hero_paid_who), style = typography.caption, color = light.inkMuted)
        }
        HeroCard(
            Modifier.align(Alignment.BottomEnd).padding(bottom = MyGymSpace.lg).rotate(3f),
        ) {
            StatusLine(stringResource(Res.string.signin_hero_renews), light.statusExpiring, light.statusExpiringSoft)
            Spacer(Modifier.height(MyGymSpace.sm))
            Text(stringResource(Res.string.signin_hero_renews_who), style = typography.title, color = light.ink)
        }
        Row(
            Modifier.align(Alignment.TopEnd).rotate(2f)
                .background(Color.White.copy(alpha = 0.16f), MyGymRadius.pillShape)
                .border(1.dp, Color.White.copy(alpha = 0.28f), MyGymRadius.pillShape)
                .padding(horizontal = MyGymSpace.md, vertical = MyGymSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MyGymSpace.sm),
        ) {
            Box(Modifier.size(8.dp).background(Color.White, CircleShape))
            Text(stringResource(Res.string.signin_hero_active), style = typography.caption, color = Color.White)
        }
    }
}

@Composable
private fun HeroCard(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .width(196.dp)
            .shadow(16.dp, MyGymRadius.cardShape, ambientColor = Color.Black, spotColor = Color.Black)
            .background(Color.White, MyGymRadius.cardShape)
            .padding(MyGymSpace.lg),
    ) { content() }
}

/** A dot and a word in a soft pill, as the member list marks a status. */
@Composable
private fun StatusLine(text: String, color: Color, soft: Color) {
    Row(
        Modifier.background(soft, MyGymRadius.pillShape).padding(horizontal = MyGymSpace.sm, vertical = MyGymSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(text, style = MyGymTheme.typography.label, color = color)
    }
}

/** The number field and what happens next, for builds that offer phone sign-in. */
@Composable
private fun PhoneEntry(state: SignInUiState, vm: SignInViewModel) {
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
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

/** One button per provider, then a line that says what's happening or what went wrong. */
@Composable
private fun IdentityButtons(identity: IdentitySignIn, state: SignInUiState, vm: SignInViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
        identity.providers.forEach { provider ->
            val ask = { vm.signInWith(provider) { identity.ask(provider) } }
            when (provider) {
                IdentityProvider.APPLE -> AppleSignInButton(onClick = ask)
                IdentityProvider.GOOGLE -> GoogleButton(onClick = ask, enabled = !state.isBusy)
            }
        }
        val error = state.identityError?.asString()
        val status = error ?: if (state.signingInWith != null) stringResource(Res.string.signin_signing_in) else null
        if (status != null) {
            Text(
                status,
                style = MyGymTheme.typography.caption,
                color = if (error != null) MyGymTheme.colors.statusDue else MyGymTheme.colors.inkMuted,
            )
        }
    }
}

/** Google's button as its guidelines draw it: the four-colour G on a plain outlined pill. */
@Composable
private fun GoogleButton(onClick: () -> Unit, enabled: Boolean) {
    val colors = MyGymTheme.colors
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MyGymRadius.pillShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.line),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Image(GoogleG, contentDescription = null, Modifier.size(20.dp))
            Spacer(Modifier.width(MyGymSpace.md))
            Text(stringResource(Res.string.signin_google), style = MyGymTheme.typography.bodyStrong, color = colors.ink)
        }
    }
}

/** "—— or use your mobile number ——" between the providers and the number. */
@Composable
private fun OrPhone() {
    Row(Modifier.padding(vertical = MyGymSpace.xl), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(MyGymTheme.colors.line))
        Text(
            stringResource(Res.string.signin_or_phone),
            Modifier.padding(horizontal = MyGymSpace.md),
            style = MyGymTheme.typography.caption,
            color = MyGymTheme.colors.inkMuted,
        )
        Box(Modifier.weight(1f).height(1.dp).background(MyGymTheme.colors.line))
    }
}

@Composable
private fun CodeStep(state: SignInUiState, vm: SignInViewModel) {
    val colors = MyGymTheme.colors
    val typography = MyGymTheme.typography
    Column(Modifier.fillMaxSize().background(colors.surface)) {
        // A band of the hero's clay carries over, so the code reads as the next card of the same screen.
        Row(Modifier.fillMaxWidth().background(BrandClay).statusBarsPadding().padding(horizontal = MyGymSpace.gutter, vertical = MyGymSpace.md)) {
            MyGymIconButton(
                MyGymIcons.ChevronLeft,
                stringResource(Res.string.signin_change_number),
                vm::changeNumber,
                container = Color.White.copy(alpha = 0.16f),
                content = Color.White,
            )
        }
        Column(
            Modifier.weight(1f).background(BrandClay).background(colors.surface, MyGymRadius.sheetShape)
                .verticalScroll(rememberScrollState()).screenGutter()
                .padding(top = MyGymSpace.xl, bottom = MyGymSpace.lg),
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

/**
 * The launch screen's clay tile and dumbbell, so the splash hands over to a screen that's clearly the same app.
 * [onClay] swaps it to a white tile and name for sitting on the clay hero.
 */
@Composable
internal fun BrandMark(onClay: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
        val tile = if (onClay) Color.White else BrandClay
        Box(Modifier.size(48.dp).background(tile, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
            Icon(MyGymIcons.Dumbbell, contentDescription = null, tint = if (onClay) BrandClay else Color.White, modifier = Modifier.size(28.dp))
        }
        Text(
            stringResource(Res.string.app_name),
            style = MyGymTheme.typography.screenTitle,
            color = if (onClay) Color.White else MyGymTheme.colors.ink,
        )
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
internal val MyGymTypography.headline: TextStyle
    get() = displayXl.copy(fontSize = 38.sp, lineHeight = 40.sp, fontFeatureSettings = null)

/** "+91 90000 00001" for "9000000001". */
private fun displayPhone(digits: String): String =
    "$PHONE_COUNTRY_CODE ${digits.take(5)} ${digits.drop(5)}".trimEnd()

/** "0:27" for 27. */
private fun countdown(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private const val PHONE_PLACEHOLDER = "98765 43210"

/** Google's G, from its sign-in branding, on a 48-unit grid. */
private val GoogleG: ImageVector by lazy {
    ImageVector.Builder(name = "GoogleG", defaultWidth = 20.dp, defaultHeight = 20.dp, viewportWidth = 48f, viewportHeight = 48f)
        .apply {
            listOf(
                0xFFEA4335 to "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z",
                0xFF4285F4 to "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z",
                0xFFFBBC05 to "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z",
                0xFF34A853 to "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z",
            ).forEach { (color, path) ->
                addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color(color)))
            }
        }
        .build()
}

private const val CARET_BLINK_MS = 530
private const val SHAKE_STEP_MS = 45
private val SHAKE_STEPS = listOf(-14f, 12f, -9f, 6f, -3f, 0f)
