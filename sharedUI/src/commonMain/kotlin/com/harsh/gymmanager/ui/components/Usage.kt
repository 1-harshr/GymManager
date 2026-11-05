package com.harsh.gymmanager.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.harsh.gymmanager.domain.suggestions.AmountSuggestion
import com.harsh.gymmanager.presentation.formatRupees
import com.harsh.gymmanager.presentation.parseRupees
import gymmanager.sharedui.generated.resources.Res
import gymmanager.sharedui.generated.resources.chip_full
import gymmanager.sharedui.generated.resources.chip_half
import gymmanager.sharedui.generated.resources.chip_pay_later
import org.jetbrains.compose.resources.stringResource
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.suggestions.UsageStats

/** What the owner does most often, read once when the screen opens; empty until it loads. */
@Composable
fun rememberUsageStats(): State<UsageStats> {
    val deps = LocalAppDependencies.current
    return produceState(UsageStats()) { value = deps.suggestions.stats() }
}

/**
 * Amount chips for a field holding [text]; picking one writes its amount, or clears it to pay
 * later. The amount already in the field gets no chip.
 */
@Composable
fun AmountChips(suggestions: List<AmountSuggestion>, text: String, onPick: (String) -> Unit) {
    val current = parseRupees(text)
    val shown = suggestions.filterNot {
        if (it.kind == AmountSuggestion.Kind.PAY_LATER) text.isBlank() else current == it.amount
    }
    SuggestionChips(
        labels = shown.map {
            when (it.kind) {
                AmountSuggestion.Kind.FULL -> stringResource(Res.string.chip_full, formatRupees(it.amount))
                AmountSuggestion.Kind.HALF -> stringResource(Res.string.chip_half, formatRupees(it.amount))
                AmountSuggestion.Kind.PAY_LATER -> stringResource(Res.string.chip_pay_later)
                AmountSuggestion.Kind.USUAL -> formatRupees(it.amount)
            }
        },
        onPick = { i -> shown[i].let { onPick(if (it.kind == AmountSuggestion.Kind.PAY_LATER) "" else it.amount.toString()) } },
    )
}
