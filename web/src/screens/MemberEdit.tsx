import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { Camera, UserX } from 'lucide-react'
import { toast } from 'sonner'
import { supabase, MEMBER_PHOTOS } from '@/lib/supabase'
import { addDays, addMonths } from '@/lib/dates'
import { GENDER_LABEL } from '@/lib/domain'
import type { Gender, Member } from '@/lib/types'
import { welcome } from '@/lib/whatsapp'
import { useGym } from '@/data/session'
import { useMember, useToday, useWrite } from '@/data/queries'
import { explain } from '@/data/errors'
import { PageHeader } from '@/components/Layout'
import { Avatar } from '@/components/Media'
import { DateInput, SegmentedTrack, useDiscardGuard } from '@/components/bits'
import { useOfferWhatsApp } from '@/components/WhatsApp'
import { Button, EmptyState, Field, LinkButton, Skeleton, TextArea, TextInput } from '@/components/ui'

const GENDERS = (['MALE', 'FEMALE', 'OTHER'] as Gender[]).map((g) => ({ value: g, label: GENDER_LABEL[g] }))

interface Form {
  name: string
  phone: string
  gender: Gender | null
  dateOfBirth: string
  joinedOn: string
  notes: string
}

/** Uploads a picked photo to the gym's folder; the path is what's kept on the member. */
export async function uploadPhoto(bucket: string, gymId: string, owner: string, file: File): Promise<string> {
  const ext = (file.name.split('.').pop() || 'jpg').toLowerCase()
  const path = `${gymId}/${owner}-${Date.now()}.${ext}`
  const { error } = await supabase.storage.from(bucket).upload(path, await shrink(file), { upsert: true, contentType: file.type || 'image/jpeg' })
  if (error) throw error
  return path
}

/** Photos from phones are huge; 1024px on the long side is plenty for an avatar. */
async function shrink(file: File): Promise<Blob> {
  if (!file.type.startsWith('image/') || file.type === 'image/heic') return file
  try {
    const bitmap = await createImageBitmap(file)
    const scale = Math.min(1, 1024 / Math.max(bitmap.width, bitmap.height))
    if (scale === 1 && file.size < 1_000_000) return file
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bitmap.width * scale)
    canvas.height = Math.round(bitmap.height * scale)
    canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
    return await new Promise((resolve) => canvas.toBlob((b) => resolve(b ?? file), file.type === 'image/png' ? 'image/png' : 'image/jpeg', 0.85))
  } catch {
    return file
  }
}

/** A new member, or changes to one. */
export function MemberEdit() {
  const { id } = useParams()
  const existing = useMember(id)
  if (id && existing.data === undefined) {
    return (
      <div className="max-w-3xl">
        <PageHeader back title="Edit member" />
        <Skeleton className="h-96" />
      </div>
    )
  }
  if (id && existing.data === null) {
    return (
      <div className="max-w-3xl">
        <PageHeader back title="Edit member" />
        <EmptyState icon={UserX} title="This member no longer exists" body="They may have been deleted." />
      </div>
    )
  }
  return <MemberForm key={id ?? 'new'} member={existing.data ?? null} />
}

