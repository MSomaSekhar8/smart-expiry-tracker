export type ItemStatus = 'SAFE' | 'EXPIRING' | 'EXPIRED'

export interface Category {
  id: string
  name: string
  defaultShelfLifeDays: number
  warningThresholdDays: number
}

export interface ItemWithStatus {
  id: string
  ownerId: string
  name: string
  barcode: string | null
  categoryId: string
  category: string
  quantity: number
  unit: string
  purchaseDate: string | null
  expiryDate: string | null
  shelfLifeDays: number | null
  defaultShelfLifeDays: number
  warningThresholdDays: number
  notes: string | null
  status: ItemStatus
  daysUntilExpiry: number
  createdAt: string
  updatedAt: string
}

export interface ItemInput {
  name: string
  barcode?: string | null
  categoryId: string
  quantity?: number | null
  unit?: string
  purchaseDate?: string | null
  expiryDate?: string | null
  shelfLifeDays?: number | null
  notes?: string | null
}

export type UserRole = 'USER' | 'ADMIN'

export interface AuthUser {
  id: string
  email: string
  displayName: string | null
  role: UserRole
}

export interface TokenPair {
  accessToken: string
  refreshToken: string
  user: AuthUser
}

export interface WasteLogEntry {
  id: string
  userId: string
  itemId: string | null
  itemName: string | null
  quantityWasted: number
  unit: string | null
  estimatedCostLost: number | null
  loggedAt: string
}

export interface BarcodeLookupResult {
  barcode: string
  name: string | null
  brand: string | null
  category: string | null
  cached: boolean
}

export interface MonthlyPoint {
  month: string
  totalItems: number
  wastedItems: number
  costLost: number
}

export interface MonthlyWasteResponse {
  months: MonthlyPoint[]
  totalCostLost: number
  totalWasted: number
}

export interface DigestResult {
  expiringSoonCount: number
  expiredCount: number
}

export interface ApiErrorBody {
  message?: string
}

export function apiErrorMessage(err: unknown): string {
  if (typeof err === 'object' && err !== null && 'response' in err) {
    const body = (err as { response?: { data?: ApiErrorBody } }).response?.data
    if (body?.message) return body.message
  }
  if (typeof err === 'object' && err !== null && 'message' in err) {
    const message = (err as { message?: string }).message
    if (message) return message
  }
  return 'Something went wrong. Please try again.'
}