import type { MouseEvent } from 'react'
import { Avatar, Box } from '@mui/material'
import { useNavigate } from 'react-router-dom'
import { avatarColorFor } from '../utils/avatarColor'

interface ClickableAvatarProps {
  userId: number
  displayName: string
  size?: number
}

export function ClickableAvatar({ userId, displayName, size = 40 }: ClickableAvatarProps) {
  const navigate = useNavigate()

  const handleClick = (event: MouseEvent) => {
    event.stopPropagation()
    navigate(`/users/${userId}`)
  }

  return (
    <Box
      component="button"
      type="button"
      onClick={handleClick}
      aria-label={`${displayName}のプロフィール`}
      sx={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer', flex: 'none', lineHeight: 0 }}
    >
      <Avatar sx={{ bgcolor: avatarColorFor(userId), width: size, height: size, fontSize: size * 0.375 }}>
        {displayName.charAt(0)}
      </Avatar>
    </Box>
  )
}
