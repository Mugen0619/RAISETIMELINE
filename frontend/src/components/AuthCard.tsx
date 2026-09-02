import { Avatar, Box, Paper, Stack, Typography } from '@mui/material'
import type { ReactNode } from 'react'

interface AuthCardProps {
  title: string
  subtitle: string
  children: ReactNode
}

export function AuthCard({ title, subtitle, children }: AuthCardProps) {
  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        padding: 3,
        background: (theme) =>
          `radial-gradient(circle at 15% 12%, ${theme.palette.primary.main}24, transparent 42%), ${theme.palette.background.default}`,
      }}
    >
      <Paper
        elevation={0}
        sx={{
          width: '100%',
          maxWidth: 400,
          padding: 4,
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 4,
        }}
      >
        <Stack alignItems="center" spacing={1} sx={{ marginBottom: 3 }}>
          <Avatar
            sx={{
              width: 46,
              height: 46,
              bgcolor: 'primary.main',
              fontFamily: "'Zen Maru Gothic', sans-serif",
              fontWeight: 900,
            }}
          >
            R
          </Avatar>
          <Typography variant="h6" component="h1" fontWeight={700} textAlign="center">
            {title}
          </Typography>
          <Typography variant="body2" color="text.secondary" textAlign="center">
            {subtitle}
          </Typography>
        </Stack>
        {children}
      </Paper>
    </Box>
  )
}
