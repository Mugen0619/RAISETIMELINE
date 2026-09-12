import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PostComposerDialog } from './PostComposerDialog'
import { uploadPostImage } from '../api/images'

vi.mock('../api/images', async () => {
  const actual = await vi.importActual<typeof import('../api/images')>('../api/images')
  return {
    ...actual,
    uploadPostImage: vi.fn(),
  }
})

const mockedUploadPostImage = vi.mocked(uploadPostImage)

afterEach(() => {
  mockedUploadPostImage.mockReset()
})

function makeFile(name: string, type: string, sizeBytes = 100): File {
  const file = new File(['x'.repeat(Math.min(sizeBytes, 1000))], name, { type })
  Object.defineProperty(file, 'size', { value: sizeBytes })
  return file
}

describe('PostComposerDialog', () => {
  it('disables the submit button when both body and images are empty', () => {
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

  it('submits the trimmed body with an empty image list when valid', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<PostComposerDialog open mode="create" onClose={vi.fn()} onSubmit={onSubmit} />)

    const textarea = screen.getByRole('textbox')
    await user.click(textarea)
    await user.paste('  hello  ')

    await user.click(screen.getByRole('button', { name: '投稿する' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('hello', []))
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

  it('enables submit and uploads images when only images are attached', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    mockedUploadPostImage.mockResolvedValue('https://example-bucket.s3.amazonaws.com/posts/uploaded.png')
    render(<PostComposerDialog open mode="create" onClose={vi.fn()} onSubmit={onSubmit} />)

    const file = makeFile('photo.png', 'image/png')
    await user.upload(screen.getByLabelText('画像を選択'), file)

    expect(await screen.findAllByAltText('')).toHaveLength(1)
    expect(screen.getByRole('button', { name: '投稿する' })).toBeEnabled()

    await user.click(screen.getByRole('button', { name: '投稿する' }))

    await waitFor(() => expect(mockedUploadPostImage).toHaveBeenCalledWith(file))
    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith('', ['https://example-bucket.s3.amazonaws.com/posts/uploaded.png']),
    )
  })

  it('shows an error and rejects the file when it exceeds the size limit', async () => {
    const user = userEvent.setup()
    render(<PostComposerDialog open mode="create" onClose={vi.fn()} onSubmit={vi.fn()} />)

    const oversized = makeFile('big.png', 'image/png', 6 * 1024 * 1024)
    await user.upload(screen.getByLabelText('画像を選択'), oversized)

    expect(await screen.findByText(/ファイルサイズが大きすぎます/)).toBeInTheDocument()
    expect(screen.queryAllByAltText('')).toHaveLength(0)
  })

  it('shows an error and rejects the file when the format is unsupported', async () => {
    // 実際のファイル選択ダイアログでは"すべてのファイル"指定でaccept属性外の形式も選べるため、
    // user-eventのaccept属性フィルタを無効化してコンポーネント側のバリデーションを検証する
    const user = userEvent.setup({ applyAccept: false })
    render(<PostComposerDialog open mode="create" onClose={vi.fn()} onSubmit={vi.fn()} />)

    const unsupported = makeFile('anim.gif', 'image/gif')
    await user.upload(screen.getByLabelText('画像を選択'), unsupported)

    expect(await screen.findByText(/対応していない形式です/)).toBeInTheDocument()
    expect(screen.queryAllByAltText('')).toHaveLength(0)
  })

  it('prevents attaching more than 4 images', async () => {
    const user = userEvent.setup()
    render(<PostComposerDialog open mode="create" onClose={vi.fn()} onSubmit={vi.fn()} />)

    const files = [
      makeFile('a.png', 'image/png'),
      makeFile('b.png', 'image/png'),
      makeFile('c.png', 'image/png'),
      makeFile('d.png', 'image/png'),
      makeFile('e.png', 'image/png'),
    ]
    await user.upload(screen.getByLabelText('画像を選択'), files)

    expect(await screen.findAllByAltText('')).toHaveLength(4)
    expect(screen.getByText(/画像は最大4枚までです/)).toBeInTheDocument()
  })

  it('removes an attached image individually', async () => {
    const user = userEvent.setup()
    render(<PostComposerDialog open mode="create" onClose={vi.fn()} onSubmit={vi.fn()} />)

    const file = makeFile('photo.png', 'image/png')
    await user.upload(screen.getByLabelText('画像を選択'), file)
    expect(await screen.findAllByAltText('')).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: '画像を削除' }))

    expect(screen.queryAllByAltText('')).toHaveLength(0)
    expect(screen.getByRole('button', { name: '投稿する' })).toBeDisabled()
  })

  it('pre-fills existing images in edit mode and reuses their URLs without re-uploading', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(
      <PostComposerDialog
        open
        mode="edit"
        initialBody="existing body"
        initialImageUrls={['https://example-bucket.s3.amazonaws.com/posts/existing.png']}
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />,
    )

    expect(await screen.findAllByAltText('')).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: '更新する' }))

    expect(mockedUploadPostImage).not.toHaveBeenCalled()
    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith('existing body', [
        'https://example-bucket.s3.amazonaws.com/posts/existing.png',
      ]),
    )
  })
})
