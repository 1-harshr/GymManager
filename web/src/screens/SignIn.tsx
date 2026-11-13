import { useEffect, useRef, useState } from 'react'
import { Check, ChevronLeft } from 'lucide-react'
import { supabase, googleSignInEnabled, phoneSignInEnabled } from '@/lib/supabase'
import { formatPhone, localDigits } from '@/lib/format'
import { Button, IconButton, LinkButton, cx } from '@/components/ui'
import { BrandMark } from '@/components/Brand'
import { GoogleG } from '@/components/GoogleG'
import { isIOS, PlayBadge } from '@/components/PlayBadge'
import { appPath, appUrl, PLAY_QR_URL, PRIVACY_URL } from '@/lib/location'

const CODE_DIGITS = 6
const RESEND_WAIT = 30

/** What Supabase Auth's errors mean for the person signing in. */
function explainAuth(error: { message?: string; code?: string; status?: number } | null): string {
  const code = error?.code ?? ''
  if (code === 'otp_expired' || /invalid|expired/i.test(error?.message ?? '')) return 'That code didn’t work. Check it, or send a new one.'
  if (code.startsWith('over_') || error?.status === 429) return 'Too many tries. Wait a minute and try again.'
  if (code === 'sms_send_failed') return 'Couldn’t send a code to that number. Check it and try again.'
  if (/fetch|network/i.test(error?.message ?? '')) return 'No internet connection. Check it and try again.'
  return 'Couldn’t sign in. Try again.'
}

/**
 * The first page until someone signs in: Google where it's set up, or a mobile number and then the code
 * texted to it. On wide screens the brand gets its own panel beside the form.
 */
