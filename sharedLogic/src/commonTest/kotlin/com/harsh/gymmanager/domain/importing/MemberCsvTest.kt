package com.harsh.gymmanager.domain.importing

import com.harsh.gymmanager.domain.model.Gender
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemberCsvTest {
    private val today = LocalDate(2026, 11, 5)

    @Test
    fun readsAllColumnsWithAliasesAndIndianDates() {
        val csv = "﻿Full Name,Mobile No,Sex,DOB,Date of Joining,Remarks\r\n" +
            "Ravi Kumar,+91 98765 43210,M,15/08/1998,05-11-2026,\"Knee injury, go easy\"\r\n" +
            "Asha Singh,09123456780,female,1999-01-02,3 Nov 2026,\r\n"
        val preview = parseMembersCsv(csv, today, existingPhones = emptyList())

        assertEquals(2, preview.rowCount)
        assertTrue(preview.issues.isEmpty(), preview.issues.toString())
        val (ravi, asha) = preview.members
        assertEquals("Ravi Kumar", ravi.name)
        assertEquals("9876543210", ravi.phone)
        assertEquals(Gender.MALE, ravi.gender)
        assertEquals(LocalDate(1998, 8, 15), ravi.dateOfBirth)
        assertEquals(today, ravi.joinedOn)
        assertEquals("Knee injury, go easy", ravi.notes)
        assertEquals("9123456780", asha.phone)
        assertEquals(LocalDate(2026, 11, 3), asha.joinedOn)
    }

    @Test
    fun skipsMissingNamesAndDuplicatePhones() {
        val csv = "name,phone\n,9000000001\nRavi,9876543210\nRavi again,98765-43210\nAsha,9123456780\n\n"
        val preview = parseMembersCsv(csv, today, existingPhones = listOf("91234 56780"))

        assertEquals(listOf("Ravi"), preview.members.map { it.name })
        assertEquals(3, preview.skippedCount)
        assertEquals(listOf(2, 4, 5), preview.issues.map { it.line })
        assertEquals(4, preview.rowCount)
    }

    @Test
    fun keepsRowsWithUnreadableFieldsAsWarnings() {
        val csv = "Name;Phone;Gender;Joined\nRavi;12345;xyz;31/02/2026\n"
        val preview = parseMembersCsv(csv, today, emptyList())

        val ravi = preview.members.single()
        assertNull(ravi.phone)
        assertNull(ravi.gender)
        assertEquals(today, ravi.joinedOn)
        assertEquals(3, preview.warningCount)
        assertEquals(0, preview.skippedCount)
    }

    @Test
    fun twoDigitYearsStayInThePast() {
        assertEquals(LocalDate(1998, 8, 15), parseDate("15/8/98", today))
        assertEquals(LocalDate(2026, 1, 5), parseDate("5.1.26", today))
        assertEquals(LocalDate(2026, 11, 5), parseDate("5-Nov-2026", today))
    }

    @Test
    fun phoneNumbersSavedAsNumbersBySpreadsheets() {
        assertEquals("9876543210", normalizeIndianPhone("9876543210.0"))
        assertEquals("9876543210", normalizeIndianPhone("919876543210"))
        assertNull(normalizeIndianPhone("98765"))
    }

    @Test
    fun rejectsFilesWithoutANameColumn() {
        assertFailsWith<ImportFileException> { parseMembersCsv("phone\n9876543210\n", today, emptyList()) }
        assertFailsWith<ImportFileException> { parseMembersCsv("", today, emptyList()) }
    }
}
