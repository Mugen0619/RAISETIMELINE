# 技術スタック

[要件定義書](./requirements.md)へ戻る

技術スタックはRaiseTech中級編カリキュラムでJava / Spring Boot backendを扱う前提のため、フレームワークの代替案比較は行わず、バージョン選定を中心に整理する。バージョンは下書き案のメジャーバージョンを基本的に維持しつつ、2026年9月時点で確認できた各メジャーラインの最新安定版を記載する(実装着手時に再度最新パッチへの追従を確認すること)。

インフラ(AWS/Terraform)は[infrastructure.md](./infrastructure.md)を参照。

## フロントエンド

| 技術 | バージョン | 備考 |
|---|---|---|
| React | 19.2系 | 執筆時点の最新パッチは19.2.8 |
| TypeScript | 5.9系 | |
| Vite | 7.3系 | 執筆時点の最新パッチは7.3.0。Vite 8(Rolldownバンドラ搭載)は新メジャーとして存在するが、今回は下書き案のVite 7系を維持 |
| MUI(Material UI) | 7.3系 | MUIは v7 → v9 と続けてメジャーアップしているが、今回は下書き案のv7系を維持 |
| React Router | 7.18系 | 画面遷移・認証ガードに使用 |
| Vitest | 3.2系 | フロントエンドの単体・結合テスト。Vitest 5系は依存関係解決が複雑になり導入時にエラーが出たため3系を採用 |
| React Testing Library | 16.3系 | Vitestと組み合わせてコンポーネントテストに使用(`@testing-library/jest-dom` 6.9系、`@testing-library/user-event` 14.6系、`jsdom` 26.1系) |
| Playwright | 1.63系 | 代表的なユーザージャーニーのE2Eテストに使用。バックエンド・フロントエンドを実際に起動した状態でローカル実行する(CIでの自動実行は対象外) |

## バックエンド

| 技術 | バージョン | 備考 |
|---|---|---|
| Java | 25(LTS) | 2025年9月GAのLTS版。次期LTSは27(2027年9月予定) |
| Spring Boot | 4.0系 | 執筆時点の最新パッチは4.0.7。Spring Boot 4.0.xはJava 21〜25をサポート |
| Gradle | 9系 | Spring Boot 4.0.xの最小要件はGradle 8.14だが、9系が推奨 |
| AWS SDK for Java v2 | 2.54系 | 投稿画像アップロード用のS3署名付きURL(presigned URL)発行に使用(`S3Presigner`) |
| springdoc-openapi | 3.1系 | OpenAPI 3.1仕様書・Swagger UIの自動生成(`/swagger-ui.html`)。Spring Boot 4系に対応したv3系を採用 |

## データベース

| 技術 | バージョン | 備考 |
|---|---|---|
| PostgreSQL | 17系 | 執筆時点の最新パッチは17.11。PostgreSQL 18が新メジャーとして存在するが、今回は下書き案の17系を維持 |
