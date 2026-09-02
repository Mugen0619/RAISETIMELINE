# データモデル設計

[要件定義書](./requirements.md)へ戻る

## 主要エンティティ

| エンティティ | 主なカラム | 備考 |
|---|---|---|
| User | id, username, email, password_hash, display_name, bio, avatar_url, created_at | |
| Post | id, user_id(FK), body(≤280文字), created_at, updated_at | |
| PostImage | id, post_id(FK), image_url, sort_order | 1投稿に対し複数枚を許容 |
| Comment | id, post_id(FK), user_id(FK), body, created_at | |
| Like | id, post_id(FK), user_id(FK), created_at | post_id + user_idでユニーク制約 |
| Follow | id, follower_id(FK→User), followee_id(FK→User), created_at | follower_id + followee_idでユニーク制約 |
| RefreshToken | id, user_id(FK→User), token_hash, expires_at, created_at | 生トークンは保存せずSHA-256ハッシュのみ保存。使用時に削除し新トークンを再発行(ローテーション) |

詳細なER図・インデックス設計は実装フェーズで別途整理する。

## 関連ドキュメント

各機能とデータモデルの対応は[features/](./features/)配下の各機能定義書の「関連データモデル」を参照。
