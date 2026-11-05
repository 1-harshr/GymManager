package com.harsh.gymmanager.domain.usecase

import com.harsh.gymmanager.domain.model.Payment
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.model.PaymentRecord
import com.harsh.gymmanager.domain.repository.PaymentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** Fails with [com.harsh.gymmanager.domain.OverpaymentException] if [amount] exceeds what is still owed. */
class RecordPaymentUseCase(
    private val repository: PaymentRepository,
    private val clock: Clock,
    private val timeZone: TimeZone,
) {
    /** [paidOn] defaults to today. */
    suspend operator fun invoke(
        membershipId: Long,
        amount: Long,
        mode: PaymentMode = PaymentMode.CASH,
        paidOn: LocalDate? = null,
        note: String? = null,
    ): Result<Long> = resultOf {
        repository.addPayment(
            Payment(
                membershipId = membershipId,
                amount = amount,
                mode = mode,
                paidOn = paidOn ?: clock.todayIn(timeZone),
                note = note,
            )
        )
    }
}

class DeletePaymentUseCase(private val repository: PaymentRepository) {
    suspend operator fun invoke(id: Long): Result<Unit> = resultOf { repository.deletePayment(id) }
}

/** Payments as lists show them, newest first. */
class ObservePaymentsUseCase(private val repository: PaymentRepository) {
    /** The latest [limit] payments, whenever they were made. */
    fun recent(limit: Int): Flow<List<PaymentRecord>> = repository.observePayments(EARLIEST, LATEST, limit)

    private companion object {
        val EARLIEST = LocalDate(1900, 1, 1)
        val LATEST = LocalDate(9999, 12, 31)
    }
}

class PaymentUseCases(repository: PaymentRepository, clock: Clock, timeZone: TimeZone) {
    val recordPayment = RecordPaymentUseCase(repository, clock, timeZone)
    val deletePayment = DeletePaymentUseCase(repository)
    val observePayments = ObservePaymentsUseCase(repository)
}
