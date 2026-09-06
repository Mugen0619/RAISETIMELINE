import { useState, type ChangeEvent } from 'react'
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'

const MAX_BODY_LENGTH = 280

interface PostComposerDialogProps {
  open: boolean
  mode: 'create' | 'edit'
  initialBody?: string
  onClose: () => void
  onSubmit: (body: string) => Promise<void>
}

// bodyの初期値はマウント時に一度だけ読む。開くたびに内容をリセットしたい場合は
// 呼び出し側でkeyを変えて再マウントさせること(例: TimelinePage.tsx参照)。
export function PostComposerDialog({ open, mode, initialBody = '', onClose, onSubmit }: PostComposerDialogProps) {
  const [body, setBody] = useState(initialBody)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const remaining = MAX_BODY_LENGTH - body.length
  const isValid = body.trim().length > 0 && body.length <= MAX_BODY_LENGTH

  const handleChange = (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    setBody(event.target.value)
  }

  const handleSubmit = async () => {
    if (!isValid) return
    setError(null)
    setIsSubmitting(true)
    try {
      await onSubmit(body.trim())
    } catch {
      setError(mode === 'create' ? '投稿に失敗しました。時間をおいて再度お試しください。' : '更新に失敗しました。時間をおいて再度お試しください。')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        {mode === 'create' ? '投稿する' : '投稿を編集'}
        <IconButton onClick={onClose} size="small" aria-label="閉じる">
          <CloseIcon fontSize="small" />
        </IconButton>
      </DialogTitle>
      <DialogContent>
        <Stack spacing={1.5}>
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            autoFocus
            multiline
            minRows={4}
            placeholder="いまどうしてる？(280文字まで)"
            value={body}
            onChange={handleChange}
          />
          <Typography
            variant="body2"
            align="right"
            fontFamily="monospace"
            color={remaining < 0 ? 'error.main' : remaining <= 20 ? 'warning.main' : 'text.secondary'}
          >
            {remaining}
          </Typography>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ padding: 2 }}>
        <Button
          variant="contained"
          disabled={!isValid || isSubmitting}
          onClick={handleSubmit}
        >
          {mode === 'create' ? '投稿する' : '更新する'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
