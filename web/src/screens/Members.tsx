import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { Archive, ArrowDown, ArrowUp, FileUp, RefreshCw, RotateCcw, Search, Trash2, UserPlus, Users, X } from 'lucide-react'
import { toast } from 'sonner'
import { supabase } from '@/lib/supabase'
import { shortDate } from '@/lib/dates'
import { byName, matchesFilter, matchesQuery, MEMBER_FILTERS, type MemberFilter, type MemberView } from '@/lib/domain'
import { formatPhone, formatRupees, plural } from '@/lib/format'
import { useGym } from '@/data/session'
import { useMemberSummaries, useToday, useWrite } from '@/data/queries'
import { explain } from '@/data/errors'
import { PageHeader } from '@/components/Layout'
import { Avatar } from '@/components/Media'
import { StatusBadge } from '@/components/MemberBits'
import { Button, Card, ConfirmSheet, EmptyState, Segmented, Skeleton, Tick, cx, useDebounced } from '@/components/ui'

type SortKey = 'name' | 'end' | 'due'

const sorters: Record<SortKey, (a: MemberView, b: MemberView) => number> = {
  name: byName,
  end: (a, b) => (a.latest_end_date ?? '').localeCompare(b.latest_end_date ?? '') || byName(a, b),
  due: (a, b) => a.amount_due - b.amount_due || byName(a, b),
}

