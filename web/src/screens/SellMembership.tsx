import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { supabase } from '@/lib/supabase'
import { addDays, daysBetween, fullDate } from '@/lib/dates'
import { amountSuggestions, byUse, discountSuggestions, durationLabel, MODE_LABEL, planEnd, renewalStartDate } from '@/lib/domain'
import { formatRupees, parseRupees } from '@/lib/format'
import type { MembershipRecord, PaymentMode, Plan } from '@/lib/types'
import { receipt } from '@/lib/whatsapp'
import { useGym } from '@/data/session'
import { useMember, useMemberships, usePlans, useToday, useUsageStats, useWrite } from '@/data/queries'
import { explain } from '@/data/errors'
import { PageHeader } from '@/components/Layout'
import { Avatar } from '@/components/Media'
import { PlanPicker } from '@/components/PlanPicker'
import { PlanDialog } from '@/components/PlanDialog'
import { Chips, DateInput, MoneyInput, SegmentedTrack, useDiscardGuard } from '@/components/bits'
import { useOfferWhatsApp } from '@/components/WhatsApp'
import { Button, Card, Field, LinkButton, Skeleton, TextArea, TextInput, cx } from '@/components/ui'

const MODES = (['CASH', 'UPI', 'OTHER'] as PaymentMode[]).map((m) => ({ value: m, label: MODE_LABEL[m] }))

/** Sell a member a plan, or renew theirs: dates, price and discount (adjustable for this sale), and what's paid now. */
export function SellMembership() {
  const { id } = useParams()
  const member = useMember(id)
  const memberships = useMemberships(id)
  const plans = usePlans()
  if (!member.data || !memberships.data || !plans.data) {
    return (
      <>
        <PageHeader back title="Membership" />
        <Skeleton className="h-96" />
      </>
    )
  }
  return <SellForm member={member.data} memberships={memberships.data} plans={plans.data} />
}

