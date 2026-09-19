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

## API仕様書(Swagger UI)

アプリ起動後、`http://localhost:8080/swagger-ui.html` でSwagger UIにアクセスできる(springdoc-openapiによりコードから自動生成)。
認証が必要なAPIは、右上の「Authorize」ボタンから `/api/auth/login` 等で取得したアクセストークンを入力すると、Swagger UI上でそのまま呼び出せる。

## テスト

```
./gradlew test
```

## 本番環境(prodプロファイル)

`SPRING_PROFILES_ACTIVE=prod` で起動すると、Actuatorのヘルスチェック(`/actuator/health`のみ公開)が有効になる(構造化JSONログは`logback-spring.xml`により`test`以外の全プロファイルで有効)。RDS接続情報・CORS許可オリジン(`CORS_ALLOWED_ORIGIN`)等は環境変数で切り替える。Dockerイメージのbuild、ECR push、ECSへのデプロイ手順は[infra/production/README.md](../infra/production/README.md)を参照。
