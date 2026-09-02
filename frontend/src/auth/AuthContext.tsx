import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { clearTokens, getAccessToken, getStoredUser, type StoredUser } from '../api/tokenStorage'

interface AuthContextValue {
  user: StoredUser | null
  isAuthenticated: boolean
  setUser: (user: StoredUser) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<StoredUser | null>(() => getStoredUser())

  const setUser = useCallback((nextUser: StoredUser) => {
    setUserState(nextUser)
  }, [])

  const logout = useCallback(() => {
    clearTokens()
    setUserState(null)
  }, [])

  const isAuthenticated = Boolean(user && getAccessToken())

  return <AuthContext.Provider value={{ user, isAuthenticated, setUser, logout }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
