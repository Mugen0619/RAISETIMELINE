import { Box, Card, CardActions, CardContent, IconButton, Stack, Typography } from '@mui/material'
import FavoriteIcon from '@mui/icons-material/Favorite'
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder'
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline'
import { useNavigate } from 'react-router-dom'
import type { PostResponse } from '../api/posts'
import { formatRelativeTime } from '../utils/relativeTime'
import { TextLinkButton } from './TextLinkButton'
import { ClickableAvatar } from './ClickableAvatar'
import { UserNameLink } from './UserNameLink'

interface PostCardProps {
  post: PostResponse
  isOwner: boolean
  onDelete: (post: PostResponse) => void
  onToggleLike: (post: PostResponse) => void
}

export function PostCard({ post, isOwner, onDelete, onToggleLike }: PostCardProps) {
  const navigate = useNavigate()

  return (
    <Card variant="outlined" sx={{ borderRadius: 3 }}>
      <CardContent>
        <Stack direction="row" spacing={1.5}>
          <ClickableAvatar userId={post.userId} displayName={post.displayName} />
          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Stack direction="row" spacing={0.75} alignItems="baseline" flexWrap="wrap">
              <UserNameLink userId={post.userId} displayName={post.displayName} />
              <Typography component="span" variant="body2" color="text.secondary" fontFamily="monospace">
                @{post.username}
              </Typography>
              <Typography component="span" variant="body2" color="text.secondary" fontFamily="monospace">
                ・{formatRelativeTime(post.createdAt)}
              </Typography>
            </Stack>
            <Box
              role="button"
              tabIndex={0}
              onClick={() => navigate(`/posts/${post.id}`)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' || event.key === ' ') navigate(`/posts/${post.id}`)
              }}
              sx={{ cursor: 'pointer' }}
            >
              <Typography sx={{ marginTop: 0.5, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                {post.body}
              </Typography>
            </Box>
          </Box>
        </Stack>
      </CardContent>

      <CardActions sx={{ paddingX: 2, paddingBottom: 1.5, paddingTop: 0 }}>
        <Stack direction="row" spacing={0.5} alignItems="center">
          <IconButton
            size="small"
            onClick={() => onToggleLike(post)}
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

        <Stack direction="row" spacing={0.5} alignItems="center" sx={{ marginLeft: 1.5 }}>
          <ChatBubbleOutlineIcon fontSize="small" sx={{ color: 'text.secondary' }} />
          <Typography variant="body2" color="text.secondary" fontFamily="monospace">
            {post.commentCount}
          </Typography>
        </Stack>

        {isOwner && (
          <TextLinkButton onClick={() => onDelete(post)} sx={{ marginLeft: 'auto', '&:hover': { color: 'error.main' } }}>
            削除
          </TextLinkButton>
        )}
      </CardActions>
    </Card>
  )
}
