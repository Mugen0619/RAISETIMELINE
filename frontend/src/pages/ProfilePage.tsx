import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  AppBar,
  Avatar,
  Box,
  Button,
  CircularProgress,
  Container,
  IconButton,
  Paper,
  Stack,
  Toolbar,
  Typography,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import { useAuth } from '../auth/AuthContext'
import { fetchProfile, fetchUserPosts, toggleFollow, type ProfileResponse } from '../api/users'
import { deletePost, toggleLike, type PostResponse } from '../api/posts'
import { avatarColorFor } from '../utils/avatarColor'
import { PostCard } from '../components/PostCard'
import { ConfirmDialog } from '../components/ConfirmDialog'

const PAGE_SIZE = 20

export function ProfilePage() {
  const { userId: userIdParam } = useParams<{ userId: string }>()
  const userId = Number(userIdParam)
  const navigate = useNavigate()
  const { user: currentUser } = useAuth()

  const [profile, setProfile] = useState<ProfileResponse | null>(null)
  const [posts, setPosts] = useState<PostResponse[]>([])
  const [nextPage, setNextPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [isTogglingFollow, setIsTogglingFollow] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<PostResponse | null>(null)

  const sentinelRef = useRef<HTMLDivElement | null>(null)

  const loadProfileAndPosts = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const [profileResult, postsResult] = await Promise.all([
        fetchProfile(userId),
        fetchUserPosts(userId, 0, PAGE_SIZE),
      ])
      setProfile(profileResult)
      setPosts(postsResult.content)
      setNextPage(1)
      setHasMore(postsResult.page.number + 1 < postsResult.page.totalPages)
    } catch {
      setError('プロフィールの取得に失敗しました。')
    } finally {
      setIsLoading(false)
    }
  }, [userId])

  useEffect(() => {
    loadProfileAndPosts()
  }, [loadProfileAndPosts])

  const loadMore = useCallback(async () => {
    if (isLoading || isLoadingMore || !hasMore) return
    setIsLoadingMore(true)
    try {
      const result = await fetchUserPosts(userId, nextPage, PAGE_SIZE)
      setPosts((prev) => [...prev, ...result.content])
      setNextPage((page) => page + 1)
      setHasMore(result.page.number + 1 < result.page.totalPages)
    } catch {
      // 失敗時は何もしない: 再度スクロールした際に再試行される
    } finally {
      setIsLoadingMore(false)
    }
  }, [userId, nextPage, hasMore, isLoading, isLoadingMore])

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

  const handleToggleFollow = async () => {
    if (!profile || isTogglingFollow) return
    setIsTogglingFollow(true)
    try {
      const result = await toggleFollow(profile.userId)
      setProfile({ ...profile, followedByMe: result.following, followerCount: result.followerCount })
    } catch {
      setError('フォロー操作に失敗しました。')
    } finally {
      setIsTogglingFollow(false)
    }
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

  if (isLoading) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
        <CircularProgress />
      </Box>
    )
  }

  if (!profile) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 3 }}>
        <Typography color="error">{error ?? 'ユーザーが見つかりませんでした。'}</Typography>
      </Box>
    )
  }

  const isMe = profile.userId === currentUser?.userId

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="sticky" color="inherit" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar sx={{ gap: 1.5 }}>
          <IconButton edge="start" onClick={() => navigate(-1)} aria-label="戻る">
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" fontWeight={700}>
            {profile.displayName}
          </Typography>
        </Toolbar>
      </AppBar>

      <Container maxWidth="sm" sx={{ paddingY: 2 }}>
        {error && (
          <Typography color="error" sx={{ marginBottom: 2 }}>
            {error}
          </Typography>
        )}

        <Paper variant="outlined" sx={{ padding: 2, borderRadius: 3, marginBottom: 2 }}>
          <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
            <Avatar sx={{ bgcolor: avatarColorFor(profile.userId), width: 64, height: 64, fontSize: 24 }}>
              {profile.displayName.charAt(0)}
            </Avatar>
            {isMe ? (
              <Button variant="outlined" size="small" onClick={() => navigate(`/users/${profile.userId}/edit`)}>
                プロフィールを編集
              </Button>
            ) : (
              <Button
                variant={profile.followedByMe ? 'outlined' : 'contained'}
                size="small"
                onClick={handleToggleFollow}
                disabled={isTogglingFollow}
              >
                {profile.followedByMe ? 'フォロー中' : 'フォローする'}
              </Button>
            )}
          </Stack>

          <Typography variant="h6" fontWeight={700} sx={{ marginTop: 1.5 }}>
            {profile.displayName}
          </Typography>
          <Typography variant="body2" color="text.secondary" fontFamily="monospace">
            @{profile.username}
          </Typography>
          {profile.bio && (
            <Typography sx={{ marginTop: 1, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {profile.bio}
            </Typography>
          )}

          <Stack direction="row" spacing={2.5} sx={{ marginTop: 1.5 }}>
            <Typography
              component="button"
              type="button"
              onClick={() => navigate(`/users/${profile.userId}/following`)}
              variant="body2"
              color="text.secondary"
              sx={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}
            >
              <strong>{profile.followingCount}</strong> フォロー中
            </Typography>
            <Typography
              component="button"
              type="button"
              onClick={() => navigate(`/users/${profile.userId}/followers`)}
              variant="body2"
              color="text.secondary"
              sx={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}
            >
              <strong>{profile.followerCount}</strong> フォロワー
            </Typography>
          </Stack>
        </Paper>

        {posts.length === 0 ? (
          <Typography color="text.secondary" align="center" sx={{ paddingY: 6 }}>
            まだ投稿がありません。
          </Typography>
        ) : (
          <Stack spacing={1.5}>
            {posts.map((post) => (
              <PostCard
                key={post.id}
                post={post}
                isOwner={post.userId === currentUser?.userId}
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

      <ConfirmDialog
        open={deleteTarget !== null}
        title="投稿を削除しますか？"
        description="削除すると元に戻せません。"
        onCancel={() => setDeleteTarget(null)}
        onConfirm={handleDeleteConfirmed}
      />
    </Box>
  )
}
