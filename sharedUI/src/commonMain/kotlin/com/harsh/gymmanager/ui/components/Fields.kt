package com.harsh.gymmanager.ui.components

import androidx.compose.material3.Surface
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.harsh.gymmanager.ui.format.formatFull
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
private fun FieldLabel(label: String, optional: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.xs)) {
        Text(label, style = AkhadaTheme.typography.caption.copy(fontWeight = AkhadaTheme.typography.bodyStrong.fontWeight))
        if (optional) Text(stringResource(Res.string.optional), style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.inkMuted)
    }
}

@Composable
private fun FieldError(error: String?) {
    if (error != null) Text(error, style = AkhadaTheme.typography.caption, color = AkhadaTheme.colors.statusDue)
}

/** A labelled input on a filled ground. A [prefix] such as "₹" turns it into an amount field. */
@Composable
fun AkhadaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    optional: Boolean = false,
    error: String? = null,
    placeholder: String? = null,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
) {
    val colors = AkhadaTheme.colors
    val typography = AkhadaTheme.typography
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label, optional)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            textStyle = typography.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
            interactionSource = interaction,
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .fieldGround(focused = focused, error = error != null)
                        .padding(horizontal = AkhadaSpace.lg, vertical = 14.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.sm),
                ) {
                    if (prefix != null) Text(prefix, style = typography.title.copy(fontFamily = typography.stat.fontFamily), color = colors.inkMuted)
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = typography.body, color = colors.inkMuted)
                        inner()
                    }
                }
            },
        )
        FieldError(error)
    }
}

@Composable
private fun Modifier.fieldGround(focused: Boolean, error: Boolean): Modifier {
    val colors = AkhadaTheme.colors
    val ring = when {
        error -> colors.statusDue
        focused -> colors.ink
        else -> null
    }
    val filled = background(colors.surfaceSunken, AkhadaRadius.fieldShape)
    return if (ring != null) filled.border(2.dp, ring, AkhadaRadius.fieldShape) else filled
}

/**
 * A date field that opens the date picker. Optional dates get a clear button once set. Dates after
 * [latest] can't be picked; with no value yet, the picker opens on [openAt]'s month.
 */
@Composable
fun DateField(
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    optional: Boolean = false,
    placeholder: String? = null,
    latest: LocalDate? = null,
    openAt: LocalDate? = null,
) {
    val colors = AkhadaTheme.colors
    var picking by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(label, optional)
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .fieldGround(focused = false, error = false)
                .clickable(onClickLabel = stringResource(Res.string.choose_field, label)) { picking = true }
                .padding(start = AkhadaSpace.lg, end = AkhadaSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value?.formatFull() ?: placeholder ?: stringResource(Res.string.choose_a_date),
                Modifier.weight(1f),
                style = AkhadaTheme.typography.body,
                color = if (value == null) colors.inkMuted else colors.ink,
            )
            if (optional && value != null) {
                AkhadaIconButton(
                    AkhadaIcons.X, stringResource(Res.string.clear_field, label), onClick = { onValueChange(null) },
                    size = 36.dp, iconSize = 18.dp, container = colors.surfaceSunken, content = colors.inkMuted,
                )
            } else {
                Icon(AkhadaIcons.Calendar, contentDescription = null, Modifier.size(20.dp), tint = colors.inkMuted)
            }
        }
    }
    if (picking) {
        AkhadaDatePickerDialog(
            initial = value,
            latest = latest,
            openAt = openAt,
            onDismiss = { picking = false },
            onConfirm = { picking = false; onValueChange(it) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AkhadaDatePickerDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    latest: LocalDate? = null,
    openAt: LocalDate? = null,
) {
    val colors = AkhadaTheme.colors
    // The picker works in UTC midnight millis; convert at the edges only.
    fun LocalDate.millis() = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.millis(),
        initialDisplayedMonthMillis = (initial ?: openAt)?.millis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = latest == null || utcTimeMillis <= latest.millis()
            override fun isSelectableYear(year: Int) = latest == null || year <= latest.year
        },
    )
    // Selection in ink, as everywhere else in the app.
    val pickerColors = DatePickerDefaults.colors(
        containerColor = colors.surface,
        selectedDayContainerColor = colors.ink,
        selectedDayContentColor = colors.onInk,
        selectedYearContainerColor = colors.ink,
        selectedYearContentColor = colors.onInk,
        todayDateBorderColor = colors.ink,
        todayContentColor = colors.ink,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onConfirm(Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date)
                    } ?: onDismiss()
                },
            ) { Text(stringResource(Res.string.done), color = colors.ink, style = AkhadaTheme.typography.bodyStrong) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel), color = colors.inkMuted, style = AkhadaTheme.typography.bodyStrong) }
        },
        colors = pickerColors,
    ) {
        DatePicker(state = state, colors = pickerColors)
    }
}

/**
 * Quick picks under a field, most used first. The chip matching the field's current value is
 * filled in ink; tapping one fills the field.
 */
@Composable
fun SuggestionChips(labels: List<String>, selected: Int?, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    if (labels.isEmpty()) return
    val colors = AkhadaTheme.colors
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
        labels.forEachIndexed { index, label ->
            val on = index == selected
            Surface(
                onClick = { onPick(index) },
                shape = AkhadaRadius.pillShape,
                color = if (on) colors.ink else colors.surface,
                contentColor = if (on) colors.onInk else colors.ink,
                border = if (on) null else BorderStroke(1.dp, colors.line),
            ) {
                Text(
                    label,
                    Modifier.heightIn(min = 36.dp).padding(horizontal = AkhadaSpace.md, vertical = AkhadaSpace.sm),
                    style = AkhadaTheme.typography.caption.copy(fontWeight = AkhadaTheme.typography.bodyStrong.fontWeight),
                )
            }
        }
    }
}
