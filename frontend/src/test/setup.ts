import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'
import '@testing-library/jest-dom/vitest'

// globals:trueを使わない構成のため、RTLの自動クリーンアップ(afterEachでのunmount)を明示的に登録する
afterEach(() => {
  cleanup()
})

// jsdomはIntersectionObserverを実装していないため、無限スクロール実装をレンダリングできるようスタブを用意する
class IntersectionObserverStub implements IntersectionObserver {
  readonly root: Element | Document | null = null
  readonly rootMargin: string = ''
  readonly thresholds: ReadonlyArray<number> = []
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
  takeRecords(): IntersectionObserverEntry[] {
    return []
  }
}

Object.defineProperty(globalThis, 'IntersectionObserver', {
  writable: true,
  value: IntersectionObserverStub,
})

// jsdomはwindow.scrollToを実装しておらず、呼び出すとコンソールに警告が出るため無害化する
Object.defineProperty(window, 'scrollTo', {
  writable: true,
  value: () => {},
})

// jsdomはURL.createObjectURL/revokeObjectURLを実装していないため、画像プレビュー機能のテスト用にスタブを用意する
Object.defineProperty(URL, 'createObjectURL', {
  writable: true,
  value: () => 'blob:mock-url',
})

Object.defineProperty(URL, 'revokeObjectURL', {
  writable: true,
  value: () => {},
})
