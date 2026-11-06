import { useEffect, useState, type ReactNode } from 'react'
import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { Bell, ChevronLeft, Home, LogOut, Menu as MenuIcon, MessageCircle, Plus, Search, Settings, Tag, UserCog, Users, WifiOff, X, type LucideIcon } from 'lucide-react'
import { supabase } from '@/lib/supabase'
import { formatPhone } from '@/lib/format'
import { useGym } from '@/data/session'
import { useUnreadCount } from '@/data/inbox'
import { useReminders } from '@/data/reminders'
import { BrandMark } from './Brand'
import { GymLogo } from './Media'
import { CommandPalette } from './CommandPalette'
import { WhatsAppProvider } from './WhatsApp'
import { IconButton, cx } from './ui'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  end?: boolean
  badge?: number
}

/**
 * The frame around every signed-in page: a fixed sidebar with the gym, its sections and the account; a
 * slim top bar with search. Narrow windows fold the sidebar into a slide-in menu.
 */
export function Layout() {
  const { membership, isOwner, phone, email } = useGym()
  const navigate = useNavigate()
  const location = useLocation()
  const unread = useUnreadCount()
  const reminders = useReminders()
  const online = useOnline()
  const [menuOpen, setMenuOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)

  useEffect(() => setMenuOpen(false), [location.pathname])
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setSearchOpen(true)
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  const main: NavItem[] = [
    { to: '/', label: 'Dashboard', icon: Home, end: true },
    { to: '/members', label: 'Members', icon: Users },
    { to: '/plans', label: 'Plans', icon: Tag },
  ]
  const more: NavItem[] = [
    { to: '/inbox', label: 'Alerts', icon: Bell, badge: unread },
    { to: '/reminders', label: 'Reminders', icon: MessageCircle, badge: reminders.toSend },
    { to: '/settings/staff', label: 'Staff', icon: UserCog },
    { to: '/settings', label: 'Settings', icon: Settings, end: true },
  ]

  const sidebar = (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between px-5 pb-2 pt-5">
        <BrandMark compact />
        <IconButton icon={X} label="Close menu" filled={false} className="lg:hidden" onClick={() => setMenuOpen(false)} />
      </div>
      <button
        onClick={() => navigate(isOwner ? '/settings/gym' : '/settings')}
        className="mx-3 mt-4 flex items-center gap-3 rounded-[16px] p-2.5 text-left transition hover:bg-surface"
      >
        <GymLogo gym={membership.gym} size={40} />
        <span className="min-w-0">
          <span className="block truncate text-body-strong">{membership.gym.name}</span>
          <span className="block truncate text-caption text-muted">{membership.gym.address || (isOwner ? 'Owner' : 'Staff')}</span>
        </span>
      </button>
      <nav className="mt-5 flex flex-col gap-0.5 px-3">
        {main.map((item) => (
          <SideLink key={item.to} {...item} />
        ))}
      </nav>
      <p className="px-6 pb-2 pt-6 text-label text-muted">Gym</p>
      <nav className="flex flex-col gap-0.5 px-3">
        {more.map((item) => (
          <SideLink key={item.to} {...item} />
        ))}
      </nav>
      <div className="mt-auto border-t border-line/70 p-3">
        <div className="flex items-center gap-3 rounded-[14px] px-2.5 py-2">
          <div className="min-w-0 flex-1">
            <p className="truncate text-body-strong">{membership.name || 'You'}</p>
            <p className="truncate text-caption text-muted">{formatPhone(phone) || email}</p>
          </div>
          <IconButton icon={LogOut} label="Sign out" filled={false} onClick={() => supabase.auth.signOut()} />
        </div>
      </div>
    </div>
  )

  return (
    <WhatsAppProvider>
      <div className="min-h-dvh lg:pl-[260px]">
        <aside className="fixed inset-y-0 left-0 z-40 hidden w-[260px] border-r border-line/70 bg-bg lg:block">{sidebar}</aside>
        {menuOpen ? (
          <div className="fixed inset-0 z-50 lg:hidden">
            <div className="absolute inset-0 bg-scrim animate-fade" onClick={() => setMenuOpen(false)} />
            <aside className="absolute inset-y-0 left-0 w-[280px] bg-bg shadow-[var(--shadow-float)] animate-slide-in">{sidebar}</aside>
          </div>
        ) : null}

        <header className="sticky top-0 z-30 border-b border-line/60 bg-bg/80 backdrop-blur-md">
          <div className="mx-auto flex h-16 max-w-[1280px] items-center gap-3 px-4 sm:px-6 lg:px-10">
            <IconButton icon={MenuIcon} label="Menu" filled={false} className="lg:hidden" onClick={() => setMenuOpen(true)} />
            <button
              onClick={() => setSearchOpen(true)}
              className="flex h-10 min-w-0 flex-1 items-center gap-2 rounded-full bg-surface px-4 text-left text-body text-muted ring-1 ring-line/70 transition hover:ring-line sm:max-w-md"
            >
              <Search className="size-[18px] shrink-0" />
              <span className="truncate">Search members</span>
              <kbd className="ml-auto hidden rounded-md bg-sunken px-1.5 py-0.5 font-sans text-[12px] font-semibold sm:block">⌘K</kbd>
            </button>
            <div className="ml-auto flex items-center gap-2">
              <IconButton icon={Bell} label={unread ? `Alerts, ${unread} unread` : 'Alerts'} badge={unread} filled={false} onClick={() => navigate('/inbox')} />
              <button
                onClick={() => navigate('/members/new')}
                className="flex h-10 items-center gap-2 rounded-full bg-accent px-4 text-[14px] font-semibold text-on-accent transition hover:brightness-110 active:scale-[0.98]"
              >
                <Plus className="size-[18px]" strokeWidth={2.5} />
                <span className="hidden sm:inline">Add member</span>
              </button>
            </div>
          </div>
          {!online ? (
            <div className="flex items-center justify-center gap-2 bg-ink px-4 py-2 text-caption text-on-ink">
              <WifiOff className="size-4" /> Can’t reach the gym online. Showing what was last loaded.
            </div>
          ) : null}
        </header>

        <main className="mx-auto max-w-[1280px] px-4 pb-16 sm:px-6 lg:px-10">
          <Outlet />
        </main>
        <CommandPalette open={searchOpen} onClose={() => setSearchOpen(false)} />
      </div>
    </WhatsAppProvider>
  )
}

