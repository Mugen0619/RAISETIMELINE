import { useEffect, useState } from 'react'
import { Alert, Avatar, Box, Button, CircularProgress, Paper, Stack, Typography } from '@mui/material'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import { useNavigate } from 'react-router-dom'
import { fetchMe, type MeResponse } from '../api/auth'
import { useAuth } from '../auth/AuthContext'

export function HomePage() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()
  const [me, setMe] = useState<MeResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    fetchMe()
      .then((response) => {
        if (!cancelled) setMe(response)
      })
      .catch(() => {
        if (!cancelled) setError('ユーザー情報の取得に失敗しました。')
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const displayName = me?.displayName ?? user?.displayName
  const username = me?.username ?? user?.username

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        padding: 3,
        background: 'background.default',
      }}
    >
      <Paper
        elevation={0}
        sx={{ width: '100%', maxWidth: 420, padding: 4, border: '1px solid', borderColor: 'divider', borderRadius: 4 }}
      >
        <Stack alignItems="center" spacing={2}>
          <Avatar sx={{ width: 56, height: 56, bgcolor: 'primary.main' }}>
            <CheckCircleIcon fontSize="large" />
          </Avatar>
          <Typography variant="h5" fontWeight={700}>
            ログイン成功
          </Typography>

          {isLoading ? (
            <CircularProgress size={24} />
          ) : error ? (
            <Alert severity="warning" sx={{ width: '100%' }}>
              {error}
            </Alert>
          ) : (
            <Stack alignItems="center" spacing={0.5}>
              <Typography variant="h6">{displayName}</Typography>
              <Typography variant="body2" color="text.secondary">
                @{username}
              </Typography>
            </Stack>
          )}

          <Button variant="outlined" color="error" onClick={handleLogout} sx={{ marginTop: 2 }}>
            ログアウト
          </Button>
        </Stack>
      </Paper>
    </Box>
  )
}
