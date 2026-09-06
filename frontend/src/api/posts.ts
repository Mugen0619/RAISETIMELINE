import { apiRequest } from './client'

export interface PostResponse {
  id: number
  userId: number
  username: string
  displayName: string
  body: string
  createdAt: string
  updatedAt: string
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

export async function createPost(body: string): Promise<PostResponse> {
  return apiRequest<PostResponse>('/posts', {
    method: 'POST',
    body: JSON.stringify({ body }),
  })
}

export async function fetchTimeline(page: number, size: number): Promise<TimelinePage> {
  return apiRequest<TimelinePage>(`/posts?page=${page}&size=${size}`, { method: 'GET' })
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
