import type { Page } from '@playwright/test'
import { expect } from '@playwright/test'

export interface TestUser {
  email: string
  username: string
  displayName: string
  password: string
}

/**
 * DBを共有した状態で何度実行しても衝突しないよう、実行のたびに一意なユーザーを生成する。
 */
export function createTestUser(label: string): TestUser {
  const unique = `${Date.now()}${Math.floor(Math.random() * 10000)}`
  return {
    email: `e2e-${label}-${unique}@example.com`,
    username: `e2e${label}${unique}`.slice(0, 32),
    displayName: `E2E ${label} ${unique}`,
    password: 'Password123!',
  }
}

/** 新規登録画面からアカウントを作成する。 */
export async function registerViaUi(page: Page, user: TestUser): Promise<void> {
  await page.goto('/register')
  await page.getByLabel('表示名').fill(user.displayName)
  await page.getByLabel('ユーザー名').fill(user.username)
  await page.getByLabel('メールアドレス').fill(user.email)
  // 「パスワード」と「パスワード確認」の2フィールドがあり紛らわしいため、一意なplaceholderで指定する
  await page.getByPlaceholder('8文字以上・英数字混合').fill(user.password)
  await page.getByLabel('パスワード確認').fill(user.password)
  await page.getByRole('button', { name: '登録してはじめる' }).click()
  await expect(page).toHaveURL(/\/login$/)
}

/** ログイン画面からログインし、タイムラインに遷移するまで待つ。 */
export async function loginViaUi(page: Page, user: TestUser): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel('パスワード').fill(user.password)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).toHaveURL(/\/home$/)
}

export async function registerAndLogin(page: Page, user: TestUser): Promise<void> {
  await registerViaUi(page, user)
  await loginViaUi(page, user)
}
