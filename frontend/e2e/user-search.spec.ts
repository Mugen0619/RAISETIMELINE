import { test, expect } from '@playwright/test'
import { createTestUser, registerAndLogin } from './helpers.js'

test('ユーザー検索→検索結果からプロフィール画面へ遷移', async ({ browser }) => {
  const target = createTestUser('target')
  const searcher = createTestUser('searcher')

  const targetContext = await browser.newContext()
  const searcherContext = await browser.newContext()

  try {
    const targetPage = await targetContext.newPage()
    const searcherPage = await searcherContext.newPage()

    await test.step('検索対象となるユーザーを作成しておく', async () => {
      await registerAndLogin(targetPage, target)
    })

    await test.step('別ユーザーでログインし、ユーザー名で検索する', async () => {
      await registerAndLogin(searcherPage, searcher)

      await searcherPage.getByRole('button', { name: 'ユーザー検索' }).click()
      await expect(searcherPage).toHaveURL(/\/search$/)

      await searcherPage.getByPlaceholder('ユーザー名・表示名で検索').fill(target.username)
      await expect(searcherPage.getByText(target.displayName)).toBeVisible()
    })

    await test.step('検索結果をクリックするとプロフィール画面に遷移する', async () => {
      await searcherPage.getByText(target.displayName).click()
      await expect(searcherPage).toHaveURL(/\/users\/\d+$/)
      // ヘッダーとプロフィールカードの両方に同名の見出しが表示されるため、いずれか一つの表示を確認する
      await expect(searcherPage.getByRole('heading', { name: target.displayName }).first()).toBeVisible()
      await expect(searcherPage.getByText(`@${target.username}`)).toBeVisible()
    })
  } finally {
    await targetContext.close()
    await searcherContext.close()
  }
})
