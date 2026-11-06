import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useBlocker } from 'react-router'
import { MoreHorizontal, type LucideIcon } from 'lucide-react'
import type { Tone } from '@/lib/domain'
import { formatRupees, parseRupees } from '@/lib/format'
import { ConfirmSheet, IconButton, cx } from './ui'

const toneClass: Record<Tone, string> = {
  active: 'bg-active-soft text-active',
  expiring: 'bg-expiring-soft text-expiring',
  expired: 'bg-expired-soft text-expired',
  due: 'bg-due-soft text-due',
  accent: 'bg-accent-soft text-on-accent-soft',
}
const dotClass: Record<Tone, string> = { active: 'bg-active', expiring: 'bg-expiring', expired: 'bg-expired', due: 'bg-due', accent: 'bg-accent' }

/** A soft pill with a dot: status, dues, roles. */
export function Badge({ tone, children, dot = true, className }: { tone: Tone; children: ReactNode; dot?: boolean; className?: string }) {
  return (
    <span className={cx('inline-flex h-7 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full px-3 text-[13px] font-semibold leading-none', toneClass[tone], className)}>
      {dot ? <span className={cx('size-1.5 rounded-full', dotClass[tone])} /> : null}
      {children}
    </span>
  )
}

export function DueBadge({ amount }: { amount: number }) {
  return amount > 0 ? <Badge tone="due">{formatRupees(amount)}</Badge> : null
}

export function Dot({ tone }: { tone: Tone }) {
  return <span className={cx('inline-block size-2 rounded-full', dotClass[tone])} />
}

export interface MenuItem {
  label: string
  icon?: LucideIcon
  onClick: () => void
  destructive?: boolean
}

/** A round "more" button with a small menu below it. */
export function Menu({ items, label, icon = MoreHorizontal, filled = true }: { items: MenuItem[]; label: string; icon?: LucideIcon; filled?: boolean }) {
  const [open, setOpen] = useState(false)
  const box = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const close = (e: MouseEvent | KeyboardEvent) => {
      if (e instanceof KeyboardEvent ? e.key === 'Escape' : !box.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', close)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', close)
    }
  }, [open])
  if (items.length === 0) return null
  return (
    <div ref={box} className="relative" onClick={(e) => e.stopPropagation()}>
      <IconButton icon={icon} label={label} filled={filled} aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((o) => !o)} />
      {open ? (
        <div role="menu" className="absolute right-0 top-12 z-30 min-w-56 overflow-hidden rounded-[18px] bg-surface p-1.5 shadow-[var(--shadow-float)] ring-1 ring-line/60 animate-pop">
          {items.map((item) => (
            <button
              key={item.label}
              role="menuitem"
              onClick={() => {
                setOpen(false)
                item.onClick()
              }}
              className={cx('flex w-full items-center gap-3 rounded-[12px] px-3 py-2.5 text-left text-body transition hover:bg-sunken', item.destructive && 'text-due')}
            >
              {item.icon ? <item.icon className="size-[18px]" /> : null}
              {item.label}
            </button>
          ))}
        </div>
      ) : null}
    </div>
  )
}

export function Switch({ checked, onChange, label }: { checked: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={cx('relative h-8 w-[52px] shrink-0 rounded-full transition-colors', checked ? 'bg-accent' : 'bg-line')}
    >
      <span className={cx('absolute left-0 top-1 size-6 rounded-full bg-white shadow transition-transform duration-200', checked ? 'translate-x-6' : 'translate-x-1')} />
    </button>
  )
}

/** A row in a settings-like card: text on the left, a control on the right. */
export function SwitchRow({ title, body, checked, onChange }: { title: string; body?: string; checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <div className="flex items-center gap-4 p-4">
      <div className="flex-1">
        <p className="text-body-strong">{title}</p>
        {body ? <p className="text-caption text-muted">{body}</p> : null}
      </div>
      <Switch checked={checked} onChange={onChange} label={title} />
    </div>
  )
}

