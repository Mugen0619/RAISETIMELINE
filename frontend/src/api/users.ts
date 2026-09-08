import { apiRequest } from './client'
import { getStoredUser } from './tokenStorage'
import type { PostResponse } from './posts'

export interface ProfileResponse {
  userId: number
  username: string
  displayName: string
  bio: string | null
  avatarUrl: string | null
  followerCount: number
  followingCount: number
  followedByMe: boolean
  createdAt: string
}

export interface FollowUserResponse {
  userId: number
  username: string
  displayName: string
  avatarUrl: string | null
  followedByMe: boolean
}

export interface FollowResponse {
  userId: number
  following: boolean
  followerCount: number
}

interface PageMeta {
  size: number
  number: number
  totalElements: number
  totalPages: number
}

export interface FollowUsersPage {
  content: FollowUserResponse[]
  page: PageMeta
}

export interface UserPostsPage {
  content: PostResponse[]
  page: PageMeta
}

// フォロー一覧・フォロワー一覧の追加ページネーションは現状不要なため、
// 想定件数を十分カバーできるサイズで1ページのみ取得する
const FOLLOW_LIST_PAGE_SIZE = 100

export async function fetchProfile(userId: number): Promise<ProfileResponse> {
  return apiRequest<ProfileResponse>(`/users/${userId}`, { method: 'GET' })
}

export async function updateProfile(displayName: string, bio: string): Promise<ProfileResponse> {
  const currentUser = getStoredUser()
  if (!currentUser) throw new Error('not authenticated')

  return apiRequest<ProfileResponse>(`/users/${currentUser.userId}`, {
    method: 'PUT',
    body: JSON.stringify({ displayName, bio }),
  })
}

export async function toggleFollow(userId: number): Promise<FollowResponse> {
  return apiRequest<FollowResponse>(`/users/${userId}/follow`, { method: 'POST' })
}

export async function fetchFollowing(userId: number): Promise<FollowUserResponse[]> {
  const result = await apiRequest<FollowUsersPage>(`/users/${userId}/following?page=0&size=${FOLLOW_LIST_PAGE_SIZE}`, {
    method: 'GET',
  })
  return result.content
}

export async function fetchFollowers(userId: number): Promise<FollowUserResponse[]> {
  const result = await apiRequest<FollowUsersPage>(`/users/${userId}/followers?page=0&size=${FOLLOW_LIST_PAGE_SIZE}`, {
    method: 'GET',
  })
  return result.content
}

export async function fetchUserPosts(userId: number, page: number, size: number): Promise<UserPostsPage> {
  return apiRequest<UserPostsPage>(`/users/${userId}/posts?page=${page}&size=${size}`, { method: 'GET' })
}
