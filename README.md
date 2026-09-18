# RAISETIMELINE

RaiseTech中級編の課題として開発する、X/Twitter風のテキストベースSNS。RaiseTech受講生・卒業生など学習コミュニティ内での交流を想定した会員制SNSで、タイムライン・投稿・コメント・いいね・フォロー・ユーザー検索を中核機能とする。

詳細な要件は[docs/requirements.md](docs/requirements.md)、技術スタックは[docs/tech-stack.md](docs/tech-stack.md)を参照。

## 主な機能

- ユーザー登録・ログイン(JWT認証、リフレッシュトークンによる自動再認証)
- タイムライン(新着順・無限スクロール、新着投稿のバナー通知、フォロー中タイムラインの絞り込み)
- 投稿の作成・編集・削除(280文字制限、画像添付は最大4枚)
- 投稿詳細画面でのコメント投稿・削除
- 投稿への「いいね」(トグル)
- フォロー・フォロー解除、フォロー一覧・フォロワー一覧
- プロフィール表示・編集
- ユーザー検索(ユーザー名・表示名の部分一致)

## セットアップ

- バックエンド: [backend/README.md](backend/README.md)を参照(Java / Spring Boot、port 8080固定)
- フロントエンド: `frontend/`ディレクトリで `npm install && npm run dev`(React / Vite、port 5173固定、`/api`をバックエンドへプロキシ)
- ローカルDB: リポジトリルートで `docker compose up -d`(PostgreSQL、port 5432固定)

## E2Eテスト

代表的なユーザージャーニー(新規登録〜投稿、投稿詳細でのコメント・いいね・フォロー、ユーザー検索)、主要画面のアクセシビリティ検査、主要画面のブラウザパフォーマンス計測をPlaywrightで行う。
単体・結合・コンポーネントテストとは異なり、バックエンド(実DB込み)とフロントエンドを実際に起動した状態で実行する。CIでの自動実行は対象外で、ローカルでのみ実行する。

1. 上記のセットアップ手順に従い、DB・バックエンド・フロントエンドをすべて起動しておく
2. 初回のみ、`frontend/`ディレクトリでブラウザ本体を取得する
   ```
   npx playwright install chromium
   ```
3. `frontend/`ディレクトリで実行する(ユーザージャーニー・アクセシビリティ・パフォーマンスをまとめて実行)
   ```
   npm run test:e2e
   ```
   個別に実行したい場合は、対象のファイルを指定する。
   ```
   npx playwright test e2e/accessibility.spec.ts
   npx playwright test e2e/performance.spec.ts
   ```

テストのたびに一意なユーザー名・投稿内容を生成するため、DBをリセットせずに繰り返し実行できる。

### アクセシビリティテスト

[`@axe-core/playwright`](https://github.com/dequelabs/axe-core-npm/tree/develop/packages/playwright)を使い、主要画面(ログイン・登録・タイムライン・投稿詳細・プロフィール)にアクセシビリティ違反(axe-coreのデフォルトルールセット)がないことを検証する(`frontend/e2e/accessibility.spec.ts`)。違反が見つかった場合はテスト側ではなく製品コード側を修正する方針とし、実際に本実装時も`main`ランドマークの欠如・見出し階層の飛びを検出し、該当コンポーネント(`AuthCard`・`TimelinePage`・`PostDetailPage`・`ProfilePage`)を修正して解消している。

### ブラウザパフォーマンステスト

`page.evaluate()`でブラウザのPerformance API(`performance.now()`)にアクセスし、主要画面(タイムライン・投稿詳細・プロフィール)の初回表示が3秒以内に完了することを検証する(`frontend/e2e/performance.spec.ts`)。SPAのため、Navigation Timingの`load`イベント(静的アセットの読み込み完了)だけでは、データ取得(API呼び出し)を経て実際にコンテンツが描画されるまでの時間を捉えられない。そのため、ナビゲーション開始(`timeOrigin`)から実コンテンツが画面に表示されるまでの経過時間を計測する(`load`完了時刻は参考値としてあわせてログ出力する)。しきい値はローカル開発環境(Vite dev server + ローカルDB)での実行を前提とした、個人開発規模で現実的な値。

2026-09-18にローカル環境で計測した実測値(参考値):

| 画面 | 実コンテンツ表示までの時間 | (参考)navigation load完了 | しきい値 |
|---|---|---|---|
| タイムライン | 約509ms | 約203ms | 3000ms |
| 投稿詳細 | 約261ms | 約158ms | 3000ms |
| プロフィール | 約241ms | 約156ms | 3000ms |

## 負荷試験

タイムライン取得・フォロー中タイムライン取得・ログインの代表的なAPIについて、[k6](https://k6.io/)で負荷試験を行う。個人開発規模(数十仮想ユーザー程度)を想定し、E2Eテストと同様にローカル実行のみを対象とする。詳細は[k6/README.md](k6/README.md)を参照。
