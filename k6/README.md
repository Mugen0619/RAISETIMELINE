# k6負荷試験

バックエンドAPIの代表的なエンドポイントに対する負荷試験。個人開発規模(数十仮想ユーザー程度)を想定しており、本番相当の大規模負荷(数千〜数万仮想ユーザー)やCIでの自動実行は対象外とする。E2Eテスト(Playwright)と同じくローカル実行のみを対象とする。

## 対象シナリオ

| シナリオ | ファイル | 対象API | 備考 |
|---|---|---|---|
| ログイン | `scenarios/login.js` | `POST /api/auth/login` | 同一アカウントへの並行ログイン |
| 全体タイムライン取得 | `scenarios/timeline.js` | `GET /api/posts` | 新着順・コメント数・いいね数を含む一覧取得 |
| フォロー中タイムライン取得 | `scenarios/following-timeline.js` | `GET /api/timeline/following` | N+1対策の一括集計APIの負荷特性を確認する |

いずれも仮想ユーザー数を10→30→50人と段階的に増やしながら5分間実行し(`lib/config.js`の`RAMPING_STAGES`)、レスポンスタイムを計測する。しきい値は「95%のリクエストが500ms以内に完了すること」「リクエスト失敗率1%未満」とする(`lib/config.js`の`DEFAULT_THRESHOLDS`)。

## 前提条件

- [k6](https://k6.io/) がインストールされていること(Windowsの場合 `winget install GrafanaLabs.k6` 等)
- バックエンド・DBをローカルで起動していること(`docker compose up -d`でDB起動、`cd backend && ./gradlew bootRun`でバックエンド起動。詳細はリポジトリルートの[README.md](../README.md)を参照)

## 実行方法

```bash
cd k6
k6 run scenarios/login.js
k6 run scenarios/timeline.js
k6 run scenarios/following-timeline.js
```

各シナリオは`setup()`内で、負荷試験に必要なテストデータ(専用ユーザー・投稿・コメント・いいね・フォロー関係)をAPI経由で自動的に作成する。実行のたびに一意なユーザー名(`k6_<実行時刻>_...`)を使うため、複数回実行してもユーザー名が衝突することはない。

対象のバックエンドURLはデフォルトで`http://localhost:8080`。変更する場合は`-e BASE_URL=...`で上書きする。

```bash
k6 run -e BASE_URL=http://localhost:8080 scenarios/timeline.js
```

動作確認だけしたい場合は、`--vus`/`--duration`でスクリプト内の`stages`設定を一時的に上書きできる(本格実行の前のスモークテストに使う)。

```bash
k6 run --vus 1 --duration 5s scenarios/login.js
```

## レポート

実行が終わると、各シナリオのディレクトリ直下にHTML形式のレポート(`report-login.html`等)が出力される。ブラウザで開くと、しきい値の合否・レスポンスタイムの分布・リクエスト数等が確認できる。レポートファイルは実行のたびに生成される成果物のため`.gitignore`でGit管理対象外にしている。

## テストデータの後片付け

各シナリオの`setup()`で作成したユーザー(`username`が`k6_`で始まる)は、負荷試験専用のテストデータとしてDBに残り続ける。繰り返し実行するとDBが肥大化するため、必要に応じて手動でクリーンアップする。

```sql
-- 依存関係の順に削除する(refresh_tokens → likes/comments → post_images → posts → follows → users)。
-- likes/commentsは「k6ユーザー自身が行ったもの」と「k6ユーザーの投稿に対して(他ユーザー含め)
-- 行われたもの」の両方を削除しないと、後段のDELETE FROM postsが外部キー制約違反で失敗しうる。
DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'k6_%');
DELETE FROM likes WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'k6_%')
	OR post_id IN (SELECT id FROM posts WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'k6_%'));
DELETE FROM comments WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'k6_%')
	OR post_id IN (SELECT id FROM posts WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'k6_%'));
DELETE FROM post_images WHERE post_id IN (SELECT id FROM posts WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'k6_%'));
DELETE FROM posts WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'k6_%');
DELETE FROM follows WHERE follower_id IN (SELECT id FROM users WHERE username LIKE 'k6_%') OR followee_id IN (SELECT id FROM users WHERE username LIKE 'k6_%');
DELETE FROM users WHERE username LIKE 'k6_%';
```

## 実測結果(参考値)

2026-09-18にローカル環境(Windows、Docker ComposeのPostgreSQL 17、バックエンド単体プロセス)で実行した結果。実行環境やマシンスペックによって変わるため、あくまで参考値とする。

| シナリオ | 総リクエスト数 | 失敗率 | 平均 | p95 | しきい値(p95<500ms)判定 |
|---|---|---|---|---|---|
| login | 7,372 | 0% | 100.02ms | 124.92ms | ✅ 達成 |
| timeline | 8,022 | 0% | 18.81ms | 33.01ms | ✅ 達成 |
| following-timeline | 8,085 | 0% | 20.51ms | 34.34ms | ✅ 達成 |

50仮想ユーザーまでの範囲では、3シナリオともしきい値を大きく下回るレスポンスタイムで安定して応答した。特にフォロー中タイムライン(`GET /api/timeline/following`)は、N+1対策として実装した一括集計API([database-design.md](../docs/database-design.md)、`PostService`の実装を参照)が、全体タイムラインと同程度のレスポンスタイムで応答しており、意図した設計の効果を負荷試験でも確認できた。ログインはBCryptによるパスワードハッシュ検証のCPUコストの分、他の2シナリオよりレスポンスタイムが長い傾向にあるが、しきい値には十分な余裕がある。

## ディレクトリ構成

```
k6/
├─ lib/
│  ├─ config.js    # BASE_URL・段階的VU数・しきい値の共通設定
│  ├─ auth.js       # ユーザー登録・ログインのヘルパー
│  ├─ posts.js       # 投稿・コメント・いいね・フォローのヘルパー(setup用)
│  └─ report.js     # HTMLレポート出力の共通ヘルパー(k6-reporterを使用)
├─ scenarios/
│  ├─ login.js
│  ├─ timeline.js
│  └─ following-timeline.js
└─ README.md
```
