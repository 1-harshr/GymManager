import { useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router'
import { CornerDownLeft, Search, UserPlus } from 'lucide-react'
import { byName, matchesQuery } from '@/lib/domain'
import { formatPhone } from '@/lib/format'
import { useMemberSummaries, useToday } from '@/data/queries'
import { Avatar } from './Media'
import { StatusBadge } from './MemberBits'
import { cx } from './ui'

/** ⌘K: find a member by name or phone and jump to them, without leaving the keyboard. */
export function CommandPalette({ open, onClose }: { open: boolean; onClose: () => void }) {
  const navigate = useNavigate()
  const today = useToday()
  const members = useMemberSummaries().data ?? []
  const [query, setQuery] = useState('')
  const [index, setIndex] = useState(0)
  const list = useRef<HTMLUListElement>(null)

  useEffect(() => {
    if (open) {
      setQuery('')
      setIndex(0)
    }
  }, [open])

  const results = useMemo(() => members.filter((m) => matchesQuery(m, query)).sort((a, b) => Number(a.is_archived) - Number(b.is_archived) || byName(a, b)).slice(0, 8), [members, query])

  useEffect(() => setIndex(0), [query])
  useEffect(() => list.current?.children[index]?.scrollIntoView({ block: 'nearest' }), [index])

  if (!open) return null
  const go = (path: string) => {
    onClose()
    navigate(path)
  }
  const choose = () => (results[index] ? go(`/members/${results[index].member_id}`) : query.trim() ? go('/members/new') : undefined)

  return createPortal(
    <div className="fixed inset-0 z-[60] flex items-start justify-center p-4 pt-[12vh]">
      <div className="absolute inset-0 bg-scrim animate-fade" onClick={onClose} />
      <div className="relative w-full max-w-xl overflow-hidden rounded-[22px] bg-surface shadow-[var(--shadow-float)] ring-1 ring-line/60 animate-pop">
        <div className="flex items-center gap-3 border-b border-line/70 px-5">
          <Search className="size-5 text-muted" />
          <input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Escape') onClose()
              else if (e.key === 'ArrowDown') (e.preventDefault(), setIndex((i) => Math.min(results.length - 1, i + 1)))
              else if (e.key === 'ArrowUp') (e.preventDefault(), setIndex((i) => Math.max(0, i - 1)))
              else if (e.key === 'Enter') choose()
            }}
            placeholder="Search by name or phone"
            className="h-14 min-w-0 flex-1 bg-transparent text-[17px] outline-none placeholder:text-muted"
          />
          <kbd className="rounded-md bg-sunken px-1.5 py-0.5 text-[12px] font-semibold text-muted">Esc</kbd>
        </div>
        {results.length > 0 ? (
          <ul ref={list} className="max-h-[50vh] overflow-y-auto p-2" role="listbox">
            {results.map((m, i) => (
              <li key={m.member_id} role="option" aria-selected={i === index}>
                <button
                  onMouseMove={() => setIndex(i)}
                  onClick={() => go(`/members/${m.member_id}`)}
                  className={cx('flex w-full items-center gap-3 rounded-[14px] px-3 py-2.5 text-left', i === index && 'bg-sunken')}
                >
                  <Avatar name={m.name} photoPath={m.photo_path} size={36} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-body-strong">{m.name}</span>
                    <span className="block truncate text-caption text-muted">{formatPhone(m.phone) || 'No phone'}</span>
                  </span>
                  <StatusBadge member={m} today={today} />
                  {i === index ? <CornerDownLeft className="size-4 text-muted" /> : null}
                </button>
              </li>
            ))}
          </ul>
        ) : (
          <div className="p-2">
            <p className="px-3 py-4 text-body text-muted">{query.trim() ? `No one matches “${query.trim()}”.` : 'No members yet.'}</p>
            <button onClick={() => go('/members/new')} className="flex w-full items-center gap-3 rounded-[14px] bg-sunken px-3 py-2.5 text-left text-body-strong">
              <UserPlus className="size-5 text-accent" /> Add a new member
            </button>
          </div>
        )}
      </div>
    </div>,
    document.body,
  )
}
