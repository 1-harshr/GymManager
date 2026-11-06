import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { Archive, Pencil, Plus, RotateCcw, Tag } from 'lucide-react'
import { toast } from 'sonner'
import { supabase } from '@/lib/supabase'
import { byName, durationLabel } from '@/lib/domain'
import { formatRupees, plural } from '@/lib/format'
import type { Plan } from '@/lib/types'
import { useGym } from '@/data/session'
import { usePlans, useUsageStats, useWrite } from '@/data/queries'
import { explain } from '@/data/errors'
import { PageHeader } from '@/components/Layout'
import { Badge, Menu, Switch } from '@/components/bits'
import { PlanDialog } from '@/components/PlanDialog'
import { Button, Card, EmptyState, Skeleton, Tick, cx } from '@/components/ui'

/** What the gym sells. Owners add, edit, archive and restore plans; staff see the list. Plans are never deleted. */
export function Plans() {
  const { isOwner } = useGym()
  const plans = usePlans()
  const usage = useUsageStats()
  const [params, setParams] = useSearchParams()
  const [showArchived, setShowArchived] = useState(false)
  const [editing, setEditing] = useState<Plan | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const creating = params.get('new') === '1'

  const all = plans.data ?? []
  const shown = useMemo(
    () => all.filter((p) => showArchived || !p.is_archived).sort((a, b) => Number(a.is_archived) - Number(b.is_archived) || byName(a, b)),
    [all, showArchived],
  )
  const activeCount = all.filter((p) => !p.is_archived).length

  const setArchived = useWrite(async ({ ids, archived }: { ids: string[]; archived: boolean }) => {
    const { data, error } = await supabase.from('plans').update({ is_archived: archived }).in('id', ids).select('id')
    if (error) throw error
    return data.length
  })
  const archive = (ids: string[], archived: boolean, single?: boolean) =>
    setArchived.mutate(
      { ids, archived },
      {
        onSuccess: (n) => {
          toast(single ? (archived ? 'Plan archived' : 'Plan restored') : `${archived ? 'Archived' : 'Restored'} ${plural(n, 'plan', 'plans')}`)
          setSelected(new Set())
        },
        onError: (e) => toast(explain(e)),
      },
    )
  const selectedPlans = shown.filter((p) => selected.has(p.id))
  const closeNew = () => {
    const next = new URLSearchParams(params)
    next.delete('new')
    setParams(next, { replace: true })
  }

  return (
    <>
      <PageHeader
        title="Plans"
        subtitle={plans.data ? plural(activeCount, 'plan', 'plans') : undefined}
        actions={
          isOwner ? (
            <Button size="sm" icon={Plus} onClick={() => setParams({ new: '1' })}>
              Add plan
            </Button>
          ) : null
        }
      />
      {!plans.data ? (
        <Skeleton className="h-64" />
      ) : all.length === 0 ? (
        <Card>
          <EmptyState
            icon={Tag}
            title="No plans yet"
            body="Add the plans you sell, like Gym · Monthly for ₹800."
            action={isOwner ? <Button icon={Plus} onClick={() => setParams({ new: '1' })}>Add plan</Button> : undefined}
          />
        </Card>
      ) : (
        <Card className="overflow-hidden">
          <div className="flex flex-wrap items-center gap-3 border-b border-line/70 px-5 py-3">
            {selected.size > 0 ? (
              <>
                <span className="text-body-strong">{selected.size} selected</span>
                {selectedPlans.some((p) => !p.is_archived) ? (
                  <Button size="sm" kind="secondary" icon={Archive} onClick={() => archive(selectedPlans.filter((p) => !p.is_archived).map((p) => p.id), true)}>
                    Archive
                  </Button>
                ) : null}
                {selectedPlans.some((p) => p.is_archived) ? (
                  <Button size="sm" kind="secondary" icon={RotateCcw} onClick={() => archive(selectedPlans.filter((p) => p.is_archived).map((p) => p.id), false)}>
                    Restore
                  </Button>
                ) : null}
              </>
            ) : (
              <span className="text-caption text-muted">{isOwner ? 'Click a plan to edit it.' : 'Only the owner can change plans.'}</span>
            )}
            <label className="ml-auto flex items-center gap-3 text-body">
              Show archived plans
              <Switch checked={showArchived} onChange={setShowArchived} label="Show archived plans" />
            </label>
          </div>
          <table className="w-full text-left">
            <thead>
              <tr className="border-b border-line/70 text-label text-muted">
                {isOwner ? <th className="w-12 py-3 pl-5" /> : null}
                <th className={cx('py-3 font-semibold', isOwner ? 'px-3' : 'px-5')}>Plan</th>
                <th className="px-3 py-3 font-semibold">Length</th>
                <th className="hidden px-3 py-3 font-semibold sm:table-cell">Sold</th>
                <th className="px-3 py-3 text-right font-semibold">Price</th>
                {isOwner ? <th className="w-16 py-3 pr-4" /> : null}
              </tr>
            </thead>
            <tbody>
              {shown.map((p) => (
                <tr
                  key={p.id}
                  onClick={isOwner ? () => setEditing(p) : undefined}
                  className={cx('border-b border-line/50 transition last:border-0', isOwner && 'cursor-pointer hover:bg-sunken/60', selected.has(p.id) && 'bg-accent-soft/60')}
                >
                  {isOwner ? (
                    <td className="py-3.5 pl-5" onClick={(e) => e.stopPropagation()}>
                      <Tick
                        checked={selected.has(p.id)}
                        label={`Select ${p.name}`}
                        onChange={() =>
                          setSelected((s) => {
                            const next = new Set(s)
                            if (next.has(p.id)) next.delete(p.id)
                            else next.add(p.id)
                            return next
                          })
                        }
                      />
                    </td>
                  ) : null}
                  <td className={cx('py-3.5', isOwner ? 'px-3' : 'px-5')}>
                    <span className="flex items-center gap-2">
                      <span className={cx('text-body-strong', p.is_archived && 'text-muted')}>{p.name}</span>
                      {p.is_archived ? (
                        <Badge tone="expired" dot={false}>
                          Archived
                        </Badge>
                      ) : null}
                    </span>
                  </td>
                  <td className="px-3 py-3.5 text-body text-muted">{durationLabel(p.duration_value, p.duration_unit)}</td>
                  <td className="hidden px-3 py-3.5 text-body text-muted tnum sm:table-cell">{usage.plan_uses[p.id] ?? 0}</td>
                  <td className={cx('px-3 py-3.5 text-right font-display text-[20px] font-bold tnum', p.is_archived && 'text-muted')}>{formatRupees(p.default_price)}</td>
                  {isOwner ? (
                    <td className="py-2 pr-4 text-right">
                      <Menu
                        label={`More for ${p.name}`}
                        filled={false}
                        items={[
                          { label: 'Edit', icon: Pencil, onClick: () => setEditing(p) },
                          p.is_archived
                            ? { label: 'Restore', icon: RotateCcw, onClick: () => archive([p.id], false, true) }
                            : { label: 'Archive', icon: Archive, onClick: () => archive([p.id], true, true) },
                        ]}
                      />
                    </td>
                  ) : null}
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
      <PlanDialog open={isOwner && (creating || !!editing)} plan={editing} onClose={() => (setEditing(null), closeNew())} />
    </>
  )
}
