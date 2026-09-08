import { useCallback, useEffect, useState, type ChangeEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  AppBar,
  Alert,
  Box,
  Button,
  CircularProgress,
  Container,
  IconButton,
  Stack,
  TextField,
  Toolbar,
  Typography,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import { useAuth } from '../auth/AuthContext'
import { fetchProfile, updateProfile } from '../api/users'

const DISPLAY_NAME_MAX_LENGTH = 64
const BIO_MAX_LENGTH = 160

export function ProfileEditPage() {
  const { userId: userIdParam } = useParams<{ userId: string }>()
  const userId = Number(userIdParam)
  const navigate = useNavigate()
  const { user: currentUser } = useAuth()

  const [displayName, setDisplayName] = useState('')
  const [bio, setBio] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isOwnProfile = userId === currentUser?.userId

  const loadProfile = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const profile = await fetchProfile(userId)
      setDisplayName(profile.displayName)
      setBio(profile.bio ?? '')
    } catch {
      setError('プロフィールの取得に失敗しました。')
    } finally {
      setIsLoading(false)
    }
  }, [userId])

  useEffect(() => {
    if (!isOwnProfile) {
      navigate(`/users/${userId}`, { replace: true })
      return
    }
    loadProfile()
  }, [isOwnProfile, userId, navigate, loadProfile])

  const isValid = displayName.trim().length > 0 && displayName.length <= DISPLAY_NAME_MAX_LENGTH && bio.length <= BIO_MAX_LENGTH

  const handleSubmit = async () => {
    if (!isValid) return
    setIsSubmitting(true)
    setError(null)
    try {
      await updateProfile(displayName.trim(), bio)
      navigate(`/users/${userId}`)
    } catch {
      setError('更新に失敗しました。時間をおいて再度お試しください。')
    } finally {
      setIsSubmitting(false)
    }
  }

  if (!isOwnProfile) {
    return null
  }

  if (isLoading) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
        <CircularProgress />
      </Box>
    )
  }

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="sticky" color="inherit" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar sx={{ gap: 1.5 }}>
          <IconButton edge="start" onClick={() => navigate(-1)} aria-label="戻る">
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" fontWeight={700}>
            プロフィールを編集
          </Typography>
        </Toolbar>
      </AppBar>

      <Container maxWidth="sm" sx={{ paddingY: 3 }}>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}

          <TextField
            label="表示名"
            value={displayName}
            onChange={(event: ChangeEvent<HTMLInputElement>) => setDisplayName(event.target.value)}
            required
            error={displayName.trim().length === 0 || displayName.length > DISPLAY_NAME_MAX_LENGTH}
            helperText={`${displayName.length}/${DISPLAY_NAME_MAX_LENGTH}`}
          />

          <TextField
            label="自己紹介"
            value={bio}
            onChange={(event: ChangeEvent<HTMLInputElement>) => setBio(event.target.value)}
            multiline
            minRows={3}
            error={bio.length > BIO_MAX_LENGTH}
            helperText={`${bio.length}/${BIO_MAX_LENGTH}`}
          />

          <Button variant="contained" size="large" disabled={!isValid || isSubmitting} onClick={handleSubmit}>
            保存する
          </Button>
        </Stack>
      </Container>
    </Box>
  )
}
