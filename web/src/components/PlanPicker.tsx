import { Check, Tag } from 'lucide-react'
import { useNavigate } from 'react-router'
import { durationLabel } from '@/lib/domain'
import { formatRupees } from '@/lib/format'
import type { Plan } from '@/lib/types'
import { useGym } from '@/data/session'
import { Button, EmptyState, cx } from './ui'

/** Plans to choose from as selectable tiles, most used first. */
export function PlanPicker({ plans, selected, onSelect, emptyBody }: { plans: Plan[]; selected: string | null; onSelect: (plan: Plan) => void; emptyBody: string }) {
  const { isOwner } = useGym()
  const navigate = useNavigate()
  if (plans.length === 0) {
    return <EmptyState icon={Tag} title="No plans yet" body={emptyBody} action={isOwner ? <Button onClick={() => navigate('/plans?new=1')}>Add plan</Button> : undefined} />
  }
  return (
    <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3" role="radiogroup">
      {plans.map((p) => {
        const on = p.id === selected
        return (
          <button
            key={p.id}
            type="button"
            role="radio"
            aria-checked={on}
            onClick={() => onSelect(p)}
            className={cx(
              'relative rounded-[16px] p-4 text-left transition',
              on ? 'bg-accent-soft ring-2 ring-accent' : 'bg-sunken/70 ring-1 ring-transparent hover:ring-line',
            )}
          >
            {on ? (
              <span className="absolute right-3 top-3 flex size-5 items-center justify-center rounded-full bg-accent text-on-accent">
                <Check className="size-3.5" strokeWidth={3} />
              </span>
            ) : null}
            <span className="block truncate pr-6 text-body-strong">{p.name}</span>
            <span className="mt-2 flex items-baseline justify-between gap-2">
              <span className="font-display text-[24px] font-bold leading-7 tnum">{formatRupees(p.default_price)}</span>
              <span className="text-caption text-muted">{durationLabel(p.duration_value, p.duration_unit)}</span>
            </span>
          </button>
        )
      })}
    </div>
  )
}
