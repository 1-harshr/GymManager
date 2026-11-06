import { useEffect, useRef, useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type TextareaHTMLAttributes } from 'react'
import { createPortal } from 'react-dom'
import { Loader2, X, type LucideIcon } from 'lucide-react'

export function cx(...classes: (string | false | null | undefined)[]): string {
  return classes.filter(Boolean).join(' ')
}

type ButtonKind = 'primary' | 'secondary' | 'danger' | 'ink' | 'ghost'

const kinds: Record<ButtonKind, string> = {
  primary: 'bg-accent text-on-accent hover:brightness-110',
  secondary: 'bg-sunken text-ink hover:bg-line',
  danger: 'bg-due-soft text-due hover:brightness-95',
  ink: 'bg-ink text-on-ink hover:opacity-90',
  ghost: 'bg-transparent text-ink hover:bg-sunken',
}

/** The pill button: one primary per screen, secondary for the alternative, danger for deletes. */
export function Button({
  kind = 'primary',
  icon: Icon,
  busy,
  size = 'md',
  className,
  children,
  disabled,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { kind?: ButtonKind; icon?: LucideIcon; busy?: boolean; size?: 'sm' | 'md' }) {
  return (
    <button
      {...rest}
      disabled={disabled || busy}
      className={cx(
        'inline-flex select-none items-center justify-center gap-2 rounded-full font-semibold transition active:scale-[0.98] disabled:opacity-40 disabled:active:scale-100',
        size === 'md' ? 'h-[52px] px-6 text-body-strong' : 'h-10 px-4 text-[14px] leading-5',
        kinds[kind],
        className,
      )}
    >
      {busy ? <Loader2 className="size-5 animate-spin" /> : Icon ? <Icon className={size === 'md' ? 'size-5' : 'size-4'} strokeWidth={2} /> : null}
      {children}
    </button>
  )
}

/** A round icon button on the ground or a card; [filled] for the header's buttons. */
export function IconButton({
  icon: Icon,
  label,
  badge,
  className,
  filled = true,
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { icon: LucideIcon; label: string; badge?: number; filled?: boolean }) {
  return (
    <button
      {...rest}
      aria-label={label}
      title={label}
      className={cx(
        'relative inline-flex size-11 shrink-0 items-center justify-center rounded-full text-ink transition active:scale-95 disabled:opacity-40',
        filled ? 'bg-sunken hover:bg-line' : 'hover:bg-sunken',
        className,
      )}
    >
      <Icon className="size-[22px]" strokeWidth={2} />
      {badge ? (
        <span className="absolute -right-0.5 -top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-accent px-1.5 text-[11px] font-semibold leading-none text-on-accent ring-2 ring-bg">
          {badge > 9 ? '9+' : badge}
        </span>
      ) : null}
    </button>
  )
}

/** A text action inside cards: "See all", "Record payment". */
export function LinkButton({ className, ...rest }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      {...rest}
      className={cx('rounded-full px-2 py-1 text-body-strong text-ink underline decoration-1 underline-offset-4 hover:text-accent disabled:opacity-40', className)}
    />
  )
}

export function Card({ className, children, onClick }: { className?: string; children: ReactNode; onClick?: () => void }) {
  const Tag = onClick ? 'button' : 'div'
  return (
    <Tag
      onClick={onClick}
      className={cx(
        'block w-full rounded-card bg-surface text-left',
        onClick && 'transition hover:ring-1 hover:ring-line active:scale-[0.995]',
        className,
      )}
    >
      {children}
    </Tag>
  )
}

/** Capitals, spaced: the label above a group of cards. */
export function SectionLabel({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex items-center justify-between px-1 pb-2 pt-6">
      <h2 className="text-label text-muted">{children}</h2>
      {action}
    </div>
  )
}

export function Field({
  label,
  hint,
  error,
  children,
}: {
  label?: string
  hint?: ReactNode
  error?: string | null
  children: ReactNode
}) {
  return (
    <label className="block">
      {label ? (
        <span className="mb-2 flex items-baseline justify-between gap-2 text-body-strong">
          {label}
          {hint === 'Optional' ? <span className="text-caption font-normal text-muted">Optional</span> : null}
        </span>
      ) : null}
      {children}
      {error ? (
        <span className="mt-2 block text-caption text-due">{error}</span>
      ) : hint && hint !== 'Optional' ? (
        <span className="mt-2 block text-caption text-muted">{hint}</span>
      ) : null}
    </label>
  )
}

const inputClass =
  'w-full rounded-field bg-sunken px-4 text-body text-ink placeholder:text-muted/60 outline-none ring-ink transition focus:ring-2 aria-[invalid=true]:ring-2 aria-[invalid=true]:ring-due disabled:opacity-50'

export function TextInput({
  prefix,
  className,
  invalid,
  ...rest
}: InputHTMLAttributes<HTMLInputElement> & { prefix?: ReactNode; invalid?: boolean }) {
  if (prefix) {
    return (
      <div className={cx('flex h-[52px] items-center rounded-field bg-sunken ring-ink transition focus-within:ring-2', invalid && 'ring-2 ring-due', className)}>
        <span className="pl-4 pr-1 text-body text-muted">{prefix}</span>
        <input {...rest} aria-invalid={invalid} className="h-full min-w-0 flex-1 bg-transparent pr-4 text-body outline-none placeholder:text-muted/60" />
      </div>
    )
  }
  return <input {...rest} aria-invalid={invalid} className={cx(inputClass, 'h-[52px]', className)} />
}

export function TextArea({ className, ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea {...rest} className={cx(inputClass, 'min-h-24 resize-y py-3', className)} />
}

/** A row of choices where one is on, like the app's segmented chips. */
export function Segmented<T extends string>({
  options,
  value,
  onChange,
  className,
}: {
  options: { value: T; label: string; count?: number }[]
  value: T
  onChange: (value: T) => void
  className?: string
}) {
  return (
    <div className={cx('flex gap-2 overflow-x-auto pb-1 [scrollbar-width:none]', className)} role="tablist">
      {options.map((o) => (
        <button
          key={o.value}
          role="tab"
          aria-selected={o.value === value}
          onClick={() => onChange(o.value)}
          className={cx(
            'h-9 shrink-0 rounded-full px-4 text-[14px] font-semibold transition',
            o.value === value ? 'bg-ink text-on-ink' : 'bg-surface text-ink hover:bg-sunken',
          )}
        >
          {o.label}
          {o.count !== undefined ? <span className={cx('ml-1.5 tnum', o.value === value ? 'opacity-70' : 'text-muted')}>{o.count}</span> : null}
        </button>
      ))}
    </div>
  )
}

export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cx('size-6 animate-spin text-muted', className)} />
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cx('skeleton rounded-card', className)} />
}

