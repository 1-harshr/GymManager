package com.harsh.gymmanager.domain.usecase

import com.harsh.gymmanager.domain.importing.ImportPreview
import com.harsh.gymmanager.domain.importing.parseMembersCsv
import com.harsh.gymmanager.domain.model.Member
import com.harsh.gymmanager.domain.repository.MemberRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Adds members from a CSV file: [preview] reads and checks it, then [import] saves what it found. */
class ImportMembersUseCase(
    private val repository: MemberRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    suspend fun preview(csv: String): Result<ImportPreview> = resultOf {
        parseMembersCsv(csv, clock.todayIn(timeZone), repository.getPhones())
    }

    /** Saves all of [members] in one transaction and returns how many were added. */
    suspend fun import(members: List<Member>): Result<Int> = resultOf { repository.addMembers(members).size }
}
