import { createContext, useContext, useState, type ReactNode } from 'react'
import { MessageCircle } from 'lucide-react'
import { toast } from 'sonner'
import { formatPhone } from '@/lib/format'
import { getPref, setPref, missYouLogKey, reminderLogKey } from '@/lib/prefs'
import { KIND_LABEL, KIND_TITLE, waLink, type Message, type MessageKind } from '@/lib/whatsapp'
import { useGym } from '@/data/session'
import { Button, Sheet, Segmented } from './ui'

type Offer = (messages: (Message | null)[]) => void

const OfferContext = createContext<Offer>(() => {})

/** Offers a message on WhatsApp: after adding a member, a sale, a payment, or from the member's page. */
export function useOfferWhatsApp(): Offer {
  return useContext(OfferContext)
}

export function WhatsAppProvider({ children }: { children: ReactNode }) {
  const [messages, setMessages] = useState<Message[]>([])
  const offer: Offer = (list) => {
    const usable = list.filter((m): m is Message => m !== null)
    if (usable.length) setMessages(usable)
  }
  return (
    <OfferContext.Provider value={offer}>
      {children}
      <WhatsAppSheet messages={messages} onClose={() => setMessages([])} />
    </OfferContext.Provider>
  )
}

function WhatsAppSheet({ messages, onClose }: { messages: Message[]; onClose: () => void }) {
  const [kind, setKind] = useState<MessageKind | null>(null)
  const { membership } = useGym()
  const message = messages.find((m) => m.kind === kind) ?? messages[0]
  const close = () => {
    setKind(null)
    onClose()
  }
  return (
    <Sheet open={messages.length > 0} onClose={close} title={message ? KIND_TITLE[message.kind] : undefined}>
      {message ? (
        <div>
          <p className="text-caption text-muted">
            To {message.memberName} · {formatPhone(message.number.slice(2))}
          </p>
          {messages.length > 1 ? (
            <Segmented className="mt-4" options={messages.map((m) => ({ value: m.kind, label: KIND_LABEL[m.kind] }))} value={message.kind} onChange={setKind} />
          ) : null}
          <div className="mt-4 max-h-[300px] overflow-y-auto whitespace-pre-wrap rounded-[16px] rounded-tl-[4px] bg-[var(--wa-bubble)] p-4 text-[15px] leading-[21px] text-[var(--wa-ink)]">
            {message.text}
          </div>
          <p className="mt-3 text-caption text-muted">WhatsApp opens with this message typed in. Tap send there.</p>
          <div className="mt-5 flex flex-col gap-2 pb-1">
            <Button
              icon={MessageCircle}
              onClick={() => {
                if (!openLink(waLink(message))) window.location.href = waLink(message)
                logSent(membership.gym.id, message)
                close()
              }}
            >
              Open WhatsApp
            </Button>
            <Button kind="ghost" onClick={close}>
              Not now
            </Button>
          </div>
        </div>
      ) : null}
    </Sheet>
  )
}

/** Opening the link counts as sent, as on the phone. Reminders and "we miss you"s are logged for their cooldowns. */
function logSent(gymId: string, message: Message) {
  const key = message.kind === 'REMINDER' ? reminderLogKey(gymId) : message.kind === 'MISS_YOU' ? missYouLogKey(gymId) : null
  if (!key) return
  const log = getPref<Record<string, number>>(key, {})
  setPref(key, { ...log, [message.memberId]: Date.now() })
}

/** Sends one straight away, without the sheet (the reminders run). */
export function openWhatsApp(message: Message, gymId: string) {
  if (!openLink(waLink(message))) {
    toast('Couldn’t open WhatsApp')
    return false
  }
  logSent(gymId, message)
  return true
}

/** A new tab that can't reach back into this one; false if the browser blocked it. */
function openLink(url: string): boolean {
  const tab = window.open(url, '_blank')
  if (!tab) return false
  tab.opener = null
  return true
}
