import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './tokenStorage'

const API_BASE = '/api'

export class ApiError extends Error {
  status: number
  fieldErrors?: Record<string, string>

  constructor(status: number, message: string, fieldErrors?: Record<string, string>) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

interface ApiRequestOptions extends RequestInit {
  /** ログイン・登録・リフレッシュ自体の呼び出し用。Authorizationヘッダーを付けず、401時の自動リフレッシュも行わない */
  skipAuth?: boolean
}

interface RefreshResponseBody {
  accessToken: string
  refreshToken: string
}

interface ApiErrorBody {
  message?: string
  fieldErrors?: Record<string, string>
}

// 同時に複数のリクエストが401を受け取った場合でもrefreshは1回だけ実行するための共有Promise
let refreshPromise: Promise<string | null> | null = null

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) return null

  const response = await fetch(`${API_BASE}/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  })

  if (!response.ok) return null

  const data: RefreshResponseBody = await response.json()
  setTokens(data.accessToken, data.refreshToken)
  return data.accessToken
}

function buildHeaders(token: string | null, skipAuth: boolean | undefined, extra: HeadersInit | undefined): HeadersInit {
  return {
    'Content-Type': 'application/json',
    ...(token && !skipAuth ? { Authorization: `Bearer ${token}` } : {}),
    ...extra,
  }
}

function redirectToLogin(): void {
  clearTokens()
  window.location.assign('/login')
}

async function parseErrorBody(response: Response): Promise<ApiErrorBody> {
  try {
    return (await response.json()) as ApiErrorBody
  } catch {
    return {}
  }
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const { skipAuth, headers, ...rest } = options

  let response = await fetch(`${API_BASE}${path}`, {
    ...rest,
    headers: buildHeaders(getAccessToken(), skipAuth, headers),
  })

  if (response.status === 401 && !skipAuth) {
    if (!refreshPromise) {
      refreshPromise = refreshAccessToken().finally(() => {
        refreshPromise = null
      })
    }
    const newAccessToken = await refreshPromise

    if (!newAccessToken) {
      redirectToLogin()
      throw new ApiError(401, 'unauthorized')
    }

    response = await fetch(`${API_BASE}${path}`, {
      ...rest,
      headers: buildHeaders(newAccessToken, skipAuth, headers),
    })

    if (response.status === 401) {
      redirectToLogin()
      throw new ApiError(401, 'unauthorized')
    }
  }

  if (!response.ok) {
    const body = await parseErrorBody(response)
    throw new ApiError(response.status, body.message ?? response.statusText, body.fieldErrors)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
