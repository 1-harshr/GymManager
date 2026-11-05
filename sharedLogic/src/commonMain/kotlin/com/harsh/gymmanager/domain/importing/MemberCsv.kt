package com.harsh.gymmanager.domain.importing

import com.harsh.gymmanager.domain.model.Gender
import com.harsh.gymmanager.domain.model.Member
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month

enum class ImportIssueKind(val skipsRow: Boolean) {
    NO_NAME(true),
    DUPLICATE_PHONE(true),
    BAD_PHONE(false),
    BAD_GENDER(false),
    BAD_BIRTH_DATE(false),
    BAD_JOIN_DATE(false),
}

/** A problem with one row of an import file: which member, and the value that couldn't be used. */
data class ImportIssue(val line: Int, val kind: ImportIssueKind, val name: String? = null, val value: String? = null) {
    /** Skipped rows are not added at all; the others are added with that field left empty. */
    val skipped: Boolean get() = kind.skipsRow
}

data class ImportPreview(
    /** Members ready to add, in file order. */
    val members: List<Member>,
    val issues: List<ImportIssue>,
    /** Data rows in the file, header excluded. */
    val rowCount: Int,
) {
    val skippedCount: Int get() = issues.count { it.skipped }
    val warningCount: Int get() = issues.count { !it.skipped }
}

enum class ImportFileProblem { EMPTY, NO_NAME_COLUMN }

class ImportFileException(val problem: ImportFileProblem) : IllegalArgumentException(problem.name)

private enum class Column { NAME, PHONE, GENDER, DATE_OF_BIRTH, JOINED_ON, NOTES }

private val ColumnAliases = mapOf(
    Column.NAME to setOf("name", "full name", "member", "member name", "naam"),
    Column.PHONE to setOf("phone", "mobile", "phone number", "mobile number", "mobile no", "phone no", "contact", "contact number", "number"),
    Column.GENDER to setOf("gender", "sex"),
    Column.DATE_OF_BIRTH to setOf("date of birth", "dob", "birthday", "birth date", "birthdate"),
    Column.JOINED_ON to setOf("joined", "joined on", "join date", "joining date", "date of joining", "doj", "admission date"),
    Column.NOTES to setOf("notes", "note", "remarks", "remark", "comments"),
)

/**
 * Reads members from CSV text, as saved by Excel or Google Sheets.
 *
 * The first row must name the columns; only Name is required. Rows without a name, and
 * rows whose phone is already used by a member or an earlier row, are skipped. A phone,
 * gender or date that can't be read is left empty with a warning instead of losing the row.
 * Dates are read day first (5/11/2026 is 5 November), as written in India.
 */
fun parseMembersCsv(text: String, today: LocalDate, existingPhones: Collection<String>): ImportPreview {
    val rows = readCsv(text.removePrefix("﻿"))
    val header = rows.firstOrNull() ?: throw ImportFileException(ImportFileProblem.EMPTY)
    val columns = header.mapIndexedNotNull { index, title ->
        val key = title.trim().lowercase().replace(Regex("[_.]"), " ").replace(Regex("\\s+"), " ")
        ColumnAliases.entries.firstOrNull { key in it.value }?.let { it.key to index }
    }.toMap()
    if (Column.NAME !in columns) {
        throw ImportFileException(ImportFileProblem.NO_NAME_COLUMN)
    }

    val seenPhones = existingPhones.mapNotNull(::normalizeIndianPhone).toMutableSet()
    val members = mutableListOf<Member>()
    val issues = mutableListOf<ImportIssue>()
    val dataRows = rows.drop(1).withIndex().filterNot { (_, cells) -> cells.all { it.isBlank() } }

    for ((index, cells) in dataRows) {
        val line = index + 2
        fun cell(column: Column) = columns[column]?.let { cells.getOrNull(it)?.trim() }.orEmpty()

        val name = cell(Column.NAME).replace(Regex("\\s+"), " ")
        if (name.isEmpty()) {
            issues += ImportIssue(line, ImportIssueKind.NO_NAME)
            continue
        }

        val rawPhone = cell(Column.PHONE)
        var phone: String? = null
        if (rawPhone.isNotEmpty()) {
            val normalized = normalizeIndianPhone(rawPhone)
            when {
                normalized == null -> issues += ImportIssue(line, ImportIssueKind.BAD_PHONE, name, rawPhone)
                normalized in seenPhones -> {
                    issues += ImportIssue(line, ImportIssueKind.DUPLICATE_PHONE, name, normalized)
                    continue
                }
                else -> phone = normalized
            }
        }

        val rawGender = cell(Column.GENDER)
        val gender = parseGender(rawGender)
        if (rawGender.isNotEmpty() && gender == null) {
            issues += ImportIssue(line, ImportIssueKind.BAD_GENDER, name, rawGender)
        }

        val rawBirth = cell(Column.DATE_OF_BIRTH)
        val dateOfBirth = parseDate(rawBirth, today)
        if (rawBirth.isNotEmpty() && dateOfBirth == null) {
            issues += ImportIssue(line, ImportIssueKind.BAD_BIRTH_DATE, name, rawBirth)
        }

        val rawJoined = cell(Column.JOINED_ON)
        val joined = parseDate(rawJoined, today)
        if (rawJoined.isNotEmpty() && joined == null) {
            issues += ImportIssue(line, ImportIssueKind.BAD_JOIN_DATE, name, rawJoined)
        }

        phone?.let { seenPhones += it }
        members += Member(
            name = name,
            phone = phone,
            gender = gender,
            dateOfBirth = dateOfBirth,
            notes = cell(Column.NOTES).ifEmpty { null },
            joinedOn = joined ?: today,
        )
    }
    return ImportPreview(members, issues, rowCount = dataRows.size)
}

