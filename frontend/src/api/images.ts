import { apiRequest } from './client'

export const MAX_IMAGES_PER_POST = 4
export const MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024
export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const

interface PresignResponse {
  uploadUrl: string
  imageUrl: string
}

/**
 * サイズ・形式をアップロード前にチェックする。問題なければnull、
 * 問題があればユーザーに表示するエラーメッセージを返す。
 */
export function validateImageFile(file: File): string | null {
  if (!ALLOWED_IMAGE_TYPES.includes(file.type as (typeof ALLOWED_IMAGE_TYPES)[number])) {
    return `${file.name}: 対応していない形式です(jpeg・png・webpのみ)。`
  }
  if (file.size > MAX_IMAGE_SIZE_BYTES) {
    return `${file.name}: ファイルサイズが大きすぎます(5MBまで)。`
  }
  return null
}

async function presignImageUpload(file: File): Promise<PresignResponse> {
  return apiRequest<PresignResponse>('/posts/images/presign', {
    method: 'POST',
    body: JSON.stringify({ contentType: file.type, fileSizeBytes: file.size }),
  })
}

/**
 * 署名付きURLを取得し、S3へ直接PUTアップロードする。成功した場合、
 * 投稿作成/更新APIに渡す画像URLを返す。
 */
export async function uploadPostImage(file: File): Promise<string> {
  const { uploadUrl, imageUrl } = await presignImageUpload(file)

  const uploadResponse = await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type },
    body: file,
  })

  if (!uploadResponse.ok) {
    throw new Error(`image upload failed with status ${uploadResponse.status}`)
  }

  return imageUrl
}
