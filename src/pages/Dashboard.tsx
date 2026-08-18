import { useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { AddItemModal } from '@/components/AddItemModal'
import { CategoryDonut } from '@/components/CategoryDonut'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { ItemCard, ItemCardSkeleton } from '@/components/ItemCard'
import { StatusBadge } from '@/components/StatusBadge'
import { SummaryCards } from '@/components/SummaryCards'
import { WasteModal } from '@/components/WasteModal'
import { useCategories, useItems } from '@/hooks/useItems'
import { formatDateTime } from '@/lib/dates'
import { useQuery } from '@/lib/useQuery'
import api, { toErrorMessage } from '@/lib/apiClient'
import type { ItemWithStatus, WasteLogEntry } from '@/lib/types'
import { toast } from 'sonner'

export default function Dashboard() {
  const navigate = useNavigate()
  const { items, loading, createItem, deleteItem, markWasted, refetch } = useItems()
  const { categories } = useCategories()
  const [addOpen, setAddOpen] = useState(false)
  const [confirmItem, setConfirmItem] = useState<ItemWithStatus | null>(null)
  const [wasteItem, setWasteItem] = useState<ItemWithStatus | null>(null)

  const { data: wasteLog, loading: wasteLoading } = useQuery<WasteLogEntry[]>(() =>
    api.get('/waste-log', { params: { limit: 6 } }).then((r) => r.data),
  )

  const needsAttention = useMemo(
    () =>
      [...items]
        .filter((i) => i.status !== 'SAFE')
        .sort((a, b) => a.daysUntilExpiry - b.daysUntilExpiry)
        .slice(0, 5),
    [items],
  )

  const handleWasteSubmit = async (quantityWasted: number, estimatedCostLost: number | null) => {
    if (!wasteItem) return
    await markWasted(wasteItem.id, quantityWasted, estimatedCostLost)
    toast.success('Waste recorded and item removed')
    setWasteItem(null)
    void refetch()
  }

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Your pantry</h1>
          <p className="text-sm text-muted-foreground">Everything you're watching, at a glance.</p>
        </div>
        <Button onClick={() => setAddOpen(true)}>
          <Plus className="h-4 w-4" />
          Add item
        </Button>
      </div>

      <SummaryCards items={items} loading={loading} />

      <div className="grid gap-6 lg:grid-cols-5">
        <Card className="lg:col-span-3">
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-base">Needs attention</CardTitle>
            <Link to="/items" className="flex items-center gap-1 text-sm font-medium text-primary hover:underline">
              View all <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </CardHeader>
          <CardContent className="space-y-3">
            {loading ? (
              Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} className="h-16 w-full" />)
            ) : needsAttention.length === 0 ? (
              <EmptyState
                title="Nothing needs attention"
                description="All items are safe. Add more items to keep the pantry stocked."
                action={{ label: 'Add an item', onClick: () => setAddOpen(true) }}
              />
            ) : (
              needsAttention.map((item) => (
                <div
                  key={item.id}
                  className="flex items-center justify-between gap-3 rounded-lg border p-3"
                >
                  <div className="min-w-0">
                    <p className="truncate font-medium">{item.name}</p>
                    <p className="text-xs text-muted-foreground">
                      {item.quantity} {item.unit} · {item.category}
                    </p>
                  </div>
                  <StatusBadge status={item.status} daysUntilExpiry={item.daysUntilExpiry} />
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card className="lg:col-span-2">
          <CardHeader className="pb-2">
            <CardTitle className="text-base">Recent waste</CardTitle>
          </CardHeader>
          <CardContent>
            {wasteLoading ? (
              <div className="space-y-2">
                {Array.from({ length: 3 }).map((_, i) => (
                  <Skeleton key={i} className="h-10 w-full" />
                ))}
              </div>
            ) : !wasteLog || wasteLog.length === 0 ? (
              <EmptyState title="No waste yet" description="Items you mark as wasted show up here." />
            ) : (
              <ul className="space-y-2">
                {wasteLog.map((entry) => (
                  <li key={entry.id} className="flex items-center justify-between gap-2 text-sm">
                    <span className="truncate">{entry.itemName ?? 'Item'}</span>
                    <span className="shrink-0 text-xs text-muted-foreground">
                      {entry.quantityWasted} {entry.unit ?? ''} · {formatDateTime(entry.loggedAt)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-base">Categories</CardTitle>
        </CardHeader>
        <CardContent>
          <CategoryDonut items={items} loading={loading} />
        </CardContent>
      </Card>

      <div>
        <h2 className="mb-3 text-lg font-semibold">Recently added</h2>
        {loading ? (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {Array.from({ length: 4 }).map((_, i) => (
              <ItemCardSkeleton key={i} />
            ))}
          </div>
        ) : items.length === 0 ? (
          <EmptyState
            title="Your pantry is empty"
            description="Start by adding the first item — scan its barcode or enter it manually."
            action={{ label: 'Add an item', onClick: () => setAddOpen(true) }}
          />
        ) : (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {items.slice(0, 4).map((item) => (
              <ItemCard
                key={item.id}
                item={item}
                onEdit={() => navigate(`/items/${item.id}/edit`)}
                onDelete={setConfirmItem}
                onWaste={setWasteItem}
              />
            ))}
          </div>
        )}
      </div>

      <AddItemModal
        open={addOpen}
        onOpenChange={setAddOpen}
        defaultCategoryId={categories[0]?.id}
        onSubmit={createItem}
      />
      <WasteModal
        item={wasteItem}
        open={!!wasteItem}
        onOpenChange={(open) => !open && setWasteItem(null)}
        onSubmit={handleWasteSubmit}
      />
      <ConfirmDialog
        open={!!confirmItem}
        onOpenChange={(open) => !open && setConfirmItem(null)}
        item={confirmItem}
        onConfirm={async () => {
          if (!confirmItem) return
          await deleteItem(confirmItem.id)
          toast.success('Item deleted')
          setConfirmItem(null)
          void refetch()
        }}
      />
    </div>
  )
}