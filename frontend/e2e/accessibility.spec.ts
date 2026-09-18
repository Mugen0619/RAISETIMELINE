import { test, expect } from '@playwright/test'
import { createTestUser, expectNoAccessibilityViolations, registerAndLogin } from './helpers.js'

// axe-coreによる主要画面のアクセシビリティ検査。
// 違反が見つかった場合は、テスト側ではなく製品コード側(該当するコンポーネント)を修正する。
test.describe('アクセシビリティ', () => {
  test('ログイン画面', async ({ page }) => {
    await page.goto('/login')
    await expectNoAccessibilityViolations(page)
  })

  test('登録画面', async ({ page }) => {
    await page.goto('/register')
    await expectNoAccessibilityViolations(page)
  })

  test('タイムライン画面', async ({ page }) => {
    const user = createTestUser('a11y-tl')
    await registerAndLogin(page, user)

    // 投稿が1件もない状態だとPostCard(見出し等を含みうる)がレンダリングされないため、
    // 実際にタイムラインへ表示される内容もあわせて検査できるよう投稿を1件作成しておく。
    const postBody = `A11yテスト投稿(タイムライン) ${Date.now()}`
    await page.getByRole('button', { name: 'いまどうしてる？' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('いまどうしてる？(280文字まで)').fill(postBody)
    await dialog.getByRole('button', { name: '投稿する' }).click()
    await expect(page.getByText(postBody)).toBeVisible()

    await expectNoAccessibilityViolations(page)
  })

  test('投稿詳細画面', async ({ page }) => {
    const user = createTestUser('a11y-post')
    await registerAndLogin(page, user)

    const postBody = `A11yテスト投稿 ${Date.now()}`
    await page.getByRole('button', { name: 'いまどうしてる？' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('いまどうしてる？(280文字まで)').fill(postBody)
    await dialog.getByRole('button', { name: '投稿する' }).click()
    await page.getByText(postBody).click()
    await expect(page).toHaveURL(/\/posts\/\d+$/)
    // データ取得(loadData)完了前はローディングスピナーのみが表示され、mainランドマークや
    // h1を持たないため、実際のコンテンツが表示されるまで待ってから検査する。
    await expect(page.getByText(postBody)).toBeVisible()

    await expectNoAccessibilityViolations(page)
  })

  test('プロフィール画面', async ({ page }) => {
    const user = createTestUser('a11y-prof')
    await registerAndLogin(page, user)

    await page.getByRole('button', { name: `@${user.username}` }).click()
    await expect(page).toHaveURL(/\/users\/\d+$/)
    // データ取得(loadProfileAndPosts)完了前はローディングスピナーのみが表示され、
    // mainランドマークやh1を持たないため、実際のコンテンツが表示されるまで待ってから検査する。
    await expect(page.getByText(`@${user.username}`)).toBeVisible()

    await expectNoAccessibilityViolations(page)
  })
})
