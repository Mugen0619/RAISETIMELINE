import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { UserSearchPage } from './UserSearchPage'
import * as usersApi from '../api/users'
import type { UserSummaryResponse } from '../api/users'

vi.mock('../api/users')

const mockedSearchUsers = vi.mocked(usersApi.searchUsers)

function makeUser(overrides: Partial<UserSummaryResponse> = {}): UserSummaryResponse {
  return {
    userId: 1,
    username: 'someone',
    displayName: 'Someone',
    avatarUrl: null,
    ...overrides,
  }
}

function renderSearchPage() {
  return render(
    <MemoryRouter initialEntries={['/search']}>
      <Routes>
        <Route path="/search" element={<UserSearchPage />} />
        <Route path="/users/:userId" element={<div>プロフィールページ</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('UserSearchPage', () => {
  it('shows a prompt before any keyword is entered, without calling the API', () => {
    renderSearchPage()

    expect(screen.getByText('ユーザー名や表示名で検索してみましょう。')).toBeInTheDocument()
    expect(mockedSearchUsers).not.toHaveBeenCalled()
  })

  it('searches and shows matching users by username or display name', async () => {
    const user = userEvent.setup()
    mockedSearchUsers.mockResolvedValue([
      makeUser({ userId: 1, username: 'hina_takahashi', displayName: '高橋 陽菜' }),
    ])

    renderSearchPage()
    await user.type(screen.getByPlaceholderText('ユーザー名・表示名で検索'), 'hina')

    expect(await screen.findByText('高橋 陽菜')).toBeInTheDocument()
    expect(screen.getByText('@hina_takahashi')).toBeInTheDocument()
    expect(mockedSearchUsers).toHaveBeenCalledWith('hina')
  })

  it('shows a not-found message when there are no matches', async () => {
    const user = userEvent.setup()
    mockedSearchUsers.mockResolvedValue([])

    renderSearchPage()
    await user.type(screen.getByPlaceholderText('ユーザー名・表示名で検索'), 'nobody-like-this')

    expect(await screen.findByText('該当するユーザーが見つかりません。')).toBeInTheDocument()
  })

  it('clears results and returns to the prompt when the keyword is cleared', async () => {
    const user = userEvent.setup()
    mockedSearchUsers.mockResolvedValue([makeUser({ displayName: 'Someone' })])

    renderSearchPage()
    const input = screen.getByPlaceholderText('ユーザー名・表示名で検索')
    await user.type(input, 'someone')
    await screen.findByText('Someone')

    await user.clear(input)

    expect(await screen.findByText('ユーザー名や表示名で検索してみましょう。')).toBeInTheDocument()
    expect(screen.queryByText('Someone')).not.toBeInTheDocument()
  })

  it('navigates to the profile page when a result is clicked', async () => {
    const user = userEvent.setup()
    mockedSearchUsers.mockResolvedValue([makeUser({ userId: 42, displayName: 'Click Target' })])

    renderSearchPage()
    await user.type(screen.getByPlaceholderText('ユーザー名・表示名で検索'), 'click')
    await screen.findByText('Click Target')

    await user.click(screen.getByText('Click Target'))

    expect(await screen.findByText('プロフィールページ')).toBeInTheDocument()
  })
})
