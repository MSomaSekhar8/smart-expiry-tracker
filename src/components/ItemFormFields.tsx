import { useEffect, useRef, useState } from 'react'
import { CalendarDays, Minus, Plus } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { BarcodeScannerInput } from '@/components/BarcodeScannerInput'
import { toISODate } from '@/lib/dates'
import type { BarcodeLookupResult, Category, ItemInput } from '@/lib/types'

const UNIT_OPTIONS = ['kg', 'g', 'L', 'ml', 'pcs', 'packs', 'bottles', 'boxes']
const MAX_QUANTITY = 999
const CATEGORY_UNIT_SUGGESTIONS: Record<string, string> = {
  grocery: 'kg',
  medicine: 'pcs',
  perishable: 'kg',
}

function suggestUnitFor(categoryId: string, categories: Category[]): string {
  const name = categories.find((c) => c.id === categoryId)?.name
  return (name && CATEGORY_UNIT_SUGGESTIONS[name]) || 'unit'
}

function parseQuantity(value: string): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}

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
    initialValues?.quantity != null ? String(initialValues.quantity) : '1',
  )
  const [unit, setUnit] = useState(initialValues?.unit ?? suggestUnitFor(defaultCategory, categories))
  const unitTouchedRef = useRef(false)
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
      if (match) {
        setCategoryId(match.id)
        if (!unitTouchedRef.current) setUnit(suggestUnitFor(match.id, categories))
      }
    }
  }

  const handleCategoryChange = (id: string) => {
    setCategoryId(id)
    if (!unitTouchedRef.current) setUnit(suggestUnitFor(id, categories))
  }

  const handleIncrement = () => {
    setQuantity(String(Math.min(parseQuantity(quantity) + 1, MAX_QUANTITY)))
  }

  const handleDecrement = () => {
    setQuantity(String(Math.max(parseQuantity(quantity) - 1, 1)))
  }

  const handleQuantityChange = (raw: string) => {
    if (/^\d*([.]\d{0,2})?$/.test(raw)) setQuantity(raw)
  }

  const handleQuantityBlur = () => {
    const n = parseQuantity(quantity)
    if (quantity === '' || n < 1) setQuantity('1')
    else if (n > MAX_QUANTITY) setQuantity(String(MAX_QUANTITY))
    else setQuantity(String(n))
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
    const qty = parseQuantity(quantity)
    if (quantity === '' || qty < 1 || qty > MAX_QUANTITY) {
      setError(`Quantity must be between 1 and ${MAX_QUANTITY}`)
      return
    }
    setError(null)
    onSubmit({
      name: name.trim(),
      barcode: barcode || null,
      categoryId,
      quantity: qty,
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
          <Select value={categoryId} onValueChange={handleCategoryChange}>
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
          <div className="flex items-center gap-2">
            <Button
              type="button"
              size="icon"
              variant="outline"
              className="h-9 w-9 shrink-0"
              aria-label="Decrease quantity"
              disabled={parseQuantity(quantity) <= 1}
              onClick={handleDecrement}
            >
              <Minus className="h-4 w-4" />
            </Button>
            <Input
              id="item-qty"
              inputMode="decimal"
              autoComplete="off"
              className="h-9 w-20 text-center"
              value={quantity}
              onChange={(e) => handleQuantityChange(e.target.value)}
              onBlur={handleQuantityBlur}
            />
            <Button
              type="button"
              size="icon"
              variant="outline"
              className="h-9 w-9 shrink-0"
              aria-label="Increase quantity"
              disabled={parseQuantity(quantity) >= MAX_QUANTITY}
              onClick={handleIncrement}
            >
              <Plus className="h-4 w-4" />
            </Button>
          </div>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="item-unit">Unit</Label>
          <Select
            value={unit}
            onValueChange={(v) => {
              setUnit(v)
              unitTouchedRef.current = true
            }}
          >
            <SelectTrigger id="item-unit">
              <SelectValue placeholder="unit" />
            </SelectTrigger>
            <SelectContent>
              {Array.from(new Set([...UNIT_OPTIONS, 'unit', unit])).map((option) => (
                <SelectItem key={option} value={option}>
                  {option}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
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