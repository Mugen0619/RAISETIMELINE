import { useEffect, useState, type ChangeEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  AppBar,
  Box,
  CircularProgress,
  Container,
  IconButton,
  InputAdornment,
  Stack,
  TextField,
  Toolbar,
  Typography,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import SearchIcon from '@mui/icons-material/Search'
import { searchUsers, type UserSummaryResponse } from '../api/users'
import { ClickableAvatar } from '../components/ClickableAvatar'
import { UserNameLink } from '../components/UserNameLink'

const SEARCH_DEBOUNCE_MS = 300

export function UserSearchPage() {
  const navigate = useNavigate()

  const [keyword, setKeyword] = useState('')
  const [results, setResults] = useState<UserSummaryResponse[]>([])
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [hasSearched, setHasSearched] = useState(false)

  useEffect(() => {
    const trimmed = keyword.trim()

    if (!trimmed) {
      setResults([])
      setHasSearched(false)
      setError(null)
      return
    }

    setIsLoading(true)
    setError(null)

    const timer = window.setTimeout(async () => {
      try {
        const found = await searchUsers(trimmed)
        setResults(found)
      } catch {
        setError('検索に失敗しました。')
      } finally {
        setIsLoading(false)
        setHasSearched(true)
      }
    }, SEARCH_DEBOUNCE_MS)

    return () => window.clearTimeout(timer)
  }, [keyword])

  const showEmptyPrompt = !keyword.trim()
  const showNoResults = !isLoading && !showEmptyPrompt && hasSearched && results.length === 0

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="sticky" color="inherit" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar sx={{ gap: 1.5 }}>
          <IconButton edge="start" onClick={() => navigate(-1)} aria-label="戻る">
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" fontWeight={700}>
            ユーザー検索
          </Typography>
        </Toolbar>
      </AppBar>

      <Container maxWidth="sm" sx={{ paddingY: 2 }}>
        <TextField
          fullWidth
          value={keyword}
          onChange={(event: ChangeEvent<HTMLInputElement>) => setKeyword(event.target.value)}
          placeholder="ユーザー名・表示名で検索"
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon fontSize="small" />
                </InputAdornment>
              ),
            },
          }}
          sx={{ marginBottom: 2 }}
        />

        {error && (
          <Typography color="error" sx={{ marginBottom: 2 }}>
            {error}
          </Typography>
        )}

        {isLoading ? (
          <Stack alignItems="center" sx={{ paddingY: 6 }}>
            <CircularProgress />
          </Stack>
        ) : showEmptyPrompt ? (
          <Typography color="text.secondary" align="center" sx={{ paddingY: 6 }}>
            ユーザー名や表示名で検索してみましょう。
          </Typography>
        ) : showNoResults ? (
          <Typography color="text.secondary" align="center" sx={{ paddingY: 6 }}>
            該当するユーザーが見つかりません。
          </Typography>
        ) : (
          <Stack spacing={1.5}>
            {results.map((user) => (
              <Stack
                key={user.userId}
                direction="row"
                spacing={1.5}
                alignItems="center"
                sx={{ padding: 1.5, border: 1, borderColor: 'divider', borderRadius: 3 }}
              >
                <ClickableAvatar userId={user.userId} displayName={user.displayName} />
                <Box sx={{ minWidth: 0 }}>
                  <UserNameLink userId={user.userId} displayName={user.displayName} />
                  <Typography variant="body2" color="text.secondary" fontFamily="monospace">
                    @{user.username}
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
