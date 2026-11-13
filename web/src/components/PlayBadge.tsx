import { PLAY_STORE_URL } from '@/lib/location'
import { cx } from './ui'

/** Play's four-colour triangle, drawn as its four facets. */
function PlayMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} aria-hidden>
      <path fill="#4285F4" d="M3.6 2.2 13.4 12l-9.8 9.8c-.4-.2-.6-.6-.6-1.1V3.3c0-.5.2-.9.6-1.1z" />
      <path fill="#34A853" d="M3.6 2.2c.4-.2.9-.2 1.4.1l11.4 6.6-3 3.1z" />
      <path fill="#EA4335" d="m13.4 12 3 3.1L5 21.7c-.5.3-1 .3-1.4.1z" />
      <path fill="#FBBC04" d="m16.4 8.9 3.6 2.1c.8.5.8 1.5 0 2l-3.6 2.1-3-3.1z" />
    </svg>
  )
}

/**
 * "Get it on Google Play", drawn the way Play's own badge is: the mark, then the words on two lines, on black.
 * [small] is the compact one for under a form.
 */
export function PlayBadge({ small, className }: { small?: boolean; className?: string }) {
  // An iPhone can't install it, so the badge would only be a dead end there.
  if (isIOS()) return null
  return (
    <a
      href={PLAY_STORE_URL}
      target="_blank"
      rel="noreferrer"
      aria-label="Get it on Google Play"
      className={cx(
        'inline-flex items-center rounded-[10px] bg-black text-white ring-1 ring-white/25 transition hover:bg-[#1f1f1f] active:scale-[0.98]',
        small ? 'h-10 gap-2 px-3' : 'h-[52px] gap-3 px-4',
        className,
      )}
    >
      <PlayMark className={small ? 'size-5' : 'size-7'} />
      <span className="flex flex-col items-start leading-none">
        <span className={cx('font-medium uppercase tracking-wide', small ? 'text-[9px]' : 'text-[10px]')}>Get it on</span>
        <span className={cx('font-semibold', small ? 'mt-0.5 text-[15px]' : 'mt-1 text-[20px]')}>Google Play</span>
      </span>
    </a>
  )
}

/** iPhones and iPads, including iPads that report themselves as a Mac. */
export function isIOS(): boolean {
  return /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.userAgent.includes('Macintosh') && navigator.maxTouchPoints > 1)
}
