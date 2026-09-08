import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { ProfileEditPage } from './ProfileEditPage'
import { AuthProvider } from '../auth/AuthContext'
import * as usersApi from '../api/users'
import type { ProfileResponse } from '../api/users'

vi.mock('../api/users')

const mockedFetchProfile = vi.mocked(usersApi.fetchProfile)
const mockedUpdateProfile = vi.mocked(usersApi.updateProfile)

function makeProfile(overrides: Partial<ProfileResponse> = {}): ProfileResponse {
  return {
    userId: 1,
    username: 'me',
    displayName: 'Me',
    bio: 'original bio',
    avatarUrl: null,
    followerCount: 0,
    followingCount: 0,
    followedByMe: false,
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

function renderEditPage(editUserId: number, currentUserId = 1) {
  localStorage.setItem('accessToken', 'test-token')
  localStorage.setItem('refreshToken', 'test-refresh')
  localStorage.setItem('authUser', JSON.stringify({ userId: currentUserId, username: 'me', displayName: 'Me' }))

  return render(
    <MemoryRouter initialEntries={[`/users/${editUserId}/edit`]}>
      <AuthProvider>
        <Routes>
          <Route path="/users/:userId/edit" element={<ProfileEditPage />} />
          <Route path="/users/:userId" element={<div>プロフィールページ</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  localStorage.clear()
})

describe('ProfileEditPage', () => {
  it('pre-fills the form with the current profile', async () => {
    mockedFetchProfile.mockResolvedValue(makeProfile())

    renderEditPage(1)

    expect(await screen.findByDisplayValue('Me')).toBeInTheDocument()
    expect(screen.getByDisplayValue('original bio')).toBeInTheDocument()
  })

  it('submits the updated profile and navigates back', async () => {
    const user = userEvent.setup()
    mockedFetchProfile.mockResolvedValue(makeProfile())
    mockedUpdateProfile.mockResolvedValue(makeProfile({ displayName: 'New Name', bio: 'new bio' }))

    renderEditPage(1)
    const displayNameInput = await screen.findByDisplayValue('Me')
    await user.clear(displayNameInput)
    await user.type(displayNameInput, 'New Name')

    await user.click(screen.getByRole('button', { name: '保存する' }))

    expect(mockedUpdateProfile).toHaveBeenCalledWith('New Name', 'original bio')
    expect(await screen.findByText('プロフィールページ')).toBeInTheDocument()
  })

  it('disables submit when display name is blank', async () => {
    const user = userEvent.setup()
    mockedFetchProfile.mockResolvedValue(makeProfile())

    renderEditPage(1)
    const displayNameInput = await screen.findByDisplayValue('Me')
    await user.clear(displayNameInput)

    expect(screen.getByRole('button', { name: '保存する' })).toBeDisabled()
  })

  it('redirects away when visiting another users edit page', async () => {
    mockedFetchProfile.mockResolvedValue(makeProfile({ userId: 2, username: 'other', displayName: 'Other' }))

    renderEditPage(2, 1)

    expect(await screen.findByText('プロフィールページ')).toBeInTheDocument()
    expect(mockedFetchProfile).not.toHaveBeenCalled()
  })
})
