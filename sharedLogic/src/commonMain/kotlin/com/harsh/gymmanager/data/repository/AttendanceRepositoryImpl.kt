package com.harsh.gymmanager.data.repository

import com.harsh.gymmanager.data.local.dao.CheckInDao
import com.harsh.gymmanager.data.local.entity.CheckInEntity
import com.harsh.gymmanager.domain.model.CheckIn
import com.harsh.gymmanager.domain.repository.AttendanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

class AttendanceRepositoryImpl(
    private val dao: CheckInDao,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : AttendanceRepository {
    override suspend fun checkIn(memberId: String): CheckIn =
        dao.checkIn(CheckInEntity(memberId = memberId.toLong(), checkedInOn = clock.todayIn(timeZone), checkedInAt = clock.now())).toDomain()

    override suspend fun undoCheckIn(id: String) = dao.deleteById(id.toLong())

    override fun observeCheckIns(date: LocalDate): Flow<List<CheckIn>> =
        dao.observeOn(date).map { rows -> rows.map { it.toDomain() } }

    override fun observeMemberCheckIns(memberId: String, limit: Int): Flow<List<CheckIn>> =
        dao.observeForMember(memberId.toLong(), limit).map { rows -> rows.map { it.toDomain() } }

    override fun observeLastVisits(): Flow<Map<String, LocalDate>> =
        dao.observeLastVisits().map { rows -> rows.associate { it.memberId.toString() to it.lastVisit } }

    private fun CheckInEntity.toDomain() = CheckIn(id.toString(), memberId.toString(), checkedInOn, checkedInAt)
}