/** Everyone at the gym as a table: search, filter, sort, and tick rows to renew, archive or delete them together. */
export function Members() {
  const navigate = useNavigate()
  const today = useToday()
  const { isOwner } = useGym()
  const [params, setParams] = useSearchParams()
  const filter = (params.get('filter') as MemberFilter) || 'ALL'
  const sortKey = (params.get('sort') as SortKey) || 'name'
  const desc = params.get('dir') === 'desc'
  const [query, setQuery] = useState(params.get('q') ?? '')
  const settled = useDebounced(query, query.trim() ? 300 : 0)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [confirmDelete, setConfirmDelete] = useState(false)
  const members = useMemberSummaries()

  const update = (change: (p: URLSearchParams) => void) => {
    const next = new URLSearchParams(params)
    change(next)
    if (next.toString() !== params.toString()) setParams(next, { replace: true })
  }
  useEffect(() => update((p) => (settled.trim() ? p.set('q', settled) : p.delete('q'))), [settled]) // eslint-disable-line react-hooks/exhaustive-deps

  const shown = useMemo(() => {
    const list = (members.data ?? []).filter((m) => matchesFilter(m, filter) && matchesQuery(m, settled)).sort(sorters[sortKey])
    return desc ? list.reverse() : list
  }, [members.data, filter, settled, sortKey, desc])

  useEffect(() => {
    const visible = new Set(shown.map((m) => m.member_id))
    setSelected((s) => (Array.from(s).every((id) => visible.has(id)) ? s : new Set(Array.from(s).filter((id) => visible.has(id)))))
  }, [shown])

  const ids = Array.from(selected)
  const allTicked = shown.length > 0 && shown.every((m) => selected.has(m.member_id))
  const toggle = (id: string) =>
    setSelected((s) => {
      const next = new Set(s)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  const sortBy = (key: SortKey) =>
    update((p) => {
      if (sortKey === key) p.set('dir', desc ? 'asc' : 'desc')
      else (p.set('sort', key), p.delete('dir'))
      if (key === 'name' && p.get('dir') !== 'desc') (p.delete('sort'), p.delete('dir'))
    })

  const setArchived = useWrite(async (archived: boolean) => {
    const { data, error } = await supabase.from('members').update({ is_archived: archived }).in('id', ids).select('id')
    if (error) throw error
    return data.length
  })
  const remove = useWrite(async () => {
    const { data, error } = await supabase.from('members').delete().in('id', ids).select('id')
    if (error) throw error
    return data.length
  })
  const archive = (archived: boolean) =>
    setArchived.mutate(archived, {
      onSuccess: (n) => {
        toast(`${archived ? 'Archived' : 'Restored'} ${plural(n, 'member', 'members')}`)
        setSelected(new Set())
      },
      onError: (e) => toast(explain(e)),
    })
  const archivedList = filter === 'ARCHIVED'
  const busy = setArchived.isPending || remove.isPending

  return (
    <>
      <PageHeader
        title="Members"
        subtitle={members.data ? plural(shown.length, 'member', 'members') : undefined}
        actions={
          <>
            <Button kind="secondary" size="sm" icon={FileUp} onClick={() => navigate('/members/import')}>
              Import
            </Button>
            <Button size="sm" icon={UserPlus} onClick={() => navigate('/members/new')}>
              Add member
            </Button>
          </>
        }
      />

      <Card className="overflow-hidden">
        <div className="flex flex-col gap-3 border-b border-line/70 p-4 lg:flex-row lg:items-center">
          <div className="flex h-10 items-center gap-2 rounded-full bg-sunken px-4 ring-ink transition focus-within:ring-2 lg:w-72">
            <Search className="size-[18px] text-muted" />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Search name or phone" className="h-full min-w-0 flex-1 bg-transparent text-body outline-none placeholder:text-muted" />
            {query ? (
              <button aria-label="Clear search" onClick={() => setQuery('')} className="rounded-full p-0.5 text-muted hover:text-ink">
                <X className="size-4" />
              </button>
            ) : null}
          </div>
          <Segmented
            className="lg:ml-auto"
            options={MEMBER_FILTERS.map((f) => ({ ...f, count: members.data ? members.data.filter((m) => matchesFilter(m, f.value)).length : undefined }))}
            value={filter}
            onChange={(f) => {
              update((p) => (f === 'ALL' ? p.delete('filter') : p.set('filter', f)))
              setSelected(new Set())
            }}
          />
        </div>

        {selected.size > 0 ? (
          <div className="flex flex-wrap items-center gap-2 border-b border-line/70 bg-accent-soft px-4 py-2.5 text-on-accent-soft animate-fade">
            <span className="mr-2 text-body-strong">{selected.size} selected</span>
            {!archivedList ? (
              <Button size="sm" kind="ink" icon={RefreshCw} onClick={() => navigate(`/members/renew?ids=${ids.join(',')}`)}>
                Renew
              </Button>
            ) : null}
            {isOwner && !archivedList ? (
              <Button size="sm" kind="secondary" icon={Archive} busy={setArchived.isPending} disabled={busy} onClick={() => archive(true)}>
                Archive
              </Button>
            ) : null}
            {isOwner && archivedList ? (
              <Button size="sm" kind="secondary" icon={RotateCcw} busy={setArchived.isPending} disabled={busy} onClick={() => archive(false)}>
                Restore
              </Button>
            ) : null}
            {isOwner ? (
              <Button size="sm" kind="danger" icon={Trash2} disabled={busy} onClick={() => setConfirmDelete(true)}>
                Delete
              </Button>
            ) : null}
            <button onClick={() => setSelected(new Set())} className="ml-auto text-caption font-semibold underline underline-offset-4">
              Clear selection
            </button>
          </div>
        ) : null}

        {!members.data ? (
          <div className="space-y-2 p-4">
            {Array.from({ length: 8 }, (_, i) => (
              <Skeleton key={i} className="h-12 rounded-[12px]" />
            ))}
          </div>
        ) : shown.length === 0 ? (
          settled.trim() ? (
            <EmptyState icon={Search} title={`No one matches “${settled.trim()}”`} body="Check the spelling, or search by phone number." />
          ) : filter === 'ALL' ? (
            <EmptyState
              icon={Users}
              title="No members yet"
              body="Add your first member, or bring them all in from a spreadsheet."
              action={
                <div className="flex gap-2">
                  <Button icon={UserPlus} onClick={() => navigate('/members/new')}>
                    Add member
                  </Button>
                  <Button kind="secondary" icon={FileUp} onClick={() => navigate('/members/import')}>
                    Import from file
                  </Button>
                </div>
              }
            />
          ) : (
            <EmptyState icon={Users} title="No one here" body="Members show up here when they match this filter." />
          )
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left">
              <thead>
                <tr className="border-b border-line/70 text-label text-muted">
                  <th className="w-12 py-3 pl-4">
                    <Tick checked={allTicked} label="Select all" onChange={() => setSelected(allTicked ? new Set() : new Set(shown.map((m) => m.member_id)))} />
                  </th>
                  <SortHead label="Member" active={sortKey === 'name'} desc={desc} onClick={() => sortBy('name')} />
                  <th className="hidden px-3 py-3 font-semibold md:table-cell">Phone</th>
                  <th className="px-3 py-3 font-semibold">Status</th>
                  <SortHead label="Ends" active={sortKey === 'end'} desc={desc} onClick={() => sortBy('end')} className="hidden sm:table-cell" />
                  <SortHead label="Due" active={sortKey === 'due'} desc={desc} onClick={() => sortBy('due')} right />
                </tr>
              </thead>
              <tbody>
                {shown.map((m) => {
                  const on = selected.has(m.member_id)
                  return (
                    <tr
                      key={m.member_id}
                      onClick={() => navigate(`/members/${m.member_id}`)}
                      className={cx('group cursor-pointer border-b border-line/50 transition last:border-0', on ? 'bg-accent-soft/60' : 'hover:bg-sunken/60')}
                    >
                      <td className="py-3 pl-4" onClick={(e) => e.stopPropagation()}>
                        <Tick checked={on} label={`Select ${m.name}`} onChange={() => toggle(m.member_id)} />
                      </td>
                      <td className="px-3 py-3">
                        <span className="flex items-center gap-3">
                          <Avatar name={m.name} photoPath={m.photo_path} size={36} />
                          <span className="min-w-0">
                            <span className="block truncate text-body-strong">{m.name}</span>
                            <span className="block truncate text-caption text-muted md:hidden">{formatPhone(m.phone)}</span>
                          </span>
                        </span>
                      </td>
                      <td className="hidden px-3 py-3 text-body text-muted tnum md:table-cell">{formatPhone(m.phone) || '—'}</td>
                      <td className="px-3 py-3">
                        <StatusBadge member={m} today={today} />
                      </td>
                      <td className="hidden px-3 py-3 text-body text-muted sm:table-cell">{m.latest_end_date ? shortDate(m.latest_end_date, today) : '—'}</td>
                      <td className={cx('px-4 py-3 text-right text-body-strong tnum', m.amount_due > 0 ? 'text-due' : 'text-muted')}>
                        {m.amount_due > 0 ? formatRupees(m.amount_due) : '—'}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <ConfirmSheet
        open={confirmDelete}
        busy={remove.isPending}
        title={`Delete ${plural(selected.size, 'member', 'members')}?`}
        body="Their memberships and payments are deleted too, and this can’t be undone. Archive them instead to keep their history."
        confirmLabel={`Delete ${plural(selected.size, 'member', 'members')}`}
        onClose={() => setConfirmDelete(false)}
        onConfirm={() =>
          remove.mutate(undefined, {
            onSuccess: (n) => {
              toast(`Deleted ${plural(n, 'member', 'members')}`)
              setConfirmDelete(false)
              setSelected(new Set())
            },
            onError: (e) => toast(explain(e)),
          })
        }
      />
    </>
  )
}

function SortHead({ label, active, desc, onClick, right, className }: { label: string; active: boolean; desc: boolean; onClick: () => void; right?: boolean; className?: string }) {
  const Arrow = desc ? ArrowDown : ArrowUp
  return (
    <th className={cx('px-3 py-3 font-semibold', right && 'pr-4 text-right', className)} aria-sort={active ? (desc ? 'descending' : 'ascending') : 'none'}>
      <button onClick={onClick} className={cx('inline-flex items-center gap-1 uppercase tracking-[0.08em] transition hover:text-ink', active && 'text-ink')}>
        {label}
        <Arrow className={cx('size-3.5', !active && 'opacity-0')} />
      </button>
    </th>
  )
}
