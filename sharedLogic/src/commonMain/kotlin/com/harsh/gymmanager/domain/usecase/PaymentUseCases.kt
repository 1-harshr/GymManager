package com.harsh.gymmanager.domain.usecase

import com.harsh.gymmanager.domain.model.Payment
import com.harsh.gymmanager.domain.model.PaymentMode
import com.harsh.gymmanager.domain.repository.PaymentRepository
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

class PaymentUseCases(repository: PaymentRepository, clock: Clock, timeZone: TimeZone) {
    val recordPayment = RecordPaymentUseCase(repository, clock, timeZone)
    val deletePayment = DeletePaymentUseCase(repository)
}
