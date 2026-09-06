import { useCallback, useEffect, useState, type ChangeEvent } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  AppBar,
  Avatar,
  Box,
  CircularProgress,
  Container,
  IconButton,
  Paper,
  Stack,
  TextField,
  Toolbar,
  Typography,
} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import FavoriteIcon from '@mui/icons-material/Favorite'
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder'
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline'
import SendIcon from '@mui/icons-material/Send'
import { useAuth } from '../auth/AuthContext'
import {
  createComment,
  deleteComment,
  deletePost,
  fetchComments,
  fetchPostDetail,
  toggleLike,
  updatePost,
  type CommentResponse,
  type PostResponse,
} from '../api/posts'
import { avatarColorFor } from '../utils/avatarColor'
import { formatRelativeTime } from '../utils/relativeTime'
import { PostComposerDialog } from '../components/PostComposerDialog'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { TextLinkButton } from '../components/TextLinkButton'

const MAX_COMMENT_LENGTH = 280

export function PostDetailPage() {
  const { id } = useParams<{ id: string }>()
  const postId = Number(id)
  const navigate = useNavigate()
  const { user } = useAuth()

  const [post, setPost] = useState<PostResponse | null>(null)
  const [comments, setComments] = useState<CommentResponse[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [commentBody, setCommentBody] = useState('')
  const [isSubmittingComment, setIsSubmittingComment] = useState(false)

  const [editOpen, setEditOpen] = useState(false)
  const [deletePostConfirmOpen, setDeletePostConfirmOpen] = useState(false)
  const [commentToDelete, setCommentToDelete] = useState<CommentResponse | null>(null)

  const loadData = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const [postDetail, commentList] = await Promise.all([fetchPostDetail(postId), fetchComments(postId)])
      setPost(postDetail)
      setComments(commentList)
    } catch {
      setError('投稿の取得に失敗しました。')
    } finally {
      setIsLoading(false)
    }
  }, [postId])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleToggleLike = async () => {
    if (!post) return
    try {
      const result = await toggleLike(post.id)
      setPost({ ...post, likeCount: result.likeCount, likedByMe: result.liked })
    } catch {
      setError('いいねの操作に失敗しました。')
    }
  }

  const commentIsValid = commentBody.trim().length > 0 && commentBody.length <= MAX_COMMENT_LENGTH

  const handleCommentSubmit = async () => {
    if (!commentIsValid) return
    setIsSubmittingComment(true)
    setError(null)
    try {
      const created = await createComment(postId, commentBody.trim())
      setComments((prev) => [...prev, created])
      setPost((prev) => (prev ? { ...prev, commentCount: prev.commentCount + 1 } : prev))
      setCommentBody('')
    } catch {
      setError('コメントの投稿に失敗しました。')
    } finally {
      setIsSubmittingComment(false)
    }
  }

  const handleDeleteCommentConfirmed = async () => {
    if (!commentToDelete) return
    const target = commentToDelete
    setCommentToDelete(null)
    try {
      await deleteComment(target.id)
      setComments((prev) => prev.filter((comment) => comment.id !== target.id))
      setPost((prev) => (prev ? { ...prev, commentCount: Math.max(0, prev.commentCount - 1) } : prev))
    } catch {
      setError('コメントの削除に失敗しました。')
    }
  }

  const handleEditSubmit = async (body: string) => {
    if (!post) return
    const updated = await updatePost(post.id, body)
    setPost(updated)
    setEditOpen(false)
  }

  const handleDeletePostConfirmed = async () => {
    if (!post) return
    setDeletePostConfirmOpen(false)
    try {
      await deletePost(post.id)
      navigate('/home', { replace: true })
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

  if (!post) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 3 }}>
        <Typography color="error">{error ?? '投稿が見つかりませんでした。'}</Typography>
      </Box>
    )
  }

  const isOwner = post.userId === user?.userId

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar position="sticky" color="inherit" elevation={0} sx={{ borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar sx={{ gap: 1.5 }}>
          <IconButton edge="start" onClick={() => navigate('/home')} aria-label="戻る">
            <ArrowBackIcon />
          </IconButton>
          <Typography variant="h6" fontWeight={700}>
            投稿
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
          <Stack direction="row" spacing={1.5}>
            <Avatar sx={{ bgcolor: avatarColorFor(post.userId) }}>{post.displayName.charAt(0)}</Avatar>
            <Box sx={{ minWidth: 0, flex: 1 }}>
              <Stack direction="row" spacing={0.75} alignItems="baseline" flexWrap="wrap">
                <Typography component="span" fontWeight={700}>
                  {post.displayName}
                </Typography>
                <Typography component="span" variant="body2" color="text.secondary" fontFamily="monospace">
                  @{post.username}
                </Typography>
                <Typography component="span" variant="body2" color="text.secondary" fontFamily="monospace">
                  ・{formatRelativeTime(post.createdAt)}
                </Typography>
              </Stack>
              <Typography sx={{ marginTop: 1, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                {post.body}
              </Typography>

              <Stack direction="row" spacing={0} alignItems="center" sx={{ marginTop: 1.5 }}>
                <Stack direction="row" spacing={0.5} alignItems="center">
                  <IconButton
                    size="small"
                    onClick={handleToggleLike}
                    color={post.likedByMe ? 'error' : 'default'}
                    aria-pressed={post.likedByMe}
                    aria-label="いいね"
                  >
                    {post.likedByMe ? <FavoriteIcon fontSize="small" /> : <FavoriteBorderIcon fontSize="small" />}
                  </IconButton>
                  <Typography variant="body2" color="text.secondary" fontFamily="monospace">
                    {post.likeCount}
                  </Typography>
                </Stack>

                <Stack direction="row" spacing={0.5} alignItems="center" sx={{ marginLeft: 2 }}>
                  <ChatBubbleOutlineIcon fontSize="small" sx={{ color: 'text.secondary' }} />
                  <Typography variant="body2" color="text.secondary" fontFamily="monospace">
                    {post.commentCount}
                  </Typography>
                </Stack>

                {isOwner && (
                  <Stack direction="row" spacing={2} sx={{ marginLeft: 'auto' }}>
                    <TextLinkButton onClick={() => setEditOpen(true)}>編集</TextLinkButton>
                    <TextLinkButton
                      onClick={() => setDeletePostConfirmOpen(true)}
                      sx={{ '&:hover': { color: 'error.main' } }}
                    >
                      削除
                    </TextLinkButton>
                  </Stack>
                )}
              </Stack>
            </Box>
          </Stack>
        </Paper>

        <Stack direction="row" spacing={1.5} alignItems="center" sx={{ marginBottom: 2 }}>
          <TextField
            fullWidth
            size="small"
            placeholder="コメントする…"
            value={commentBody}
            onChange={(event: ChangeEvent<HTMLInputElement>) => setCommentBody(event.target.value)}
          />
          <IconButton
            color="primary"
            disabled={!commentIsValid || isSubmittingComment}
            onClick={handleCommentSubmit}
            aria-label="コメントを送信"
          >
            <SendIcon />
          </IconButton>
        </Stack>

        <Stack spacing={1.5}>
          {comments.length === 0 ? (
            <Typography color="text.secondary" align="center" sx={{ paddingY: 4 }}>
              まだコメントはありません。
            </Typography>
          ) : (
            comments.map((comment) => (
              <Paper key={comment.id} variant="outlined" sx={{ padding: 1.5, borderRadius: 3 }}>
                <Stack direction="row" spacing={1.5}>
                  <Avatar sx={{ width: 32, height: 32, fontSize: 13, bgcolor: avatarColorFor(comment.userId) }}>
                    {comment.displayName.charAt(0)}
                  </Avatar>
                  <Box sx={{ minWidth: 0, flex: 1 }}>
                    <Stack direction="row" spacing={0.75} alignItems="baseline">
                      <Typography component="span" fontWeight={700} fontSize={13.5}>
                        {comment.displayName}
                      </Typography>
                      <Typography component="span" variant="body2" color="text.secondary" fontFamily="monospace" fontSize={12}>
                        ・{formatRelativeTime(comment.createdAt)}
                      </Typography>
                    </Stack>
                    <Typography variant="body2" sx={{ marginTop: 0.5, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                      {comment.body}
                    </Typography>
                    {comment.userId === user?.userId && (
                      <Box sx={{ marginTop: 0.5 }}>
                        <TextLinkButton onClick={() => setCommentToDelete(comment)} sx={{ '&:hover': { color: 'error.main' } }}>
                          削除
                        </TextLinkButton>
                      </Box>
                    )}
                  </Box>
                </Stack>
              </Paper>
            ))
          )}
        </Stack>
      </Container>

      <PostComposerDialog
        key={editOpen ? `edit-${post.id}` : 'edit-closed'}
        open={editOpen}
        mode="edit"
        initialBody={post.body}
        onClose={() => setEditOpen(false)}
        onSubmit={handleEditSubmit}
      />

      <ConfirmDialog
        open={deletePostConfirmOpen}
        title="投稿を削除しますか？"
        description="削除すると元に戻せません。"
        onCancel={() => setDeletePostConfirmOpen(false)}
        onConfirm={handleDeletePostConfirmed}
      />

      <ConfirmDialog
        open={commentToDelete !== null}
        title="コメントを削除しますか？"
        description="削除すると元に戻せません。"
        onCancel={() => setCommentToDelete(null)}
        onConfirm={handleDeleteCommentConfirmed}
      />
    </Box>
  )
}
