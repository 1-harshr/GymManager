package com.harsh.gymmanager.ui.screens.importmembers

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harsh.gymmanager.LocalAppDependencies
import com.harsh.gymmanager.domain.importing.ImportPreview
import com.harsh.gymmanager.presentation.importmembers.ImportMembersEvent
import com.harsh.gymmanager.presentation.importmembers.ImportMembersViewModel
import com.harsh.gymmanager.ui.components.AkhadaButton
import com.harsh.gymmanager.ui.components.AkhadaCard
import com.harsh.gymmanager.ui.components.Badge
import com.harsh.gymmanager.ui.components.BadgeTone
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.format.formatPhone
import com.harsh.gymmanager.ui.icons.AkhadaIcons
import com.harsh.gymmanager.ui.platform.rememberTextFilePicker
import com.harsh.gymmanager.ui.theme.AkhadaRadius
import com.harsh.gymmanager.ui.theme.AkhadaSpace
import com.harsh.gymmanager.ui.theme.AkhadaTheme
import com.harsh.gymmanager.ui.theme.screenGutter

private const val PREVIEW_LIMIT = 20

@Composable
fun ImportMembersScreen(onBack: () -> Unit, onImported: (Int) -> Unit) {
    val deps = LocalAppDependencies.current
    val colors = AkhadaTheme.colors
    val vm = viewModel { ImportMembersViewModel(deps.importMembers) }
    val state by vm.state.collectAsStateWithLifecycle()
    val pickFile = rememberTextFilePicker(vm::onFilePicked)
    LaunchedEffect(vm) { vm.events.collect { if (it is ImportMembersEvent.Imported) onImported(it.count) } }

    val preview = state.preview
    Column(Modifier.fillMaxSize()) {
        PushedHeader("Import members", onBack, subtitle = state.fileName ?: "From a CSV file")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).screenGutter().padding(bottom = AkhadaSpace.xl),
            verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
        ) {
            if (preview == null) {
                AkhadaCard {
                    Text("Bring in members from a spreadsheet", style = AkhadaTheme.typography.title)
                    Text(
                        "In Excel or Google Sheets, put the column names in the first row, then save or download the sheet as CSV. Only Name is required.",
                        style = AkhadaTheme.typography.body,
                        color = colors.inkMuted,
                    )
                    Text(
                        "Name,Phone,Gender,Date of birth,Joined on,Notes\nRavi Kumar,98765 43210,M,15/08/1998,05/11/2026,Knee injury",
                        Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceSunken, AkhadaRadius.fieldShape)
                            .horizontalScroll(rememberScrollState())
                            .padding(AkhadaSpace.md),
                        style = AkhadaTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
                        softWrap = false,
                    )
                    Text(
                        "Dates are read day first, so 05/11/2026 is 5 November. Members whose phone number is already saved are skipped.",
                        style = AkhadaTheme.typography.caption,
                        color = colors.inkMuted,
                    )
                }
            } else {
                ImportSummary(preview)
            }
            state.errorMessage?.let { Text(it, style = AkhadaTheme.typography.body, color = colors.statusDue) }
        }
        Column(Modifier.screenGutter().navigationBarsPadding().padding(vertical = AkhadaSpace.md), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
            val ready = preview?.members?.size ?: 0
            if (ready > 0) {
                AkhadaButton(
                    if (state.isImporting) "Adding…" else if (ready == 1) "Add 1 member" else "Add $ready members",
                    vm::import,
                    Modifier.fillMaxWidth(),
                    enabled = !state.isImporting,
                )
            }
            AkhadaButton(
                if (state.isReading) "Reading…" else if (preview == null) "Choose CSV file" else "Choose a different file",
                pickFile,
                Modifier.fillMaxWidth(),
                kind = if (ready > 0) ButtonKind.Secondary else ButtonKind.Primary,
                enabled = !state.isReading && !state.isImporting,
                icon = AkhadaIcons.FileUp,
            )
        }
    }
}

@Composable
private fun ImportSummary(preview: ImportPreview) {
    val colors = AkhadaTheme.colors
    AkhadaCard {
        Text(
            when (preview.members.size) {
                0 -> "No members to add"
                1 -> "1 member ready to add"
                else -> "${preview.members.size} members ready to add"
            },
            style = AkhadaTheme.typography.title,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
            Badge(plural(preview.rowCount, "row"), BadgeTone.Expired, showDot = false)
            if (preview.skippedCount > 0) Badge("${preview.skippedCount} skipped", BadgeTone.Due)
            if (preview.warningCount > 0) Badge(plural(preview.warningCount, "warning"), BadgeTone.Expiring)
        }
    }
    if (preview.issues.isNotEmpty()) {
        SectionHeader("Check these rows", Modifier.padding(top = AkhadaSpace.md))
        AkhadaCard {
            preview.issues.forEach { issue ->
                Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
                    Text("Row ${issue.line}", style = AkhadaTheme.typography.caption, color = colors.inkMuted)
                    Text(
                        (if (issue.skipped) "Skipped: " else "") + issue.message,
                        Modifier.weight(1f),
                        style = AkhadaTheme.typography.caption,
                        color = if (issue.skipped) colors.statusDue else colors.ink,
                    )
                }
            }
        }
    }
    if (preview.members.isNotEmpty()) {
        SectionHeader("Members to add", Modifier.padding(top = AkhadaSpace.md))
        AkhadaCard {
            preview.members.take(PREVIEW_LIMIT).forEach { member ->
                Row {
                    Text(member.name, Modifier.weight(1f), style = AkhadaTheme.typography.body)
                    Text(member.phone?.let(::formatPhone) ?: "No phone", style = AkhadaTheme.typography.caption, color = colors.inkMuted)
                }
            }
            if (preview.members.size > PREVIEW_LIMIT) {
                Text("and ${preview.members.size - PREVIEW_LIMIT} more", style = AkhadaTheme.typography.caption, color = colors.inkMuted)
            }
        }
    }
}

private fun plural(count: Int, word: String) = if (count == 1) "1 $word" else "$count ${word}s"
