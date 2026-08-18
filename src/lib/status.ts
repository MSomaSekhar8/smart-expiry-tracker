import type { ItemStatus } from './types'

export const STATUS_META: Record<
  ItemStatus,
  { label: string; icon: 'check' | 'clock' | 'alert'; tint: string; text: string; bg: string; border: string }
> = {
  SAFE: {
    label: 'Safe',
    icon: 'check',
    tint: 'text-emerald-600 dark:text-emerald-400',
    text: 'text-emerald-700 dark:text-emerald-300',
    bg: 'bg-emerald-50 dark:bg-emerald-500/10',
    border: 'border-emerald-200 dark:border-emerald-500/30',
  },
  EXPIRING: {
    label: 'Expiring soon',
    icon: 'clock',
    tint: 'text-amber-600 dark:text-amber-400',
    text: 'text-amber-700 dark:text-amber-300',
    bg: 'bg-amber-50 dark:bg-amber-500/10',
    border: 'border-amber-200 dark:border-amber-500/30',
  },
  EXPIRED: {
    label: 'Expired',
    icon: 'alert',
    tint: 'text-rose-600 dark:text-rose-400',
    text: 'text-rose-700 dark:text-rose-300',
    bg: 'bg-rose-50 dark:bg-rose-500/10',
    border: 'border-rose-200 dark:border-rose-500/30',
  },
}

export function statusMeta(status: ItemStatus) {
  return STATUS_META[status]
}

export const STATUS_ORDER: ItemStatus[] = ['EXPIRING', 'EXPIRED', 'SAFE']

export function formatDaysLeft(days: number): string {
  if (days < 0) return 'Expired'
  if (days === 0) return 'Expires today'
  if (days === 1) return 'Expires tomorrow'
  return `In ${days} days`
}