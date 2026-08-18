import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import api, { tokenStore } from '@/lib/apiClient'
import type { AuthUser, TokenPair } from '@/lib/types'

interface AuthContextValue {
  user: AuthUser | null
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, displayName?: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    const restore = async () => {
      if (!tokenStore.access) {
        if (!cancelled) setLoading(false)
        return
      }
      try {
        const { data } = await api.get<AuthUser>('/auth/me')
        if (!cancelled) setUser(data)
      } catch {
        tokenStore.clear()
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    restore()
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    const onUnauthorized = () => setUser(null)
    window.addEventListener('auth:unauthorized', onUnauthorized)
    return () => window.removeEventListener('auth:unauthorized', onUnauthorized)
  }, [])

  const applyTokens = useCallback((pair: TokenPair) => {
    tokenStore.set(pair)
    setUser(pair.user)
  }, [])

  const login = useCallback(
    async (email: string, password: string) => {
      const { data } = await api.post<TokenPair>('/auth/login', { email, password })
      applyTokens(data)
    },
    [applyTokens],
  )

  const register = useCallback(
    async (email: string, password: string, displayName?: string) => {
      const { data } = await api.post<TokenPair>('/auth/register', {
        email,
        password,
        displayName: displayName || null,
      })
      applyTokens(data)
    },
    [applyTokens],
  )

  const logout = useCallback(async () => {
    try {
      await api.post('/auth/logout')
    } catch {
      // token is dropped regardless
    }
    tokenStore.clear()
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ user, loading, login, register, logout }),
    [user, loading, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}