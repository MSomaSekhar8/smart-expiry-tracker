import { CalendarDays, Package, Tag } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { StatusBadge } from '@/components/StatusBadge'
import { ItemActions } from '@/components/ItemActions'
import { formatDate, isExpiryToday } from '@/lib/dates'
import type { ItemWithStatus } from '@/lib/types'

interface ItemCardProps {
  item: ItemWithStatus
  onEdit: (item: ItemWithStatus) => void
  onDelete: (item: ItemWithStatus) => void
  onWaste: (item: ItemWithStatus) => void
}

export function ItemCard({ item, onEdit, onDelete, onWaste }: ItemCardProps) {
  const expiredToday = isExpiryToday(item.expiryDate)
  return (
    <Card className="overflow-hidden transition-shadow hover:shadow-md">
      <CardHeader className="flex flex-row items-start justify-between gap-2 space-y-0 p-4 pb-2">
        <div className="min-w-0">
          <CardTitle className="truncate text-base">{item.name}</CardTitle>
          <p className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
            <Tag className="h-3 w-3" />
            {item.category}
          </p>
        </div>
        <ItemActions item={item} onEdit={onEdit} onDelete={onDelete} onWaste={onWaste} />
      </CardHeader>
      <CardContent className="space-y-3 p-4 pt-2">
        <div className="flex items-center justify-between gap-2">
          <StatusBadge status={item.status} daysUntilExpiry={item.daysUntilExpiry} />
          <span className="text-sm font-medium">
            {item.quantity} {item.unit}
          </span>
        </div>
        <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <CalendarDays className="h-3.5 w-3.5" />
          <span className={expiredToday ? 'font-semibold text-destructive' : ''}>
            Expires {formatDate(item.expiryDate)}
          </span>
        </div>
        {item.notes && (
          <p className="line-clamp-2 text-xs text-muted-foreground">{item.notes}</p>
        )}
      </CardContent>
    </Card>
  )
}

export function ItemCardSkeleton() {
  return (
    <Card>
      <CardHeader className="space-y-2 p-4 pb-2">
        <Skeleton className="h-4 w-2/3" />
        <Skeleton className="h-3 w-1/3" />
      </CardHeader>
      <CardContent className="space-y-2 p-4 pt-2">
        <Skeleton className="h-5 w-24 rounded-full" />
        <Skeleton className="h-3 w-1/2" />
      </CardContent>
    </Card>
  )
}

export { Package }