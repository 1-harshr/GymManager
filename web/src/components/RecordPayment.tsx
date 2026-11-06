import { useState } from 'react'
import { toast } from 'sonner'
import { amountSuggestions, due, MODE_LABEL } from '@/lib/domain'
import { formatRupees, parseRupees } from '@/lib/format'
import type { Member, MembershipRecord, PaymentMode } from '@/lib/types'
import { receipt } from '@/lib/whatsapp'
import { recordPayment, useToday, useUsageStats, useWrite, type NewPayment } from '@/data/queries'
import { useGym } from '@/data/session'
import { explain } from '@/data/errors'
import { Chips, DateInput, MoneyInput, SegmentedTrack } from './bits'
import { Button, Field, Sheet, TextInput } from './ui'
import { useOfferWhatsApp } from './WhatsApp'

const MODES = (['CASH', 'UPI', 'OTHER'] as PaymentMode[]).map((m) => ({ value: m, label: MODE_LABEL[m] }))

/** Money in against one membership: the amount (full due to start with), how, when, and a note. */
export function RecordPaymentSheet({ member, membership, onClose }: { member: Member; membership: MembershipRecord | null; onClose: () => void }) {
  return (
    <Sheet open={!!membership} onClose={onClose} title="Record payment">
      {membership ? <Form key={membership.id} member={member} membership={membership} onClose={onClose} /> : null}
    </Sheet>
  )
}

function Form({ member, membership, onClose }: { member: Member; membership: MembershipRecord; onClose: () => void }) {
  const { membership: gym } = useGym()
  const today = useToday()
  const usage = useUsageStats()
  const offer = useOfferWhatsApp()
  const owed = due(membership)
  const [amount, setAmount] = useState(owed > 0 ? String(owed) : '')
  const [mode, setMode] = useState<PaymentMode>('CASH')
  const [paidOn, setPaidOn] = useState(today)
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const save = useWrite((p: NewPayment) => recordPayment(gym.gym.id, p))
  const value = parseRupees(amount)

  function submit() {
    if (!value) return setError('Enter an amount in rupees')
    setError(null)
    save.mutate(
      { membershipId: membership.id, amount: value, mode, paidOn: paidOn || today, note: note.trim() || null },
      {
        onSuccess: (payment) => {
          toast(`Payment of ${formatRupees(value)} recorded`)
          onClose()
          offer([receipt(member, gym.gym.name, { ...membership, payments: [...membership.payments, payment] }, payment)])
        },
        onError: (e) => setError(explain(e)),
      },
    )
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        submit()
      }}
      className="space-y-5 pb-2"
    >
      <p className="-mt-1 text-caption text-muted">
        {formatRupees(owed)} due on {membership.plan_name}
      </p>
      <Field label="Amount" error={error}>
        <MoneyInput value={amount} onChange={(v) => (setAmount(v), setError(null))} invalid={!!error} autoFocus />
        <Chips
          chips={amountSuggestions(owed, usage)
            .filter((c) => c.amount !== value)
            .map((c) => ({ key: c.label, label: c.label, onClick: () => setAmount(c.amount === null ? '' : String(c.amount)) }))}
        />
      </Field>
      <SegmentedTrack options={MODES} value={mode} onChange={(m) => m && setMode(m)} />
      <Field label="Paid on">
        <DateInput value={paidOn} onChange={setPaidOn} max={today} />
      </Field>
      <Field label="Note" hint="Optional">
        <TextInput value={note} onChange={(e) => setNote(e.target.value)} placeholder="e.g. paid by brother" />
      </Field>
      <Button type="submit" className="w-full" busy={save.isPending}>
        {save.isPending ? 'Saving…' : value ? `Record ${formatRupees(value)}` : 'Record payment'}
      </Button>
    </form>
  )
}
