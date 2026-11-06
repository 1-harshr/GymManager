/** Rows as the database has them; amounts are whole rupees. */
export type StaffRole = 'owner' | 'staff'
export type Gender = 'MALE' | 'FEMALE' | 'OTHER'
export type DurationUnit = 'DAYS' | 'MONTHS'
export type PaymentMode = 'CASH' | 'UPI' | 'OTHER'
export type MemberStatus = 'ACTIVE' | 'EXPIRING_SOON' | 'EXPIRED' | 'NONE'

export interface Gym {
  id: string
  name: string
  address: string | null
  logo_path: string | null
  timezone: string
}

/** The signed-in person's place in their gym. */
export interface Membership {
  gym: Gym
  role: StaffRole
  name: string | null
}

export interface Plan {
  id: string
  name: string
  duration_value: number
  duration_unit: DurationUnit
  default_price: number
  is_archived: boolean
}

export interface Member {
  id: string
  name: string
  phone: string | null
  gender: Gender | null
  date_of_birth: string | null
  photo_path: string | null
  notes: string | null
  joined_on: string
  is_archived: boolean
  created_at: string
}

export interface MemberSummary {
  member_id: string
  name: string
  phone: string | null
  photo_path: string | null
  is_archived: boolean
  latest_end_date: string | null
  amount_due: number
  status: MemberStatus
}

export interface Payment {
  id: string
  membership_id: string
  amount: number
  mode: PaymentMode
  paid_on: string
  note: string | null
  created_at: string
}

export interface MembershipRecord {
  id: string
  member_id: string
  plan_id: string | null
  plan_name: string
  start_date: string
  end_date: string
  price: number
  discount: number
  notes: string | null
  created_at: string
  payments: Payment[]
}

export interface StaffMember {
  user_id: string
  name: string | null
  role: StaffRole
  joined_at: string
}

export interface Invite {
  code: string
  role: StaffRole
  expires_at: string
}

export interface DashboardStats {
  active_count: number
  expiring_soon_count: number
  expired_count: number
  members_with_dues_count: number
  total_due: number
  collected_this_month: number
}
