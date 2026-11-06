import { useState } from 'react'
import { toast } from 'sonner'
import { supabase } from '@/lib/supabase'
import { durationLabel, LENGTH_CHIPS } from '@/lib/domain'
import { parseRupees } from '@/lib/format'
import type { DurationUnit, Plan } from '@/lib/types'
import { useGym } from '@/data/session'
import { useWrite } from '@/data/queries'
import { explain } from '@/data/errors'
import { Chips, MoneyInput, SegmentedTrack } from './bits'
import { Button, Field, Sheet, TextInput } from './ui'

/** A new plan ([plan] null) or changes to one. Owners only; the database refuses anyone else. */
export function PlanDialog({ open, plan, onClose, onSaved }: { open: boolean; plan: Plan | null; onClose: () => void; onSaved?: (plan: Plan) => void }) {
  return (
    <Sheet open={open} onClose={onClose} title={plan ? 'Edit plan' : 'New plan'}>
      {open ? <PlanForm key={plan?.id ?? 'new'} plan={plan} onClose={onClose} onSaved={onSaved} /> : null}
    </Sheet>
  )
}

function PlanForm({ plan, onClose, onSaved }: { plan: Plan | null; onClose: () => void; onSaved?: (plan: Plan) => void }) {
  const { membership } = useGym()
  const [name, setName] = useState(plan?.name ?? '')
  const [length, setLength] = useState(String(plan?.duration_value ?? 1))
  const [unit, setUnit] = useState<DurationUnit>(plan?.duration_unit ?? 'MONTHS')
  const [price, setPrice] = useState(plan ? String(plan.default_price) : '')
  const [tried, setTried] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const lengthValue = /^\d+$/.test(length) && Number(length) > 0 ? Number(length) : null
  const priceValue = parseRupees(price)

  const save = useWrite(async () => {
    const row = { name: name.trim(), duration_value: lengthValue!, duration_unit: unit, default_price: priceValue! }
    const query = plan
      ? supabase.from('plans').update(row).eq('id', plan.id)
      : supabase.from('plans').insert({ ...row, gym_id: membership.gym.id, is_archived: false })
    const { data, error } = await query.select('id, name, duration_value, duration_unit, default_price, is_archived').single<Plan>()
    if (error) throw error
    return data
  })

  function submit() {
    setTried(true)
    if (!name.trim() || !lengthValue || priceValue === null) return
    save.mutate(undefined, {
      onSuccess: (saved) => {
        toast('Plan saved')
        onClose()
        onSaved?.(saved)
      },
      onError: (e) => setError(explain(e)),
    })
  }

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        submit()
      }}
      className="space-y-5 pb-2"
    >
      <Field label="Plan name" error={tried && !name.trim() ? 'Plan name is required' : null}>
        <TextInput autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Gym · Monthly" invalid={tried && !name.trim()} />
      </Field>
      <Field label="Length" error={tried && !lengthValue ? 'Enter a number of days or months' : null}>
        <div className="grid grid-cols-[110px_1fr] gap-3">
          <TextInput inputMode="numeric" value={length} onChange={(e) => setLength(e.target.value.replace(/\D/g, '').slice(0, 4))} invalid={tried && !lengthValue} />
          <SegmentedTrack
            options={[
              { value: 'DAYS' as DurationUnit, label: 'Days' },
              { value: 'MONTHS' as DurationUnit, label: 'Months' },
            ]}
            value={unit}
            onChange={(u) => u && setUnit(u)}
          />
        </div>
        <Chips
          chips={LENGTH_CHIPS.filter((c) => !(c.value === lengthValue && c.unit === unit)).map((c) => ({
            key: `${c.value}${c.unit}`,
            label: durationLabel(c.value, c.unit),
            onClick: () => (setLength(String(c.value)), setUnit(c.unit)),
          }))}
        />
      </Field>
      <Field label="Price" error={tried && priceValue === null ? 'Enter a price in rupees' : null}>
        <MoneyInput value={price} onChange={setPrice} invalid={tried && priceValue === null} />
      </Field>
      {plan ? <p className="text-caption text-muted">Changes apply to memberships you sell from now on. Ones already sold keep their price and dates.</p> : null}
      {error ? <p className="text-caption text-due">{error}</p> : null}
      <div className="flex justify-end gap-2">
        <Button type="button" kind="ghost" onClick={onClose}>
          Cancel
        </Button>
        <Button type="submit" busy={save.isPending}>
          {save.isPending ? 'Saving…' : plan ? 'Save changes' : 'Add plan'}
        </Button>
      </div>
    </form>
  )
}
