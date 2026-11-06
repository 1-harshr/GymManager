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
import com.harsh.gymmanager.ui.components.MyGymButton
import com.harsh.gymmanager.ui.components.MyGymCard
import com.harsh.gymmanager.ui.components.Badge
import com.harsh.gymmanager.ui.components.BadgeTone
import com.harsh.gymmanager.ui.components.ButtonKind
import com.harsh.gymmanager.ui.components.PushedHeader
import com.harsh.gymmanager.ui.components.SectionHeader
import com.harsh.gymmanager.ui.format.asString
import com.harsh.gymmanager.ui.format.formatPhone
import com.harsh.gymmanager.ui.icons.MyGymIcons
import com.harsh.gymmanager.ui.platform.rememberTextFilePicker
import com.harsh.gymmanager.ui.platform.rememberFileSaver
import com.harsh.gymmanager.ui.LocalSnackbar
import com.harsh.gymmanager.domain.importing.MEMBERS_TEMPLATE_FILE_NAME
import com.harsh.gymmanager.domain.importing.membersCsvTemplate
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import com.harsh.gymmanager.ui.theme.MyGymRadius
import com.harsh.gymmanager.ui.theme.MyGymSpace
import com.harsh.gymmanager.ui.theme.MyGymTheme
import com.harsh.gymmanager.ui.theme.screenGutter

private const val PREVIEW_LIMIT = 20

@Composable
fun ImportMembersScreen(onBack: () -> Unit, onImported: (Int) -> Unit) {
    val deps = LocalAppDependencies.current
    val colors = MyGymTheme.colors
    val vm = viewModel { ImportMembersViewModel(deps.importMembers) }
    val state by vm.state.collectAsStateWithLifecycle()
    val pickFile = rememberTextFilePicker(vm::onFilePicked)
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val saveFile = rememberFileSaver("text/csv") { saved ->
        if (saved) scope.launch { snackbar.showSnackbar(getString(Res.string.template_saved)) }
    }
    LaunchedEffect(vm) { vm.events.collect { if (it is ImportMembersEvent.Imported) onImported(it.count) } }

    val preview = state.preview
    Column(Modifier.fillMaxSize()) {
        PushedHeader(stringResource(Res.string.import_members), onBack, subtitle = state.fileName ?: stringResource(Res.string.from_csv))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).screenGutter().padding(bottom = MyGymSpace.xl),
            verticalArrangement = Arrangement.spacedBy(MyGymSpace.md),
        ) {
            if (preview == null) {
                MyGymCard {
                    Text(stringResource(Res.string.import_intro_title), style = MyGymTheme.typography.title)
                    Text(
                        stringResource(Res.string.import_intro_body),
                        style = MyGymTheme.typography.body,
                        color = colors.inkMuted,
                    )
                    Text(
                        "Name,Phone,Gender,Date of birth,Joined on,Notes\nRavi Kumar,98765 43210,M,15/08/1998,05/11/2026,Knee injury",
                        Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceSunken, MyGymRadius.fieldShape)
                            .horizontalScroll(rememberScrollState())
                            .padding(MyGymSpace.md),
                        style = MyGymTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
                        softWrap = false,
                    )
                    Text(
                        stringResource(Res.string.import_intro_note),
                        style = MyGymTheme.typography.caption,
                        color = colors.inkMuted,
                    )
                    MyGymButton(
                        stringResource(Res.string.download_template),
                        { saveFile(MEMBERS_TEMPLATE_FILE_NAME, membersCsvTemplate().encodeToByteArray()) },
                        Modifier.fillMaxWidth(),
                        kind = ButtonKind.Secondary,
                    )
                }
            } else {
                ImportSummary(preview)
            }
            state.errorMessage?.let { Text(it.asString(), style = MyGymTheme.typography.body, color = colors.statusDue) }
        }
        Column(Modifier.screenGutter().navigationBarsPadding().padding(vertical = MyGymSpace.md), verticalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
            val ready = preview?.members?.size ?: 0
            if (ready > 0) {
                MyGymButton(
                    if (state.isImporting) stringResource(Res.string.adding) else pluralStringResource(Res.plurals.add_members_button, ready, ready),
                    vm::import,
                    Modifier.fillMaxWidth(),
                    enabled = !state.isImporting,
                )
            }
            MyGymButton(
                if (state.isReading) stringResource(Res.string.reading) else if (preview == null) stringResource(Res.string.choose_csv) else stringResource(Res.string.choose_different_file),
                pickFile,
                Modifier.fillMaxWidth(),
                kind = if (ready > 0) ButtonKind.Secondary else ButtonKind.Primary,
                enabled = !state.isReading && !state.isImporting,
                icon = MyGymIcons.FileUp,
            )
        }
    }
}

@Composable
private fun ImportSummary(preview: ImportPreview) {
    val colors = MyGymTheme.colors
    MyGymCard {
        Text(
            if (preview.members.isEmpty()) stringResource(Res.string.no_members_to_add) else pluralStringResource(Res.plurals.members_ready, preview.members.size, preview.members.size),
            style = MyGymTheme.typography.title,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MyGymSpace.sm)) {
            Badge(pluralStringResource(Res.plurals.rows_count, preview.rowCount, preview.rowCount), BadgeTone.Expired, showDot = false)
            if (preview.skippedCount > 0) Badge(stringResource(Res.string.skipped_count, preview.skippedCount), BadgeTone.Due)
            if (preview.warningCount > 0) Badge(pluralStringResource(Res.plurals.warnings_count, preview.warningCount, preview.warningCount), BadgeTone.Expiring)
        }
    }
    if (preview.issues.isNotEmpty()) {
        SectionHeader(stringResource(Res.string.check_rows), Modifier.padding(top = MyGymSpace.md))
        MyGymCard {
            preview.issues.forEach { issue ->
                Row(horizontalArrangement = Arrangement.spacedBy(MyGymSpace.md)) {
                    Text(stringResource(Res.string.row_number, issue.line), style = MyGymTheme.typography.caption, color = colors.inkMuted)
                    Text(
                        issue.asString(),
                        Modifier.weight(1f),
                        style = MyGymTheme.typography.caption,
                        color = if (issue.skipped) colors.statusDue else colors.ink,
                    )
                }
            }
        }
    }
    if (preview.members.isNotEmpty()) {
        SectionHeader(stringResource(Res.string.members_to_add), Modifier.padding(top = MyGymSpace.md))
        MyGymCard {
            preview.members.take(PREVIEW_LIMIT).forEach { member ->
                Row {
                    Text(member.name, Modifier.weight(1f), style = MyGymTheme.typography.body)
                    Text(member.phone?.let(::formatPhone) ?: stringResource(Res.string.no_phone), style = MyGymTheme.typography.caption, color = colors.inkMuted)
                }
            }
            if (preview.members.size > PREVIEW_LIMIT) {
                Text(stringResource(Res.string.and_more, preview.members.size - PREVIEW_LIMIT), style = MyGymTheme.typography.caption, color = colors.inkMuted)
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