export function SignIn() {
  const [phone, setPhone] = useState('')
  const [step, setStep] = useState<'phone' | 'code'>('phone')
  const [busy, setBusy] = useState<false | 'phone' | 'code' | 'google'>(false)
  const [error, setError] = useState<string | null>(null)
  const [resendIn, setResendIn] = useState(0)

  useEffect(() => {
    if (resendIn <= 0) return
    const t = setTimeout(() => setResendIn((s) => s - 1), 1000)
    return () => clearTimeout(t)
  }, [resendIn])

  const fullPhone = '+91' + phone

  async function sendCode() {
    if (phone.length !== 10 || busy) return
    setBusy('phone')
    setError(null)
    const { error } = await supabase.auth.signInWithOtp({ phone: fullPhone })
    setBusy(false)
    if (error) return setError(explainAuth(error))
    setStep('code')
    setResendIn(RESEND_WAIT)
  }

  async function google() {
    setBusy('google')
    setError(null)
    const { error } = await supabase.auth.signInWithOAuth({
      provider: 'google',
      options: { redirectTo: appUrl(), queryParams: { prompt: 'select_account' } },
    })
    if (error) {
      setBusy(false)
      setError('Couldn’t sign in with that account. Try again, or use your mobile number.')
    }
  }

  return (
    <div className="grid min-h-dvh lg:grid-cols-[1.05fr_1fr]">
      <BrandPanel />
      <main className="flex min-h-dvh flex-col px-4 pb-[max(1rem,env(safe-area-inset-bottom))] pt-[max(2rem,env(safe-area-inset-top))] sm:px-10 lg:justify-center lg:px-16">
        <div className="mx-auto flex w-full max-w-md flex-1 flex-col lg:flex-none">
          {step === 'phone' ? (
            <div key="phone" className="flex flex-1 flex-col animate-rise">
              <div className="lg:hidden">
                <BrandMark />
              </div>
              <h1 className="mt-14 whitespace-pre-line text-headline lg:mt-0">{'Your whole gym,\non every phone'}</h1>
              <p className="mt-2 text-body text-muted">Members, renewals and payments stay in step on every phone you and your staff use.</p>
              {appPath() === '/delete-account' ? (
                <p className="mt-4 rounded-field bg-sunken px-4 py-3 text-body">Sign in to delete your account. You’ll see what’s deleted before anything happens.</p>
              ) : null}

              {googleSignInEnabled ? (
                <>
                  <button
                    onClick={google}
                    disabled={!!busy}
                    className="mt-8 flex h-[52px] w-full items-center justify-center gap-3 rounded-full border border-line bg-surface text-body-strong transition hover:bg-sunken active:scale-[0.99] disabled:opacity-50"
                  >
                    <GoogleG className="size-5" />
                    {busy === 'google' ? 'Signing in…' : 'Continue with Google'}
                  </button>
                  {phoneSignInEnabled ? (
                    <div className="my-6 flex items-center gap-3 text-caption text-muted">
                      <span className="h-px flex-1 bg-line" />
                      or use your mobile number
                      <span className="h-px flex-1 bg-line" />
                    </div>
                  ) : error ? (
                    <p className="mt-3 text-caption text-due">{error}</p>
                  ) : null}
                </>
              ) : (
                <div className="mt-8" />
              )}

              {phoneSignInEnabled ? (
                <form
                  className="flex flex-1 flex-col"
                  onSubmit={(e) => {
                    e.preventDefault()
                    sendCode()
                  }}
                >
                  <label className="mb-2 text-body-strong" htmlFor="phone">
                    Mobile number
                  </label>
                  <div
                    className={cx(
                      'flex h-16 items-center rounded-field bg-sunken ring-ink transition focus-within:ring-2',
                      error && 'ring-2 ring-due',
                    )}
                  >
                    <span className="flex items-center gap-1.5 pl-5 pr-3">
                      <span className="text-title" aria-hidden>
                        🇮🇳
                      </span>
                      <span className="font-display text-[28px] font-bold text-muted">+91</span>
                    </span>
                    <span className="h-7 w-px bg-line" />
                    <input
                      id="phone"
                      inputMode="tel"
                      autoComplete="tel-national"
                      autoFocus
                      placeholder="98765 43210"
                      value={phone.length > 5 ? `${phone.slice(0, 5)} ${phone.slice(5)}` : phone}
                      onChange={(e) => {
                        setPhone(localDigits(e.target.value))
                        setError(null)
                      }}
                      className="h-full min-w-0 flex-1 bg-transparent px-3 font-display text-[28px] font-bold tnum outline-none placeholder:text-muted/45"
                    />
                    <Check
                      className={cx('mr-5 size-[22px] text-active transition', phone.length === 10 ? 'scale-100 opacity-100' : 'scale-50 opacity-0')}
                    />
                  </div>
                  <p className={cx('mt-2 text-caption', error ? 'text-due' : 'text-muted')}>{error ?? 'We’ll text a 6-digit code to this number.'}</p>
                  <div className="mt-auto pt-8 lg:mt-8">
                    <Button type="submit" className="w-full" disabled={phone.length !== 10} busy={busy === 'phone'}>
                      {busy === 'phone' ? 'Sending…' : 'Get code'}
                    </Button>
                  </div>
                </form>
              ) : null}
            </div>
          ) : (
            <CodeStep
              phone={fullPhone}
              resendIn={resendIn}
              onBack={() => {
                setStep('phone')
                setError(null)
              }}
              onResend={async () => {
                const { error } = await supabase.auth.signInWithOtp({ phone: fullPhone })
                if (error) return explainAuth(error)
                setResendIn(RESEND_WAIT)
                return null
              }}
            />
          )}
        </div>
        {/* On a phone the clay panel is hidden, so the app gets its own quiet line by the policy. */}
        <div className="mx-auto mt-6 flex flex-col items-center gap-3">
          <PlayBadge small className="lg:hidden" />
          <a href={PRIVACY_URL} className="text-caption text-muted underline underline-offset-2">
            Privacy policy and terms
          </a>
        </div>
      </main>
    </div>
  )
}

