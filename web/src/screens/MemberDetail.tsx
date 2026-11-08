import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { Archive, MessageCircle, Pencil, Phone, RotateCcw, Trash2, UserCheck, UserX } from 'lucide-react'
import { toast } from 'sonner'
import { supabase } from '@/lib/supabase'
import { addDays, age, daysBetween, fullDate, shortDate, shortMonthOf, startOfWeek, type IsoDate } from '@/lib/dates'
import { charged, dateRange, due, GENDER_LABEL, MODE_LABEL, memberStatus, paid, reminderCandidates, statusLabel, statusTone, withStatus } from '@/lib/domain'
import { formatPhone, formatRupees, plural } from '@/lib/format'
import type { Member, MembershipRecord, Payment } from '@/lib/types'
import { receipt, reminder, welcome, whatsAppNumber } from '@/lib/whatsapp'
import { useGym } from '@/data/session'
import { useMember, useMemberships, useToday, useWrite } from '@/data/queries'
import { checkIn, checkInTime, MEMBER_HEATMAP_WEEKS, undoCheckIn, useMemberCheckIns, type CheckIn } from '@/data/attendance'
import { explain } from '@/data/errors'
import { PageHeader } from '@/components/Layout'
import { Avatar } from '@/components/Media'
import { Badge, Menu } from '@/components/bits'
import { RecordPaymentSheet } from '@/components/RecordPayment'
import { useOfferWhatsApp } from '@/components/WhatsApp'
import { Button, Card, ConfirmSheet, EmptyState, IconButton, LinkButton, Skeleton, cx } from '@/components/ui'

type Confirm =
  | { kind: 'member' }
  | { kind: 'membership'; membership: MembershipRecord }
  | { kind: 'payment'; payment: Payment }

