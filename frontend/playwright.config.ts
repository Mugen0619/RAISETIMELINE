import { defineConfig, devices } from '@playwright/test'

// バックエンド(実サーバー・実DB)とフロントエンドを事前に起動しておくことを前提とする。
// 起動手順はREADME.mdの「E2Eテスト」を参照。
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  // CI上ではplaywright-report(HTML)もあわせて生成し、失敗時にアーティファクトとして確認できるようにする
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
