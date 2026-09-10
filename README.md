# RAISETIMELINE

RaiseTech中級編の課題として開発する、X/Twitter風のテキストベースSNS。RaiseTech受講生・卒業生など学習コミュニティ内での交流を想定した会員制SNSで、タイムライン・投稿・コメント・いいね・フォロー・ユーザー検索を中核機能とする。

詳細な要件は[docs/requirements.md](docs/requirements.md)、技術スタックは[docs/tech-stack.md](docs/tech-stack.md)を参照。

## 主な機能

- ユーザー登録・ログイン(JWT認証、リフレッシュトークンによる自動再認証)
- タイムライン(新着順・無限スクロール、新着投稿のバナー通知、フォロー中タイムラインの絞り込み)
- 投稿の作成・編集・削除(280文字制限)
- 投稿詳細画面でのコメント投稿・削除
- 投稿への「いいね」(トグル)
- フォロー・フォロー解除、フォロー一覧・フォロワー一覧
- プロフィール表示・編集
- ユーザー検索(ユーザー名・表示名の部分一致)

## 今後実装予定

- 画像添付

## セットアップ

- バックエンド: [backend/README.md](backend/README.md)を参照(Java / Spring Boot、port 8080固定)
- フロントエンド: `frontend/`ディレクトリで `npm install && npm run dev`(React / Vite、port 5173固定、`/api`をバックエンドへプロキシ)
- ローカルDB: リポジトリルートで `docker compose up -d`(PostgreSQL、port 5432固定)
