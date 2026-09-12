import { useCallback, useEffect, useRef, useState } from 'react'
import {
  AppBar,
  Box,
  Button,
  CircularProgress,
  Container,
  Fab,
  IconButton,
  Paper,
  Snackbar,
  Stack,
  Tab,
  Tabs,
  Toolbar,
  Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import SearchIcon from '@mui/icons-material/Search'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import {
  createPost,
  deletePost,
  fetchFollowingTimeline,
  fetchTimeline,
  toggleLike,
  type PostResponse,
  type TimelinePage as TimelinePageResponse,
} from '../api/posts'
import { PostCard } from '../components/PostCard'
import { PostComposerDialog } from '../components/PostComposerDialog'
import { ConfirmDialog } from '../components/ConfirmDialog'

const PAGE_SIZE = 20
const NEW_POSTS_POLL_INTERVAL_MS = 30000

type TabValue = 'all' | 'following'

function fetchTimelineForTab(tab: TabValue, page: number, size: number): Promise<TimelinePageResponse> {
  return tab === 'all' ? fetchTimeline(page, size) : fetchFollowingTimeline(page, size)
}

export function TimelinePage() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()

  const [tab, setTab] = useState<TabValue>('all')
  const [posts, setPosts] = useState<PostResponse[]>([])
  const [nextPage, setNextPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [isLoadingInitial, setIsLoadingInitial] = useState(true)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [composerOpen, setComposerOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<PostResponse | null>(null)
  const [hasNewPosts, setHasNewPosts] = useState(false)

  const sentinelRef = useRef<HTMLDivElement | null>(null)
  const postsRef = useRef<PostResponse[]>(posts)
  useEffect(() => {
    postsRef.current = posts
  }, [posts])

  const loadInitial = useCallback(async () => {
    setIsLoadingInitial(true)
    setError(null)
    try {
      const result = await fetchTimelineForTab(tab, 0, PAGE_SIZE)
      setPosts(result.content)
      setNextPage(1)
      setHasMore(result.page.number + 1 < result.page.totalPages)
      setHasNewPosts(false)
    } catch {
      setError('タイムラインの取得に失敗しました。')
    } finally {
      setIsLoadingInitial(false)
    }
  }, [tab])

  useEffect(() => {
    loadInitial()
  }, [loadInitial])

  const loadMore = useCallback(async () => {
    // 初回読み込み完了前はsentinelがビューポート内に入り得るため、
    // isLoadingInitialで初回フェッチと競合しないようにガードする
    if (isLoadingInitial || isLoadingMore || !hasMore) return
    setIsLoadingMore(true)
    try {
      const result = await fetchTimelineForTab(tab, nextPage, PAGE_SIZE)
      setPosts((prev) => [...prev, ...result.content])
      setNextPage((page) => page + 1)
      setHasMore(result.page.number + 1 < result.page.totalPages)
    } catch {
      // 失敗時は何もしない: 再度スクロールした際に再試行される
    } finally {
      setIsLoadingMore(false)
    }
  }, [tab, nextPage, hasMore, isLoadingMore, isLoadingInitial])

  useEffect(() => {
    const node = sentinelRef.current
    if (!node) return

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting) loadMore()
      },
      { rootMargin: '200px' },
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [loadMore])

  useEffect(() => {
    const timer = window.setInterval(async () => {
      try {
        const result = await fetchTimelineForTab(tab, 0, 1)
        const latest = result.content[0]
        const currentTop = postsRef.current[0]
        if (latest && (!currentTop || latest.id !== currentTop.id)) {
          setHasNewPosts(true)
        }
      } catch {
        // ポーリング失敗時は次回に任せる
      }
    }, NEW_POSTS_POLL_INTERVAL_MS)

    return () => window.clearInterval(timer)
  }, [tab])

  const handleRefreshToLatest = () => {
    loadInitial()
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const handleCreateSubmit = async (body: string, imageUrls: string[]) => {
    const created = await createPost(body, imageUrls)
    if (tab === 'all') {
      setPosts((prev) => [created, ...prev])
    }
    setComposerOpen(false)
  }

  const handleToggleLike = async (post: PostResponse) => {
    try {
      const result = await toggleLike(post.id)
      setPosts((prev) =>
        prev.map((p) => (p.id === post.id ? { ...p, likeCount: result.likeCount, likedByMe: result.liked } : p)),
      )
    } catch {
      setError('いいねの操作に失敗しました。')
    }
  }

  const handleDeleteConfirmed = async () => {
    if (!deleteTarget) return
    const target = deleteTarget
    setDeleteTarget(null)
    try {
      await deletePost(target.id)
      setPosts((prev) => prev.filter((post) => post.id !== target.id))
    } catch {
      setError('削除に失敗しました。時間をおいて再度お試しください。')
    }
  }

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="sticky" color="inherit" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar sx={{ gap: 1.5 }}>
          <Typography variant="h6" fontWeight={700} fontFamily="'Zen Maru Gothic', sans-serif">
            タイムライン
          </Typography>
          <Box sx={{ flexGrow: 1 }} />
          <IconButton size="small" onClick={() => navigate('/search')} aria-label="ユーザー検索">
            <SearchIcon fontSize="small" />
          </IconButton>
          <Typography
            component="button"
            type="button"
            onClick={() => user && navigate(`/users/${user.userId}`)}
            variant="body2"
            color="text.secondary"
            sx={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer', '&:hover': { textDecoration: 'underline' } }}
          >
            @{user?.username}
          </Typography>
          <Button size="small" color="inherit" onClick={handleLogout}>
            ログアウト
          </Button>
        </Toolbar>
      </AppBar>

      <Container maxWidth="sm" sx={{ paddingY: 2 }}>
        <Tabs value={tab} onChange={(_, value: TabValue) => setTab(value)} sx={{ marginBottom: 2 }}>
          <Tab label="全体" value="all" />
          <Tab label="フォロー中" value="following" />
        </Tabs>

        <Paper
          variant="outlined"
          role="button"
          tabIndex={0}
          onClick={() => setComposerOpen(true)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') setComposerOpen(true)
          }}
          sx={{ padding: 1.5, marginBottom: 2, borderRadius: 3, cursor: 'pointer' }}
        >
          <Typography color="text.secondary">いまどうしてる？</Typography>
        </Paper>

        {error && (
          <Typography color="error" sx={{ marginBottom: 2 }}>
            {error}
          </Typography>
        )}

        {isLoadingInitial ? (
          <Stack alignItems="center" sx={{ paddingY: 6 }}>
            <CircularProgress />
          </Stack>
        ) : posts.length === 0 ? (
          <Typography color="text.secondary" align="center" sx={{ paddingY: 6 }}>
            {tab === 'following' ? 'フォロー中のユーザーの投稿はまだありません。' : 'まだ投稿がありません。'}
          </Typography>
        ) : (
          <Stack spacing={1.5}>
            {posts.map((post) => (
              <PostCard
                key={post.id}
                post={post}
                isOwner={post.userId === user?.userId}
                onDelete={setDeleteTarget}
                onToggleLike={handleToggleLike}
              />
            ))}
          </Stack>
        )}

        <Box ref={sentinelRef} sx={{ height: 1 }} />
        {isLoadingMore && (
          <Stack alignItems="center" sx={{ paddingY: 3 }}>
            <CircularProgress size={24} />
          </Stack>
        )}
      </Container>

      <Fab
        color="primary"
        onClick={() => setComposerOpen(true)}
        sx={{ position: 'fixed', bottom: 24, right: 24 }}
        aria-label="投稿する"
      >
        <AddIcon />
      </Fab>

      <PostComposerDialog
        key={composerOpen ? 'create-open' : 'create-closed'}
        open={composerOpen}
        mode="create"
        onClose={() => setComposerOpen(false)}
        onSubmit={handleCreateSubmit}
      />

      <ConfirmDialog
        open={deleteTarget !== null}
        title="投稿を削除しますか？"
        description="削除すると元に戻せません。"
        onCancel={() => setDeleteTarget(null)}
        onConfirm={handleDeleteConfirmed}
      />

      <Snackbar
        open={hasNewPosts}
        message="新しい投稿があります"
        action={
          <Button color="primary" size="small" onClick={handleRefreshToLatest}>
            更新
          </Button>
        }
        anchorOrigin={{ vertical: 'top', horizontal: 'center' }}
      />
    </Box>
  )
}
