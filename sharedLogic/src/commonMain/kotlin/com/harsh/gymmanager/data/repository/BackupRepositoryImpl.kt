package com.harsh.gymmanager.data.repository

import com.harsh.gymmanager.data.local.dao.BackupDao
import com.harsh.gymmanager.data.local.entity.MemberEntity
import com.harsh.gymmanager.data.local.entity.MembershipEntity
import com.harsh.gymmanager.data.local.entity.MessageLogEntity
import com.harsh.gymmanager.data.local.entity.PaymentEntity
import com.harsh.gymmanager.data.local.entity.PlanEntity
import com.harsh.gymmanager.domain.backup.BackupFile
import com.harsh.gymmanager.domain.backup.MemberBackup
import com.harsh.gymmanager.domain.backup.MembershipBackup
import com.harsh.gymmanager.domain.backup.MessageBackup
import com.harsh.gymmanager.domain.backup.PaymentBackup
import com.harsh.gymmanager.domain.backup.PlanBackup
import com.harsh.gymmanager.domain.repository.BackupRepository
import kotlin.time.Instant

class BackupRepositoryImpl(private val dao: BackupDao) : BackupRepository {

    override suspend fun snapshot(createdAt: Long): BackupRepository.Snapshot {
        val t = dao.everything()
        val backup = BackupFile(
            createdAt = createdAt,
            members = t.members.map {
                MemberBackup(
                    id = it.id, name = it.name, phone = it.phone, gender = it.gender, dateOfBirth = it.dateOfBirth,
                    notes = it.notes, joinedOn = it.joinedOn, isArchived = it.isArchived,
                    createdAt = it.createdAt.toEpochMilliseconds(), updatedAt = it.updatedAt.toEpochMilliseconds(),
                )
            },
            plans = t.plans.map {
                PlanBackup(it.id, it.name, it.durationValue, it.durationUnit, it.defaultPrice, it.isArchived, it.createdAt.toEpochMilliseconds())
            },
            memberships = t.memberships.map {
                MembershipBackup(it.id, it.memberId, it.planId, it.planName, it.startDate, it.endDate, it.price, it.discount, it.notes, it.createdAt.toEpochMilliseconds())
            },
            payments = t.payments.map {
                PaymentBackup(it.id, it.membershipId, it.amount, it.mode, it.paidOn, it.note, it.createdAt.toEpochMilliseconds())
            },
            // Messages to members kept online have no number here; a backup only holds the phone's own members.
            messages = t.messages.mapNotNull { m ->
                m.memberId.toLongOrNull()?.let { MessageBackup(m.id, it, m.kind, m.sentAt.toEpochMilliseconds()) }
            },
        )
        val photoPaths = t.members.mapNotNull { m -> m.photoPath?.let { m.id to it } }.toMap()
        return BackupRepository.Snapshot(backup, photoPaths)
    }

    override suspend fun replaceAll(backup: BackupFile, photoPaths: Map<Long, String>) {
        dao.replaceAll(
            BackupDao.Tables(
                members = backup.members.map {
                    MemberEntity(
                        id = it.id, name = it.name, phone = it.phone, gender = it.gender, dateOfBirth = it.dateOfBirth,
                        photoPath = photoPaths[it.id], notes = it.notes, joinedOn = it.joinedOn, isArchived = it.isArchived,
                        createdAt = Instant.fromEpochMilliseconds(it.createdAt), updatedAt = Instant.fromEpochMilliseconds(it.updatedAt),
                    )
                },
                plans = backup.plans.map {
                    PlanEntity(it.id, it.name, it.durationValue, it.durationUnit, it.defaultPrice, it.isArchived, Instant.fromEpochMilliseconds(it.createdAt))
                },
                memberships = backup.memberships.map {
                    MembershipEntity(it.id, it.memberId, it.planId, it.planName, it.startDate, it.endDate, it.price, it.discount, it.notes, Instant.fromEpochMilliseconds(it.createdAt))
                },
                payments = backup.payments.map {
                    PaymentEntity(it.id, it.membershipId, it.amount, it.mode, it.paidOn, it.note, Instant.fromEpochMilliseconds(it.createdAt))
                },
                messages = backup.messages.map { MessageLogEntity(it.id, it.memberId.toString(), it.kind, Instant.fromEpochMilliseconds(it.sentAt)) },
            )
        )
    }
}