/** One member: who they are, where their membership stands, what they owe, and every sale and payment. */
export function MemberDetail() {
  const { id } = useParams()
  const navigate = useNavigate()
  const today = useToday()
  const { isOwner, membership: gym } = useGym()
  const offer = useOfferWhatsApp()
  const member = useMember(id)
  const memberships = useMemberships(id)
  const visits = useMemberCheckIns(id)
  const [paying, setPaying] = useState<MembershipRecord | null>(null)
  const [confirm, setConfirm] = useState<Confirm | null>(null)

  const list = memberships.data ?? []
  const latestEnd = list.reduce<string | null>((max, m) => (!max || m.end_date > max ? m.end_date : max), null)
  const status = memberStatus(latestEnd, today)
  const totalDue = list.reduce((s, m) => s + due(m), 0)
  const owing = list.filter((m) => due(m) > 0)
  const oldestOwing = owing[owing.length - 1] ?? null

  const write = useWrite(async (action: () => PromiseLike<{ error: unknown }>) => {
    const { error } = await action()
    if (error) throw error
  })

  const messages = useMemo(() => {
    const m = member.data
    if (!m) return []
    const view = withStatus([{ member_id: m.id, name: m.name, phone: m.phone, photo_path: m.photo_path, is_archived: m.is_archived, latest_end_date: latestEnd, amount_due: totalDue, status: 'NONE' }], today)[0]
    const remindable = reminderCandidates([view], today).length > 0
    return [remindable ? reminder({ ...view, id: m.id }, gym.gym.name, today) : null, list[0] ? receipt(m, gym.gym.name, list[0]) : null, welcome(m, gym.gym.name)]
  }, [member.data, list, latestEnd, totalDue, today, gym.gym.name])

  if (member.data === null) {
    return (
      <>
        <PageHeader back title="Member" />
        <EmptyState icon={UserX} title="This member no longer exists" body="They may have been deleted." />
      </>
    )
  }
  const m = member.data
  const sellLabel = list.length === 0 ? 'Sell membership' : 'Renew membership'
  const sell = () => navigate(`/members/${id}/sell`)
  const run = (action: () => PromiseLike<{ error: unknown }>, done: string, after?: () => void) =>
    write.mutate(action, {
      onSuccess: () => {
        toast(done)
        setConfirm(null)
        after?.()
      },
      onError: (e) => toast(explain(e)),
    })

  return (
    <>
      <PageHeader
        back
        title={m?.name ?? ''}
        subtitle={m ? `Joined ${fullDate(m.joined_on)}` : undefined}
        actions={
          m ? (
            <>
              {whatsAppNumber(m.phone) ? <IconButton icon={MessageCircle} label={`Message ${m.name} on WhatsApp`} onClick={() => offer(messages)} /> : null}
              {m.phone ? <IconButton icon={Phone} label={`Call ${m.name}`} onClick={() => (window.location.href = `tel:${m.phone}`)} /> : null}
              <Menu
                label={`More for ${m.name}`}
                items={[
                  { label: 'Edit details', icon: Pencil, onClick: () => navigate(`/members/${id}/edit`) },
                  ...(isOwner
                    ? [
                        m.is_archived
                          ? { label: 'Restore member', icon: RotateCcw, onClick: () => run(() => supabase.from('members').update({ is_archived: false }).eq('id', m.id), 'Member restored') }
                          : { label: 'Archive member', icon: Archive, onClick: () => run(() => supabase.from('members').update({ is_archived: true }).eq('id', m.id), 'Member archived') },
                        { label: 'Delete permanently', icon: Trash2, destructive: true, onClick: () => setConfirm({ kind: 'member' }) },
                      ]
                    : []),
                ]}
              />
            </>
          ) : null
        }
      />
      {!m || !memberships.data ? (
        <div className="grid gap-6 lg:grid-cols-[340px_1fr]">
          <Skeleton className="h-72" />
          <Skeleton className="h-96" />
        </div>
      ) : (
        <div className="grid items-start gap-6 animate-rise lg:grid-cols-[340px_1fr]">
          <div className="space-y-3 lg:sticky lg:top-24">
          <Card className="overflow-hidden">
            <div className="flex items-center gap-4 p-6">
              <Avatar name={m.name} photoPath={m.photo_path} size={72} />
              <div className="min-w-0">
                {m.is_archived ? <Badge tone="expired">Archived</Badge> : <Badge tone={statusTone(status)}>{statusLabel(status, latestEnd, today)}</Badge>}
                <p className="mt-2 text-caption text-muted">{!latestEnd ? 'No membership yet' : `${status === 'EXPIRED' ? 'Ended' : 'Ends'} ${fullDate(latestEnd)}`}</p>
              </div>
            </div>
            {list.length > 0 ? (
              <div className={cx('mx-6 mb-5 rounded-[16px] px-4 py-3', totalDue > 0 ? 'bg-due-soft' : 'bg-active-soft')}>
                <p className={cx('text-caption', totalDue > 0 ? 'text-due' : 'text-active')}>{totalDue > 0 ? 'Due' : 'Dues'}</p>
                <p className={cx('font-display text-[28px] font-bold leading-8 tnum', totalDue > 0 ? 'text-due' : 'text-active')}>{totalDue > 0 ? formatRupees(totalDue) : 'Nothing due'}</p>
              </div>
            ) : null}
            <Facts member={m} today={today} />
          </Card>

          <div className="flex flex-col gap-2">
            {oldestOwing ? (
              <>
                <Button className="w-full" onClick={() => setPaying(oldestOwing)}>
                  Record payment
                </Button>
                <Button kind="secondary" className="w-full" onClick={sell}>
                  {sellLabel}
                </Button>
              </>
            ) : list.length === 0 || status === 'EXPIRING_SOON' || status === 'EXPIRED' ? (
              <Button className="w-full" onClick={sell}>
                {sellLabel}
              </Button>
            ) : (
              <Button kind="secondary" className="w-full" onClick={sell}>
                {sellLabel}
              </Button>
            )}
          </div>

          {!m.is_archived && visits.data ? (
            <AttendanceCard
              visits={visits.data}
              today={today}
              busy={write.isPending}
              onCheckIn={() => {
                const note =
                  status === 'EXPIRED' ? ' · membership has ended' : status === 'NONE' ? ' · no membership yet' : totalDue > 0 ? ` · ${formatRupees(totalDue)} due` : ''
                run(() => checkIn(gym.gym.id, m.id).then(() => ({ error: null })), `${m.name} checked in${note}`)
              }}
              onUndo={(c) => run(() => undoCheckIn(c.id).then(() => ({ error: null })), `Check-in for ${m.name} undone`)}
            />
          ) : null}
          </div>
          <div>
          {list.length > 0 ? (
            <>
              <h2 className="pb-3 text-title">History</h2>
              <div className="space-y-3">
                {list.map((ms) => (
                  <MembershipCard
                    key={ms.id}
                    membership={ms}
                    today={today}
                    isOwner={isOwner}
                    showPay={due(ms) > 0 && owing.length > 1}
                    onPay={() => setPaying(ms)}
                    onDelete={() => setConfirm({ kind: 'membership', membership: ms })}
                    onDeletePayment={(p) => setConfirm({ kind: 'payment', payment: p })}
                  />
                ))}
              </div>
            </>
          ) : (
            <Card className="p-6">
              <EmptyState icon={UserX} title="No membership yet" body="Sell one to start tracking their dates and payments." />
            </Card>
          )}
          </div>
          <RecordPaymentSheet member={m} membership={paying} onClose={() => setPaying(null)} />
        </div>
      )}

      {m && confirm?.kind === 'member' ? (
        <ConfirmSheet
          open
          busy={write.isPending}
          title={`Delete ${m.name}?`}
          body="This removes their memberships and payments too, and can’t be undone. Archive them instead to keep their history."
          confirmLabel="Delete member"
          onClose={() => setConfirm(null)}
          onConfirm={() => run(() => supabase.from('members').delete().eq('id', m.id), 'Member deleted', () => navigate('/members', { replace: true }))}
        />
      ) : null}
      {confirm?.kind === 'membership' ? (
        <ConfirmSheet
          open
          busy={write.isPending}
          title={`Delete ${confirm.membership.plan_name}?`}
          body={`Its ${plural(confirm.membership.payments.length, 'payment is', 'payments are')} deleted with it. Use this only to fix a mistake.`}
          confirmLabel="Delete membership"
          onClose={() => setConfirm(null)}
          onConfirm={() => run(() => supabase.from('memberships').delete().eq('id', confirm.membership.id), 'Membership deleted')}
        />
      ) : null}
      {confirm?.kind === 'payment' ? (
        <ConfirmSheet
          open
          busy={write.isPending}
          title={`Delete payment of ${formatRupees(confirm.payment.amount)}?`}
          body={`Paid on ${fullDate(confirm.payment.paid_on)} by ${MODE_LABEL[confirm.payment.mode]}. The amount goes back to due.`}
          confirmLabel="Delete payment"
          onClose={() => setConfirm(null)}
          onConfirm={() => run(() => supabase.from('payments').delete().eq('id', confirm.payment.id), 'Payment deleted')}
        />
      ) : null}
    </>
  )
}

