import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { Download, FileSpreadsheet, FileUp } from 'lucide-react'
import { toast } from 'sonner'
import { supabase } from '@/lib/supabase'
import { CsvError, parseMembersCsv, TEMPLATE, type Issue, type ParseResult } from '@/lib/csv'
import { formatPhone, plural } from '@/lib/format'
import { useGym } from '@/data/session'
import { rows, useToday, useWrite } from '@/data/queries'
import { explain } from '@/data/errors'
import { PageHeader } from '@/components/Layout'
import { Badge } from '@/components/bits'
import { Button, Card } from '@/components/ui'

const PREVIEW = 20

function issueText(i: Issue): string {
  switch (i.kind) {
    case 'NO_NAME':
      return 'Skipped: no name'
    case 'DUPLICATE_PHONE':
      return `Skipped: ${i.name}’s phone ${formatPhone(i.value)} is already used by another member`
    case 'BAD_PHONE':
      return `${i.name}: phone “${i.value}” isn’t a 10-digit number, so it was left empty`
    case 'BAD_GENDER':
      return `${i.name}: gender “${i.value}” wasn’t recognised, so it was left empty`
    case 'BAD_BIRTH_DATE':
      return `${i.name}: date of birth “${i.value}” wasn’t recognised, so it was left empty`
    case 'BAD_JOIN_DATE':
      return `${i.name}: join date “${i.value}” wasn’t recognised, so today was used`
  }
}