function SellForm({ member, memberships, plans }: { member: NonNullable<ReturnType<typeof useMember>['data']>; memberships: MembershipRecord[]; plans: Plan[] }) {
  const navigate = useNavigate()
  const today = useToday()
  const usage = useUsageStats()
  const { membership: gym, isOwner } = useGym()
  const offer = useOfferWhatsApp()
  const active = useMemo(() => byUse(plans.filter((p) => !p.is_archived), usage), [plans, usage])
  const latestEnd = memberships.reduce<string | null>((max, m) => (!max || m.end_date > max ? m.end_date : max), null)

  const [plan, setPlan] = useState<Plan | null>(null)
  const [start, setStart] = useState('')
  const [end, setEnd] = useState('')
  const [days, setDays] = useState('')
  const [price, setPrice] = useState('')
  const [discount, setDiscount] = useState('')
  const [paidNow, setPaidNow] = useState('')
  const [paidEdited, setPaidEdited] = useState(false)
  const [mode, setMode] = useState<PaymentMode>('CASH')
  const [notes, setNotes] = useState('')
  const [touched, setTouched] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [newPlan, setNewPlan] = useState(false)
  const guard = useDiscardGuard(touched)

  const priceValue = parseRupees(price)
  const discountValue = discount.trim() ? parseRupees(discount) : 0
  const chargedValue = priceValue !== null && discountValue !== null ? priceValue - discountValue : null
  const paidValue = paidNow.trim() ? parseRupees(paidNow) : 0

  // The amount paid follows the charge until it's typed in.
  useEffect(() => {
    if (!paidEdited) setPaidNow(chargedValue && chargedValue > 0 ? String(chargedValue) : '')
  }, [chargedValue, paidEdited])

  function choose(p: Plan, byPerson = true) {
    const s = renewalStartDate(latestEnd, today)
    const e = planEnd(p, s)
    setPlan(p)
    setStart(s)
    setEnd(e)
    setDays(String(daysBetween(s, e) + 1))
    setPrice(String(p.default_price))
    setDiscount('')
    setError(null)
    if (byPerson) setTouched(true)
  }

  // The last plan they were on comes ready, if it's still sold.
  useEffect(() => {
    const last = memberships[0]?.plan_id
    const match = active.find((p) => p.id === last)
    if (match && !plan) choose(match, false)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active])

  const customised = !!plan && (end !== planEnd(plan, start) || priceValue !== plan.default_price)

  function changeStart(s: string) {
    if (!s) return setStart(s)
    let e = end
    if (plan && end === planEnd(plan, start)) e = planEnd(plan, s)
    else if (Number(days) >= 1) e = addDays(s, Number(days) - 1)
    setStart(s)
    setEnd(e)
    setDays(String(daysBetween(s, e) + 1))
    setTouched(true)
  }
  function changeEnd(e: string) {
    setEnd(e)
    if (e && start) setDays(String(daysBetween(start, e) + 1))
    setTouched(true)
  }
  function changeDays(d: string) {
    const clean = d.replace(/\D/g, '').slice(0, 4)
    setDays(clean)
    if (Number(clean) >= 1 && start) setEnd(addDays(start, Number(clean) - 1))
    setTouched(true)
  }

  const save = useWrite(async () => {
    const payment = paidValue && paidValue > 0 ? { amount: paidValue, mode, paid_on: today } : undefined
    const { data, error } = await supabase.rpc('add_memberships', {
      items: [
        {
          member_id: member.id,
          plan_id: plan!.id,
          plan_name: plan!.name,
          start_date: start,
          end_date: end,
          price: priceValue,
          discount: discountValue,
          notes: notes.trim() || null,
          ...(payment ? { payment } : {}),
        },
      ],
    })
    if (error) throw error
    const newId = (data as string[])[0]
    const { data: sold } = await supabase
      .from('memberships')
      .select('id, member_id, plan_id, plan_name, start_date, end_date, price, discount, notes, created_at, payments(id, membership_id, amount, mode, paid_on, note, created_at)')
      .eq('id', newId)
      .single<MembershipRecord>()
    return sold
  })

  function submit() {
    const problem = !plan
      ? 'Choose a plan'
      : !start || !end || !(Number(days) >= 1)
        ? 'Choose the dates'
        : end < start
          ? 'End date can’t be before the start date'
          : priceValue === null
            ? 'Enter a price in rupees'
            : discountValue === null
              ? 'Enter a discount in rupees'
              : chargedValue! < 0
                ? 'Discount can’t be more than the price'
                : paidValue === null
                  ? 'Enter the amount paid in rupees'
                  : paidValue > chargedValue!
                    ? `Amount paid can’t be more than ${formatRupees(chargedValue!)}`
                    : null
    if (problem) return setError(problem)
    setError(null)
    save.mutate(undefined, {
      onSuccess: (sold) => {
        guard.release()
        toast('Membership saved')
        navigate(-1)
        if (sold) offer([receipt(member, gym.gym.name, sold)])
      },
      onError: (e) => setError(explain(e)),
    })
  }

  const renewing = memberships.length > 0
  const dueAfter = chargedValue !== null && paidValue !== null ? chargedValue - paidValue : null
  return (
    <>
      <PageHeader
        back
        leading={<Avatar name={member.name} photoPath={member.photo_path} size={48} />}
        title={renewing ? 'Renew membership' : 'New membership'}
        subtitle={member.name}
      />
      <div className="grid items-start gap-6 lg:grid-cols-[1fr_340px]">
        <div className="space-y-4">
          <Card className="p-6">
            <div className="mb-4 flex items-center justify-between">
              <h2 className="text-title">Plan</h2>
              {isOwner && active.length > 0 ? <LinkButton onClick={() => setNewPlan(true)}>New plan</LinkButton> : null}
            </div>
            <PlanPicker plans={active} selected={plan?.id ?? null} onSelect={(p) => choose(p)} emptyBody="Add a plan first, then come back to sell it." />
          </Card>

          {plan ? (
            <>
              <Card className="p-6 animate-rise">
                <h2 className="mb-4 text-title">Dates and price</h2>
                <div className="grid gap-4 sm:grid-cols-[1fr_1fr_120px]">
                  <Field label="Starts">
                    <DateInput value={start} onChange={changeStart} />
                  </Field>
                  <Field label="Ends">
                    <DateInput value={end} onChange={changeEnd} min={start} />
                  </Field>
                  <Field label="Days">
                    <TextInput inputMode="numeric" value={days} onChange={(e) => changeDays(e.target.value)} />
                  </Field>
                </div>
                <div className="mt-4 grid gap-4 sm:grid-cols-2">
                  <Field label="Price">
                    <MoneyInput value={price} onChange={(v) => (setPrice(v), setTouched(true))} />
                  </Field>
                  <Field label="Discount" hint="Optional">
                    <MoneyInput value={discount} onChange={(v) => (setDiscount(v), setTouched(true))} placeholder="0" />
                    <Chips
                      chips={discountSuggestions(priceValue ?? 0, usage)
                        .filter((d) => d !== discountValue)
                        .map((d) => ({ key: String(d), label: `${formatRupees(d)} off`, onClick: () => (setDiscount(String(d)), setTouched(true)) }))}
                    />
                  </Field>
                </div>
                {customised ? (
                  <div className="mt-4 flex flex-wrap items-center gap-2 rounded-[14px] bg-expiring-soft px-4 py-3 text-caption text-expiring">
                    Changed for this sale. The membership is still called “{plan.name}”.
                    <LinkButton
                      className="ml-auto text-expiring"
                      onClick={() => {
                        const e = planEnd(plan, start)
                        setEnd(e)
                        setDays(String(daysBetween(start, e) + 1))
                        setPrice(String(plan.default_price))
                      }}
                    >
                      Reset to plan
                    </LinkButton>
                  </div>
                ) : null}
              </Card>

              <Card className="p-6 animate-rise">
                <h2 className="mb-4 text-title">Paid now</h2>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label="Amount paid" hint="Leave empty if paying later">
                    <MoneyInput value={paidNow} onChange={(v) => (setPaidNow(v), setPaidEdited(true), setTouched(true))} placeholder="Leave empty if paying later" />
                    <Chips
                      chips={amountSuggestions(chargedValue ?? 0, usage, true)
                        .filter((c) => (c.amount === null ? paidNow.trim() !== '' : c.amount !== paidValue))
                        .map((c) => ({ key: c.label, label: c.label, onClick: () => (setPaidNow(c.amount === null ? '' : String(c.amount)), setPaidEdited(true)) }))}
                    />
                  </Field>
                  <Field label="Paid by">
                    <SegmentedTrack options={MODES} value={mode} onChange={(m) => m && setMode(m)} />
                  </Field>
                </div>
                <div className="mt-4">
                  <Field label="Notes" hint="Optional">
                    <TextArea value={notes} onChange={(e) => (setNotes(e.target.value), setTouched(true))} className="min-h-20" />
                  </Field>
                </div>
              </Card>
            </>
          ) : null}
        </div>

        <Card className="p-6 lg:sticky lg:top-24">
          <h2 className="text-title">Summary</h2>
          {plan ? (
            <>
              <p className="mt-3 text-body-strong">{plan.name}</p>
              <p className="text-caption text-muted">
                {start && end ? `${fullDate(start)} – ${fullDate(end)}` : 'Choose the dates'} · {durationLabel(Number(days) || 0, 'DAYS')}
              </p>
              <dl className="mt-5 space-y-2 text-body">
                <Sum label="Price" value={priceValue !== null ? formatRupees(priceValue) : '–'} strike={!!discountValue && discountValue > 0} />
                {discountValue ? <Sum label="Discount" value={`− ${formatRupees(discountValue)}`} /> : null}
                <Sum label="Charged" value={chargedValue !== null && chargedValue >= 0 ? formatRupees(chargedValue) : '–'} strong />
                <Sum label="Paid now" value={paidValue ? `${formatRupees(paidValue)} · ${MODE_LABEL[mode]}` : 'Nothing yet'} />
              </dl>
              <div className="my-4 h-px bg-line" />
              <div className="flex items-baseline justify-between">
                <span className="text-body text-muted">Due after this</span>
                <span className={cx('font-display text-[26px] font-bold tnum', dueAfter && dueAfter > 0 ? 'text-due' : 'text-active')}>
                  {dueAfter === null || dueAfter < 0 ? '–' : dueAfter === 0 ? 'Nothing' : formatRupees(dueAfter)}
                </span>
              </div>
            </>
          ) : (
            <p className="mt-2 text-body text-muted">Choose a plan to see the dates and price.</p>
          )}
          {error ? <p className="mt-4 text-caption text-due">{error}</p> : null}
          <Button className="mt-5 w-full" disabled={!plan} busy={save.isPending} onClick={submit}>
            {save.isPending ? 'Saving…' : 'Save membership'}
          </Button>
        </Card>
      </div>
      <PlanDialog open={newPlan} plan={null} onClose={() => setNewPlan(false)} onSaved={(p) => choose(p)} />
      {guard.sheet}
    </>
  )
}

function Sum({ label, value, strong, strike }: { label: string; value: string; strong?: boolean; strike?: boolean }) {
  return (
    <div className="flex justify-between gap-3">
      <dt className="text-muted">{label}</dt>
      <dd className={cx('tnum', strong && 'font-semibold', strike && 'text-muted line-through')}>{value}</dd>
    </div>
  )
}
