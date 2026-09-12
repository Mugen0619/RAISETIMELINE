import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import {
  Alert,
  Box,
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
import AddPhotoAlternateIcon from '@mui/icons-material/AddPhotoAlternate'
import { MAX_IMAGES_PER_POST, uploadPostImage, validateImageFile } from '../api/images'

const MAX_BODY_LENGTH = 280

interface PendingImage {
  id: string
  previewUrl: string
  /** 新規に選択したファイル。投稿時にアップロードする */
  file?: File
  /** 既にアップロード済みの画像(編集時の初期値)。そのままURLを再利用する */
  remoteUrl?: string
}

interface PostComposerDialogProps {
  open: boolean
  mode: 'create' | 'edit'
  initialBody?: string
  initialImageUrls?: string[]
  onClose: () => void
  onSubmit: (body: string, imageUrls: string[]) => Promise<void>
}

// bodyの初期値はマウント時に一度だけ読む。開くたびに内容をリセットしたい場合は
// 呼び出し側でkeyを変えて再マウントさせること(例: TimelinePage.tsx参照)。
export function PostComposerDialog({
  open,
  mode,
  initialBody = '',
  initialImageUrls = [],
  onClose,
  onSubmit,
}: PostComposerDialogProps) {
  const [body, setBody] = useState(initialBody)
  const [images, setImages] = useState<PendingImage[]>(() =>
    initialImageUrls.map((url) => ({ id: url, previewUrl: url, remoteUrl: url })),
  )
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  // アンマウント時のクリーンアップで最新のimagesを参照するため、refでミラーする
  const imagesRef = useRef(images)
  useEffect(() => {
    imagesRef.current = images
  }, [images])

  useEffect(() => {
    // 選択済みのローカルファイルに対して発行したobject URLを、アンマウント時に解放する
    return () => {
      imagesRef.current.forEach((image) => {
        if (image.file) URL.revokeObjectURL(image.previewUrl)
      })
    }
  }, [])

  const remaining = MAX_BODY_LENGTH - body.length
  const bodyOverLimit = body.length > MAX_BODY_LENGTH
  const hasBody = body.trim().length > 0
  const hasImages = images.length > 0
  const isValid = !bodyOverLimit && (hasBody || hasImages)

  const handleChange = (event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    setBody(event.target.value)
  }

  const handleFilesSelected = (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? [])
    event.target.value = ''
    if (files.length === 0) return

    const availableSlots = MAX_IMAGES_PER_POST - images.length
    if (availableSlots <= 0) {
      setError(`画像は最大${MAX_IMAGES_PER_POST}枚までです。`)
      return
    }

    const accepted: PendingImage[] = []
    let rejectionMessage: string | null = null
    let skippedForLimit = false

    files.forEach((file, index) => {
      if (accepted.length >= availableSlots) {
        skippedForLimit = true
        return
      }
      const validationError = validateImageFile(file)
      if (validationError) {
        rejectionMessage = rejectionMessage ?? validationError
        return
      }
      accepted.push({ id: `${file.name}-${file.lastModified}-${index}`, previewUrl: URL.createObjectURL(file), file })
    })

    if (accepted.length > 0) {
      setImages((prev) => [...prev, ...accepted])
    }

    if (rejectionMessage) {
      setError(rejectionMessage)
    } else if (skippedForLimit) {
      setError(`画像は最大${MAX_IMAGES_PER_POST}枚までです。`)
    } else {
      setError(null)
    }
  }

  const handleRemoveImage = (id: string) => {
    setImages((prev) => {
      const target = prev.find((image) => image.id === id)
      if (target?.file) URL.revokeObjectURL(target.previewUrl)
      return prev.filter((image) => image.id !== id)
    })
  }

  const handleSubmit = async () => {
    if (!isValid) return
    setError(null)
    setIsSubmitting(true)
    try {
      const imageUrls: string[] = []
      for (const image of images) {
        if (image.remoteUrl) {
          imageUrls.push(image.remoteUrl)
        } else if (image.file) {
          imageUrls.push(await uploadPostImage(image.file))
        }
      }
      await onSubmit(body.trim(), imageUrls)
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

          <Stack direction="row" spacing={1} alignItems="center">
            <Button
              size="small"
              variant="outlined"
              startIcon={<AddPhotoAlternateIcon fontSize="small" />}
              onClick={() => fileInputRef.current?.click()}
              disabled={images.length >= MAX_IMAGES_PER_POST}
            >
              画像を追加
            </Button>
            <Typography variant="body2" color="text.secondary">
              最大{MAX_IMAGES_PER_POST}枚({images.length}/{MAX_IMAGES_PER_POST})
            </Typography>
            <input
              ref={fileInputRef}
              type="file"
              name="postImages"
              accept="image/jpeg,image/png,image/webp"
              multiple
              hidden
              onChange={handleFilesSelected}
              aria-label="画像を選択"
            />
          </Stack>

          {images.length > 0 && (
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
              {images.map((image) => (
                <Box key={image.id} sx={{ position: 'relative', width: 76, height: 76 }}>
                  <Box
                    component="img"
                    src={image.previewUrl}
                    alt=""
                    sx={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: 1.5 }}
                  />
                  <IconButton
                    size="small"
                    onClick={() => handleRemoveImage(image.id)}
                    aria-label="画像を削除"
                    sx={{
                      position: 'absolute',
                      top: -8,
                      right: -8,
                      bgcolor: 'rgba(0,0,0,0.6)',
                      color: '#fff',
                      width: 20,
                      height: 20,
                      '&:hover': { bgcolor: 'rgba(0,0,0,0.8)' },
                    }}
                  >
                    <CloseIcon sx={{ fontSize: 14 }} />
                  </IconButton>
                </Box>
              ))}
            </Stack>
          )}
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
