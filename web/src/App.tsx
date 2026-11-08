import { lazy, Suspense, useEffect, useState, type ReactNode } from 'react'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import { useGym, useMembership, useSession, GymProvider } from '@/data/session'
import { useLiveChanges } from '@/data/live'
import { SignIn } from '@/screens/SignIn'
import { Onboarding } from '@/screens/Onboarding'
import { Layout } from '@/components/Layout'
import { BrandMark } from '@/components/Brand'
import { Button, Spinner } from '@/components/ui'
import { supabase } from '@/lib/supabase'
import { BASE } from '@/lib/location'
import { Home } from '@/screens/Home'
import { Members } from '@/screens/Members'
import { MemberDetail } from '@/screens/MemberDetail'
import { MemberEdit } from '@/screens/MemberEdit'
import { Plans } from '@/screens/Plans'
import { Settings } from '@/screens/Settings'
import { GymProfile } from '@/screens/GymProfile'
import { Staff } from '@/screens/Staff'
import { DeleteAccount } from '@/screens/DeleteAccount'
import { Inbox } from '@/screens/Inbox'
import { Reminders } from '@/screens/Reminders'
import { Attendance } from '@/screens/Attendance'
import { BulkRenew } from '@/screens/BulkRenew'
import { SellMembership } from '@/screens/SellMembership'

const ImportMembers = lazy(() => import('@/screens/ImportMembers'))

const router = createBrowserRouter(
  [
    {
      element: <Layout />,
      children: [
        { path: '/', element: <Home /> },
        { path: '/members', element: <Members /> },
        { path: '/members/new', element: <MemberEdit /> },
        { path: '/members/import', element: <Suspense fallback={<Loading />}><ImportMembers /></Suspense> },
        { path: '/members/renew', element: <BulkRenew /> },
        { path: '/members/:id', element: <MemberDetail /> },
        { path: '/members/:id/edit', element: <MemberEdit /> },
        { path: '/members/:id/sell', element: <SellMembership /> },
        { path: '/plans', element: <Plans /> },
        { path: '/inbox', element: <Inbox /> },
        { path: '/reminders', element: <Reminders /> },
        { path: '/attendance', element: <Attendance /> },
        { path: '/settings', element: <Settings /> },
        { path: '/settings/gym', element: <GymProfile /> },
        { path: '/settings/staff', element: <Staff /> },
        // Also the address app stores list for deleting an account.
        { path: '/settings/delete-account', element: <DeleteAccountRoute /> },
        { path: '/delete-account', element: <DeleteAccountRoute /> },
        { path: '*', element: <Home /> },
      ],
    },
  ],
  // Served from a folder on GitHub Pages, so routes are relative to it.
  { basename: BASE },
)

/** Signed out → sign in; signed in with no gym → set one up or join; otherwise the app. */
export function App() {
  const session = useSession()
  const userId = session?.user.id
  const membership = useMembership(userId)

  if (session === undefined) return <Loading />
  if (session === null) return <SignIn />
  if (membership.isPending) return <Loading />
  if (membership.isError && !membership.data) return <Unreachable onRetry={() => membership.refetch()} />
  const account = session.user.phone || session.user.email || ''
  if (!membership.data) return <Onboarding account={account} />
  return (
    <GymProvider
      value={{
        membership: membership.data,
        userId: session.user.id,
        phone: session.user.phone || null,
        email: session.user.email || null,
        isOwner: membership.data.role === 'owner',
      }}
    >
      <Live gymId={membership.data.gym.id}>
        <RouterProvider router={router} />
      </Live>
    </GymProvider>
  )
}

function DeleteAccountRoute() {
  const { membership } = useGym()
  return <DeleteAccount gym={membership.gym} />
}

function Live({ gymId, children }: { gymId: string; children: ReactNode }) {
  useLiveChanges(gymId)
  return children
}

/** The brand while the session and gym load; if the server hasn't answered in 10 seconds, says so. */
function Loading() {
  const [slow, setSlow] = useState(false)
  useEffect(() => {
    const timer = setTimeout(() => setSlow(true), 10_000)
    return () => clearTimeout(timer)
  }, [])
  if (slow) return <Unreachable onRetry={() => window.location.reload()} />
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 animate-fade">
      <BrandMark />
      <Spinner />
    </div>
  )
}

function Unreachable({ onRetry }: { onRetry: () => void }) {
  const client = useQueryClient()
  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col justify-center px-6 animate-rise">
      <BrandMark />
      <h1 className="mt-10 text-headline">Can’t reach your gym</h1>
      <p className="mt-2 text-body text-muted">Check your internet connection and try again.</p>
      <div className="mt-8 flex gap-3">
        <Button icon={RefreshCw} onClick={onRetry}>
          Try again
        </Button>
        <Button
          kind="secondary"
          onClick={async () => {
            await supabase.auth.signOut({ scope: 'local' })
            client.clear()
            window.location.reload()
          }}
        >
          Sign out
        </Button>
      </div>
    </main>
  )
}
