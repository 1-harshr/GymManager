import { useNavigate } from 'react-router'
import { ChevronRight, LogOut } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { supabase } from '@/lib/supabase'
import { formatPhone } from '@/lib/format'
import { applyTheme, usePref, type Theme } from '@/lib/prefs'
import { useGym } from '@/data/session'
import { PageHeader } from '@/components/Layout'
import { GymLogo } from '@/components/Media'
import { SegmentedTrack, SwitchRow } from '@/components/bits'
import { Button, Card } from '@/components/ui'

/** The gym, its staff, how this browser shows things, and the account. */
export function Settings() {
  const navigate = useNavigate()
  const client = useQueryClient()
  const { membership, isOwner, phone, email } = useGym()
  const [theme, setTheme] = usePref<Theme>('theme', 'system')
  const [aiInsights, setAiInsights] = usePref('home.aiInsights', true)
  const [chart, setChart] = usePref('home.collectionsChart', true)

  return (
    <div className="max-w-4xl">
      <PageHeader title="Settings" />
      <div className="grid gap-x-10 gap-y-8 md:grid-cols-[220px_1fr]">
        <Section title="Gym" body={isOwner ? 'What members and staff see.' : 'Only the owner can change these.'}>
          <Card onClick={isOwner ? () => navigate('/settings/gym') : undefined} className="flex items-center gap-4 p-5">
            <GymLogo gym={membership.gym} size={56} />
            <span className="min-w-0 flex-1">
              <span className="block truncate text-title">{membership.gym.name}</span>
              <span className="block whitespace-pre-line text-caption text-muted">{membership.gym.address || 'No address'}</span>
            </span>
            {isOwner ? <ChevronRight className="size-5 text-muted" /> : null}
          </Card>
        </Section>

        <Section title="Staff" body={isOwner ? 'Invite staff and choose who can open the gym.' : 'See who works here, or leave this gym.'}>
          <Card onClick={() => navigate('/settings/staff')} className="flex items-center justify-between p-5">
            <span className="text-body-strong">Manage staff</span>
            <ChevronRight className="size-5 text-muted" />
          </Card>
        </Section>

        <Section title="Appearance" body="“System” follows this device’s light or dark setting.">
          <Card className="p-5">
            <div className="max-w-sm">
              <SegmentedTrack
                options={[
                  { value: 'system' as Theme, label: 'System' },
                  { value: 'light' as Theme, label: 'Light' },
                  { value: 'dark' as Theme, label: 'Dark' },
                ]}
                value={theme}
                onChange={(t) => {
                  if (!t) return
                  setTheme(t)
                  applyTheme(t)
                }}
              />
            </div>
          </Card>
        </Section>

        {isOwner ? (
          <Section title="Dashboard" body="Kept in this browser.">
            <Card className="divide-y divide-line/60">
              <SwitchRow
                title="Insights"
                body="A short daily brief, this month’s outlook, renewals and who needs attention. Worked out in this browser."
                checked={aiInsights}
                onChange={setAiInsights}
              />
              <SwitchRow title="Collections chart" body="This month’s collections against last month’s, day by day." checked={chart} onChange={setChart} />
            </Card>
          </Section>
        ) : null}

        <Section title="Account">
          <Card className="flex flex-wrap items-center gap-4 p-5">
            <span className="min-w-0 flex-1">
              <span className="block text-caption text-muted">Signed in as</span>
              <span className="block truncate text-body-strong">{formatPhone(phone) || email}</span>
            </span>
            <Button
              kind="secondary"
              size="sm"
              icon={LogOut}
              onClick={async () => {
                await supabase.auth.signOut()
                client.clear()
              }}
            >
              Sign out
            </Button>
          </Card>
        </Section>

        <Section title="About">
          <p className="pt-1 text-caption text-muted">My Gym for the web · Created by musa_fir ❤️ · No rights reserved</p>
        </Section>
      </div>
    </div>
  )
}

function Section({ title, body, children }: { title: string; body?: string; children: React.ReactNode }) {
  return (
    <>
      <div className="pt-1">
        <h2 className="text-title">{title}</h2>
        {body ? <p className="mt-1 text-caption text-muted">{body}</p> : null}
      </div>
      <div>{children}</div>
    </>
  )
}
