import { useCallback, useMemo } from 'react'
import api, { toErrorMessage } from '@/lib/apiClient'
import { useQuery } from '@/lib/useQuery'
import type { Category, ItemInput, ItemWithStatus } from '@/lib/types'

export interface ItemFilters {
  search?: string
  category?: string
  status?: string
  sort?: string
  dir?: string
}

function buildParams(filters: ItemFilters) {
  const params: Record<string, string> = {}
  if (filters.search) params.search = filters.search
  if (filters.category) params.category = filters.category
  if (filters.status) params.status = filters.status
  if (filters.sort) params.sort = filters.sort
  if (filters.dir) params.dir = filters.dir
  return params
}

export function useItems(filters: ItemFilters = {}) {
  const fetcher = useCallback(
    async () => {
      const { data } = await api.get<ItemWithStatus[]>('/items', {
        params: buildParams(filters),
      })
      return data
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [filters.search, filters.category, filters.status, filters.sort, filters.dir],
  )

  const { data, setData, loading, error, refetch } = useQuery<ItemWithStatus[]>(fetcher)

  const createItem = useCallback(
    async (input: ItemInput) => {
      const { data: created } = await api.post<ItemWithStatus>('/items', input)
      setData((prev) => (prev ? [created, ...prev] : [created]))
      return created
    },
    [setData],
  )

  const updateItem = useCallback(
    async (id: string, input: ItemInput) => {
      const { data: updated } = await api.put<ItemWithStatus>(`/items/${id}`, input)
      setData((prev) => (prev ? prev.map((it) => (it.id === id ? updated : it)) : prev))
      return updated
    },
    [setData],
  )

  const deleteItem = useCallback(
    async (id: string) => {
      await api.delete(`/items/${id}`)
      setData((prev) => (prev ? prev.filter((it) => it.id !== id) : prev))
    },
    [setData],
  )

  const markWasted = useCallback(
    async (id: string, quantityWasted?: number, estimatedCostLost?: number | null) => {
      await api.post(`/items/${id}/waste`, {
        quantityWasted,
        estimatedCostLost: estimatedCostLost ?? null,
      })
      setData((prev) => (prev ? prev.filter((it) => it.id !== id) : prev))
    },
    [setData],
  )

  const getItem = useCallback(async (id: string) => {
    const { data } = await api.get<ItemWithStatus>(`/items/${id}`)
    return data
  }, [])

  return { items: data ?? [], loading, error, refetch, createItem, updateItem, deleteItem, markWasted, getItem }
}

export function useCategories() {
  const fetcher = useCallback(async () => {
    const { data } = await api.get<Category[]>('/categories')
    return data
  }, [])
  const { data, loading, error } = useQuery<Category[]>(fetcher)
  return { categories: data ?? [], loading, error }
}

export function errorMessage(err: unknown): string {
  return toErrorMessage(err)
}