function SideLink({ to, label, icon: Icon, end, badge }: NavItem) {
  return (
    <NavLink
      to={to}
      end={end}
      className={({ isActive }) =>
        cx(
          'flex h-10 items-center gap-3 rounded-[12px] px-3 text-[15px] font-medium transition',
          isActive ? 'bg-surface text-ink shadow-sm ring-1 ring-line/60' : 'text-muted hover:bg-surface/60 hover:text-ink',
        )
      }
    >
      {({ isActive }) => (
        <>
          <Icon className={cx('size-[19px]', isActive && 'text-accent')} strokeWidth={2} />
          <span className="flex-1">{label}</span>
          {badge ? <span className="min-w-6 rounded-full bg-accent-soft px-2 py-0.5 text-center text-[12px] font-semibold text-on-accent-soft tnum">{badge > 99 ? '99+' : badge}</span> : null}
        </>
      )}
    </NavLink>
  )
}

function useOnline(): boolean {
  const [online, setOnline] = useState(typeof navigator === 'undefined' ? true : navigator.onLine)
  useEffect(() => {
    const on = () => setOnline(true)
    const off = () => setOnline(false)
    window.addEventListener('online', on)
    window.addEventListener('offline', off)
    return () => {
      window.removeEventListener('online', on)
      window.removeEventListener('offline', off)
    }
  }, [])
  return online
}

/** A page's title row: an optional back link, the title and a line under it, and the page's actions. */
export function PageHeader({
  title,
  subtitle,
  leading,
  actions,
  back,
}: {
  title: ReactNode
  subtitle?: ReactNode
  leading?: ReactNode
  actions?: ReactNode
  back?: string | true
}) {
  const navigate = useNavigate()
  return (
    <div className="pb-6 pt-8">
      {back ? (
        <button
          onClick={() => (typeof back === 'string' ? navigate(back) : window.history.length > 1 ? navigate(-1) : navigate('/'))}
          className="-ml-1 mb-3 flex items-center gap-1 rounded-full px-1 text-caption font-semibold text-muted transition hover:text-ink"
        >
          <ChevronLeft className="size-4" /> Back
        </button>
      ) : null}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-3">
        {leading}
        <div className="min-w-0 flex-1 basis-60">
          <h1 className="truncate font-display text-[34px] font-bold leading-[38px] tracking-[-0.01em]">{title}</h1>
          {subtitle ? <p className="mt-0.5 truncate text-body text-muted">{subtitle}</p> : null}
        </div>
        {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
      </div>
    </div>
  )
}