function MemberForm({ member }: { member: Member | null }) {
  const navigate = useNavigate()
  const today = useToday()
  const { membership: gym } = useGym()
  const offer = useOfferWhatsApp()
  const initial: Form = {
    name: member?.name ?? '',
    phone: member?.phone ?? '',
    gender: member?.gender ?? null,
    dateOfBirth: member?.date_of_birth ?? '',
    joinedOn: member?.joined_on ?? today,
    notes: member?.notes ?? '',
  }
  const [form, setForm] = useState<Form>(initial)
  const [photo, setPhoto] = useState<File | null | 'removed'>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [tried, setTried] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const dirty = photo !== null || JSON.stringify(form) !== JSON.stringify(initial)
  const guard = useDiscardGuard(dirty)

  useEffect(() => {
    if (!(photo instanceof File)) return setPreview(null)
    const url = URL.createObjectURL(photo)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [photo])

  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm((f) => ({ ...f, [key]: value }))

  const save = useWrite(async () => {
    const row = {
      name: form.name.trim().replace(/\s+/g, ' '),
      phone: form.phone.trim() || null,
      gender: form.gender,
      date_of_birth: form.dateOfBirth || null,
      notes: form.notes.trim() || null,
      joined_on: form.joinedOn || today,
    }
    if (member) {
      const photoPath = photo === 'removed' ? null : photo ? await uploadPhoto(MEMBER_PHOTOS, gym.gym.id, member.id, photo) : member.photo_path
      const { error } = await supabase.from('members').update({ ...row, photo_path: photoPath }).eq('id', member.id)
      if (error) throw error
      return { ...member, ...row, photo_path: photoPath }
    }
    const { data, error } = await supabase.from('members').insert({ ...row, gym_id: gym.gym.id, is_archived: false }).select('*').single<Member>()
    if (error) throw error
    if (photo instanceof File) {
      const path = await uploadPhoto(MEMBER_PHOTOS, gym.gym.id, data.id, photo)
      await supabase.from('members').update({ photo_path: path }).eq('id', data.id)
    }
    return data
  })

  function submit() {
    setTried(true)
    if (!form.name.trim()) return
    setError(null)
    save.mutate(undefined, {
      onSuccess: (saved) => {
        guard.release()
        if (member) {
          toast('Changes saved')
          navigate(-1)
        } else {
          toast('Member added')
          navigate(`/members/${saved.id}`, { replace: true })
          offer([welcome(saved, gym.gym.name)])
        }
      },
      onError: (e) => setError(explain(e)),
    })
  }

  const hasPhoto = photo instanceof File || (photo === null && !!member?.photo_path)
  return (
    <div className="max-w-3xl">
      <PageHeader back title={member ? 'Edit member' : 'New member'} />
      <form
        onSubmit={(e) => {
          e.preventDefault()
          submit()
        }}
        className="animate-rise"
      >
        <div className="flex items-center gap-5">
          {preview ? (
            <img src={preview} alt="" className="size-[88px] rounded-full object-cover" />
          ) : (
            <Avatar name={form.name.trim() || '?'} photoPath={photo === 'removed' ? null : member?.photo_path} size={88} />
          )}
          <div className="flex flex-col items-start gap-1">
            <Button type="button" kind="secondary" size="sm" icon={Camera} onClick={() => fileInput.current?.click()}>
              {hasPhoto ? 'Change photo' : 'Add photo'}
            </Button>
            {hasPhoto ? (
              <LinkButton type="button" onClick={() => setPhoto(member?.photo_path ? 'removed' : null)}>
                Remove photo
              </LinkButton>
            ) : null}
            <input
              ref={fileInput}
              type="file"
              accept="image/*"
              capture="user"
              hidden
              onChange={(e) => {
                const file = e.target.files?.[0]
                if (file) setPhoto(file)
                e.target.value = ''
              }}
            />
          </div>
        </div>

        <div className="mt-8 grid gap-5 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <Field label="Name" error={tried && !form.name.trim() ? 'Name is required' : null}>
              <TextInput autoFocus={!member} value={form.name} onChange={(e) => set('name', e.target.value)} placeholder="Full name" autoCapitalize="words" invalid={tried && !form.name.trim()} />
            </Field>
          </div>
          <Field label="Phone" hint="Optional">
            <TextInput value={form.phone} onChange={(e) => set('phone', e.target.value)} placeholder="10-digit mobile number" inputMode="tel" autoComplete="off" />
          </Field>
          <Field label="Gender" hint="Optional">
            <SegmentedTrack options={GENDERS} value={form.gender} onChange={(g) => set('gender', g)} clearable />
          </Field>
          <Field label="Date of birth" hint="Optional">
            <DateInput value={form.dateOfBirth} onChange={(v) => set('dateOfBirth', v)} max={addDays(today, -1)} min={addMonths(today, -100 * 12)} />
          </Field>
          <Field label="Joined on">
            <DateInput value={form.joinedOn} onChange={(v) => set('joinedOn', v)} max={today} />
          </Field>
          <div className="sm:col-span-2">
            <Field label="Notes" hint="Optional">
              <TextArea value={form.notes} onChange={(e) => set('notes', e.target.value)} placeholder="Injuries, goals, anything to remember" />
            </Field>
          </div>
        </div>
        {error ? <p className="mt-4 text-caption text-due">{error}</p> : null}
        <div className="mt-8 flex gap-3">
          <Button type="submit" className="min-w-48" busy={save.isPending}>
            {save.isPending ? 'Saving…' : member ? 'Save changes' : 'Add member'}
          </Button>
        </div>
      </form>
      {guard.sheet}
    </div>
  )
}
