import { useEffect, useState } from 'react'
import { CalendarDays } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { BarcodeScannerInput } from '@/components/BarcodeScannerInput'
import { toISODate } from '@/lib/dates'
import type { BarcodeLookupResult, Category, ItemInput } from '@/lib/types'

export interface ItemFormValues {
  name: string
  barcode: string | null
  categoryId: string
  quantity: number | null
  unit: string
  purchaseDate: string | null
  expiryDate: string | null
  shelfLifeDays: number | null
  notes: string | null
}

interface ItemFormFieldsProps {
  categories: Category[]
  categoriesLoading?: boolean
  defaultCategoryId?: string
  initialBarcode?: string | null
  initialValues?: Partial<ItemFormValues>
  submitting?: boolean
  submitLabel: string
  onSubmit: (values: ItemFormValues) => Promise<void> | void
}

export function ItemFormFields({
  categories,
  categoriesLoading = false,
  defaultCategoryId,
  initialBarcode,
  initialValues,
  submitting = false,
  submitLabel,
  onSubmit,
}: ItemFormFieldsProps) {
  const defaultCategory = defaultCategoryId ?? initialValues?.categoryId ?? categories[0]?.id ?? ''
  const [name, setName] = useState(initialValues?.name ?? '')
  const [barcode, setBarcode] = useState<string>(initialValues?.barcode ?? initialBarcode ?? '')
  const [categoryId, setCategoryId] = useState(defaultCategory)
  const [quantity, setQuantity] = useState(
    initialValues?.quantity != null ? String(initialValues.quantity) : '',
  )
  const [unit, setUnit] = useState(initialValues?.unit ?? 'unit')
  const [purchaseDate, setPurchaseDate] = useState(toISODate(initialValues?.purchaseDate) ?? '')
  const [expiryDate, setExpiryDate] = useState(toISODate(initialValues?.expiryDate) ?? '')
  const [shelfLifeDays, setShelfLifeDays] = useState(
    initialValues?.shelfLifeDays != null ? String(initialValues.shelfLifeDays) : '',
  )
  const [notes, setNotes] = useState(initialValues?.notes ?? '')
  const [error, setError] = useState<string | null>(null)

  const selectedCategory = categories.find((c) => c.id === categoryId)

  useEffect(() => {
    if (expiryDate && !shelfLifeDays && purchaseDate && selectedCategory) {
      const diff = Math.round(
        (new Date(expiryDate).getTime() - new Date(purchaseDate).getTime()) / 86400000,
      )
      if (diff > 0) setShelfLifeDays(String(diff))
    }
  }, [expiryDate, purchaseDate, shelfLifeDays, selectedCategory])

  useEffect(() => {
    if (categoryId && !purchaseDate && !expiryDate && selectedCategory) {
      const suggested = new Date()
      suggested.setDate(suggested.getDate() + selectedCategory.defaultShelfLifeDays)
      setExpiryDate(suggested.toISOString().slice(0, 10))
    }
  }, [categoryId, purchaseDate, expiryDate, selectedCategory])

  const handleBarcodeChange = (code: string, product?: BarcodeLookupResult | null) => {
    setBarcode(code)
    if (product?.name && !name.trim()) {
      setName(product.name)
    }
    if (product?.category && !selectedCategory) {
      const match = categories.find(
        (c) => c.name.toLowerCase() === product.category?.toLowerCase(),
      )
      if (match) setCategoryId(match.id)
    }
  }

  const handleSubmit = () => {
    if (!name.trim()) {
      setError('Name is required')
      return
    }
    if (!categoryId) {
      setError('Please pick a category')
      return
    }
    setError(null)
    onSubmit({
      name: name.trim(),
      barcode: barcode || null,
      categoryId,
      quantity: quantity === '' ? null : Number(quantity),
      unit: unit.trim() || 'unit',
      purchaseDate: purchaseDate || null,
      expiryDate: expiryDate || null,
      shelfLifeDays: shelfLifeDays === '' ? null : Number(shelfLifeDays),
      notes: notes.trim() || null,
    })
  }

  return (
    <form
      className="grid gap-4"
      onSubmit={(e) => {
        e.preventDefault()
        handleSubmit()
      }}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label htmlFor="item-name">Name *</Label>
          <Input
            id="item-name"
            placeholder="e.g. Oat milk"
            value={name}
            onChange={(e) => setName(e.target.value)}
            autoFocus
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="item-category">Category</Label>
          <Select value={categoryId} onValueChange={setCategoryId}>
            <SelectTrigger id="item-category">
              <SelectValue placeholder={categoriesLoading ? 'Loading…' : 'Pick a category'} />
            </SelectTrigger>
            <SelectContent>
              {categories.map((category) => (
                <SelectItem key={category.id} value={category.id}>
                  {category.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="space-y-1.5">
        <Label>Barcode</Label>
        <BarcodeScannerInput value={barcode} onChange={handleBarcodeChange} />
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label htmlFor="item-qty">Quantity</Label>
          <Input
            id="item-qty"
            type="number"
            min="0"
            step="any"
            inputMode="decimal"
            placeholder="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="item-unit">Unit</Label>
          <Input
            id="item-unit"
            placeholder="unit"
            value={unit}
            onChange={(e) => setUnit(e.target.value)}
          />
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label htmlFor="item-purchased">Purchased</Label>
          <Input
            id="item-purchased"
            type="date"
            value={purchaseDate}
            onChange={(e) => setPurchaseDate(e.target.value)}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="item-expiry" className="flex items-center gap-1">
            <CalendarDays className="h-3.5 w-3.5" />
            Expires
          </Label>
          <Input
            id="item-expiry"
            type="date"
            value={expiryDate}
            onChange={(e) => setExpiryDate(e.target.value)}
          />
        </div>
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="item-shelf-life">Shelf life (days)</Label>
        <Input
          id="item-shelf-life"
          type="number"
          min="1"
          inputMode="numeric"
          placeholder={selectedCategory ? `${selectedCategory.defaultShelfLifeDays} days suggested` : 'Days'}
          value={shelfLifeDays}
          onChange={(e) => setShelfLifeDays(e.target.value)}
        />
      </div>

      <div className="space-y-1.5">
        <Label htmlFor="item-notes">Notes</Label>
        <Textarea
          id="item-notes"
          placeholder="Optional — storage tips, reminders…"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
        />
      </div>

      {error && <p className="text-sm font-medium text-destructive">{error}</p>}

      <div className="flex justify-end">
        <Button type="submit" disabled={submitting}>
          {submitting ? 'Saving…' : submitLabel}
        </Button>
      </div>
    </form>
  )
}