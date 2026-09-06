import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { TimelinePage } from './TimelinePage'
import { AuthProvider } from '../auth/AuthContext'
import * as postsApi from '../api/posts'
import type { PostResponse, TimelinePage as TimelinePageResponse } from '../api/posts'

vi.mock('../api/posts')

const mockedFetchTimeline = vi.mocked(postsApi.fetchTimeline)
const mockedCreatePost = vi.mocked(postsApi.createPost)
const mockedDeletePost = vi.mocked(postsApi.deletePost)
const mockedToggleLike = vi.mocked(postsApi.toggleLike)

function makePost(overrides: Partial<PostResponse> = {}): PostResponse {
  return {
    id: 1,
    userId: 1,
    username: 'me',
    displayName: 'Me',
    body: 'my post',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    commentCount: 0,
    likeCount: 0,
    likedByMe: false,
    ...overrides,
  }
}

function page(content: PostResponse[], opts: { number?: number; totalPages?: number } = {}): TimelinePageResponse {
  return {
    content,
    page: { size: 20, number: opts.number ?? 0, totalElements: content.length, totalPages: opts.totalPages ?? 1 },
  }
}

function renderTimelinePage() {
  localStorage.setItem('accessToken', 'test-token')
  localStorage.setItem('refreshToken', 'test-refresh')
  localStorage.setItem('authUser', JSON.stringify({ userId: 1, username: 'me', displayName: 'Me' }))

  return render(
    <MemoryRouter>
      <AuthProvider>
        <TimelinePage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('TimelinePage', () => {
  it('renders posts from the initial timeline fetch', async () => {
    mockedFetchTimeline.mockResolvedValue(page([makePost({ id: 1, body: 'hello there' })]))

    renderTimelinePage()

    expect(await screen.findByText('hello there')).toBeInTheDocument()
    expect(mockedFetchTimeline).toHaveBeenCalledWith(0, 20)
  })

  it('shows an empty state when there are no posts', async () => {
    mockedFetchTimeline.mockResolvedValue(page([]))

    renderTimelinePage()

    expect(await screen.findByText('まだ投稿がありません。')).toBeInTheDocument()
  })

  it('shows a delete button only for the current users own posts', async () => {
    const myPost = makePost({ id: 1, userId: 1, username: 'me', body: 'my own post' })
    const othersPost = makePost({ id: 2, userId: 2, username: 'someone', displayName: 'Someone', body: 'not mine' })
    mockedFetchTimeline.mockResolvedValue(page([myPost, othersPost]))

    renderTimelinePage()

    await screen.findByText('my own post')

    const myCard = screen.getByText('my own post').closest('.MuiCard-root') as HTMLElement
    const othersCard = screen.getByText('not mine').closest('.MuiCard-root') as HTMLElement

    expect(within(myCard).getByText('削除')).toBeInTheDocument()
    expect(within(othersCard).queryByText('削除')).not.toBeInTheDocument()
  })

  it('toggles like on a post and reflects the new count', async () => {
    const user = userEvent.setup()
    const target = makePost({ id: 1, body: 'likeable post', likeCount: 2, likedByMe: false })
    mockedFetchTimeline.mockResolvedValue(page([target]))
    mockedToggleLike.mockResolvedValue({ postId: 1, liked: true, likeCount: 3 })

    renderTimelinePage()
    await screen.findByText('likeable post')

    await user.click(screen.getByRole('button', { name: 'いいね' }))

    expect(mockedToggleLike).toHaveBeenCalledWith(1)
    expect(await screen.findByText('3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'いいね' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('creates a post via the composer dialog and prepends it to the list', async () => {
    const user = userEvent.setup()
    mockedFetchTimeline.mockResolvedValue(page([makePost({ id: 1, body: 'existing post' })]))
    const created = makePost({ id: 2, body: 'brand new post' })
    mockedCreatePost.mockResolvedValue(created)

    renderTimelinePage()
    await screen.findByText('existing post')

    await user.click(screen.getByRole('button', { name: 'いまどうしてる？' }))
    const textarea = await screen.findByRole('textbox')
    await user.click(textarea)
    await user.paste('brand new post')
    await user.click(screen.getByRole('button', { name: '投稿する' }))

    expect(mockedCreatePost).toHaveBeenCalledWith('brand new post')
    expect(await screen.findByText('brand new post')).toBeInTheDocument()
    expect(screen.queryByPlaceholderText('いまどうしてる？(280文字まで)')).not.toBeInTheDocument()
  })

  it('deletes an own post after confirming', async () => {
    const user = userEvent.setup()
    const target = makePost({ id: 1, body: 'to be deleted' })
    mockedFetchTimeline.mockResolvedValue(page([target]))
    mockedDeletePost.mockResolvedValue(undefined)

    renderTimelinePage()
    await screen.findByText('to be deleted')

    await user.click(screen.getByText('削除'))
    expect(await screen.findByText('投稿を削除しますか？')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '削除する' }))

    expect(mockedDeletePost).toHaveBeenCalledWith(1)
    await waitFor(() => expect(screen.queryByText('to be deleted')).not.toBeInTheDocument())
  })

  it('does not delete when the confirmation dialog is cancelled', async () => {
    const user = userEvent.setup()
    const target = makePost({ id: 1, body: 'keep me' })
    mockedFetchTimeline.mockResolvedValue(page([target]))

    renderTimelinePage()
    await screen.findByText('keep me')

    await user.click(screen.getByText('削除'))
    await user.click(screen.getByRole('button', { name: 'キャンセル' }))

    expect(mockedDeletePost).not.toHaveBeenCalled()
    expect(screen.getByText('keep me')).toBeInTheDocument()
  })

  it('shows a new-posts banner once polling detects a newer post, and refreshes on click', async () => {
    const initial = makePost({ id: 1, body: 'first post' })
    const newer = makePost({ id: 2, body: 'second post' })

    mockedFetchTimeline
      .mockResolvedValueOnce(page([initial]))
      .mockResolvedValueOnce(page([newer]))
      .mockResolvedValueOnce(page([newer, initial]))

    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })

    renderTimelinePage()

    await vi.waitFor(() => expect(screen.getByText('first post')).toBeInTheDocument())
    expect(screen.queryByText('新しい投稿があります')).not.toBeInTheDocument()

    await vi.advanceTimersByTimeAsync(30000)

    expect(await screen.findByText('新しい投稿があります')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '更新' }))

    await vi.waitFor(() => expect(screen.getByText('second post')).toBeInTheDocument())
    expect(mockedFetchTimeline).toHaveBeenCalledTimes(3)
  })
})