/** The member's details as label and value rows. */
function Facts({ member, today }: { member: Member; today: string }) {
  const rows: [string, string][] = [
    ['Phone', formatPhone(member.phone) || '—'],
    ['Gender', member.gender ? GENDER_LABEL[member.gender] : '—'],
    ['Born', member.date_of_birth ? `${fullDate(member.date_of_birth)} (${age(member.date_of_birth, today)})` : '—'],
    ['Joined', fullDate(member.joined_on)],
  ]
  return (
    <dl className="border-t border-line/70 px-6 py-4 text-body">
      {rows.map(([label, value]) => (
        <div key={label} className="flex justify-between gap-4 py-1.5">
          <dt className="text-muted">{label}</dt>
          <dd className="text-right font-medium tnum">{value}</dd>
        </div>
      ))}
      {member.notes ? (
        <div className="pt-3">
          <dt className="text-muted">Notes</dt>
          <dd className="mt-1 whitespace-pre-line">{member.notes}</dd>
        </div>
      ) : null}
    </dl>
  )
}

function MembershipCard({
  membership: ms,
  today,
  isOwner,
  showPay,
  onPay,
  onDelete,
  onDeletePayment,
}: {
  membership: MembershipRecord
  today: string
  isOwner: boolean
  showPay: boolean
  onPay: () => void
  onDelete: () => void
  onDeletePayment: (p: Payment) => void
}) {
  const total = charged(ms)
  const got = paid(ms)
  const left = due(ms)
  const status = memberStatus(ms.end_date, today)
  return (
    <Card className="p-5">
      <div className="flex items-start gap-3">
        <div className="min-w-0 flex-1">
          <p className="text-title">{ms.plan_name}</p>
          <p className="text-caption text-muted">
            {dateRange(ms.start_date, ms.end_date)}
            {ms.discount > 0 ? ` · ${formatRupees(ms.discount)} off` : ''}
          </p>
        </div>
        {ms.start_date > today ? (
          <Badge tone="expired">Starts {shortDate(ms.start_date, today)}</Badge>
        ) : (
          <Badge tone={statusTone(status)}>{statusLabel(status, ms.end_date, today)}</Badge>
        )}
        {isOwner ? <Menu label={`More for ${ms.plan_name}`} filled={false} items={[{ label: 'Delete membership', icon: Trash2, destructive: true, onClick: onDelete }]} /> : null}
      </div>
      <div className="mt-4 grid grid-cols-3 gap-3">
        <Figure label="Charged" value={formatRupees(total)} />
        <Figure label="Paid" value={formatRupees(got)} />
        <Figure label="Due" value={left > 0 ? formatRupees(left) : 'Paid'} tone={left > 0 ? 'due' : 'active'} />
      </div>
      <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-sunken">
        <div className={cx('h-full rounded-full transition-all duration-700', left > 0 ? 'bg-accent' : 'bg-active')} style={{ width: `${total === 0 ? 100 : Math.min(100, (got / total) * 100)}%` }} />
      </div>
      {ms.payments.length > 0 ? (
        <ul className="mt-3 divide-y divide-line/70">
          {ms.payments.map((p) => (
            <li key={p.id}>
              <button
                disabled={!isOwner}
                onClick={() => onDeletePayment(p)}
                title={isOwner ? 'Delete this payment' : undefined}
                className="flex w-full items-center justify-between py-2 text-left text-body enabled:hover:text-due"
              >
                <span className="text-muted">
                  {shortDate(p.paid_on, today)} · {MODE_LABEL[p.mode]}
                  {p.note ? ` · ${p.note}` : ''}
                </span>
                <span className="font-semibold tnum">{formatRupees(p.amount)}</span>
              </button>
            </li>
          ))}
        </ul>
      ) : null}
      {ms.notes ? <p className="mt-2 text-caption text-muted">{ms.notes}</p> : null}
      {showPay ? (
        <div className="mt-2">
          <LinkButton onClick={onPay}>Record payment</LinkButton>
        </div>
      ) : null}
    </Card>
  )
}

