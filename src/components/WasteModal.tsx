import { useState } from 'react'
import { Trash } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import type { ItemWithStatus } from '@/lib/types'

interface WasteModalProps {
  item: ItemWithStatus | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (quantityWasted: number, estimatedCostLost: number | null) => Promise<void>
}

export function WasteModal({ item, open, onOpenChange, onSubmit }: WasteModalProps) {
  const [quantity, setQuantity] = useState('')
  const [cost, setCost] = useState('')
  const [submitting, setSubmitting] = useState(false)

  const reset = () => {
    setQuantity('')
    setCost('')
  }

  const handleSubmit = async () => {
    if (!item) return
    const qty = quantity === '' ? item.quantity : Number(quantity)
    const costValue = cost === '' ? null : Number(cost)
    if (Number.isNaN(qty) || qty <= 0 || (cost !== '' && Number.isNaN(costValue))) {
      return
    }
    setSubmitting(true)
    try {
      await onSubmit(qty, costValue)
      reset()
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) reset()
        onOpenChange(next)
      }}
    >
      <DialogContent className="sm:max-w-sm">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Trash className="h-4 w-4 text-destructive" />
            Mark as wasted
          </DialogTitle>
          <DialogDescription>
            {item ? (
              <>
                Recording <span className="font-medium">{item.name}</span> as wasted will remove it from
                your pantry and add it to your waste analytics.
              </>
            ) : (
              'Recording this item as wasted.'
            )}
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 py-2">
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="waste-qty">Quantity wasted</Label>
              <Input
                id="waste-qty"
                type="number"
                min="0"
                step="any"
                inputMode="decimal"
                placeholder={item ? String(item.quantity) : 'Amount'}
                value={quantity}
                onChange={(e) => setQuantity(e.target.value)}
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="waste-cost">Est. cost lost (₹)</Label>
              <Input
                id="waste-cost"
                type="number"
                min="0"
                step="0.01"
                inputMode="decimal"
                placeholder="Optional"
                value={cost}
                onChange={(e) => setCost(e.target.value)}
              />
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            variant="destructive"
            disabled={submitting}
            onClick={handleSubmit}
          >
            {submitting ? 'Recording…' : 'Record waste'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}