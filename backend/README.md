# RAISETIMELINE backend

技術スタックは[docs/tech-stack.md](../docs/tech-stack.md)を参照(Java 25 / Spring Boot 4.0系 / Gradle 9系 / PostgreSQL 17系)。

## セットアップ

1. `.env.example` を `.env` にコピーし、`JWT_SECRET` にランダムな文字列(32バイト以上)を設定する。
   ```
   cp .env.example .env
   openssl rand -base64 48   # 生成した値をJWT_SECRETに設定
   ```
   `.env` はGit管理外。`JWT_SECRET` は`./gradlew bootRun`実行時に自動で読み込まれる。
2. リポジトリルートで `docker compose up -d` を実行し、ローカルDB(PostgreSQL 17、ポート5432)を起動する。
3. `./gradlew bootRun` でアプリを起動する(ポート8080固定)。

## テスト

```
./gradlew test
```
