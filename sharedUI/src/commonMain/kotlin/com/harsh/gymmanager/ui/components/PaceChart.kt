package com.harsh.gymmanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import gymmanager.sharedui.generated.resources.Res
import gymmanager.sharedui.generated.resources.chart_day
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Running totals through the month: this month (accent, up to today) over last month (muted, in
 * full; leave it empty when there's nothing to compare). Press and drag to read both values on any
 * day. [startLabel] and [endLabel] name the month's first and last days; [description] is what
 * screen readers hear.
 */
@Composable
fun PaceChart(
    thisMonth: List<Long>,
    lastMonth: List<Long>,
    daysInMonth: Int,
    thisLabel: String,
    lastLabel: String,
    startLabel: String,
    endLabel: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = AkhadaTheme.colors
    val typography = AkhadaTheme.typography
    val days = maxOf(daysInMonth, lastMonth.size, 2)
    val top = (maxOf(thisMonth.maxOrNull() ?: 0L, lastMonth.maxOrNull() ?: 0L).coerceAtLeast(1L) * 1.1f)
    var scrubDay by remember { mutableStateOf<Int?>(null) }

    Column(modifier.semantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
        Box(Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            Canvas(
                Modifier.fillMaxWidth().height(CHART_HEIGHT)
                    .pointerInput(days) {
                        fun dayAt(x: Float) = ((x / size.width) * (days - 1)).roundToInt().coerceIn(0, days - 1)
                        // Follows the finger without consuming it, so the page still scrolls; a scroll ends the readout.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            scrubDay = dayAt(down.position.x)
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed || change.isConsumed) break
                                scrubDay = dayAt(change.position.x)
                            }
                            scrubDay = null
                        }
                    },
            ) {
                fun x(day: Int) = size.width * day / (days - 1)
                fun y(value: Long) = size.height - size.height * (value / top)
                // Baseline: a recessive hairline.
                drawLine(colors.line, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), strokeWidth = 1.dp.toPx())
                drawSeries(lastMonth, colors.chartReference, ring = null, ::x, ::y)
                drawSeries(thisMonth, colors.accent, ring = colors.surface, ::x, ::y)
                if (thisMonth.isNotEmpty()) {
                    val end = Offset(x(thisMonth.lastIndex), y(thisMonth.last()))
                    drawCircle(colors.surface, radius = 6.dp.toPx(), center = end)
                    drawCircle(colors.accent, radius = 4.dp.toPx(), center = end)
                }
                scrubDay?.let { day ->
                    drawLine(colors.inkMuted, Offset(x(day), 0f), Offset(x(day), size.height), strokeWidth = 1.dp.toPx())
                }
            }
            scrubDay?.let { day ->
                ScrubReadout(
                    day = day + 1,
                    rows = listOfNotNull(
                        thisMonth.getOrNull(day)?.let { Triple(colors.accent, thisLabel, it) },
                        lastMonth.getOrNull(day)?.let { Triple(colors.chartReference, lastLabel, it) },
                    ),
                    modifier = Modifier.align(if (day < days / 2) Alignment.TopEnd else Alignment.TopStart),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(startLabel, Modifier.weight(1f), style = typography.caption, color = colors.inkMuted)
            Text(endLabel, style = typography.caption, color = colors.inkMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.lg), verticalAlignment = Alignment.CenterVertically) {
            LegendKey(colors.accent, thisLabel)
            if (lastMonth.isNotEmpty()) LegendKey(colors.chartReference, lastLabel)
        }
    }
}

private fun DrawScope.drawSeries(values: List<Long>, color: Color, ring: Color?, x: (Int) -> Float, y: (Long) -> Float) {
    if (values.size < 2) return
    val path = Path().apply {
        moveTo(x(0), y(values[0]))
        for (i in 1..values.lastIndex) lineTo(x(i), y(values[i]))
    }
    // A surface-coloured ring keeps the line legible where it crosses the other one.
    ring?.let { drawPath(path, it, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
    drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Values lead, series names follow, each keyed by a short stroke of its colour. */
@Composable
private fun ScrubReadout(day: Int, rows: List<Triple<Color, String, Long>>, modifier: Modifier = Modifier) {
    val colors = AkhadaTheme.colors
    Column(
        modifier.background(colors.surface, AkhadaRadius.fieldShape).padding(horizontal = AkhadaSpace.md, vertical = AkhadaSpace.sm),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(stringResource(Res.string.chart_day, day), style = AkhadaTheme.typography.caption, color = colors.inkMuted)
        rows.forEach { (color, label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
                LineKey(color)
                Text(formatRupees(value), style = AkhadaTheme.typography.bodyStrong)
                Text(label, style = AkhadaTheme.typography.caption, color = colors.inkMuted)
            }
        }
    }
}

@Composable
private fun LegendKey(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
        LineKey(color)
        Text(label, style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.inkMuted)
    }
}

@Composable
private fun LineKey(color: Color) {
    Box(Modifier.size(width = 14.dp, height = 2.dp).background(color, AkhadaRadius.pillShape))
}

private val CHART_HEIGHT = 128.dp