export function EmptyState({
  icon: Icon,
  title,
  body,
  action,
}: {
  icon: LucideIcon
  title: string
  body?: string
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center px-6 py-16 text-center animate-rise">
      <div className="mb-4 flex size-16 items-center justify-center rounded-full bg-surface">
        <Icon className="size-7 text-muted" strokeWidth={1.75} />
      </div>
      <p className="text-title">{title}</p>
      {body ? <p className="mt-1 max-w-sm text-body text-muted">{body}</p> : null}
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  )
}

/**
 * A sheet: rises from the bottom on phones, a centred card on wider screens. Escape and the scrim close
 * it unless it's [busy].
 */
export function Sheet({
  open,
  onClose,
  title,
  children,
  footer,
  wide,
  busy,
}: {
  open: boolean
  onClose: () => void
  title?: string
  children: ReactNode
  footer?: ReactNode
  wide?: boolean
  busy?: boolean
}) {
  const panel = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && !busy && onClose()
    document.addEventListener('keydown', onKey)
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    panel.current?.focus()
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = overflow
    }
  }, [open, busy, onClose])
  if (!open) return null
  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center sm:p-6">
      <div className="absolute inset-0 bg-scrim animate-fade" onClick={() => !busy && onClose()} />
      <div
        ref={panel}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={cx(
          'relative flex max-h-[92dvh] w-full flex-col overflow-hidden rounded-t-sheet bg-bg outline-none animate-sheet-up sm:rounded-sheet sm:animate-pop',
          wide ? 'sm:max-w-2xl' : 'sm:max-w-lg',
        )}
      >
        <div className="mx-auto mt-2 h-1 w-10 rounded-full bg-line sm:hidden" />
        {title ? (
          <div className="flex items-center justify-between gap-4 px-5 pb-2 pt-4 sm:px-6 sm:pt-6">
            <h2 className="text-screen-title">{title}</h2>
            <IconButton icon={X} label="Close" onClick={onClose} disabled={busy} />
          </div>
        ) : null}
        <div className="flex-1 overflow-y-auto px-5 pb-5 sm:px-6">{children}</div>
        {footer ? <div className="border-t border-line/60 px-5 py-4 pb-[max(1rem,env(safe-area-inset-bottom))] sm:px-6">{footer}</div> : null}
      </div>
    </div>,
    document.body,
  )
}

/** Asks before something that can't be undone. */
export function ConfirmSheet({
  open,
  title,
  body,
  confirmLabel,
  destructive = true,
  busy,
  onConfirm,
  onClose,
}: {
  open: boolean
  title: string
  body: string
  confirmLabel: string
  destructive?: boolean
  busy?: boolean
  onConfirm: () => void
  onClose: () => void
}) {
  return (
    <Sheet open={open} onClose={onClose} busy={busy}>
      <div className="pt-6 text-center sm:pt-8">
        <h2 className="text-screen-title">{title}</h2>
        <p className="mx-auto mt-2 max-w-sm text-body text-muted">{body}</p>
        <div className="mt-6 flex flex-col gap-2">
          <Button kind={destructive ? 'danger' : 'primary'} onClick={onConfirm} busy={busy}>
            {confirmLabel}
          </Button>
          <Button kind="ghost" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
        </div>
      </div>
    </Sheet>
  )
}

/** Follows a value but only after it has stopped changing for [ms]. */
export function useDebounced<T>(value: T, ms = 200): T {
  const [settled, setSettled] = useState(value)
  useEffect(() => {
    const t = setTimeout(() => setSettled(value), ms)
    return () => clearTimeout(t)
  }, [value, ms])
  return settled
}

/** A tick box in the accent colour, for picking rows. */
export function Tick({ checked, label, onChange }: { checked: boolean; label: string; onChange: () => void }) {
  return (
    <input
      type="checkbox"
      checked={checked}
      onChange={onChange}
      aria-label={label}
      className="size-[18px] cursor-pointer rounded-[5px] accent-[var(--accent)]"
    />
  )
}
