import { describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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
}

describe('PostCard', () => {
  it('renders the post content', () => {
    render(<PostCard post={basePost} isOwner={false} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.getByText('hello world')).toBeInTheDocument()
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('@alice')).toBeInTheDocument()
  })

  it('does not show edit/delete buttons for other users posts', () => {
    render(<PostCard post={basePost} isOwner={false} onEdit={vi.fn()} onDelete={vi.fn()} />)

    expect(screen.queryByText('編集')).not.toBeInTheDocument()
    expect(screen.queryByText('削除')).not.toBeInTheDocument()
  })

  it('shows edit/delete buttons for the owner and invokes callbacks', async () => {
    const user = userEvent.setup()
    const onEdit = vi.fn()
    const onDelete = vi.fn()
    render(<PostCard post={basePost} isOwner onEdit={onEdit} onDelete={onDelete} />)

    await user.click(screen.getByText('編集'))
    expect(onEdit).toHaveBeenCalledWith(basePost)

    await user.click(screen.getByText('削除'))
    expect(onDelete).toHaveBeenCalledWith(basePost)
  })
})
