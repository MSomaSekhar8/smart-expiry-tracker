import { useCallback, useEffect, useRef, useState } from 'react'

/**
 * Minimal async-data hook: tracks loading/error state, exposes refetch,
 * and lets callers swap in the next page of data via a setter.
 */
export function useQuery<T>(fetcher: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher

  const load = useCallback(async (isMounted = true) => {
    setLoading(true)
    setError(null)
    try {
      const result = await fetcherRef.current()
      if (isMounted) setData(result)
    } catch (err) {
      if (isMounted) {
        setError(err instanceof Error ? err.message : 'Something went wrong')
      }
    } finally {
      if (isMounted) setLoading(false)
    }
  }, [])

  useEffect(() => {
    let mounted = true
    load(mounted)
    return () => {
      mounted = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return { data, setData, loading, error, refetch: () => load(true) }
}