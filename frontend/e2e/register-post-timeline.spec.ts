import { test, expect } from '@playwright/test'
import { createTestUser, registerAndLogin } from './helpers.js'

test('新規登録→ログイン→投稿作成→タイムライン表示', async ({ page }) => {
  const user = createTestUser('post')
  const postBody = `E2Eテスト投稿 ${Date.now()}`

  await test.step('新規登録してログインする', async () => {
    await registerAndLogin(page, user)
    await expect(page.getByText(`@${user.username}`)).toBeVisible()
  })

  await test.step('本文のみの投稿を作成する', async () => {
    await page.getByRole('button', { name: 'いまどうしてる？' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('いまどうしてる？(280文字まで)').fill(postBody)
    // タイムライン画面の投稿FABも同じ「投稿する」というaria-labelを持つため、ダイアログ内に絞り込む
    await dialog.getByRole('button', { name: '投稿する' }).click()
  })

  await test.step('タイムラインに投稿が表示される', async () => {
    await expect(page.getByText(postBody)).toBeVisible()
    // ユーザー名・表示名はテスト実行ごとに一意なため、投稿者情報も紐づいて表示されていることを確認できる
    await expect(page.getByText(user.displayName)).toBeVisible()
  })
})
