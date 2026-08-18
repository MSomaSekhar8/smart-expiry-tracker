import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { ArrowLeft, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ItemFormFields, type ItemFormValues } from '@/components/ItemFormFields'
import { useCategories, useItems } from '@/hooks/useItems'
import { toErrorMessage } from '@/lib/apiClient'
import type { ItemWithStatus } from '@/lib/types'
import { toast } from 'sonner'

export default function ItemForm() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { categories, loading: categoriesLoading } = useCategories()
  const { getItem, createItem, updateItem } = useItems()
  const [item, setItem] = useState<ItemWithStatus | null>(null)
  const [loading, setLoading] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isEdit = !!id

  useEffect(() => {
    if (!isEdit) return
    let cancelled = false
    setLoading(true)
    getItem(id!)
      .then((data) => {
        if (!cancelled) setItem(data)
      })
      .catch((err) => {
        if (!cancelled) setError(toErrorMessage(err))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [id, isEdit, getItem])

  const handleSubmit = useCallback(
    async (values: ItemFormValues) => {
      setSubmitting(true)
      setError(null)
      try {
        const input = {
          name: values.name,
          barcode: values.barcode,
          categoryId: values.categoryId,
          quantity: values.quantity ?? undefined,
          unit: values.unit,
          purchaseDate: values.purchaseDate,
          expiryDate: values.expiryDate,
          shelfLifeDays: values.shelfLifeDays,
          notes: values.notes,
        }
        if (isEdit && id) {
          await updateItem(id, input)
          toast.success('Item updated')
        } else {
          const created = await createItem(input)
          toast.success(`${created.name} added`)
        }
        navigate('/items')
      } catch (err) {
        setError(toErrorMessage(err))
      } finally {
        setSubmitting(false)
      }
    },
    [isEdit, id, updateItem, createItem, navigate],
  )

  return (
    <div className="mx-auto max-w-xl space-y-6 px-4 py-6">
      <div>
        <Button variant="ghost" size="sm" className="mb-2 -ml-2" onClick={() => navigate('/items')}>
          <ArrowLeft className="h-4 w-4" />
          Back to items
        </Button>
        <h1 className="text-2xl font-semibold tracking-tight">
          {isEdit ? 'Edit item' : 'Add item'}
        </h1>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">
            {isEdit ? item?.name ?? 'Loading…' : 'New pantry item'}
          </CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="flex items-center justify-center py-10">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          ) : categoriesLoading ? (
            <div className="animate-pulse space-y-4 py-4">
              <div className="h-9 w-full rounded-md bg-muted" />
              <div className="h-9 w-full rounded-md bg-muted" />
              <div className="h-9 w-full rounded-md bg-muted" />
            </div>
          ) : error && !item ? (
            <div className="space-y-4">
              <p className="text-sm font-medium text-destructive">{error}</p>
              <Button variant="outline" onClick={() => navigate('/items')}>
                Back to items
              </Button>
            </div>
          ) : (
            <ItemFormFields
              categories={categories}
              initialValues={
                item
                  ? {
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
                  : undefined
              }
              submitting={submitting}
              submitLabel={isEdit ? 'Save changes' : 'Add item'}
              onSubmit={handleSubmit}
            />
          )}
        </CardContent>
      </Card>
    </div>
  )
}