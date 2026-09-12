import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ProfilePage } from './ProfilePage'
import { AuthProvider } from '../auth/AuthContext'
import * as usersApi from '../api/users'
import * as postsApi from '../api/posts'
import type { ProfileResponse } from '../api/users'
import type { PostResponse } from '../api/posts'

vi.mock('../api/users')
vi.mock('../api/posts')

const mockedFetchProfile = vi.mocked(usersApi.fetchProfile)
const mockedFetchUserPosts = vi.mocked(usersApi.fetchUserPosts)
const mockedToggleFollow = vi.mocked(usersApi.toggleFollow)
const mockedToggleLike = vi.mocked(postsApi.toggleLike)
const mockedDeletePost = vi.mocked(postsApi.deletePost)

function makeProfile(overrides: Partial<ProfileResponse> = {}): ProfileResponse {
  return {
    userId: 2,
    username: 'other',
    displayName: 'Other User',
    bio: 'hello, this is my bio',
    avatarUrl: null,
    followerCount: 3,
    followingCount: 1,
    followedByMe: false,
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

function makePost(overrides: Partial<PostResponse> = {}): PostResponse {
  return {
    id: 1,
    userId: 2,
    username: 'other',
    displayName: 'Other User',
    body: 'a post',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    commentCount: 0,
    likeCount: 0,
    likedByMe: false,
    imageUrls: [],
    ...overrides,
  }
}

function postsPage(content: PostResponse[]) {
  return { content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 } }
}

function renderProfilePage(profileUserId: number, currentUserId = 1) {
  localStorage.setItem('accessToken', 'test-token')
  localStorage.setItem('refreshToken', 'test-refresh')
  localStorage.setItem('authUser', JSON.stringify({ userId: currentUserId, username: 'me', displayName: 'Me' }))

  return render(
    <MemoryRouter initialEntries={[`/users/${profileUserId}`]}>
      <AuthProvider>
        <Routes>
          <Route path="/users/:userId" element={<ProfilePage />} />
          <Route path="/users/:userId/edit" element={<div>編集ページ</div>} />
          <Route path="/users/:userId/following" element={<div>フォロー中一覧</div>} />
          <Route path="/users/:userId/followers" element={<div>フォロワー一覧</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
})

describe('ProfilePage', () => {
  it('renders profile info and post list', async () => {
    mockedFetchProfile.mockResolvedValue(makeProfile())
    mockedFetchUserPosts.mockResolvedValue(postsPage([makePost({ body: 'their post' })]))

    renderProfilePage(2)

    expect(await screen.findByText('their post')).toBeInTheDocument()
    expect(screen.getByText('hello, this is my bio')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
  })

  it('shows a follow button for another users profile and toggles it', async () => {
    const user = userEvent.setup()
    mockedFetchProfile.mockResolvedValue(makeProfile({ followedByMe: false, followerCount: 3 }))
    mockedFetchUserPosts.mockResolvedValue(postsPage([]))
    mockedToggleFollow.mockResolvedValue({ userId: 2, following: true, followerCount: 4 })

    renderProfilePage(2)
    const followButton = await screen.findByRole('button', { name: 'フォローする' })
    await user.click(followButton)

    expect(mockedToggleFollow).toHaveBeenCalledWith(2)
    expect(await screen.findByRole('button', { name: 'フォロー中' })).toBeInTheDocument()
    expect(screen.getByText('4')).toBeInTheDocument()
  })

  it('shows an edit button instead of a follow button on my own profile', async () => {
    mockedFetchProfile.mockResolvedValue(makeProfile({ userId: 1, username: 'me', displayName: 'Me' }))
    mockedFetchUserPosts.mockResolvedValue(postsPage([]))

    renderProfilePage(1, 1)

    expect(await screen.findByRole('button', { name: 'プロフィールを編集' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'フォローする' })).not.toBeInTheDocument()
  })

  it('navigates to the edit page when the edit button is clicked', async () => {
    const user = userEvent.setup()
    mockedFetchProfile.mockResolvedValue(makeProfile({ userId: 1, username: 'me', displayName: 'Me' }))
    mockedFetchUserPosts.mockResolvedValue(postsPage([]))

    renderProfilePage(1, 1)
    const editButton = await screen.findByRole('button', { name: 'プロフィールを編集' })

    await user.click(editButton)
    expect(await screen.findByText('編集ページ')).toBeInTheDocument()
  })

  it('navigates to the following/followers list pages', async () => {
    const user = userEvent.setup()
    mockedFetchProfile.mockResolvedValue(makeProfile())
    mockedFetchUserPosts.mockResolvedValue(postsPage([]))

    renderProfilePage(2)
    const followingButton = await screen.findByRole('button', { name: /フォロー中/ })

    await user.click(followingButton)
    expect(await screen.findByText('フォロー中一覧')).toBeInTheDocument()
  })

  it('toggles like on a post in the list', async () => {
    const user = userEvent.setup()
    mockedFetchProfile.mockResolvedValue(makeProfile())
    mockedFetchUserPosts.mockResolvedValue(postsPage([makePost({ id: 5, likeCount: 1, likedByMe: false })]))
    mockedToggleLike.mockResolvedValue({ postId: 5, liked: true, likeCount: 2 })

    renderProfilePage(2)
    await screen.findByText('a post')

    await user.click(screen.getByRole('button', { name: 'いいね' }))
    expect(mockedToggleLike).toHaveBeenCalledWith(5)
    expect(await screen.findByText('2')).toBeInTheDocument()
  })

  it('deletes an own post from the profile page after confirming', async () => {
    const user = userEvent.setup()
    mockedFetchProfile.mockResolvedValue(makeProfile({ userId: 1, username: 'me', displayName: 'Me' }))
    mockedFetchUserPosts.mockResolvedValue(postsPage([makePost({ id: 5, userId: 1, body: 'delete me' })]))
    mockedDeletePost.mockResolvedValue(undefined)

    renderProfilePage(1, 1)
    await screen.findByText('delete me')

    await user.click(screen.getByText('削除'))
    await user.click(screen.getByRole('button', { name: '削除する' }))

    expect(mockedDeletePost).toHaveBeenCalledWith(5)
  })
})
