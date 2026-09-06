import type { ReactNode } from 'react'
import { Typography, type SxProps, type Theme } from '@mui/material'

interface TextLinkButtonProps {
  onClick: () => void
  children: ReactNode
  sx?: SxProps<Theme>
}

export function TextLinkButton({ onClick, children, sx }: TextLinkButtonProps) {
  return (
    <Typography
      component="button"
      type="button"
      onClick={onClick}
      variant="body2"
      color="text.secondary"
      sx={{
        background: 'none',
        border: 'none',
        padding: 0,
        cursor: 'pointer',
        textDecoration: 'underline',
        font: 'inherit',
        ...sx,
      }}
    >
      {children}
    </Typography>
  )
}
