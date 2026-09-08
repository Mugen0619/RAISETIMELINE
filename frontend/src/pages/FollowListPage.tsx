import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { AppBar, Box, CircularProgress, Container, IconButton, Stack, Toolbar, Typography } from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import { fetchFollowers, fetchFollowing, fetchProfile, type FollowUserResponse } from '../api/users'
import { ClickableAvatar } from '../components/ClickableAvatar'
import { UserNameLink } from '../components/UserNameLink'

interface FollowListPageProps {
  mode: 'following' | 'followers'
}

export function FollowListPage({ mode }: FollowListPageProps) {
  const { userId: userIdParam } = useParams<{ userId: string }>()
  const userId = Number(userIdParam)
  const navigate = useNavigate()

  const [displayName, setDisplayName] = useState('')
  const [users, setUsers] = useState<FollowUserResponse[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const fetchList = mode === 'following' ? fetchFollowing : fetchFollowers
      const [profile, list] = await Promise.all([fetchProfile(userId), fetchList(userId)])
      setDisplayName(profile.displayName)
      setUsers(list)
    } catch {
      setError('一覧の取得に失敗しました。')
    } finally {
      setIsLoading(false)
    }
  }, [userId, mode])

  useEffect(() => {
    load()
  }, [load])

  const title = mode === 'following' ? 'フォロー中' : 'フォロワー'
  const emptyMessage = mode === 'following' ? 'まだ誰もフォローしていません。' : 'まだフォロワーがいません。'

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="sticky" color="inherit" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar sx={{ gap: 1.5 }}>
          <IconButton edge="start" onClick={() => navigate(-1)} aria-label="戻る">
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" fontWeight={700}>
            {displayName ? `${displayName}さんの${title}` : title}
          </Typography>
        </Toolbar>
      </AppBar>

      <Container maxWidth="sm" sx={{ paddingY: 2 }}>
        {error && (
          <Typography color="error" sx={{ marginBottom: 2 }}>
            {error}
          </Typography>
        )}

        {isLoading ? (
          <Stack alignItems="center" sx={{ paddingY: 6 }}>
            <CircularProgress />
          </Stack>
        ) : users.length === 0 ? (
          <Typography color="text.secondary" align="center" sx={{ paddingY: 6 }}>
            {emptyMessage}
          </Typography>
        ) : (
          <Stack spacing={1.5}>
            {users.map((listedUser) => (
              <Stack
                key={listedUser.userId}
                direction="row"
                spacing={1.5}
                alignItems="center"
                sx={{ padding: 1.5, border: 1, borderColor: 'divider', borderRadius: 3 }}
              >
                <ClickableAvatar userId={listedUser.userId} displayName={listedUser.displayName} />
                <Box sx={{ minWidth: 0 }}>
                  <UserNameLink userId={listedUser.userId} displayName={listedUser.displayName} />
                  <Typography variant="body2" color="text.secondary" fontFamily="monospace">
                    @{listedUser.username}
                  </Typography>
                </Box>
              </Stack>
            ))}
          </Stack>
        )}
      </Container>
    </Box>
  )
}
