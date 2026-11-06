import { useState } from 'react'
import { ArrowRight, ChevronLeft, Dumbbell, KeyRound } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { supabase } from '@/lib/supabase'
import { formatPhone } from '@/lib/format'
import { explain } from '@/data/errors'
import { BrandMark } from '@/components/Brand'
import { Button, Field, IconButton, LinkButton, TextInput, cx } from '@/components/ui'

type Step = 'choose' | 'create' | 'join'

/** Signed in but not part of a gym yet: set one up as its owner, or join one with an invite code. */
export function Onboarding({ account }: { account: string }) {
  const [step, setStep] = useState<Step>('choose')
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-lg flex-col px-4 pb-[max(1rem,env(safe-area-inset-bottom))] pt-[max(2rem,env(safe-area-inset-top))] sm:px-6 lg:justify-center">
      {step === 'choose' ? (
        <div className="animate-rise">
          <BrandMark />
          <h1 className="mt-14 whitespace-pre-line text-headline">{'How will you use\nMy Gym?'}</h1>
          <p className="mt-2 text-body text-muted">You can be part of one gym at a time.</p>
          <div className="mt-8 space-y-3">
            <Choice icon={Dumbbell} title="I run a gym" body="Set it up and invite your staff. You’ll be its owner." onClick={() => setStep('create')} />
            <Choice icon={KeyRound} title="I work at a gym" body="Join with the code your owner sent you." onClick={() => setStep('join')} />
          </div>
          <p className="mt-10 flex flex-wrap items-center gap-1 text-body text-muted">
            Signed in as {formatPhone(account) || account}. Not you?
            <LinkButton onClick={() => supabase.auth.signOut()}>Sign out</LinkButton>
          </p>
        </div>
      ) : step === 'create' ? (
        <CreateGym onBack={() => setStep('choose')} />
      ) : (
        <JoinGym onBack={() => setStep('choose')} />
      )}
    </main>
  )
}

function Choice({ icon: Icon, title, body, onClick }: { icon: typeof Dumbbell; title: string; body: string; onClick: () => void }) {
  return (
    <button onClick={onClick} className="group flex w-full items-center gap-4 rounded-card bg-surface p-5 text-left transition hover:ring-1 hover:ring-line active:scale-[0.99]">
      <span className="flex size-12 shrink-0 items-center justify-center rounded-full bg-accent-soft text-on-accent-soft">
        <Icon className="size-6" />
      </span>
      <span className="flex-1">
        <span className="block text-title">{title}</span>
        <span className="block text-body text-muted">{body}</span>
      </span>
      <ArrowRight className="size-5 text-muted transition group-hover:translate-x-0.5" />
    </button>
  )
}

function StepFrame({ title, body, onBack, children }: { title: string; body: string; onBack: () => void; children: React.ReactNode }) {
  return (
    <div className="flex flex-1 flex-col animate-rise lg:flex-none">
      <div>
        <IconButton icon={ChevronLeft} label="Back" onClick={onBack} />
      </div>
      <h1 className="mt-8 text-headline">{title}</h1>
      <p className="mt-2 text-body text-muted">{body}</p>
      {children}
    </div>
  )
}

function CreateGym({ onBack }: { onBack: () => void }) {
  const client = useQueryClient()
  const [name, setName] = useState('')
  const [address, setAddress] = useState('')
  const [yourName, setYourName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [tried, setTried] = useState(false)

  async function submit() {
    setTried(true)
    if (!name.trim() || !yourName.trim()) return
    setBusy(true)
    setError(null)
    const { error } = await supabase.rpc('create_gym', { name: name.trim(), address: address.trim() || null, owner_name: yourName.trim() })
    if (error) {
      setBusy(false)
      return setError(explain(error))
    }
    await client.invalidateQueries({ queryKey: ['membership'] })
  }

  return (
    <StepFrame title="Set up your gym" body="Its name is what members and staff will see." onBack={onBack}>
      <form
        className="mt-8 flex flex-1 flex-col gap-5"
        onSubmit={(e) => {
          e.preventDefault()
          submit()
        }}
      >
        <Field label="Gym name" error={tried && !name.trim() ? 'Enter the gym’s name' : null}>
          <TextInput autoFocus value={name} onChange={(e) => setName(e.target.value)} placeholder="Iron Temple Gym" invalid={tried && !name.trim()} />
        </Field>
        <Field label="Address" hint="Optional">
          <TextInput value={address} onChange={(e) => setAddress(e.target.value)} placeholder="Main Road, Ranchi" />
        </Field>
        <Field label="Your name" error={tried && !yourName.trim() ? 'Enter your name' : null}>
          <TextInput value={yourName} onChange={(e) => setYourName(e.target.value)} placeholder="So your staff know who you are" invalid={tried && !yourName.trim()} />
        </Field>
        {error ? <p className="text-caption text-due">{error}</p> : null}
        <div className="mt-auto pt-4">
          <Button type="submit" className="w-full" busy={busy}>
            Create gym
          </Button>
        </div>
      </form>
    </StepFrame>
  )
}

function JoinGym({ onBack }: { onBack: () => void }) {
  const client = useQueryClient()
  const [code, setCode] = useState('')
  const [yourName, setYourName] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [tried, setTried] = useState(false)
  const clean = code.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8)

  async function submit() {
    setTried(true)
    if (clean.length === 0 || !yourName.trim()) return
    setBusy(true)
    setError(null)
    const { error } = await supabase.rpc('accept_invite', { code: clean, staff_name: yourName.trim() })
    if (error) {
      setBusy(false)
      return setError(explain(error))
    }
    await client.invalidateQueries({ queryKey: ['membership'] })
  }

  return (
    <StepFrame title="Join your gym" body="Your gym’s owner can make a code in Settings › Staff." onBack={onBack}>
      <form
        className="mt-8 flex flex-1 flex-col gap-5"
        onSubmit={(e) => {
          e.preventDefault()
          submit()
        }}
      >
        <Field label="Invite code" error={error ?? (tried && !clean ? 'Enter the invite code' : null)}>
          <input
            autoFocus
            value={clean.length > 4 ? `${clean.slice(0, 4)}-${clean.slice(4)}` : clean}
            onChange={(e) => {
              setCode(e.target.value)
              setError(null)
            }}
            placeholder="ABCD-2345"
            autoCapitalize="characters"
            autoComplete="off"
            spellCheck={false}
            className={cx(
              'h-16 w-full rounded-field bg-sunken px-5 font-display text-[30px] font-bold tracking-[0.12em] outline-none ring-ink transition placeholder:text-muted/40 focus:ring-2',
              (error || (tried && !clean)) && 'ring-2 ring-due',
            )}
          />
        </Field>
        <Field label="Your name" error={tried && !yourName.trim() ? 'Enter your name' : null}>
          <TextInput value={yourName} onChange={(e) => setYourName(e.target.value)} placeholder="So the owner knows who joined" invalid={tried && !yourName.trim()} />
        </Field>
        <div className="mt-auto pt-4">
          <Button type="submit" className="w-full" busy={busy}>
            Join gym
          </Button>
        </div>
      </form>
    </StepFrame>
  )
}
