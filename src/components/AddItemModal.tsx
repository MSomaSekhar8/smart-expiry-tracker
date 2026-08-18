import { useState } from 'react'
import { Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { ItemFormFields, type ItemFormValues } from '@/components/ItemFormFields'
import { useCategories } from '@/hooks/useItems'
import { toErrorMessage } from '@/lib/apiClient'
import type { ItemInput, ItemWithStatus } from '@/lib/types'
import { toast } from 'sonner'

interface AddItemModalProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  defaultCategoryId?: string
  initialBarcode?: string | null
  onSubmit: (input: ItemInput) => Promise<ItemWithStatus>
}

export function AddItemModal({
  open,
  onOpenChange,
  defaultCategoryId,
  initialBarcode,
  onSubmit,
}: AddItemModalProps) {
  const [submitting, setSubmitting] = useState(false)
  const { categories, loading: categoriesLoading } = useCategories()

  const handleSubmit = async (values: ItemFormValues) => {
    setSubmitting(true)
    try {
      const created = await onSubmit(values)
      toast.success(`${created.name} added to your pantry`)
      onOpenChange(false)
    } catch (err) {
      toast.error(toErrorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Plus className="h-4 w-4" />
            Add item
          </DialogTitle>
          <DialogDescription>
            Track an item's expiry date and it'll be watched for you.
          </DialogDescription>
        </DialogHeader>
        <ItemFormFields
          categories={categories}
          categoriesLoading={categoriesLoading}
          defaultCategoryId={defaultCategoryId}
          initialBarcode={initialBarcode}
          submitting={submitting}
          submitLabel="Add item"
          onSubmit={handleSubmit}
        />
      </DialogContent>
    </Dialog>
  )
}