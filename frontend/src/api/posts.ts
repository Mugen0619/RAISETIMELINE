import { apiRequest } from './client'

export interface PostResponse {
  id: number
  userId: number
  username: string
  displayName: string
  body: string
  createdAt: string
  updatedAt: string
  commentCount: number
  likeCount: number
  likedByMe: boolean
}

export interface CommentResponse {
  id: number
  postId: number
  userId: number
  username: string
  displayName: string
  body: string
  createdAt: string
}

export interface LikeResponse {
  postId: number
  liked: boolean
  likeCount: number
}

interface PageMeta {
  size: number
  number: number
  totalElements: number
  totalPages: number
}

export interface TimelinePage {
  content: PostResponse[]
  page: PageMeta
}

interface CommentsPage {
  content: CommentResponse[]
  page: PageMeta
}

// コメントの追加ページネーション(無限スクロール等)は現状不要なため、
// 1投稿あたりの想定コメント数を十分カバーできるサイズで1ページのみ取得する
const COMMENTS_PAGE_SIZE = 100

export async function createPost(body: string): Promise<PostResponse> {
  return apiRequest<PostResponse>('/posts', {
    method: 'POST',
    body: JSON.stringify({ body }),
  })
}

export async function fetchTimeline(page: number, size: number): Promise<TimelinePage> {
  return apiRequest<TimelinePage>(`/posts?page=${page}&size=${size}`, { method: 'GET' })
}

export async function fetchPostDetail(id: number): Promise<PostResponse> {
  return apiRequest<PostResponse>(`/posts/${id}`, { method: 'GET' })
}

export async function updatePost(id: number, body: string): Promise<PostResponse> {
  return apiRequest<PostResponse>(`/posts/${id}`, {
    method: 'PUT',
    body: JSON.stringify({ body }),
  })
}

export async function deletePost(id: number): Promise<void> {
  await apiRequest<void>(`/posts/${id}`, { method: 'DELETE' })
}

export async function fetchComments(postId: number): Promise<CommentResponse[]> {
  const result = await apiRequest<CommentsPage>(`/posts/${postId}/comments?page=0&size=${COMMENTS_PAGE_SIZE}`, {
    method: 'GET',
  })
  return result.content
}

export async function createComment(postId: number, body: string): Promise<CommentResponse> {
  return apiRequest<CommentResponse>(`/posts/${postId}/comments`, {
    method: 'POST',
    body: JSON.stringify({ body }),
  })
}

export async function deleteComment(id: number): Promise<void> {
  await apiRequest<void>(`/comments/${id}`, { method: 'DELETE' })
}

export async function toggleLike(postId: number): Promise<LikeResponse> {
  return apiRequest<LikeResponse>(`/posts/${postId}/likes`, { method: 'POST' })
}
