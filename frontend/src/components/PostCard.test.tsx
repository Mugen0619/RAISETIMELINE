import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { PostCard } from './PostCard'
import type { PostResponse } from '../api/posts'

const basePost: PostResponse = {
  id: 1,
  userId: 10,
  username: 'alice',
  displayName: 'Alice',
  body: 'hello world',
  createdAt: new Date().toISOString(),
  updatedAt: new Date().toISOString(),
  commentCount: 3,
  likeCount: 5,
  likedByMe: false,
  imageUrls: [],
}

function renderCard(post: PostResponse, isOwner: boolean, onDelete = vi.fn(), onToggleLike = vi.fn()) {
  return render(
    <MemoryRouter initialEntries={['/home']}>
      <Routes>
        <Route path="/home" element={<PostCard post={post} isOwner={isOwner} onDelete={onDelete} onToggleLike={onToggleLike} />} />
        <Route path="/posts/:id" element={<div>投稿詳細ページ</div>} />
        <Route path="/users/:userId" element={<div>プロフィールページ</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('PostCard', () => {
  it('renders the post content and like/comment counts', () => {
    renderCard(basePost, false)

    expect(screen.getByText('hello world')).toBeInTheDocument()
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('@alice')).toBeInTheDocument()
    expect(screen.getByText('5')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
  })

  it('does not show a delete button for other users posts', () => {
    renderCard(basePost, false)

    expect(screen.queryByText('削除')).not.toBeInTheDocument()
  })

  it('shows a delete button for the owner and invokes the callback', async () => {
    const user = userEvent.setup()
    const onDelete = vi.fn()
    renderCard(basePost, true, onDelete)

    await user.click(screen.getByText('削除'))
    expect(onDelete).toHaveBeenCalledWith(basePost)
  })

  it('invokes onToggleLike when the like button is clicked, without navigating', async () => {
    const user = userEvent.setup()
    const onToggleLike = vi.fn()
    renderCard(basePost, false, vi.fn(), onToggleLike)

    await user.click(screen.getByRole('button', { name: 'いいね' }))
    expect(onToggleLike).toHaveBeenCalledWith(basePost)
    expect(screen.queryByText('投稿詳細ページ')).not.toBeInTheDocument()
  })

  it('shows a filled like icon when already liked by me', () => {
    renderCard({ ...basePost, likedByMe: true }, false)

    expect(screen.getByRole('button', { name: 'いいね' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('navigates to the post detail page when the card body is clicked', async () => {
    const user = userEvent.setup()
    renderCard(basePost, false)

    await user.click(screen.getByText('hello world'))
    expect(await screen.findByText('投稿詳細ページ')).toBeInTheDocument()
  })

  it('navigates to the profile page when the author name is clicked, not the post detail page', async () => {
    const user = userEvent.setup()
    renderCard(basePost, false)

    await user.click(screen.getByText('Alice'))
    expect(await screen.findByText('プロフィールページ')).toBeInTheDocument()
    expect(screen.queryByText('投稿詳細ページ')).not.toBeInTheDocument()
  })

  it('navigates to the profile page when the avatar is clicked', async () => {
    const user = userEvent.setup()
    renderCard(basePost, false)

    await user.click(screen.getByRole('button', { name: 'Aliceのプロフィール' }))
    expect(await screen.findByText('プロフィールページ')).toBeInTheDocument()
  })
})
