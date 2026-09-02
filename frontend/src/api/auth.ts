import { apiRequest } from './client'
import { setStoredUser, setTokens } from './tokenStorage'

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  userId: number
  username: string
  displayName: string
}

export interface RefreshResponse {
  accessToken: string
  refreshToken: string
}

export interface MeResponse {
  id: number
  username: string
  email: string
  displayName: string
}

export async function register(
  email: string,
  password: string,
  username: string,
  displayName: string,
): Promise<AuthResponse> {
  return apiRequest<AuthResponse>('/auth/register', {
    method: 'POST',
    skipAuth: true,
    body: JSON.stringify({ email, password, username, displayName }),
  })
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const response = await apiRequest<AuthResponse>('/auth/login', {
    method: 'POST',
    skipAuth: true,
    body: JSON.stringify({ email, password }),
  })
  setTokens(response.accessToken, response.refreshToken)
  setStoredUser({ userId: response.userId, username: response.username, displayName: response.displayName })
  return response
}

export async function refresh(refreshToken: string): Promise<RefreshResponse> {
  const response = await apiRequest<RefreshResponse>('/auth/refresh', {
    method: 'POST',
    skipAuth: true,
    body: JSON.stringify({ refreshToken }),
  })
  setTokens(response.accessToken, response.refreshToken)
  return response
}

export async function fetchMe(): Promise<MeResponse> {
  return apiRequest<MeResponse>('/me', { method: 'GET' })
}