/** A 10-digit Indian mobile number, dropping spaces, dashes and a +91 or 0 prefix; null if it isn't one. */
fun normalizeIndianPhone(raw: String): String? {
    // Spreadsheets often turn phone numbers into floats like 9876543210.0
    val digits = raw.trim().removeSuffix(".0").filter(Char::isDigit)
    val local = when {
        digits.length == 12 && digits.startsWith("91") -> digits.drop(2)
        digits.length == 11 && digits.startsWith("0") -> digits.drop(1)
        else -> digits
    }
    return local.takeIf { it.length == 10 }
}

private fun parseGender(raw: String): Gender? = when (raw.trim().lowercase()) {
    "m", "male", "man", "boy", "purush" -> Gender.MALE
    "f", "female", "woman", "girl", "mahila" -> Gender.FEMALE
    "o", "other", "others" -> Gender.OTHER
    else -> null
}

private val MonthNames = Month.entries.associateBy { it.name.take(3).lowercase() }

/** Reads 2026-11-05, 5/11/2026, 05-11-26, 5.11.2026 and 5 Nov 2026 (or 5-Nov-2026). */
internal fun parseDate(raw: String, today: LocalDate): LocalDate? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    Regex("""(\d{4})-(\d{1,2})-(\d{1,2})""").matchEntire(text)?.let { m ->
        val (y, mo, d) = m.destructured
        return dateOrNull(y.toInt(), mo.toInt(), d.toInt())
    }
    Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2}|\d{4})""").matchEntire(text)?.let { m ->
        val (d, mo, y) = m.destructured
        return dateOrNull(fullYear(y, today), mo.toInt(), d.toInt())
    }
    Regex("""(\d{1,2})[\s\-]+([A-Za-z]{3,9})[\s\-,]+(\d{2}|\d{4})""").matchEntire(text)?.let { m ->
        val (d, monthName, y) = m.destructured
        val month = MonthNames[monthName.take(3).lowercase()] ?: return null
        return dateOrNull(fullYear(y, today), month.ordinal + 1, d.toInt())
    }
    return null
}

/** Two-digit years mean this century unless that would be in the future. */
private fun fullYear(y: String, today: LocalDate): Int {
    if (y.length == 4) return y.toInt()
    val century = today.year / 100 * 100
    val candidate = century + y.toInt()
    return if (candidate > today.year) candidate - 100 else candidate
}

private fun dateOrNull(year: Int, month: Int, day: Int): LocalDate? =
    runCatching { LocalDate(year, month, day) }.getOrNull()

/** Splits CSV into rows of cells: quoted fields, doubled quotes, CRLF, and `;` files from some Excel locales. */
internal fun readCsv(text: String): List<List<String>> {
    val firstLine = text.lineSequence().firstOrNull().orEmpty()
    val delimiter = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
            c == '"' && (quoted || cell.isEmpty()) -> quoted = !quoted
            !quoted && c == delimiter -> { row += cell.toString(); cell.clear() }
            !quoted && (c == '\n' || c == '\r') -> {
                if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                row += cell.toString(); cell.clear()
                rows += row; row = mutableListOf()
            }
            else -> cell.append(c)
        }
        i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
    return rows
}
