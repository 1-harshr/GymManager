package com.harsh.gymmanager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.ui.theme.AkhadaElevation
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaSize
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class TabItem(val label: String, val icon: ImageVector)

/**
 * The floating pill that switches between the main screens. The ink pill can be dragged
 * across the bar: it follows the finger, ticks on each tab it crosses and springs to the
 * nearest one on release.
 */
@Composable
fun FloatingTabBar(
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AkhadaTheme.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val itemPx = with(LocalDensity.current) { AkhadaSize.tabItemWidth.toPx() }
    val maxPx = itemPx * (tabs.size - 1)
    val itemHeight = AkhadaSize.tabBarHeight - AkhadaSize.tabBarPadding * 2

    val indicator = remember { Animatable(selected * itemPx) }
    val glide = spring<Float>(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)
    var dragging by remember { mutableStateOf(false) }
    var hover by remember { mutableIntStateOf(selected) }
    val currentSelected by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)

    LaunchedEffect(selected, itemPx) {
        if (!dragging) {
            hover = selected
            indicator.animateTo(selected * itemPx, glide)
        }
    }
    val lift by animateFloatAsState(if (dragging) 1.06f else 1f, label = "lift")

    Surface(
        modifier = modifier,
        shape = AkhadaRadius.pillShape,
        color = colors.surface,
        shadowElevation = AkhadaElevation.float,
    ) {
        Box(
            Modifier
                .padding(AkhadaSize.tabBarPadding)
                .size(AkhadaSize.tabItemWidth * tabs.size, itemHeight)
                .pointerInput(itemPx, tabs.size) {
                    fun indexAt(x: Float) = (x / itemPx).toInt().coerceIn(0, tabs.size - 1)
                    detectHorizontalDragGestures(
                        onDragStart = { start ->
                            dragging = true
                            hover = indexAt(start.x)
                            scope.launch {
                                indicator.animateTo(
                                    (start.x - itemPx / 2).coerceIn(0f, maxPx),
                                    spring(stiffness = Spring.StiffnessHigh),
                                )
                            }
                        },
                        onDragEnd = {
                            dragging = false
                            val target = hover
                            scope.launch { indicator.animateTo(target * itemPx, glide) }
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            select(target)
                        },
                        onDragCancel = {
                            dragging = false
                            hover = currentSelected
                            scope.launch { indicator.animateTo(currentSelected * itemPx, glide) }
                        },
                    ) { change, dx ->
                        change.consume()
                        val x = (indicator.value + dx).coerceIn(0f, maxPx)
                        scope.launch { indicator.snapTo(x) }
                        val under = indexAt(x + itemPx / 2)
                        if (under != hover) {
                            hover = under
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        }
                    }
                },
        ) {
            Box(
                Modifier
                    .offset { IntOffset(indicator.value.roundToInt(), 0) }
                    .size(AkhadaSize.tabItemWidth, itemHeight)
                    .graphicsLayer { scaleX = lift; scaleY = lift }
                    .clip(AkhadaRadius.pillShape)
                    .background(colors.ink),
            )
            Row {
                tabs.forEachIndexed { index, tab ->
                    key(tab.label) {
                        val lit = index == (if (dragging) hover else selected)
                        val tint by animateColorAsState(if (lit) colors.onInk else colors.inkMuted, label = "tint")
                        Column(
                            Modifier
                                .size(AkhadaSize.tabItemWidth, itemHeight)
                                .clip(AkhadaRadius.pillShape)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    onSelect(index)
                                }
                                .semantics(mergeDescendants = true) {
                                    role = Role.Tab
                                    this.selected = index == selected
                                    contentDescription = tab.label
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(tab.icon, contentDescription = null, Modifier.size(AkhadaSize.tabIcon), tint = tint)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                tab.label,
                                style = AkhadaTheme.typography.tab,
                                color = tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
