import { AlertTriangle, Check, Clock } from 'lucide-react'
import { Badge, type BadgeProps } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { formatDaysLeft, statusMeta } from '@/lib/status'
import type { ItemStatus } from '@/lib/types'

const ICONS = {
  check: Check,
  clock: Clock,
  alert: AlertTriangle,
}

const VARIANT_MAP: Record<ItemStatus, BadgeProps['variant']> = {
  SAFE: 'emerald',
  EXPIRING: 'amber',
  EXPIRED: 'rose',
}

interface StatusBadgeProps {
  status: ItemStatus
  daysUntilExpiry?: number
  className?: string
}

export function StatusBadge({ status, daysUntilExpiry, className }: StatusBadgeProps) {
  const meta = statusMeta(status)
  const Icon = ICONS[meta.icon]
  const showDays = status !== 'SAFE' && daysUntilExpiry !== undefined && daysUntilExpiry >= 0
  return (
    <Badge variant={VARIANT_MAP[status]} className={cn('gap-1.5 font-medium', className)}>
      <Icon className="h-3 w-3" aria-hidden />
      <span>{meta.label}</span>
      {showDays && <span className="opacity-80">· {formatDaysLeft(daysUntilExpiry)}</span>}
    </Badge>
  )
}