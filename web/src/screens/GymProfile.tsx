import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { ImageUp } from 'lucide-react'
import { toast } from 'sonner'
import { useQueryClient } from '@tanstack/react-query'
import { supabase, GYM_LOGOS } from '@/lib/supabase'
import { useGym } from '@/data/session'
import { useWrite } from '@/data/queries'
import { explain } from '@/data/errors'
import { PageHeader } from '@/components/Layout'
import { GymLogo } from '@/components/Media'
import { useDiscardGuard } from '@/components/bits'
import { Button, Card, Field, LinkButton, TextArea, TextInput } from '@/components/ui'
import { uploadPhoto } from './MemberEdit'

/** The gym's name, address and logo. Owners only; staff are sent back to Settings. */
export function GymProfile() {
  const navigate = useNavigate()
  const client = useQueryClient()
  const { membership, isOwner } = useGym()
  const gym = membership.gym
  const [name, setName] = useState(gym.name)
  const [address, setAddress] = useState(gym.address ?? '')
  const [logo, setLogo] = useState<File | null | 'removed'>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [tried, setTried] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const input = useRef<HTMLInputElement>(null)
  const guard = useDiscardGuard(logo !== null || name !== gym.name || address !== (gym.address ?? ''))

  useEffect(() => {
    if (!isOwner) navigate('/settings', { replace: true })
  }, [isOwner, navigate])
  useEffect(() => {
    if (!(logo instanceof File)) return setPreview(null)
    const url = URL.createObjectURL(logo)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [logo])

  const save = useWrite(async () => {
    const logoPath = logo === 'removed' ? null : logo ? await uploadPhoto(GYM_LOGOS, gym.id, 'logo', logo) : gym.logo_path
    const { error } = await supabase.from('gyms').update({ name: name.trim(), address: address.trim() || null, logo_path: logoPath }).eq('id', gym.id)
    if (error) throw error
    await client.invalidateQueries({ queryKey: ['membership'] })
  })

  return (
    <div className="max-w-2xl">
      <PageHeader back="/settings" title="Gym details" />
      <Card className="p-6">
        <form
          onSubmit={(e) => {
            e.preventDefault()
            setTried(true)
            if (!name.trim()) return
            save.mutate(undefined, {
              onSuccess: () => {
                guard.release()
                toast('Gym details saved')
                navigate('/settings')
              },
              onError: (e) => setError(explain(e)),
            })
          }}
          className="space-y-6"
        >
          <div className="flex items-center gap-5">
            {preview ? (
              <img src={preview} alt="" className="size-[88px] rounded-full bg-white object-contain" />
            ) : (
              <GymLogo gym={{ name: name || gym.name, logo_path: logo === 'removed' ? null : gym.logo_path }} size={88} />
            )}
            <div className="flex flex-col items-start gap-1">
              <Button type="button" kind="secondary" size="sm" icon={ImageUp} onClick={() => input.current?.click()}>
                {gym.logo_path || logo instanceof File ? 'Change logo' : 'Choose logo'}
              </Button>
              {(gym.logo_path && logo !== 'removed') || logo instanceof File ? (
                <LinkButton type="button" onClick={() => setLogo(gym.logo_path ? 'removed' : null)}>
                  Remove logo
                </LinkButton>
              ) : null}
              <p className="text-caption text-muted">Without a logo, the gym’s initials are shown.</p>
              <input
                ref={input}
                type="file"
                accept="image/png,image/jpeg,image/webp"
                hidden
                onChange={(e) => {
                  const file = e.target.files?.[0]
                  if (file) setLogo(file)
                  e.target.value = ''
                }}
              />
            </div>
          </div>
          <Field label="Gym name" error={tried && !name.trim() ? 'Gym name is required' : null}>
            <TextInput value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Iron House Fitness" invalid={tried && !name.trim()} />
          </Field>
          <Field label="Address" hint="Optional">
            <TextArea value={address} onChange={(e) => setAddress(e.target.value)} placeholder="Shown in Settings" className="min-h-20" />
          </Field>
          {error ? <p className="text-caption text-due">{error}</p> : null}
          <div className="flex justify-end">
            <Button type="submit" busy={save.isPending}>
              {save.isPending ? 'Saving…' : 'Save changes'}
            </Button>
          </div>
        </form>
      </Card>
      {guard.sheet}
    </div>
  )
}
