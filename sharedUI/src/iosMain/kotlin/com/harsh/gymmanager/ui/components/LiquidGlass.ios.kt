package com.harsh.gymmanager.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.uikit.LocalUIViewController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.theme.MyGymTheme
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.CGRectMake
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIAction
import platform.UIKit.accessibilityLabel
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonConfigurationCornerStyleCapsule
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIFontWeightMedium
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventPrimaryActionTriggered
import platform.UIKit.UICornerConfiguration
import platform.UIKit.UIDevice
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIGlassEffectStyle
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageSymbolWeightMedium
import platform.UIKit.UILabel
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementAttributesDestructive
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewDistributionFillEqually
import platform.UIKit.UITabBar
import platform.UIKit.UITabBarDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIVisualEffectView
import platform.UIKit.NSDirectionalRectEdgeTop
import platform.darwin.NSObject

actual val usesLiquidGlass: Boolean =
    UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull()?.let { it >= 26 } ?: false

/**
 * True while the Compose launch splash is up. Native views sit above everything Compose draws,
 * so they wait for the splash to clear rather than showing through it.
 */
internal var splashShowing by mutableStateOf(true)

private const val FADE_SECONDS = 0.2

/**
 * Whether this screen's native controls should show. Navigating keeps the old screen up for its
 * exit animation, which a native view can't join, so they hide as soon as their screen stops
 * being the current one and come back once it is again.
 */
@Composable
private fun nativeControlsVisible(): Boolean {
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return lifecycle.isAtLeast(Lifecycle.State.RESUMED) && !splashShowing
}

/** Native views follow the phone's appearance unless told otherwise; the app has its own theme setting. */
private fun UIView.matchTheme(dark: Boolean) {
    overrideUserInterfaceStyle = if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
}

private fun UIView.fadeTo(visible: Boolean) {
    val target = if (visible) 1.0 else 0.0
    if (alpha != target) UIView.animateWithDuration(FADE_SECONDS) { alpha = target }
}

/** Placed over the Compose content, not behind it, so the glass has the content to refract. */
@OptIn(ExperimentalComposeUiApi::class)
private val overlay = UIKitInteropProperties(
    interactionMode = UIKitInteropInteractionMode.NonCooperative,
    placedAsOverlay = true,
)

private fun Color.toUIColor(): UIColor =
    UIColor.colorWithRed(red.toDouble(), green.toDouble(), blue.toDouble(), alpha.toDouble())

/** The SF Symbol drawn for each app icon used on a native control. */
private fun symbolFor(icon: ImageVector): String = when (icon) {
    MyGymIcons.Settings -> "gearshape"
    MyGymIcons.Bell -> "bell"
    MyGymIcons.ChevronLeft -> "chevron.left"
    MyGymIcons.X -> "xmark"
    MyGymIcons.MoreVertical -> "ellipsis"
    MyGymIcons.MessageCircle -> "message"
    MyGymIcons.Phone -> "phone"
    MyGymIcons.List -> "list.bullet"
    MyGymIcons.LayoutGrid -> "square.grid.2x2"
    MyGymIcons.Plus -> "plus"
    MyGymIcons.Renew -> "arrow.clockwise"
    MyGymIcons.Archive -> "archivebox"
    MyGymIcons.Restore -> "arrow.uturn.backward"
    MyGymIcons.Trash -> "trash"
    else -> "circle"
}

private fun symbol(name: String, pointSize: Double): UIImage? =
    UIImage.systemImageNamed(name, withConfiguration = UIImageSymbolConfiguration.configurationWithPointSize(pointSize, UIImageSymbolWeightMedium))

/** Measured off screen, so this is the bar alone, without the home indicator area. */
@OptIn(ExperimentalForeignApi::class)
actual val systemTabBarHeight: Dp by lazy {
    UITabBar().sizeThatFits(CGSizeMake(400.0, 1000.0)).useContents { height }.dp
}

