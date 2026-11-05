package com.harsh.gymmanager.ui.screens.importmembers

import gymmanager.sharedui.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource
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
import com.harsh.gymmanager.domain.importing.ImportIssue
import com.harsh.gymmanager.domain.importing.ImportIssueKind
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
import com.harsh.gymmanager.ui.format.asString
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
        PushedHeader(stringResource(Res.string.import_members), onBack, subtitle = state.fileName ?: stringResource(Res.string.from_csv))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).screenGutter().padding(bottom = AkhadaSpace.xl),
            verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md),
        ) {
            if (preview == null) {
                AkhadaCard {
                    Text(stringResource(Res.string.import_intro_title), style = AkhadaTheme.typography.title)
                    Text(
                        stringResource(Res.string.import_intro_body),
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
                        stringResource(Res.string.import_intro_note),
                        style = AkhadaTheme.typography.caption,
                        color = colors.inkMuted,
                    )
                }
            } else {
                ImportSummary(preview)
            }
            state.errorMessage?.let { Text(it.asString(), style = AkhadaTheme.typography.body, color = colors.statusDue) }
        }
        Column(Modifier.screenGutter().navigationBarsPadding().padding(vertical = AkhadaSpace.md), verticalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
            val ready = preview?.members?.size ?: 0
            if (ready > 0) {
                AkhadaButton(
                    if (state.isImporting) stringResource(Res.string.adding) else pluralStringResource(Res.plurals.add_members_button, ready, ready),
                    vm::import,
                    Modifier.fillMaxWidth(),
                    enabled = !state.isImporting,
                )
            }
            AkhadaButton(
                if (state.isReading) stringResource(Res.string.reading) else if (preview == null) stringResource(Res.string.choose_csv) else stringResource(Res.string.choose_different_file),
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
            if (preview.members.isEmpty()) stringResource(Res.string.no_members_to_add) else pluralStringResource(Res.plurals.members_ready, preview.members.size, preview.members.size),
            style = AkhadaTheme.typography.title,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.sm)) {
            Badge(pluralStringResource(Res.plurals.rows_count, preview.rowCount, preview.rowCount), BadgeTone.Expired, showDot = false)
            if (preview.skippedCount > 0) Badge(stringResource(Res.string.skipped_count, preview.skippedCount), BadgeTone.Due)
            if (preview.warningCount > 0) Badge(pluralStringResource(Res.plurals.warnings_count, preview.warningCount, preview.warningCount), BadgeTone.Expiring)
        }
    }
    if (preview.issues.isNotEmpty()) {
        SectionHeader(stringResource(Res.string.check_rows), Modifier.padding(top = AkhadaSpace.md))
        AkhadaCard {
            preview.issues.forEach { issue ->
                Row(horizontalArrangement = Arrangement.spacedBy(AkhadaSpace.md)) {
                    Text(stringResource(Res.string.row_number, issue.line), style = AkhadaTheme.typography.caption, color = colors.inkMuted)
                    Text(
                        issue.asString(),
                        Modifier.weight(1f),
                        style = AkhadaTheme.typography.caption,
                        color = if (issue.skipped) colors.statusDue else colors.ink,
                    )
                }
            }
        }
    }
    if (preview.members.isNotEmpty()) {
        SectionHeader(stringResource(Res.string.members_to_add), Modifier.padding(top = AkhadaSpace.md))
        AkhadaCard {
            preview.members.take(PREVIEW_LIMIT).forEach { member ->
                Row {
                    Text(member.name, Modifier.weight(1f), style = AkhadaTheme.typography.body)
                    Text(member.phone?.let(::formatPhone) ?: stringResource(Res.string.no_phone), style = AkhadaTheme.typography.caption, color = colors.inkMuted)
                }
            }
            if (preview.members.size > PREVIEW_LIMIT) {
                Text(stringResource(Res.string.and_more, preview.members.size - PREVIEW_LIMIT), style = AkhadaTheme.typography.caption, color = colors.inkMuted)
            }
        }
    }
}

@Composable
private fun ImportIssue.asString(): String {
    val who = name.orEmpty()
    val what = value.orEmpty()
    return when (kind) {
        ImportIssueKind.NO_NAME -> stringResource(Res.string.issue_no_name)
        ImportIssueKind.DUPLICATE_PHONE -> stringResource(Res.string.issue_duplicate_phone, who, what)
        ImportIssueKind.BAD_PHONE -> stringResource(Res.string.issue_bad_phone, who, what)
        ImportIssueKind.BAD_GENDER -> stringResource(Res.string.issue_bad_gender, who, what)
        ImportIssueKind.BAD_BIRTH_DATE -> stringResource(Res.string.issue_bad_birth_date, who, what)
        ImportIssueKind.BAD_JOIN_DATE -> stringResource(Res.string.issue_bad_join_date, who, what)
    }
}
