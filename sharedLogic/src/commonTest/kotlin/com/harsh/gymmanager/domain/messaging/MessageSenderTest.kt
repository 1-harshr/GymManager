package com.harsh.gymmanager.domain.messaging

import com.harsh.gymmanager.domain.MembershipStatus
import com.harsh.whatsapp.cloud.Template
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import com.harsh.gymmanager.templates.gymTemplateDefinition
import com.harsh.gymmanager.templates.gymTemplates
import com.harsh.gymmanager.templates.render
import com.harsh.gymmanager.templates.variableCount

class MessageSenderTest {
    private val welcome = MemberMessage.Welcome("1", "Ravi Kumar", "919876543210", "My Gym")

    @Test
    fun linkSenderOpensTheWaMeLink() = runTest {
        val opened = mutableListOf<String>()

        val outcome = LinkSender { opened += it }.send(welcome, "Hi Ravi")

        assertEquals(SendOutcome.Opened, outcome)
        assertEquals(listOf("https://wa.me/919876543210?text=Hi%20Ravi"), opened)
    }

    @Test
    fun linkSenderReportsNoWhatsAppApp() = runTest {
        val outcome = LinkSender { throw IllegalArgumentException() }.send(welcome, "Hi")

        assertEquals(SendOutcome.Failed(SendFailure.NO_WHATSAPP_APP), outcome)
    }

    @Test
    fun receiptTemplateFillsVariablesInOrder() {
        val receipt = MemberMessage.Receipt(
            memberId = "1",
            memberName = "Ravi Kumar",
            whatsAppNumber = "919876543210",
            gymName = "My Gym",
            receiptNumber = "12",
            planName = "Monthly",
            startDate = LocalDate(2026, 11, 6),
            endDate = LocalDate(2026, 12, 5),
            price = 1500,
            discount = 0,
            amountPaid = 1000,
            amountDue = 500,
            payment = null,
        )

        assertEquals(
            Template("gym_receipt", "en", listOf("My Gym", "12", "Ravi", "Monthly", "6 Nov 2026", "5 Dec 2026", "₹1,000", "₹500")),
            gymTemplate(receipt),
        )
    }

    @Test
    fun reminderPicksTemplateByStatus() {
        fun reminder(status: MembershipStatus, end: LocalDate?) =
            gymTemplate(MemberMessage.Reminder("1", "Ravi", "91", "My Gym", status, end, amountDue = 300)).name

        assertEquals("gym_membership_ending", reminder(MembershipStatus.EXPIRING_SOON, LocalDate(2026, 11, 8)))
        assertEquals("gym_membership_ended", reminder(MembershipStatus.EXPIRED, LocalDate(2026, 11, 1)))
        assertEquals("gym_payment_due", reminder(MembershipStatus.ACTIVE, LocalDate(2027, 1, 1)))
        assertEquals("gym_payment_due", reminder(MembershipStatus.EXPIRED, null))
    }

    @Test
    fun everyMessageFitsAnApprovedTemplateDefinition() {
        val messages = listOf(
            welcome,
            MemberMessage.Receipt("1", "Ravi", "91", "My Gym", "12", "Monthly", LocalDate(2026, 11, 6), LocalDate(2026, 12, 5), 1500, 0, 1000, 500, null),
            MemberMessage.Reminder("1", "Ravi", "91", "My Gym", MembershipStatus.EXPIRING_SOON, LocalDate(2026, 11, 8), 0),
            MemberMessage.Reminder("1", "Ravi", "91", "My Gym", MembershipStatus.EXPIRED, LocalDate(2026, 11, 1), 0),
            MemberMessage.Reminder("1", "Ravi", "91", "My Gym", MembershipStatus.ACTIVE, null, 300),
            MemberMessage.MissYou("1", "Ravi", "91", "My Gym", LocalDate(2026, 10, 20)),
        )
        val used = messages.map { message ->
            val template = gymTemplate(message)
            val definition = assertNotNull(gymTemplateDefinition(template.name, template.languageCode), "${template.name} isn't defined")
            assertEquals(definition.variableCount, template.bodyParameters.size, "${template.name} needs ${definition.variableCount} values")
            template.name
        }
        assertEquals(gymTemplates.map { it.name }.toSet(), used.toSet(), "every defined template is used")
    }

    @Test
    fun previewFillsTheTemplate() {
        val template = gymTemplate(welcome)
        assertEquals(
            "Hi Ravi, welcome to My Gym! Your membership is set up. Message us here if you have any questions.",
            gymTemplateDefinition(template.name, template.languageCode)!!.render(template.bodyParameters),
        )
    }
}