/**
 * A real UITabBar, which iOS 26 draws as a floating glass capsule centred in its frame. The frame
 * is just the bar's own height: given the home indicator area too, it stretches its items to fill it.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun SystemTabBar(tabs: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val select by rememberUpdatedState(onSelect)
    val delegate = remember { TabBarDelegate { select(it) } }
    val accent = MyGymTheme.colors.accent.toUIColor()
    val dark = MyGymTheme.colors.isDark
    val visible = nativeControlsVisible()

    UIKitView(
        factory = {
            UITabBar().apply {
                this.delegate = delegate
                alpha = 0.0
            }
        },
        modifier = modifier.fillMaxWidth().height(systemTabBarHeight),
        update = { bar ->
            if (bar.items?.map { (it as UITabBarItem).title } != tabs.map { it.label }) {
                val items = tabs.mapIndexed { index, tab ->
                    UITabBarItem(title = tab.label, image = UIImage.systemImageNamed(tab.symbol), tag = index.toLong())
                }
                bar.setItems(items, animated = false)
            }
            bar.selectedItem = bar.items?.getOrNull(selected) as? UITabBarItem
            bar.tintColor = accent
            bar.matchTheme(dark)
            bar.fadeTo(visible)
        },
        properties = overlay,
    )
}

private class TabBarDelegate(private val onSelect: (Int) -> Unit) : NSObject(), UITabBarDelegateProtocol {
    override fun tabBar(tabBar: UITabBar, didSelectItem: UITabBarItem) = onSelect(didSelectItem.tag.toInt())
}

private const val BADGE_HEIGHT = 18.0

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier,
    prominent: Boolean,
    badge: String?,
    menu: List<MenuAction>?,
) {
    val click by rememberUpdatedState(onClick)
    val colors = MyGymTheme.colors
    val ink = colors.ink.toUIColor()
    val accent = colors.accent.toUIColor()
    val onAccent = colors.onAccent.toUIColor()
    val visible = nativeControlsVisible()
    val side = size.value.toDouble()

    UIKitView(
        factory = {
            val badgeLabel = UILabel().apply {
                font = UIFont.systemFontOfSize(11.0, UIFontWeightSemibold)
                textAlignment = NSTextAlignmentCenter
                layer.cornerRadius = BADGE_HEIGHT / 2
                clipsToBounds = true
                tag = BADGE_TAG
            }
            UIButton.buttonWithType(0).apply {
                addAction(UIAction.actionWithHandler { click() }, forControlEvents = UIControlEventPrimaryActionTriggered)
                addSubview(badgeLabel)
                alpha = 0.0
            }
        },
        modifier = modifier.size(size).semantics { this.contentDescription = contentDescription },
        update = { button ->
            val config = if (prominent) UIButtonConfiguration.prominentGlassButtonConfiguration() else UIButtonConfiguration.glassButtonConfiguration()
            config.image = symbol(symbolFor(icon), if (prominent) 22.0 else 17.0)
            config.cornerStyle = UIButtonConfigurationCornerStyleCapsule
            config.baseForegroundColor = if (prominent) onAccent else ink
            button.configuration = config
            button.tintColor = if (prominent) accent else ink
            button.accessibilityLabel = contentDescription
            button.menu = menu?.let { actions -> UIMenu.menuWithTitle("", children = actions.map { it.toUIAction() }) }
            button.showsMenuAsPrimaryAction = menu != null

            val label = button.viewWithTag(BADGE_TAG) as UILabel
            label.hidden = badge == null
            label.text = badge
            label.backgroundColor = accent
            label.textColor = onAccent
            val width = maxOf(BADGE_HEIGHT, (badge?.length ?: 0) * 7.0 + 10.0)
            label.setFrame(CGRectMake(side - width, 0.0, width, BADGE_HEIGHT))
            button.bringSubviewToFront(label)
            button.matchTheme(colors.isDark)
            button.fadeTo(visible)
        },
        properties = overlay,
    )
}

private const val BADGE_TAG = 77L

private fun MenuAction.toUIAction(): UIAction =
    UIAction.actionWithTitle(label, image = null, identifier = null) { _ -> onClick() }.also {
        if (destructive) it.attributes = UIMenuElementAttributesDestructive
    }

private val ActionBarItemWidth = 84.dp

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun GlassActionBar(actions: List<BulkAction>, modifier: Modifier) {
    val currentActions by rememberUpdatedState(actions)
    val colors = MyGymTheme.colors
    val ink = colors.ink.toUIColor()
    val danger = colors.statusDue.toUIColor()
    val visible = nativeControlsVisible()

    UIKitView(
        factory = {
            val stack = UIStackView().apply {
                distribution = UIStackViewDistributionFillEqually
                tag = STACK_TAG
                translatesAutoresizingMaskIntoConstraints = false
            }
            UIVisualEffectView(effect = UIGlassEffect.effectWithStyle(UIGlassEffectStyle.UIGlassEffectStyleRegular).apply { interactive = true }).apply {
                cornerConfiguration = UICornerConfiguration.capsuleConfiguration()
                contentView.addSubview(stack)
                // Pinned rather than given a frame: the bar is still zero-sized when it's first updated,
                // and buttons laid out then all pile up at its leading edge.
                NSLayoutConstraint.activateConstraints(
                    listOf(
                        stack.leadingAnchor.constraintEqualToAnchor(contentView.leadingAnchor),
                        stack.trailingAnchor.constraintEqualToAnchor(contentView.trailingAnchor),
                        stack.topAnchor.constraintEqualToAnchor(contentView.topAnchor),
                        stack.bottomAnchor.constraintEqualToAnchor(contentView.bottomAnchor),
                    ),
                )
                alpha = 0.0
            }
        },
        modifier = modifier.width(ActionBarItemWidth * actions.size).height(GlassCapsuleHeight),
        update = { bar ->
            val stack = bar.contentView.viewWithTag(STACK_TAG) as UIStackView
            stack.arrangedSubviews.forEach { (it as UIView).removeFromSuperview() }
            actions.forEachIndexed { index, action ->
                val config = UIButtonConfiguration.plainButtonConfiguration()
                config.image = symbol(symbolFor(action.icon), 20.0)
                config.title = action.label
                // Set like the tab bar's items: icon over a small label.
                config.titleTextAttributesTransformer = { attributes ->
                    (attributes ?: emptyMap<Any?, Any?>()) + (NSFontAttributeName to UIFont.systemFontOfSize(10.0, UIFontWeightMedium))
                }
                config.imagePlacement = NSDirectionalRectEdgeTop
                config.imagePadding = 2.0
                config.baseForegroundColor = if (action.destructive) danger else ink
                val button = UIButton.buttonWithType(0)
                button.configuration = config
                button.addAction(UIAction.actionWithHandler { currentActions.getOrNull(index)?.onClick?.invoke() }, forControlEvents = UIControlEventPrimaryActionTriggered)
                stack.addArrangedSubview(button)
            }
            bar.matchTheme(colors.isDark)
            bar.fadeTo(visible)
        },
        properties = overlay,
    )
}

private const val STACK_TAG = 78L

@Composable
actual fun SystemConfirmPrompt(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val host = LocalUIViewController.current
    val dark = MyGymTheme.colors.isDark
    val confirm by rememberUpdatedState(onConfirm)
    val dismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(Unit) {
        var answered = false
        val alert = UIAlertController.alertControllerWithTitle(title, body, UIAlertControllerStyleActionSheet)
        alert.overrideUserInterfaceStyle = if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
        alert.addAction(
            UIAlertAction.actionWithTitle(confirmLabel, if (destructive) UIAlertActionStyleDestructive else UIAlertActionStyleDefault) { _ ->
                answered = true
                confirm()
            },
        )
        // Tapping outside the sheet also runs the cancel action.
        alert.addAction(UIAlertAction.actionWithTitle(cancelLabel, UIAlertActionStyleCancel) { _ ->
            answered = true
            dismiss()
        })
        host.presentViewController(alert, animated = true, completion = null)
        // The caller can drop the prompt without an answer (say, by leaving the screen); take it down too.
        onDispose { if (!answered) alert.dismissViewControllerAnimated(true, completion = null) }
    }
}