/** Many members at once from a CSV: a template to fill in, a preview of what will be added, then one save. */
export default function ImportMembers() {
  const navigate = useNavigate()
  const today = useToday()
  const { membership } = useGym()
  const [file, setFile] = useState<string | null>(null)
  const [result, setResult] = useState<ParseResult | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reading, setReading] = useState(false)
  const [dragging, setDragging] = useState(false)
  const input = useRef<HTMLInputElement>(null)

  async function read(f: File) {
    setReading(true)
    setError(null)
    setFile(f.name)
    try {
      const phones = await rows<{ phone: string | null }[]>(supabase.from('members').select('phone').limit(20000))
      setResult(parseMembersCsv(await f.text(), today, phones.map((p) => p.phone)))
    } catch (e) {
      setResult(null)
      setError(e instanceof CsvError ? e.message : `Couldn’t read ${f.name}. Save it as CSV and try again.`)
    } finally {
      setReading(false)
    }
  }

  const add = useWrite(async (list: ParseResult['members']) => {
    const { data, error } = await supabase
      .from('members')
      .insert(list.map((m) => ({ ...m, gym_id: membership.gym.id, is_archived: false })))
      .select('id')
    if (error) throw error
    return data.length
  })

  const ready = result?.members.length ?? 0
  const skipped = result?.issues.filter((i) => i.skipped).length ?? 0
  const warnings = (result?.issues.length ?? 0) - skipped

  return (
    <div className="max-w-4xl">
      <PageHeader
        back
        title="Import members"
        subtitle={file ?? 'From a CSV file'}
        actions={
          ready > 0 ? (
            <Button
              busy={add.isPending}
              onClick={() =>
                add.mutate(result!.members, {
                  onSuccess: (n) => {
                    toast(`Added ${plural(n, 'member', 'members')}`)
                    navigate('/members')
                  },
                  onError: (e) => setError(explain(e)),
                })
              }
            >
              {add.isPending ? 'Adding…' : `Add ${plural(ready, 'member', 'members')}`}
            </Button>
          ) : null
        }
      />
      <div className="grid gap-6 md:grid-cols-[1fr_320px]">
        <div className="space-y-4">
          <button
            onClick={() => input.current?.click()}
            onDragOver={(e) => (e.preventDefault(), setDragging(true))}
            onDragLeave={() => setDragging(false)}
            onDrop={(e) => {
              e.preventDefault()
              setDragging(false)
              const f = e.dataTransfer.files[0]
              if (f) read(f)
            }}
            className={`flex w-full flex-col items-center rounded-card border-2 border-dashed px-6 py-10 text-center transition ${dragging ? 'border-accent bg-accent-soft' : 'border-line bg-surface hover:border-muted'}`}
          >
            <FileUp className="size-8 text-accent" />
            <span className="mt-3 text-title">{reading ? 'Reading…' : file ? 'Choose a different file' : 'Choose CSV file'}</span>
            <span className="mt-1 text-caption text-muted">or drop it here</span>
          </button>
          <input
            ref={input}
            type="file"
            accept=".csv,text/csv"
            hidden
            onChange={(e) => {
              const f = e.target.files?.[0]
              if (f) read(f)
              e.target.value = ''
            }}
          />
          {error ? <p className="text-caption text-due">{error}</p> : null}

          {result ? (
            <Card className="p-6 animate-rise">
              <p className="text-title">{ready > 0 ? `${plural(ready, 'member', 'members')} ready to add` : 'No members to add'}</p>
              <div className="mt-3 flex flex-wrap gap-2">
                <Badge tone="expired" dot={false}>
                  {plural(result.rows, 'row', 'rows')}
                </Badge>
                {skipped ? <Badge tone="due">{skipped} skipped</Badge> : null}
                {warnings ? <Badge tone="expiring">{plural(warnings, 'warning', 'warnings')}</Badge> : null}
              </div>
              {result.issues.length > 0 ? (
                <>
                  <h3 className="mt-6 text-label text-muted">Check these rows</h3>
                  <ul className="mt-2 space-y-1.5">
                    {result.issues.map((i, n) => (
                      <li key={n} className="flex gap-3 text-body">
                        <span className="w-16 shrink-0 text-muted tnum">Row {i.line}</span>
                        <span className={i.skipped ? 'text-due' : ''}>{issueText(i)}</span>
                      </li>
                    ))}
                  </ul>
                </>
              ) : null}
              {ready > 0 ? (
                <>
                  <h3 className="mt-6 text-label text-muted">Members to add</h3>
                  <ul className="mt-2 divide-y divide-line/50">
                    {result.members.slice(0, PREVIEW).map((m, n) => (
                      <li key={n} className="flex justify-between gap-3 py-2 text-body">
                        <span className="font-semibold">{m.name}</span>
                        <span className="text-muted tnum">{m.phone ? formatPhone(m.phone) : 'No phone'}</span>
                      </li>
                    ))}
                  </ul>
                  {ready > PREVIEW ? <p className="mt-2 text-caption text-muted">and {ready - PREVIEW} more</p> : null}
                </>
              ) : null}
            </Card>
          ) : null}
        </div>

        <Card className="h-fit p-6">
          <FileSpreadsheet className="size-6 text-accent" />
          <h2 className="mt-3 text-title">Bring in members from a spreadsheet</h2>
          <p className="mt-2 text-body text-muted">
            Download the template, fill in one member per row in Excel or Google Sheets, then save or download it as CSV and choose it here. Only Name is required.
          </p>
          <pre className="mt-4 overflow-x-auto rounded-[12px] bg-sunken p-3 text-[12px] leading-5">
            {'Name,Phone,Gender,Date of birth,Joined on,Notes\nRavi Kumar,98765 43210,M,15/08/1998,05/11/2026,Knee injury'}
          </pre>
          <p className="mt-3 text-caption text-muted">Dates are read day first, so 05/11/2026 is 5 November. Members whose phone number is already saved are skipped.</p>
          <Button
            kind="secondary"
            size="sm"
            icon={Download}
            className="mt-4"
            onClick={() => {
              const url = URL.createObjectURL(new Blob([TEMPLATE], { type: 'text/csv' }))
              const a = Object.assign(document.createElement('a'), { href: url, download: 'members-template.csv' })
              a.click()
              URL.revokeObjectURL(url)
              toast('Template saved. Fill it in, save it as CSV, then choose it here.')
            }}
          >
            Download template
          </Button>
        </Card>
      </div>
    </div>
  )
}
