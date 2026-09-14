# ログ構造化

[要件定義書](./requirements.md)へ戻る

外部監視ツール(Datadog等)との連携、分散トレーシング基盤の構築は対象外とし、今回はアプリケーションのログファイル(標準出力)をJSON形式に構造化するところまでを対象とする。

## 技術スタック

| 技術 | バージョン | 備考 |
|---|---|---|
| logstash-logback-encoder | 9.0系 | Logback用JSONエンコーダー。Jackson 3系に対応しており、本プロジェクトのSpring Boot 4 / Jackson 3構成と一致する |

## 出力形式

`logback-spring.xml`で、`test`プロファイル以外は`LogstashEncoder`によるJSON出力に統一する(`test`プロファイルはテスト結果の可読性を優先し、従来のテキスト形式のまま出力する)。

各ログ行には以下のフィールドを含める。

| フィールド | 内容 | 付与元 |
|---|---|---|
| `@timestamp`, `level`, `message` | ログの標準フィールド | logstash-logback-encoderの標準機能 |
| `service` | サービス名の固定値(`spring.application.name`) | `logback-spring.xml`の`customFields` |
| `traceId` | リクエスト単位で発行するUUID | `RequestLoggingFilter`がMDCに設定 |
| `userId` | JWT認証済みユーザーのID(未ログイン時は含めない) | `JwtAuthenticationFilter`がMDCに設定 |
| `httpStatus`, `endpoint`, `method`, `duration_ms` | アクセスログ(1リクエスト1行) | `RequestLoggingFilter`が`StructuredArguments`で付与 |
| `exceptionType`, `exceptionMessage` | 例外発生時の例外クラス名・メッセージ | `GlobalExceptionHandler`が`StructuredArguments`で付与 |

## traceId / userIdの伝播

- `RequestLoggingFilter`はSpring Securityを含む全フィルターより先に実行され(`@Order(Ordered.HIGHEST_PRECEDENCE)`)、リクエスト受付時にUUIDを発行してMDCに設定する。リクエスト完了時(`finally`)にアクセスログを1行出力し、MDCを必ずクリアする(Tomcatのスレッドプールが別リクエストに使い回されてもMDCの値が漏れ出さないようにするため)
- `JwtAuthenticationFilter`は認証に成功した場合のみ、以降のログ(コントローラー等)にユーザーIDが自動的に含まれるようMDCに設定する。MDC自体のクリアは`RequestLoggingFilter`のリクエスト終了時処理に一本化している

## 秘密情報の扱い

- パスワード・JWTトークン・カード番号はいかなる形でもログに出力しない。アクセスログにはHTTPメタデータ(ステータス・パス・メソッド・処理時間)のみを含め、リクエスト/レスポンスボディそのものはログに含めない設計とすることで、これらの秘密情報が構造的にログへ混入しないようにしている
- 例外メッセージ(`exceptionMessage`)も、既存の例外クラスは全て定型文(例:「invalid email or password」)であり、パスワードやメールアドレスの実値を含まない

## 対象外

- Datadog等の外部監視ツールとの連携
- 分散トレーシング基盤の構築(単一サービスのため、`traceId`はリクエスト単位のシンプルなUUID発行のみ)
