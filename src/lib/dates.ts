import { format, isSameDay, isToday, isTomorrow, parseISO } from 'date-fns'

export function formatDate(value: string | null | undefined): string {
  if (!value) return '—'
  const date = parseISO(value)
  if (isToday(date)) return 'Today'
  if (isTomorrow(date)) return 'Tomorrow'
  return format(date, 'MMM d, yyyy')
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '—'
  return format(parseISO(value), 'MMM d, h:mm a')
}

export function isExpiryToday(value: string | null | undefined): boolean {
  return !!value && isToday(parseISO(value))
}

export function sameDay(a: string | null | undefined, b: string | null | undefined): boolean {
  if (!a || !b) return false
  return isSameDay(parseISO(a), parseISO(b))
}

export function toISODate(value: string | null | undefined): string | null {
  if (!value) return null
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return null
  return format(d, 'yyyy-MM-dd')
}