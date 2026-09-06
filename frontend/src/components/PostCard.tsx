import { Avatar, Box, Card, CardContent, Stack, Typography } from '@mui/material'
import type { PostResponse } from '../api/posts'
import { avatarColorFor } from '../utils/avatarColor'
import { formatRelativeTime } from '../utils/relativeTime'

interface PostCardProps {
  post: PostResponse
  isOwner: boolean
  onEdit: (post: PostResponse) => void
  onDelete: (post: PostResponse) => void
}

export function PostCard({ post, isOwner, onEdit, onDelete }: PostCardProps) {
  return (
    <Card variant="outlined" sx={{ borderRadius: 3 }}>
      <CardContent>
        <Stack direction="row" spacing={1.5}>
          <Avatar sx={{ bgcolor: avatarColorFor(post.userId), width: 40, height: 40, fontSize: 15 }}>
            {post.displayName.charAt(0)}
          </Avatar>
          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Stack direction="row" spacing={0.75} alignItems="baseline" flexWrap="wrap">
              <Typography component="span" fontWeight={700} fontSize={14.5}>
                {post.displayName}
              </Typography>
              <Typography component="span" variant="body2" color="text.secondary" fontFamily="monospace">
                @{post.username}
              </Typography>
              <Typography component="span" variant="body2" color="text.secondary" fontFamily="monospace">
                ・{formatRelativeTime(post.createdAt)}
              </Typography>
            </Stack>
            <Typography sx={{ marginTop: 0.5, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
              {post.body}
            </Typography>
            {isOwner && (
              <Stack direction="row" spacing={2} sx={{ marginTop: 1 }}>
                <Typography
                  component="button"
                  onClick={() => onEdit(post)}
                  variant="body2"
                  color="text.secondary"
                  sx={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer', textDecoration: 'underline' }}
                >
                  編集
                </Typography>
                <Typography
                  component="button"
                  onClick={() => onDelete(post)}
                  variant="body2"
                  color="text.secondary"
                  sx={{
                    background: 'none',
                    border: 'none',
                    padding: 0,
                    cursor: 'pointer',
                    textDecoration: 'underline',
                    '&:hover': { color: 'error.main' },
                  }}
                >
                  削除
                </Typography>
              </Stack>
            )}
          </Box>
        </Stack>
      </CardContent>
    </Card>
  )
}
