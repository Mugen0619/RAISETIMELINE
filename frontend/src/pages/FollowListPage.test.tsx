import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { FollowListPage } from './FollowListPage'
import * as usersApi from '../api/users'
import type { FollowUserResponse, ProfileResponse } from '../api/users'

vi.mock('../api/users')

const mockedFetchProfile = vi.mocked(usersApi.fetchProfile)
const mockedFetchFollowing = vi.mocked(usersApi.fetchFollowing)
const mockedFetchFollowers = vi.mocked(usersApi.fetchFollowers)

function makeProfile(overrides: Partial<ProfileResponse> = {}): ProfileResponse {
  return {
    userId: 2,
    username: 'other',
    displayName: 'Other User',
    bio: null,
    avatarUrl: null,
    followerCount: 0,
    followingCount: 0,
    followedByMe: false,
    createdAt: new Date().toISOString(),
    ...overrides,
  }
}

function makeFollowUser(overrides: Partial<FollowUserResponse> = {}): FollowUserResponse {
  return {
    userId: 3,
    username: 'someone',
    displayName: 'Someone',
    avatarUrl: null,
    followedByMe: false,
    ...overrides,
  }
}

function renderFollowListPage(mode: 'following' | 'followers') {
  return render(
    <MemoryRouter initialEntries={[`/users/2/${mode}`]}>
      <Routes>
        <Route path={`/users/:userId/${mode}`} element={<FollowListPage mode={mode} />} />
        <Route path="/users/:userId" element={<div>プロフィールページ</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('FollowListPage', () => {
  it('renders the following list and title', async () => {
    mockedFetchProfile.mockResolvedValue(makeProfile())
    mockedFetchFollowing.mockResolvedValue([makeFollowUser({ displayName: 'Someone' })])

    renderFollowListPage('following')

    expect(await screen.findByText('Someone')).toBeInTheDocument()
    expect(screen.getByText('Other Userさんのフォロー中')).toBeInTheDocument()
    expect(mockedFetchFollowing).toHaveBeenCalledWith(2)
  })

  it('renders the followers list', async () => {
    mockedFetchProfile.mockResolvedValue(makeProfile())
    mockedFetchFollowers.mockResolvedValue([makeFollowUser({ displayName: 'Follower One' })])

    renderFollowListPage('followers')

    expect(await screen.findByText('Follower One')).toBeInTheDocument()
    expect(mockedFetchFollowers).toHaveBeenCalledWith(2)
  })

  it('shows an empty state when there is nobody in the list', async () => {
    mockedFetchProfile.mockResolvedValue(makeProfile())
    mockedFetchFollowing.mockResolvedValue([])

    renderFollowListPage('following')

    expect(await screen.findByText('まだ誰もフォローしていません。')).toBeInTheDocument()
  })

  it('navigates to the users profile when a row is clicked', async () => {
    const user = userEvent.setup()
    mockedFetchProfile.mockResolvedValue(makeProfile())
    mockedFetchFollowing.mockResolvedValue([makeFollowUser({ displayName: 'Someone' })])

    renderFollowListPage('following')
    await screen.findByText('Someone')

    await user.click(screen.getByText('Someone'))
    expect(await screen.findByText('プロフィールページ')).toBeInTheDocument()
  })
})
