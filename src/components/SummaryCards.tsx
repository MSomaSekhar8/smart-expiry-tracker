import { AlertTriangle, CalendarClock, CheckCircle2, Package } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import type { ItemWithStatus } from '@/lib/types'

interface SummaryCardsProps {
  items: ItemWithStatus[]
  loading?: boolean
  onFilterClick?: (status: 'SAFE' | 'EXPIRING' | 'EXPIRED') => void
}

export function SummaryCards({ items, loading, onFilterClick }: SummaryCardsProps) {
  const counts = {
    SAFE: items.filter((i) => i.status === 'SAFE').length,
    EXPIRING: items.filter((i) => i.status === 'EXPIRING').length,
    EXPIRED: items.filter((i) => i.status === 'EXPIRED').length,
  }

  const cards = [
    {
      label: 'Safe',
      value: counts.SAFE,
      icon: CheckCircle2,
      accent: 'text-emerald-600 dark:text-emerald-400',
      bg: 'bg-emerald-500/10',
      status: 'SAFE' as const,
    },
    {
      label: 'Expiring soon',
      value: counts.EXPIRING,
      icon: CalendarClock,
      accent: 'text-amber-600 dark:text-amber-400',
      bg: 'bg-amber-500/10',
      status: 'EXPIRING' as const,
    },
    {
      label: 'Expired',
      value: counts.EXPIRED,
      icon: AlertTriangle,
      accent: 'text-rose-600 dark:text-rose-400',
      bg: 'bg-rose-500/10',
      status: 'EXPIRED' as const,
    },
  ]

  if (loading) {
    return (
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }).map((_, i) => (
          <Card key={i}>
            <CardContent className="space-y-2 p-4">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-8 w-12" />
            </CardContent>
          </Card>
        ))}
      </div>
    )
  }

  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
      {cards.map(({ label, value, icon: Icon, accent, bg, status }) => (
        <Card
          key={status}
          className={cn('transition-shadow', onFilterClick && 'cursor-pointer hover:shadow-md')}
          onClick={onFilterClick ? () => onFilterClick(status) : undefined}
          role={onFilterClick ? 'button' : undefined}
        >
          <CardContent className="flex items-center justify-between p-4">
            <div>
              <p className="text-sm font-medium text-muted-foreground">{label}</p>
              <p className="mt-1 font-serif text-3xl font-semibold">{value}</p>
            </div>
            <span className={cn('flex h-10 w-10 items-center justify-center rounded-full', bg, accent)}>
              <Icon className="h-5 w-5" />
            </span>
          </CardContent>
        </Card>
      ))}
      <Card>
        <CardContent className="flex items-center justify-between p-4">
          <div>
            <p className="text-sm font-medium text-muted-foreground">Total items</p>
            <p className="mt-1 font-serif text-3xl font-semibold">{items.length}</p>
          </div>
          <span className="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-primary">
            <Package className="h-5 w-5" />
          </span>
        </CardContent>
      </Card>
    </div>
  )
}