/** Chips that fill in a value: "Full ₹1,500", "₹500", "100 off". */
export function Chips({ chips, onPick }: { chips: { label: string; key: string; onClick?: () => void }[]; onPick?: (key: string) => void }) {
  if (chips.length === 0) return null
  return (
    <div className="mt-2 flex flex-wrap gap-2">
      {chips.map((c) => (
        <button
          key={c.key}
          type="button"
          onClick={() => (c.onClick ? c.onClick() : onPick?.(c.key))}
          className="h-8 rounded-full bg-surface px-3 text-[13px] font-semibold ring-1 ring-line transition hover:bg-sunken animate-pop"
        >
          {c.label}
        </button>
      ))}
    </div>
  )
}

/** Rupees: a ₹ inside the field, digits only. */
export function MoneyInput({
  value,
  onChange,
  placeholder,
  invalid,
  autoFocus,
  id,
}: {
  value: string
  onChange: (v: string) => void
  placeholder?: string
  invalid?: boolean
  autoFocus?: boolean
  id?: string
}) {
  return (
    <div className={cx('flex h-[52px] items-center rounded-field bg-sunken ring-ink transition focus-within:ring-2', invalid && 'ring-2 ring-due')}>
      <span className="pl-4 pr-1 font-display text-[20px] font-bold text-muted">₹</span>
      <input
        id={id}
        inputMode="numeric"
        autoFocus={autoFocus}
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value.replace(/[^\d,]/g, ''))}
        className="h-full min-w-0 flex-1 bg-transparent pr-4 text-body-strong tnum outline-none placeholder:font-normal placeholder:text-muted/60"
      />
    </div>
  )
}

export const money = (value: string) => parseRupees(value)

export function DateInput({ value, onChange, max, min, invalid, id }: { value: string; onChange: (v: string) => void; max?: string; min?: string; invalid?: boolean; id?: string }) {
  return (
    <input
      id={id}
      type="date"
      value={value}
      max={max}
      min={min}
      onChange={(e) => onChange(e.target.value)}
      aria-invalid={invalid}
      className="h-[52px] w-full rounded-field bg-sunken px-4 text-body outline-none ring-ink transition focus:ring-2 aria-[invalid=true]:ring-2 aria-[invalid=true]:ring-due"
    />
  )
}

/** "Male / Female / Other" style: a sunken track with the chosen one raised. Tap again to clear if [clearable]. */
export function SegmentedTrack<T extends string>({
  options,
  value,
  onChange,
  clearable,
}: {
  options: { value: T; label: string }[]
  value: T | null
  onChange: (v: T | null) => void
  clearable?: boolean
}) {
  return (
    <div className="flex rounded-full bg-sunken p-1" role="radiogroup">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={o.value === value}
          onClick={() => onChange(o.value === value && clearable ? null : o.value)}
          className={cx(
            'h-10 flex-1 rounded-full text-[14px] font-semibold transition',
            o.value === value ? 'bg-surface text-ink shadow-sm' : 'text-muted hover:text-ink',
          )}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}

/**
 * "Discard changes?" before leaving a form that has edits, in the app or by closing the tab. Call
 * [release] just before navigating away after a save.
 */
export function useDiscardGuard(dirty: boolean) {
  const released = useRef(false)
  const dirtyNow = useRef(dirty)
  dirtyNow.current = dirty
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) => dirtyNow.current && !released.current && currentLocation.pathname !== nextLocation.pathname,
  )
  useEffect(() => {
    if (!dirty) return
    const warn = (e: BeforeUnloadEvent) => {
      if (!released.current) e.preventDefault()
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])
  return {
    release: () => {
      released.current = true
    },
    sheet: (
      <ConfirmSheet
        open={blocker.state === 'blocked'}
        title="Discard changes?"
        body="What you’ve entered here won’t be saved."
        confirmLabel="Discard"
        onConfirm={() => blocker.proceed?.()}
        onClose={() => blocker.reset?.()}
      />
    ),
  }
}

/** A label/value line inside a card: "Charged ₹1,500". */
export function Figure({ label, value, tone }: { label: string; value: ReactNode; tone?: 'due' | 'active' | 'muted' }) {
  return (
    <div>
      <p className="text-caption text-muted">{label}</p>
      <p className={cx('text-body-strong tnum', tone === 'due' && 'text-due', tone === 'active' && 'text-active', tone === 'muted' && 'text-muted')}>{value}</p>
    </div>
  )
}
