import { test, expect, type Locator, type Page } from '@playwright/test'
import { createTestUser, registerAndLogin } from './helpers.js'

// ローカル開発環境(Vite dev server + ローカルDB)での実行を前提とした、
// 個人開発規模で現実的なしきい値。本番相当の環境・大量データでの計測は対象外とする。
const LOAD_TIME_THRESHOLD_MS = 3000

/**
 * ブラウザのPerformance APIから、直近のページロード(Navigation Timing)の所要時間を取得する。
 * 参考情報としてログに出すのみで、しきい値判定には使わない(下記の理由を参照)。
 * SPA内のクライアントサイド遷移では新たなnavigation entryは発生しないため、
 * 計測対象の画面へは`page.goto()`で実際にページ全体を読み込ませる必要がある。
 */
async function measureNavigationTimingMs(page: Page): Promise<number | null> {
  return page.evaluate(() => {
    const [entry] = performance.getEntriesByType('navigation') as PerformanceNavigationTiming[]
    return entry ? entry.loadEventEnd - entry.startTime : null
  })
}

/**
 * ナビゲーション開始(timeOrigin)から、指定した要素が実際に表示されるまでの経過時間を計測する。
 * Navigation Timingの`load`イベントはJSバンドルの読み込み完了時点で発火し、SPAが
 * データ取得(fetch)を終えて実コンテンツを描画するタイミングとは一致しないため、
 * 「初回表示までの時間」を検証するにはこちらを使う。`performance.now()`自体もPerformance APIの一部。
 */
async function measureTimeToVisibleMs(page: Page, locator: Locator): Promise<number> {
  await locator.waitFor({ state: 'visible' })
  return page.evaluate(() => performance.now())
}

function logTiming(label: string, timeToVisibleMs: number, navigationTimingMs: number | null) {
  const navigationLabel = navigationTimingMs === null ? 'N/A' : `${navigationTimingMs.toFixed(0)}ms`
  console.log(`[performance] ${label}: timeToVisible=${timeToVisibleMs.toFixed(0)}ms (参考: navigation load=${navigationLabel})`)
}

test.describe('ブラウザパフォーマンス', () => {
  test('タイムライン画面の初回表示が3秒以内に完了する', async ({ page }) => {
    const user = createTestUser('perf-tl')
    await registerAndLogin(page, user)

    const postBody = `Perfテスト投稿(タイムライン) ${Date.now()}`
    await page.getByRole('button', { name: 'いまどうしてる？' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('いまどうしてる？(280文字まで)').fill(postBody)
    await dialog.getByRole('button', { name: '投稿する' }).click()
    await expect(page.getByText(postBody)).toBeVisible()

    await page.goto('/home', { waitUntil: 'load' })
    const timeToVisibleMs = await measureTimeToVisibleMs(page, page.getByText(postBody))
    const navigationTimingMs = await measureNavigationTimingMs(page)

    logTiming('timeline', timeToVisibleMs, navigationTimingMs)
    expect(timeToVisibleMs).toBeLessThan(LOAD_TIME_THRESHOLD_MS)
  })

  test('投稿詳細画面の初回表示が3秒以内に完了する', async ({ page }) => {
    const user = createTestUser('perf-post')
    await registerAndLogin(page, user)

    const postBody = `Perfテスト投稿 ${Date.now()}`
    await page.getByRole('button', { name: 'いまどうしてる？' }).click()
    const dialog = page.getByRole('dialog')
    await dialog.getByPlaceholder('いまどうしてる？(280文字まで)').fill(postBody)
    await dialog.getByRole('button', { name: '投稿する' }).click()
    await page.getByText(postBody).click()
    await expect(page).toHaveURL(/\/posts\/\d+$/)
    const postUrl = page.url()

    await page.goto(postUrl, { waitUntil: 'load' })
    const timeToVisibleMs = await measureTimeToVisibleMs(page, page.getByText(postBody))
    const navigationTimingMs = await measureNavigationTimingMs(page)

    logTiming('post detail', timeToVisibleMs, navigationTimingMs)
    expect(timeToVisibleMs).toBeLessThan(LOAD_TIME_THRESHOLD_MS)
  })

  test('プロフィール画面の初回表示が3秒以内に完了する', async ({ page }) => {
    const user = createTestUser('perf-prof')
    await registerAndLogin(page, user)

    await page.getByRole('button', { name: `@${user.username}` }).click()
    await expect(page).toHaveURL(/\/users\/\d+$/)
    const profileUrl = page.url()

    await page.goto(profileUrl, { waitUntil: 'load' })
    const timeToVisibleMs = await measureTimeToVisibleMs(page, page.getByText(`@${user.username}`))
    const navigationTimingMs = await measureNavigationTimingMs(page)

    logTiming('profile', timeToVisibleMs, navigationTimingMs)
    expect(timeToVisibleMs).toBeLessThan(LOAD_TIME_THRESHOLD_MS)
  })
})
