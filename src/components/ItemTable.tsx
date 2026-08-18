import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Skeleton } from '@/components/ui/skeleton'
import { StatusBadge } from '@/components/StatusBadge'
import { ItemActions } from '@/components/ItemActions'
import { formatDate, isExpiryToday } from '@/lib/dates'
import type { ItemWithStatus } from '@/lib/types'

interface ItemTableProps {
  items: ItemWithStatus[]
  onEdit: (item: ItemWithStatus) => void
  onDelete: (item: ItemWithStatus) => void
  onWaste: (item: ItemWithStatus) => void
}

export function ItemTable({ items, onEdit, onDelete, onWaste }: ItemTableProps) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Item</TableHead>
          <TableHead className="hidden sm:table-cell">Category</TableHead>
          <TableHead className="hidden md:table-cell">Qty</TableHead>
          <TableHead>Expires</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Actions</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {items.map((item) => {
          const expiredToday = isExpiryToday(item.expiryDate)
          return (
            <TableRow key={item.id}>
              <TableCell>
                <div className="font-medium">{item.name}</div>
                {item.barcode && (
                  <div className="text-xs text-muted-foreground">#{item.barcode}</div>
                )}
              </TableCell>
              <TableCell className="hidden sm:table-cell text-muted-foreground">{item.category}</TableCell>
              <TableCell className="hidden md:table-cell text-muted-foreground">
                {item.quantity} {item.unit}
              </TableCell>
              <TableCell className={expiredToday ? 'font-semibold text-destructive' : ''}>
                {formatDate(item.expiryDate)}
              </TableCell>
              <TableCell>
                <StatusBadge status={item.status} daysUntilExpiry={item.daysUntilExpiry} />
              </TableCell>
              <TableCell className="text-right">
                <ItemActions item={item} onEdit={onEdit} onDelete={onDelete} onWaste={onWaste} />
              </TableCell>
            </TableRow>
          )
        })}
      </TableBody>
    </Table>
  )
}

export function ItemTableSkeleton() {
  return (
    <div className="space-y-2">
      {Array.from({ length: 5 }).map((_, i) => (
        <Skeleton key={i} className="h-12 w-full" />
      ))}
    </div>
  )
}