import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { apiErrorMessage, type TokenPair } from './types'

export const ACCESS_TOKEN_KEY = 'pantry_access_token'
export const REFRESH_TOKEN_KEY = 'pantry_refresh_token'

export const tokenStore = {
  get access(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY)
  },
  get refresh(): string | null {
    return localStorage.getItem(REFRESH_TOKEN_KEY)
  },
  set(pair: TokenPair) {
    localStorage.setItem(ACCESS_TOKEN_KEY, pair.accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, pair.refreshToken)
  },
  clear() {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  },
}

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
})

api.interceptors.request.use((config) => {
  const token = tokenStore.access
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshing: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = tokenStore.refresh
  if (!refreshToken) return null
  try {
    const { data } = await axios.post<TokenPair>(
      `${api.defaults.baseURL}/auth/refresh`,
      { refreshToken },
    )
    tokenStore.set(data)
    return data.accessToken
  } catch {
    tokenStore.clear()
    window.dispatchEvent(new Event('auth:unauthorized'))
    return null
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as InternalAxiosRequestConfig & { _retried?: boolean }
    const status = error.response?.status
    const isAuthRequest = original?.url?.startsWith('/auth/')

    if (status === 401 && original && !original._retried && !isAuthRequest) {
      original._retried = true
      refreshing = refreshing ?? refreshAccessToken()
      const token = await refreshing
      refreshing = null
      if (token) {
        original.headers.Authorization = `Bearer ${token}`
        return api(original)
      }
    }
    return Promise.reject(error)
  },
)

export function toErrorMessage(err: unknown): string {
  return apiErrorMessage(err)
}

export default api