import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { PostDetailPage } from './PostDetailPage'
import { AuthProvider } from '../auth/AuthContext'
import * as postsApi from '../api/posts'
import type { CommentResponse, PostResponse } from '../api/posts'

vi.mock('../api/posts')

const mockedFetchPostDetail = vi.mocked(postsApi.fetchPostDetail)
const mockedFetchComments = vi.mocked(postsApi.fetchComments)
const mockedCreateComment = vi.mocked(postsApi.createComment)
const mockedDeleteComment = vi.mocked(postsApi.deleteComment)
const mockedToggleLike = vi.mocked(postsApi.toggleLike)
const mockedUpdatePost = vi.mocked(postsApi.updatePost)
const mockedDeletePost = vi.mocked(postsApi.deletePost)

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

function makeComment(overrides: Partial<CommentResponse> = {}): CommentResponse {
  return {
    id: 1,
    postId: 1,
    userId: 1,
    username: 'me',
    displayName: 'Me',
    body: 'a comment',
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

function renderDetailPage(postId = 1) {
  localStorage.setItem('accessToken', 'test-token')
  localStorage.setItem('refreshToken', 'test-refresh')
  localStorage.setItem('authUser', JSON.stringify({ userId: 1, username: 'me', displayName: 'Me' }))

  return render(
    <MemoryRouter initialEntries={[`/posts/${postId}`]}>
      <AuthProvider>
        <Routes>
          <Route path="/posts/:id" element={<PostDetailPage />} />
          <Route path="/home" element={<div>タイムライン画面</div>} />
        </Routes>
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

describe('PostDetailPage', () => {
  it('renders the post and its comments', async () => {
    mockedFetchPostDetail.mockResolvedValue(makePost({ body: 'the post body', likeCount: 2, commentCount: 1 }))
    mockedFetchComments.mockResolvedValue([makeComment({ body: 'first comment' })])

    renderDetailPage()

    expect(await screen.findByText('the post body')).toBeInTheDocument()
    expect(screen.getByText('first comment')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()
  })

  it('shows an empty state when there are no comments', async () => {
    mockedFetchPostDetail.mockResolvedValue(makePost())
    mockedFetchComments.mockResolvedValue([])

    renderDetailPage()

    expect(await screen.findByText('まだコメントはありません。')).toBeInTheDocument()
  })

  it('toggles like and updates the count', async () => {
    const user = userEvent.setup()
    mockedFetchPostDetail.mockResolvedValue(makePost({ likeCount: 2, likedByMe: false }))
    mockedFetchComments.mockResolvedValue([])
    mockedToggleLike.mockResolvedValue({ postId: 1, liked: true, likeCount: 3 })

    renderDetailPage()
    await screen.findByText('my post')

    await user.click(screen.getByRole('button', { name: 'いいね' }))

    expect(mockedToggleLike).toHaveBeenCalledWith(1)
    expect(await screen.findByText('3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'いいね' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('submits a new comment and appends it to the list', async () => {
    const user = userEvent.setup()
    mockedFetchPostDetail.mockResolvedValue(makePost({ commentCount: 0 }))
    mockedFetchComments.mockResolvedValue([])
    mockedCreateComment.mockResolvedValue(makeComment({ id: 2, body: 'new comment' }))

    renderDetailPage()
    await screen.findByText('まだコメントはありません。')

    await user.click(screen.getByPlaceholderText('コメントする…'))
    await user.paste('new comment')
    await user.click(screen.getByRole('button', { name: 'コメントを送信' }))

    expect(mockedCreateComment).toHaveBeenCalledWith(1, 'new comment')
    expect(await screen.findByText('new comment')).toBeInTheDocument()
  })

  it('does not show a delete button on other users comments', async () => {
    mockedFetchPostDetail.mockResolvedValue(makePost())
    mockedFetchComments.mockResolvedValue([
      makeComment({ id: 1, userId: 1, body: 'my comment' }),
      makeComment({ id: 2, userId: 2, username: 'other', displayName: 'Other', body: 'their comment' }),
    ])

    renderDetailPage()
    await screen.findByText('my comment')

    const myCommentCard = screen.getByText('my comment').closest('.MuiPaper-root') as HTMLElement
    const othersCommentCard = screen.getByText('their comment').closest('.MuiPaper-root') as HTMLElement

    expect(myCommentCard.querySelector('button')).not.toBeNull()
    expect(othersCommentCard.querySelector('button')).toBeNull()
  })

  it('deletes own comment after confirming', async () => {
    const user = userEvent.setup()
    mockedFetchPostDetail.mockResolvedValue(makePost({ commentCount: 1 }))
    mockedFetchComments.mockResolvedValue([makeComment({ body: 'to be removed' })])
    mockedDeleteComment.mockResolvedValue(undefined)

    renderDetailPage()
    await screen.findByText('to be removed')

    const commentCard = screen.getByText('to be removed').closest('.MuiPaper-root') as HTMLElement
    await user.click(within(commentCard).getByText('削除'))
    await user.click(screen.getByRole('button', { name: '削除する' }))

    expect(mockedDeleteComment).toHaveBeenCalledWith(1)
    await waitFor(() => expect(screen.queryByText('to be removed')).not.toBeInTheDocument())
  })

  it('edits the post via the composer dialog', async () => {
    const user = userEvent.setup()
    const original = makePost({ body: 'original body' })
    mockedFetchPostDetail.mockResolvedValue(original)
    mockedFetchComments.mockResolvedValue([])
    mockedUpdatePost.mockResolvedValue({ ...original, body: 'updated body' })

    renderDetailPage()
    await screen.findByText('original body')

    await user.click(screen.getByText('編集'))
    const textarea = await screen.findByDisplayValue('original body')
    await user.click(textarea)
    await user.clear(textarea)
    await user.paste('updated body')
    await user.click(screen.getByRole('button', { name: '更新する' }))

    expect(mockedUpdatePost).toHaveBeenCalledWith(1, 'updated body')
    expect(await screen.findByText('updated body')).toBeInTheDocument()
  })

  it('deletes the post after confirming and navigates back to the timeline', async () => {
    const user = userEvent.setup()
    mockedFetchPostDetail.mockResolvedValue(makePost({ body: 'delete this post' }))
    mockedFetchComments.mockResolvedValue([])
    mockedDeletePost.mockResolvedValue(undefined)

    renderDetailPage()
    await screen.findByText('delete this post')

    const deleteButtons = screen.getAllByText('削除')
    await user.click(deleteButtons[0])
    await user.click(screen.getByRole('button', { name: '削除する' }))

    expect(mockedDeletePost).toHaveBeenCalledWith(1)
    expect(await screen.findByText('タイムライン画面')).toBeInTheDocument()
  })

  it('does not show edit/delete controls for another users post', async () => {
    mockedFetchPostDetail.mockResolvedValue(makePost({ userId: 2, username: 'other', displayName: 'Other' }))
    mockedFetchComments.mockResolvedValue([])

    renderDetailPage()
    await screen.findByText('my post')

    expect(screen.queryByText('編集')).not.toBeInTheDocument()
    expect(screen.queryByText('削除')).not.toBeInTheDocument()
  })
})
