import { describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PostComposerDialog } from './PostComposerDialog'

describe('PostComposerDialog', () => {
  it('disables the submit button when the body is empty', () => {
    render(<PostComposerDialog open mode="create" onClose={vi.fn()} onSubmit={vi.fn()} />)

    expect(screen.getByRole('button', { name: '投稿する' })).toBeDisabled()
  })

  it('disables the submit button when the body exceeds 280 characters', async () => {
    const user = userEvent.setup()
    render(<PostComposerDialog open mode="create" onClose={vi.fn()} onSubmit={vi.fn()} />)

    const textarea = screen.getByRole('textbox')
    await user.click(textarea)
    await user.paste('a'.repeat(281))

    expect(screen.getByText('-1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '投稿する' })).toBeDisabled()
  })

  it('submits the trimmed body when valid', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<PostComposerDialog open mode="create" onClose={vi.fn()} onSubmit={onSubmit} />)

    const textarea = screen.getByRole('textbox')
    await user.click(textarea)
    await user.paste('  hello  ')

    await user.click(screen.getByRole('button', { name: '投稿する' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('hello'))
  })

  it('shows an error message and keeps the dialog open when submit fails', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(new Error('network error'))
    const onClose = vi.fn()
    render(<PostComposerDialog open mode="create" onClose={onClose} onSubmit={onSubmit} />)

    const textarea = screen.getByRole('textbox')
    await user.click(textarea)
    await user.paste('hello')
    await user.click(screen.getByRole('button', { name: '投稿する' }))

    expect(await screen.findByText('投稿に失敗しました。時間をおいて再度お試しください。')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('shows the update label and pre-fills the body in edit mode', () => {
    render(
      <PostComposerDialog open mode="edit" initialBody="existing body" onClose={vi.fn()} onSubmit={vi.fn()} />,
    )

    expect(screen.getByDisplayValue('existing body')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '更新する' })).toBeEnabled()
  })
})
