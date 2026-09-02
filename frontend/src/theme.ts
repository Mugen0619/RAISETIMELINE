import { createTheme } from '@mui/material/styles'

// docs/prototype/index.html のCSS変数(--bg, --accent 等)に準拠したライトテーマ
export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: '#0F7B6C',
      dark: '#0B5F53',
      contrastText: '#FFFFFF',
    },
    error: {
      main: '#C7402B',
    },
    background: {
      default: '#F5F4EE',
      paper: '#FFFFFF',
    },
    text: {
      primary: '#1A1F1C',
      secondary: '#656B60',
    },
    divider: '#DDDBCE',
  },
  shape: {
    borderRadius: 14,
  },
  typography: {
    fontFamily: "'M PLUS 1', 'Hiragino Sans', 'Yu Gothic', sans-serif",
    h1: { fontFamily: "'Zen Maru Gothic', 'Hiragino Maru Gothic ProN', sans-serif" },
    h2: { fontFamily: "'Zen Maru Gothic', 'Hiragino Maru Gothic ProN', sans-serif" },
    h3: { fontFamily: "'Zen Maru Gothic', 'Hiragino Maru Gothic ProN', sans-serif" },
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: 999,
          textTransform: 'none',
          fontWeight: 700,
        },
      },
    },
    MuiTextField: {
      defaultProps: {
        fullWidth: true,
        size: 'small',
      },
    },
  },
})
