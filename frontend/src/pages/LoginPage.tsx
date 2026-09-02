import { useState, type ChangeEvent, type FormEvent } from 'react'
import { Alert, Button, Stack, TextField, Typography } from '@mui/material'
import { useLocation, useNavigate, Link as RouterLink } from 'react-router-dom'
import { AuthCard } from '../components/AuthCard'
import { ApiError } from '../api/client'
import { login } from '../api/auth'
import { useAuth } from '../auth/AuthContext'

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { setUser } = useAuth()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const justRegistered = Boolean((location.state as { registered?: boolean } | null)?.registered)

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)

    try {
      const response = await login(email, password)
      setUser({ userId: response.userId, username: response.username, displayName: response.displayName })
      navigate('/home', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setError('メールアドレスまたはパスワードが正しくありません。')
      } else {
        setError('ログインに失敗しました。時間をおいて再度お試しください。')
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthCard title="RaiseTechタイムライン" subtitle="学習コミュニティのためのタイムライン">
      <Stack component="form" spacing={2} onSubmit={handleSubmit} noValidate>
        {justRegistered && <Alert severity="success">登録が完了しました。ログインしてください。</Alert>}
        {error && <Alert severity="error">{error}</Alert>}

        <TextField
          label="メールアドレス"
          type="email"
          value={email}
          onChange={(event: ChangeEvent<HTMLInputElement>) => setEmail(event.target.value)}
          placeholder="you@example.com"
          required
        />

        <TextField
          label="パスワード"
          type="password"
          value={password}
          onChange={(event: ChangeEvent<HTMLInputElement>) => setPassword(event.target.value)}
          placeholder="••••••••"
          required
        />

        <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
          ログイン
        </Button>

        <Typography variant="body2" color="text.secondary" textAlign="center">
          アカウントをお持ちでないですか？{' '}
          <Typography component={RouterLink} to="/register" variant="body2" color="primary" fontWeight={700} sx={{ textDecoration: 'none' }}>
            新規登録
          </Typography>
        </Typography>
      </Stack>
    </AuthCard>
  )
}