function CodeStep({
  phone,
  resendIn,
  onBack,
  onResend,
}: {
  phone: string
  resendIn: number
  onBack: () => void
  onResend: () => Promise<string | null>
}) {
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [shakes, setShakes] = useState(0)
  const input = useRef<HTMLInputElement>(null)

  async function verify(value: string) {
    if (value.length !== CODE_DIGITS || busy) return
    setBusy(true)
    setError(null)
    const { error } = await supabase.auth.verifyOtp({ phone, token: value, type: 'sms' })
    // On success the session changes and this page goes away.
    if (error) {
      setBusy(false)
      setCode('')
      setError(explainAuth(error))
      setShakes((n) => n + 1)
      input.current?.focus()
    }
  }

  return (
    <div className="flex flex-1 flex-col animate-rise">
      <div>
        <IconButton icon={ChevronLeft} label="Change number" onClick={onBack} />
      </div>
      <h1 className="mt-8 text-headline">Enter the code</h1>
      <p className="mt-2 text-body text-muted">Sent to {formatPhone(phone)}</p>
      <div key={shakes} className={cx('relative mt-8', shakes > 0 && 'animate-shake')}>
        <div className="grid grid-cols-6 gap-2" aria-hidden>
          {Array.from({ length: CODE_DIGITS }, (_, i) => {
            const active = i === code.length && !busy
            return (
              <div
                key={i}
                className={cx(
                  'flex h-16 items-center justify-center rounded-field bg-sunken text-stat transition',
                  error ? 'ring-2 ring-due' : active && 'ring-2 ring-ink',
                )}
              >
                {code[i] ?? (active ? <span className="h-[26px] w-0.5 animate-pulse bg-ink" /> : null)}
              </div>
            )
          })}
        </div>
        <input
          ref={input}
          aria-label="6-digit code"
          inputMode="numeric"
          autoComplete="one-time-code"
          autoFocus
          maxLength={CODE_DIGITS}
          value={code}
          onChange={(e) => {
            if (busy) return
            const next = e.target.value.replace(/\D/g, '').slice(0, CODE_DIGITS)
            setCode(next)
            setError(null)
            if (next.length === CODE_DIGITS) verify(next)
          }}
          className="absolute inset-0 h-full w-full cursor-text opacity-0"
        />
      </div>
      <p className={cx('mt-2 min-h-[18px] text-caption', error ? 'text-due' : 'text-muted')}>{error ?? (busy ? 'Checking…' : '')}</p>
      <div className="mt-6 flex items-center gap-1 text-body text-muted">
        Didn’t get it?
        {resendIn > 0 ? (
          <span className="px-2 font-semibold tnum">Send again in 0:{String(resendIn).padStart(2, '0')}</span>
        ) : (
          <LinkButton
            onClick={async () => {
              setCode('')
              setError(await onResend())
            }}
          >
            Send again
          </LinkButton>
        )}
      </div>
      <div className="mt-auto pt-8 lg:mt-8">
        <Button className="w-full" disabled={code.length !== CODE_DIGITS} busy={busy} onClick={() => verify(code)}>
          {busy ? 'Checking…' : 'Verify'}
        </Button>
      </div>
    </div>
  )
}

/** The wide-screen panel: the brand on clay, with a glimpse of what the app keeps. */
function BrandPanel() {
  return (
    <aside className="relative hidden overflow-hidden bg-[#B0441B] p-12 text-white lg:flex lg:flex-col">
      <BrandMark onClay />
      <div className="mt-auto max-w-md">
        <p className="font-display text-[64px] font-bold leading-[60px]">Renewals, dues and members. Sorted.</p>
        <p className="mt-4 text-[17px] leading-6 text-white/80">The same gym on the web, your phone and your staff’s, updated the moment anyone records a payment.</p>
        {/* A laptop can't install the app, so the code takes it straight to the phone in their pocket. */}
        {isIOS() ? null : (
          <div className="mt-8 flex items-center gap-4">
            <div className="rounded-[14px] bg-white p-2.5 shadow-xl shadow-black/20">
              <img src={PLAY_QR_URL} alt="" className="size-[84px]" />
            </div>
            <div className="flex flex-col items-start gap-2">
              <p className="text-[15px] leading-5 text-white/80">Scan to get it on your phone</p>
              <PlayBadge />
            </div>
          </div>
        )}
      </div>
      <div className="pointer-events-none absolute right-12 top-32 w-[380px] rotate-[-6deg] space-y-3 xl:right-20">
        {[
          ['Arjun Singh', 'ends 16 Nov', 'Active', '#E0F2E8', '#1C7347'],
          ['Nisha Gupta', 'ends 11 Nov', '5 days left', '#FAEBD4', '#995600'],
          ['Deepak Oraon', 'ended 17 Oct', 'Expired', '#EBEBEE', '#5F5F66'],
        ].map(([name, sub, badge, bg, fg]) => (
          <div key={name} className="flex items-center gap-3 rounded-[20px] bg-white p-4 text-[#121213] shadow-2xl shadow-black/20">
            <div className="flex size-11 items-center justify-center rounded-full bg-[#E9E9E5] font-display text-[18px] font-bold">
              {name.split(' ').map((p) => p[0]).join('')}
            </div>
            <div className="flex-1">
              <p className="text-[17px] font-semibold leading-[22px]">{name}</p>
              <p className="text-[13px] text-[#5C5C61]">{sub}</p>
            </div>
            <span className="rounded-full px-3 py-1 text-[13px] font-semibold" style={{ background: bg, color: fg }}>
              {badge}
            </span>
          </div>
        ))}
      </div>
    </aside>
  )
}
