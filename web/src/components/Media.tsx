import { useQuery } from '@tanstack/react-query'
import { supabase, GYM_LOGOS, MEMBER_PHOTOS } from '@/lib/supabase'
import { initials } from '@/lib/format'
import type { Gym } from '@/lib/types'
import { cx } from './ui'

/** A private file shown through a link that lasts an hour; refetched before it runs out. */
export function useSignedUrl(bucket: string, path: string | null | undefined) {
  return useQuery({
    queryKey: ['signed-url', bucket, path],
    enabled: !!path,
    staleTime: 50 * 60 * 1000,
    gcTime: 55 * 60 * 1000,
    queryFn: async () => {
      const { data, error } = await supabase.storage.from(bucket).createSignedUrl(path!, 3600)
      if (error) throw error
      return data.signedUrl
    },
  }).data
}

/** The member's photo, or their initials on a sunken disc. */
export function Avatar({ name, photoPath, size = 44, className }: { name: string; photoPath?: string | null; size?: number; className?: string }) {
  const url = useSignedUrl(MEMBER_PHOTOS, photoPath)
  return (
    <div
      className={cx('flex shrink-0 items-center justify-center overflow-hidden rounded-full bg-sunken font-display font-bold text-ink', className)}
      style={{ width: size, height: size, fontSize: Math.round(size * 0.4) }}
    >
      {url ? <img src={url} alt="" className="size-full object-cover" /> : initials(name)}
    </div>
  )
}

/** The gym's logo on white (logos are usually dark on light), or its initials on ink. */
export function GymLogo({ gym, size = 44 }: { gym: Pick<Gym, 'name' | 'logo_path'>; size?: number }) {
  const url = useSignedUrl(GYM_LOGOS, gym.logo_path)
  return (
    <div
      className={cx('flex shrink-0 items-center justify-center overflow-hidden rounded-full font-display font-bold', url ? 'bg-white' : 'bg-ink text-on-ink')}
      style={{ width: size, height: size, fontSize: Math.round(size * 0.4) }}
    >
      {url ? <img src={url} alt="" className="size-full object-contain" /> : initials(gym.name)}
    </div>
  )
}
