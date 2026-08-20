import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { apiErrorMessage, type TokenResponse } from './types'

// The access token lives ONLY in memory. The refresh token lives ONLY in an
// HttpOnly cookie that JavaScript can never read — see RefreshCookieService.
let accessToken: string | null = null

export const tokenStore = {
  get access(): string | null {
    return accessToken
  },
  set(pair: TokenResponse) {
    accessToken = pair.accessToken
  },
  setAccess(token: string) {
    accessToken = token
  },
  clear() {
    accessToken = null
  },
}

const baseURL = import.meta.env.VITE_API_BASE_URL
if (!baseURL) {
  throw new Error(
    'VITE_API_BASE_URL is not set. Create a .env file from .env.example (VITE_API_BASE_URL=http://localhost:8080/api for local development) or set it in the build environment.',
  )
}

// withCredentials makes the browser send the HttpOnly refresh cookie on
// requests to the API, and accept Set-Cookie responses from it.
const api = axios.create({ baseURL, withCredentials: true })

api.interceptors.request.use((config) => {
  const token = tokenStore.access
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshing: Promise<string | null> | null = null

/**
 * Asks the backend for a fresh access token using the HttpOnly refresh
 * cookie. No refresh token is ever read from or written to storage here.
 */
export async function refreshSession(): Promise<string | null> {
  try {
    const { data } = await axios.post<TokenResponse>(
      `${api.defaults.baseURL}/auth/refresh`,
      undefined,
      { withCredentials: true },
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
      refreshing = refreshing ?? refreshSession()
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