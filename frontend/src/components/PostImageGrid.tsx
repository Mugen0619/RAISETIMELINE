import { Box } from '@mui/material'

interface PostImageGridProps {
  imageUrls: string[]
}

// 1枚なら大きく1枚表示、2枚以上なら2列グリッドで表示する
export function PostImageGrid({ imageUrls }: PostImageGridProps) {
  if (imageUrls.length === 0) return null

  return (
    <Box
      sx={{
        marginTop: 1,
        display: 'grid',
        gridTemplateColumns: imageUrls.length === 1 ? '1fr' : '1fr 1fr',
        gap: 0.5,
        borderRadius: 3,
        overflow: 'hidden',
      }}
    >
      {imageUrls.map((url) => (
        <Box
          key={url}
          component="img"
          src={url}
          alt=""
          sx={{
            width: '100%',
            height: imageUrls.length === 1 ? 'auto' : 160,
            maxHeight: 320,
            objectFit: 'cover',
            display: 'block',
          }}
        />
      ))}
    </Box>
  )
}