function Figure({ label, value, tone }: { label: string; value: string; tone?: 'due' | 'active' }) {
  return (
    <div>
      <p className="text-caption text-muted">{label}</p>
      <p className={cx('text-body-strong tnum', tone === 'due' && 'text-due', tone === 'active' && 'text-active')}>{value}</p>
    </div>
  )
}

/** When they last came and how often this month, with today's check-in or the button to make it. */
function AttendanceCard({ visits, today, busy, onCheckIn, onUndo }: { visits: CheckIn[]; today: IsoDate; busy: boolean; onCheckIn: () => void; onUndo: (c: CheckIn) => void }) {
  const todays = visits.find((v) => v.checked_in_on === today)
  const last = visits[0]?.checked_in_on
  const away = last ? daysBetween(last, today) : 0
  const thisMonth = visits.filter((v) => v.checked_in_on.slice(0, 7) === today.slice(0, 7)).length
  return (
    <Card className="p-5">
      <div className="flex items-center gap-3">
        <div className="min-w-0 flex-1">
          <p className="text-label text-muted">Attendance</p>
          <p className="mt-1 text-body-strong">
            {!last ? 'No visits yet' : away === 0 ? 'Came in today' : away === 1 ? 'Last came yesterday' : `Last came ${plural(away, 'day', 'days')} ago`}
          </p>
          {last ? <p className="text-caption text-muted">{plural(thisMonth, 'visit', 'visits')} this month</p> : null}
        </div>
        {todays ? (
          <div className="text-right">
            <p className="text-caption text-active">Checked in at {checkInTime(todays)}</p>
            <LinkButton disabled={busy} onClick={() => onUndo(todays)}>
              Undo
            </LinkButton>
          </div>
        ) : (
          <Button size="sm" icon={UserCheck} busy={busy} onClick={onCheckIn}>
            Check in
          </Button>
        )}
      </div>
      {last ? <VisitHeatmap visits={visits} today={today} /> : null}
    </Card>
  )
}

/**
 * The last [MEMBER_HEATMAP_WEEKS] weeks as a grid, one column a week running Monday to Sunday, with
 * each day they came filled in.
 */
function VisitHeatmap({ visits, today }: { visits: CheckIn[]; today: IsoDate }) {
  const firstMonday = addDays(startOfWeek(today), -7 * (MEMBER_HEATMAP_WEEKS - 1))
  const days = new Set(visits.map((v) => v.checked_in_on))
  const shown = visits.filter((v) => v.checked_in_on >= firstMonday).length
  const weeks = Array.from({ length: MEMBER_HEATMAP_WEEKS }, (_, w) => addDays(firstMonday, 7 * w))
  const summary = `${plural(shown, 'visit', 'visits')} in the last ${MEMBER_HEATMAP_WEEKS} weeks`
  return (
    <div className="mt-4" role="img" aria-label={summary}>
      <div className="grid max-w-sm gap-[3px]" style={{ gridTemplateColumns: `repeat(${MEMBER_HEATMAP_WEEKS}, minmax(0, 1fr))` }}>
        {/* Each month is named over the week holding its first Monday. */}
        {weeks.map((monday) => (
          <p key={monday} className="overflow-visible whitespace-nowrap text-caption text-muted">
            {Number(monday.slice(8)) <= 7 ? shortMonthOf(monday) : null}
          </p>
        ))}
        {Array.from({ length: 7 }, (_, weekday) =>
          weeks.map((monday) => {
            const day = addDays(monday, weekday)
            if (day > today) return <span key={day} />
            const came = days.has(day)
            return (
              <span
                key={day}
                title={`${shortDate(day, today)}${came ? ' · came in' : ''}`}
                className={cx('aspect-square rounded-[3px]', came ? 'bg-active' : 'bg-sunken')}
              />
            )
          }),
        )}
      </div>
      <p className="mt-2 text-caption text-muted">{summary}</p>
    </div>
  )
}
