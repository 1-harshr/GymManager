import { Dumbbell } from 'lucide-react'
import { cx } from './ui'

/** The launch screen's clay tile and dumbbell beside the name. */
export function BrandMark({ onClay, compact }: { onClay?: boolean; compact?: boolean }) {
  return (
    <div className="flex items-center gap-3">
      <div
        className={cx(
          'flex items-center justify-center rounded-[11px]',
          compact ? 'size-9 rounded-[9px]' : 'size-12',
          onClay ? 'bg-white/15 ring-1 ring-white/25' : 'bg-[#B0441B]',
        )}
      >
        <Dumbbell className={cx('text-white', compact ? 'size-5' : 'size-7')} strokeWidth={2} />
      </div>
      <span className={cx('font-display font-bold', compact ? 'text-[24px] leading-7' : 'text-screen-title')}>My Gym</span>
    </div>
  )
}
