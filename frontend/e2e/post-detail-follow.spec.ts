import { test, expect, type Browser } from '@playwright/test'
import { createTestUser, registerAndLogin } from './helpers.js'

async function newUserPage(browser: Browser) {
  // localStorageにトークンを保存する実装のため、ユーザーごとに独立したBrowserContextを使い
  // 「別々の端末でログインしている2人のユーザー」を再現する
  const context = await browser.newContext()
  const page = await context.newPage()
  return { context, page }
}

test('投稿詳細→コメント→いいね→フォロー→プロフィール反映', async ({ browser }) => {
  const author = createTestUser('author')
  const viewer = createTestUser('viewer')
  const postBody = `E2Eフォローテスト投稿 ${Date.now()}`
  const commentBody = `E2Eコメント ${Date.now()}`

  const authorSession = await newUserPage(browser)
  const viewerSession = await newUserPage(browser)

  try {
    let postUrl = ''

    await test.step('投稿者が投稿を作成する', async () => {
      await registerAndLogin(authorSession.page, author)
      await authorSession.page.getByRole('button', { name: 'いまどうしてる？' }).click()
      const dialog = authorSession.page.getByRole('dialog')
      await dialog.getByPlaceholder('いまどうしてる？(280文字まで)').fill(postBody)
      // タイムライン画面の投稿FABも同じ「投稿する」というaria-labelを持つため、ダイアログ内に絞り込む
      await dialog.getByRole('button', { name: '投稿する' }).click()
      await authorSession.page.getByText(postBody).click()
      await expect(authorSession.page).toHaveURL(/\/posts\/\d+$/)
      postUrl = authorSession.page.url()
    })

    await test.step('閲覧者が投稿詳細画面でコメントし、いいねする', async () => {
      await registerAndLogin(viewerSession.page, viewer)
      await viewerSession.page.goto(postUrl)

      await viewerSession.page.getByPlaceholder('コメントする…').fill(commentBody)
      await viewerSession.page.getByRole('button', { name: 'コメントを送信' }).click()
      await expect(viewerSession.page.getByText(commentBody)).toBeVisible()

      const likeButton = viewerSession.page.getByRole('button', { name: 'いいね' })
      await expect(likeButton).toHaveAttribute('aria-pressed', 'false')
      await likeButton.click()
      await expect(likeButton).toHaveAttribute('aria-pressed', 'true')
    })

    await test.step('投稿者のプロフィールへ遷移してフォローする', async () => {
      await viewerSession.page.getByRole('button', { name: author.displayName, exact: true }).click()
      await expect(viewerSession.page).toHaveURL(/\/users\/\d+$/)

      const followButton = viewerSession.page.getByRole('button', { name: 'フォローする' })
      await expect(followButton).toBeVisible()
      await followButton.click()
      // プロフィール自身の「n フォロー中」統計ボタンと紛らわしいため、exactで絞り込む
      await expect(viewerSession.page.getByRole('button', { name: 'フォロー中', exact: true })).toBeVisible()
      await expect(viewerSession.page.getByText('フォロワー')).toContainText('1')
    })

    await test.step('再読み込みしてもフォロー状態がプロフィール画面に反映されている', async () => {
      await viewerSession.page.reload()
      await expect(viewerSession.page.getByRole('button', { name: 'フォロー中', exact: true })).toBeVisible()
      await expect(viewerSession.page.getByText('フォロワー')).toContainText('1')
    })
  } finally {
    await authorSession.context.close()
    await viewerSession.context.close()
  }
})
