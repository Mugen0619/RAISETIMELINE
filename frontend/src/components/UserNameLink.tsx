import type { MouseEvent } from 'react'
import { Typography } from '@mui/material'
import { useNavigate } from 'react-router-dom'

interface UserNameLinkProps {
  userId: number
  displayName: string
  fontSize?: number | string
}

export function UserNameLink({ userId, displayName, fontSize = 14.5 }: UserNameLinkProps) {
  const navigate = useNavigate()

  const handleClick = (event: MouseEvent) => {
    event.stopPropagation()
    navigate(`/users/${userId}`)
  }

  return (
    <Typography
      component="button"
      type="button"
      onClick={handleClick}
      fontWeight={700}
      fontSize={fontSize}
      sx={{
        background: 'none',
        border: 'none',
        padding: 0,
        cursor: 'pointer',
        color: 'text.primary',
        '&:hover': { textDecoration: 'underline' },
      }}
    >
      {displayName}
    </Typography>
  )
}
