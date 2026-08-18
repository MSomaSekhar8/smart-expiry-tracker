import { useMemo, useState } from 'react'
import { Plus, Search, SlidersHorizontal } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { AddItemModal } from '@/components/AddItemModal'
import { ConfirmDialog } from '@/components/ConfirmDialog'
import { EmptyState } from '@/components/EmptyState'
import { ItemCard, ItemCardSkeleton } from '@/components/ItemCard'
import { ItemTable, ItemTableSkeleton } from '@/components/ItemTable'
import { ItemFormFields } from '@/components/ItemFormFields'
import { WasteModal } from '@/components/WasteModal'
import { errorMessage, useCategories, useItems } from '@/hooks/useItems'
import type { ItemInput, ItemStatus, ItemWithStatus } from '@/lib/types'
import { toast } from 'sonner'

export default function ItemList() {
  const { categories } = useCategories()
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [category, setCategory] = useState<string>('all')
  const [status, setStatus] = useState<string>('all')
  const [sort, setSort] = useState<string>('expiry')
  const [dir, setDir] = useState<string>('asc')
  const [addOpen, setAddOpen] = useState(false)
  const [confirmItem, setConfirmItem] = useState<ItemWithStatus | null>(null)
  const [wasteItem, setWasteItem] = useState<ItemWithStatus | null>(null)
  const [editingItem, setEditingItem] = useState<ItemWithStatus | null>(null)

  const { items, loading, error, createItem, updateItem, deleteItem, markWasted, refetch } = useItems({
    search: debouncedSearch || undefined,
    category: category === 'all' ? undefined : category,
    status: status === 'all' ? undefined : status,
    sort,
    dir,
  })

  const clearFilters = () => {
    setSearch('')
    setDebouncedSearch('')
    setCategory('all')
    setStatus('all')
    setSort('expiry')
    setDir('asc')
  }

  const hasFilters = !!(debouncedSearch || category !== 'all' || status !== 'all')

  const handleSearchChange = (value: string) => {
    setSearch(value)
    window.setTimeout(() => setDebouncedSearch(value), 300)
  }

  const filteredItems = useMemo(() => items, [items])

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">My items</h1>
          <p className="text-sm text-muted-foreground">{items.length} tracked</p>
        </div>
        <Button onClick={() => setAddOpen(true)}>
          <Plus className="h-4 w-4" />
          Add item
        </Button>
      </div>

      <Card className="p-3">
        <div className="flex flex-wrap items-center gap-2">
          <div className="relative min-w-48 flex-1">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="pl-9"
              placeholder="Search items…"
              value={search}
              onChange={(e) => handleSearchChange(e.target.value)}
            />
          </div>
          <Select value={category} onValueChange={setCategory}>
            <SelectTrigger className="w-36" aria-label="Filter by category">
              <SelectValue placeholder="Category" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All categories</SelectItem>
              {categories.map((c) => (
                <SelectItem key={c.id} value={c.id}>
                  {c.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select value={status} onValueChange={setStatus}>
            <SelectTrigger className="w-36" aria-label="Filter by status">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All statuses</SelectItem>
              <SelectItem value="SAFE">Safe</SelectItem>
              <SelectItem value="EXPIRING">Expiring soon</SelectItem>
              <SelectItem value="EXPIRED">Expired</SelectItem>
            </SelectContent>
          </Select>
          <Select value={sort} onValueChange={setSort}>
            <SelectTrigger className="w-36" aria-label="Sort items">
              <SelectValue placeholder="Sort" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="expiry">Expiry date</SelectItem>
              <SelectItem value="name">Name</SelectItem>
              <SelectItem value="created">Date added</SelectItem>
              <SelectItem value="category">Category</SelectItem>
            </SelectContent>
          </Select>
          <Select value={dir} onValueChange={setDir}>
            <SelectTrigger className="w-28" aria-label="Sort direction">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="asc">Ascending</SelectItem>
              <SelectItem value="desc">Descending</SelectItem>
            </SelectContent>
          </Select>
          {hasFilters && (
            <Button variant="ghost" size="sm" onClick={clearFilters}>
              <SlidersHorizontal className="h-4 w-4" />
              Reset
            </Button>
          )}
        </div>
      </Card>

      {error ? (
        <EmptyState
          title="Couldn't load items"
          description={errorMessage(error)}
          action={{ label: 'Try again', onClick: () => void refetch() }}
        />
      ) : loading ? (
        <ItemTableSkeleton />
      ) : filteredItems.length === 0 ? (
        <EmptyState
          title={hasFilters ? 'No matching items' : 'Your pantry is empty'}
          description={
            hasFilters
              ? 'Try adjusting the search or filters.'
              : 'Add your first item and start tracking expiry dates.'
          }
          action={
            hasFilters
              ? { label: 'Reset filters', onClick: clearFilters }
              : { label: 'Add an item', onClick: () => setAddOpen(true) }
          }
        />
      ) : (
        <>
          <div className="hidden md:block">
            <ItemTable
              items={filteredItems}
              onEdit={setEditingItem}
              onDelete={setConfirmItem}
              onWaste={setWasteItem}
            />
          </div>
          <div className="grid gap-3 sm:grid-cols-2 md:hidden">
            {filteredItems.map((item) => (
              <ItemCard
                key={item.id}
                item={item}
                onEdit={setEditingItem}
                onDelete={setConfirmItem}
                onWaste={setWasteItem}
              />
            ))}
          </div>
        </>
      )}

      <AddItemModal
        open={addOpen}
        onOpenChange={setAddOpen}
        defaultCategoryId={category === 'all' ? categories[0]?.id : category}
        onSubmit={createItem}
      />

      {editingItem && (
        <EditItemDialog
          item={editingItem}
          onClose={() => setEditingItem(null)}
          onSubmit={async (input) => {
            await updateItem(editingItem.id, input)
            toast.success('Item updated')
            setEditingItem(null)
          }}
        />
      )}

      <WasteModal
        item={wasteItem}
        open={!!wasteItem}
        onOpenChange={(open) => !open && setWasteItem(null)}
        onSubmit={async (qty, cost) => {
          if (!wasteItem) return
          await markWasted(wasteItem.id, qty, cost)
          toast.success('Waste recorded and item removed')
          setWasteItem(null)
        }}
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
        }}
      />
    </div>
  )
}

interface EditItemDialogProps {
  item: ItemWithStatus
  onClose: () => void
  onSubmit: (input: ItemInput) => Promise<void>
}

function EditItemDialog({ item, onClose, onSubmit }: EditItemDialogProps) {
  const { categories } = useCategories()
  const [submitting, setSubmitting] = useState(false)

  const initialValues = {
    name: item.name,
    barcode: item.barcode ?? undefined,
    categoryId: item.categoryId,
    quantity: item.quantity,
    unit: item.unit,
    purchaseDate: item.purchaseDate ?? undefined,
    expiryDate: item.expiryDate ?? undefined,
    shelfLifeDays: item.shelfLifeDays ?? undefined,
    notes: item.notes ?? undefined,
  }

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Edit {item.name}</DialogTitle>
        </DialogHeader>
        <ItemFormFields
          categories={categories}
          initialValues={initialValues}
          submitting={submitting}
          submitLabel="Save changes"
          onSubmit={async (values) => {
            setSubmitting(true)
            try {
              await onSubmit(values)
            } finally {
              setSubmitting(false)
            }
          }}
        />
      </DialogContent>
    </Dialog>
  )
}