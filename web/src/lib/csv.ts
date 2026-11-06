/**
 * Members from a spreadsheet saved as CSV, read the way the phone app reads it: flexible column names,
 * day-first dates, Indian mobile numbers, and rows whose phone is already on file skipped.
 */
import { normalizeIndianPhone } from './domain'
import type { Gender } from './types'
import type { IsoDate } from './dates'

export const TEMPLATE = '﻿Name,Phone,Gender,Date of birth,Joined on,Notes\r\n'

export type IssueKind = 'NO_NAME' | 'DUPLICATE_PHONE' | 'BAD_PHONE' | 'BAD_GENDER' | 'BAD_BIRTH_DATE' | 'BAD_JOIN_DATE'

export interface Issue {
  line: number
  kind: IssueKind
  name: string
  value: string
  /** Skipped rows aren't added; the rest are, with the bad value left out. */
  skipped: boolean
}

export interface NewMember {
  name: string
  phone: string | null
  gender: Gender | null
  date_of_birth: IsoDate | null
  joined_on: IsoDate
  notes: string | null
}

export interface ParseResult {
  rows: number
  members: NewMember[]
  issues: Issue[]
}

export class CsvError extends Error {}

const COLUMNS: Record<keyof NewMember, string[]> = {
  name: ['name', 'full name', 'member', 'member name', 'naam'],
  phone: ['phone', 'mobile', 'phone number', 'mobile number', 'mobile no', 'phone no', 'contact', 'contact number', 'number'],
  gender: ['gender', 'sex'],
  date_of_birth: ['date of birth', 'dob', 'birthday', 'birth date', 'birthdate'],
  joined_on: ['joined', 'joined on', 'join date', 'joining date', 'date of joining', 'doj', 'admission date'],
  notes: ['notes', 'note', 'remarks', 'remark', 'comments'],
}

/** Quoted fields, doubled quotes, and any line ending. */
export function parseCsv(text: string): string[][] {
  const body = text.replace(/^﻿/, '')
  const firstLine = body.split(/\r\n|\n|\r/, 1)[0] ?? ''
  const sep = (firstLine.match(/;/g)?.length ?? 0) > (firstLine.match(/,/g)?.length ?? 0) ? ';' : ','
  const rows: string[][] = []
  let row: string[] = []
  let cell = ''
  let quoted = false
  for (let i = 0; i < body.length; i++) {
    const c = body[i]
    if (quoted) {
      if (c === '"' && body[i + 1] === '"') (cell += '"', i++)
      else if (c === '"') quoted = false
      else cell += c
    } else if (c === '"') quoted = true
    else if (c === sep) (row.push(cell), (cell = ''))
    else if (c === '\r' || c === '\n') {
      if (c === '\r' && body[i + 1] === '\n') i++
      row.push(cell)
      rows.push(row)
      row = []
      cell = ''
    } else cell += c
  }
  if (cell !== '' || row.length) (row.push(cell), rows.push(row))
  return rows
}

const MONTHS = ['jan', 'feb', 'mar', 'apr', 'may', 'jun', 'jul', 'aug', 'sep', 'oct', 'nov', 'dec']

/** "2026-11-05", "5/11/2026", "05-11-26", "5 Nov 2026", "5-Nov-2026"; day first. Null if not a real date. */
export function parseDate(raw: string, today: IsoDate): IsoDate | null {
  const s = raw.trim()
  let y: number, m: number, d: number
  let match = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})$/)
  if (match) [y, m, d] = [Number(match[1]), Number(match[2]), Number(match[3])]
  else if ((match = s.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{2}|\d{4})$/))) [d, m, y] = [Number(match[1]), Number(match[2]), Number(match[3])]
  else if ((match = s.match(/^(\d{1,2})[\s-]+([A-Za-z]{3,})[\s-]+(\d{2}|\d{4})$/))) {
    const month = MONTHS.indexOf(match[2].slice(0, 3).toLowerCase())
    if (month < 0) return null
    ;[d, m, y] = [Number(match[1]), month + 1, Number(match[3])]
  } else return null
  if (y < 100) {
    const century = Math.floor(Number(today.slice(0, 4)) / 100) * 100
    y = century + y > Number(today.slice(0, 4)) ? century - 100 + y : century + y
  }
  const date = new Date(Date.UTC(y, m - 1, d))
  if (date.getUTCFullYear() !== y || date.getUTCMonth() !== m - 1 || date.getUTCDate() !== d) return null
  return date.toISOString().slice(0, 10)
}

function parseGender(raw: string): Gender | null | undefined {
  const v = raw.trim().toLowerCase()
  if (!v) return null
  if (['m', 'male', 'man', 'boy', 'purush'].includes(v)) return 'MALE'
  if (['f', 'female', 'woman', 'girl', 'mahila'].includes(v)) return 'FEMALE'
  if (['o', 'other', 'others'].includes(v)) return 'OTHER'
  return undefined
}

export function parseMembersCsv(text: string, today: IsoDate, existingPhones: (string | null)[]): ParseResult {
  const table = parseCsv(text)
  if (table.length === 0 || table[0].every((c) => !c.trim())) throw new CsvError('The file is empty.')
  const header = table[0].map((h) => h.trim().toLowerCase().replace(/[_.]/g, ' ').replace(/\s+/g, ' '))
  const col = Object.fromEntries(
    (Object.keys(COLUMNS) as (keyof NewMember)[]).map((key) => [key, header.findIndex((h) => COLUMNS[key].includes(h))]),
  ) as Record<keyof NewMember, number>
  if (col.name < 0) throw new CsvError('Couldn’t find a Name column. The first row should be the column names, such as Name, Phone, Gender.')

  const seen = new Set(existingPhones.map(normalizeIndianPhone).filter(Boolean))
  const members: NewMember[] = []
  const issues: Issue[] = []
  let rows = 0
  table.slice(1).forEach((cells, i) => {
    if (cells.every((c) => !c.trim())) return
    rows++
    const line = i + 2
    const get = (k: keyof NewMember) => (col[k] >= 0 ? (cells[col[k]] ?? '').trim() : '')
    const name = get('name').replace(/\s+/g, ' ')
    if (!name) return void issues.push({ line, kind: 'NO_NAME', name: '', value: '', skipped: true })
    let phone: string | null = null
    const rawPhone = get('phone')
    if (rawPhone) {
      phone = normalizeIndianPhone(rawPhone)
      if (!phone) issues.push({ line, kind: 'BAD_PHONE', name, value: rawPhone, skipped: false })
      else if (seen.has(phone)) return void issues.push({ line, kind: 'DUPLICATE_PHONE', name, value: phone, skipped: true })
      else seen.add(phone)
    }
    const gender = parseGender(get('gender'))
    if (gender === undefined) issues.push({ line, kind: 'BAD_GENDER', name, value: get('gender'), skipped: false })
    const rawDob = get('date_of_birth')
    const dob = rawDob ? parseDate(rawDob, today) : null
    if (rawDob && !dob) issues.push({ line, kind: 'BAD_BIRTH_DATE', name, value: rawDob, skipped: false })
    const rawJoined = get('joined_on')
    const joined = rawJoined ? parseDate(rawJoined, today) : null
    if (rawJoined && !joined) issues.push({ line, kind: 'BAD_JOIN_DATE', name, value: rawJoined, skipped: false })
    members.push({ name, phone, gender: gender ?? null, date_of_birth: dob, joined_on: joined ?? today, notes: get('notes') || null })
  })
  return { rows, members, issues }
}
