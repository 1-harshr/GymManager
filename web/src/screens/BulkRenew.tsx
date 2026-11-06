import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { supabase } from '@/lib/supabase'
import { byName, byUse, dateRange, MODE_LABEL, planEnd, renewalStartDate } from '@/lib/domain'
import { formatRupees, plural } from '@/lib/format'
import type { PaymentMode, Plan } from '@/lib/types'
import { useMemberSummaries, usePlans, useToday, useUsageStats, useWrite } from '@/data/queries'
import { explain } from '@/data/errors'
import { PageHeader } from '@/components/Layout'
import { Avatar } from '@/components/Media'
import { PlanPicker } from '@/components/PlanPicker'
import { SegmentedTrack, Switch } from '@/components/bits'
import { Button, Card, Skeleton } from '@/components/ui'

const MODES = (['CASH', 'UPI', 'OTHER'] as PaymentMode[]).map((m) => ({ value: m, label: MODE_LABEL[m] }))

/** Renews several members onto one plan at its own price; each carries on from their own end date. */
export function BulkRenew() {
  const navigate = useNavigate()
  const today = useToday()
  const [params] = useSearchParams()
  const ids = useMemo(() => (params.get('ids') ?? '').split(',').filter(Boolean), [params])
  const members = useMemberSummaries()
  const plans = usePlans()
  const usage = useUsageStats()
  const [plan, setPlan] = useState<Plan | null>(null)
  const [markPaid, setMarkPaid] = useState(false)
  const [mode, setMode] = useState<PaymentMode>('CASH')
  const [error, setError] = useState<string | null>(null)

  const chosen = (members.data ?? []).filter((m) => ids.includes(m.member_id))
  const renewing = chosen.filter((m) => !m.is_archived).sort(byName)
  const active = byUse((plans.data ?? []).filter((p) => !p.is_archived), usage)

  const save = useWrite(async () => {
    const items = renewing.map((m) => {
      const start = renewalStartDate(m.latest_end_date, today)
      return {
        member_id: m.member_id,
        plan_id: plan!.id,
        plan_name: plan!.name,
        start_date: start,
        end_date: planEnd(plan!, start),
        price: plan!.default_price,
        discount: 0,
        ...(markPaid && plan!.default_price > 0 ? { payment: { amount: plan!.default_price, mode, paid_on: today } } : {}),
      }
    })
    const { data, error } = await supabase.rpc('add_memberships', { items })
    if (error) throw error
    return (data as string[]).length
  })

  return (
    <>
      <PageHeader back title={`Renew ${plural(ids.length, 'member', 'members')}`} subtitle="Each member continues from their own end date" />
      {!members.data || !plans.data ? (
        <Skeleton className="h-96" />
      ) : (
        <div className="grid items-start gap-6 lg:grid-cols-[1fr_380px]">
          <div className="space-y-4">
            <Card className="p-6">
              <h2 className="mb-4 text-title">Plan</h2>
              <PlanPicker plans={active} selected={plan?.id ?? null} onSelect={setPlan} emptyBody="Add a plan first, then come back to renew." />
            </Card>
            {plan ? (
              <Card className="p-6 animate-rise">
                <div className="flex items-center gap-4">
                  <div className="flex-1">
                    <p className="text-body-strong">Mark all as paid in full</p>
                    <p className="text-caption text-muted">
                      {markPaid ? `${formatRupees(plan.default_price * renewing.length)} recorded today` : `Each member will owe ${formatRupees(plan.default_price)}`}
                    </p>
                  </div>
                  <Switch checked={markPaid} onChange={setMarkPaid} label="Mark all as paid in full" />
                </div>
                {markPaid ? (
                  <div className="mt-4 max-w-sm">
                    <SegmentedTrack options={MODES} value={mode} onChange={(m) => m && setMode(m)} />
                  </div>
                ) : null}
              </Card>
            ) : null}
          </div>
          <Card className="overflow-hidden lg:sticky lg:top-24">
            <div className="border-b border-line/70 px-6 py-4">
              <h2 className="text-title">New dates</h2>
              {renewing.length < chosen.length && renewing.length > 0 ? <p className="text-caption text-muted">Archived members are left out.</p> : null}
            </div>
            <ul className="max-h-[50vh] divide-y divide-line/50 overflow-y-auto">
              {renewing.map((m) => {
                const start = renewalStartDate(m.latest_end_date, today)
                return (
                  <li key={m.member_id} className="flex items-center gap-3 px-6 py-3">
                    <Avatar name={m.name} photoPath={m.photo_path} size={32} />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-body-strong">{m.name}</span>
                      <span className="block text-caption text-muted">{plan ? dateRange(start, planEnd(plan, start)) : 'Choose a plan'}</span>
                    </span>
                  </li>
                )
              })}
            </ul>
            <div className="border-t border-line/70 p-6">
              {error ? <p className="mb-3 text-caption text-due">{error}</p> : null}
              <Button
                className="w-full"
                disabled={!plan || renewing.length === 0}
                busy={save.isPending}
                onClick={() =>
                  save.mutate(undefined, {
                    onSuccess: (n) => {
                      toast(`Renewed ${plural(n, 'member', 'members')}`)
                      navigate(-1)
                    },
                    onError: (e) => setError(explain(e)),
                  })
                }
              >
                {save.isPending ? 'Saving…' : !plan ? 'Choose a plan' : `Renew ${plural(renewing.length, 'member', 'members')}`}
              </Button>
            </div>
          </Card>
        </div>
      )}
    </>
  )
}
