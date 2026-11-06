import type { IsoDate } from '@/lib/dates'
import { statusShortLabel, statusTone, type MemberView } from '@/lib/domain'
import { Badge } from './bits'

export function StatusBadge({ member, today }: { member: Pick<MemberView, 'status' | 'latest_end_date' | 'is_archived'>; today: IsoDate }) {
  if (member.is_archived) return <Badge tone="expired">Archived</Badge>
  return <Badge tone={statusTone(member.status)}>{statusShortLabel(member.status, member.latest_end_date, today)}</Badge>
}
