# 運用ガイド(ログ調査・簡易インシデント対応)

[要件定義書](./requirements.md)へ戻る

ログの構造化(JSON化・traceId/userId付与)については[observability.md](./observability.md)を参照。本ドキュメントは、実際に障害調査を行う際に「どのログをどう検索するか」「何を確認すればよいか」を、個人開発規模を想定して簡潔にまとめたものである。

## 前提: ログの出力先

開発環境(`./gradlew bootRun`)では、ログは標準出力にJSON形式(1行1レコード)で出力される。ローカルで障害調査する場合は、標準出力をファイルにリダイレクトするか、ターミナルのスクロールバックに対して直接以下のコマンドを使う。

```bash
# 標準出力をファイルに保存しながら起動する場合の例
./gradlew bootRun | tee app.log
```

AWSデプロイ後の本番環境でのログ収集基盤(CloudWatch Logs等)の構成は、[要件定義書 9. 今後扱う技術トピック](./requirements.md#9-今後扱う技術トピック予告)のAWSデプロイフェーズで別途整理する。以下のコマンド例は、いずれもログファイル(`app.log`)に対して実行する前提とする。

## ログの読み方

### 1. 特定のリクエストを1件だけ追う(traceId)

エラーが起きた1リクエストの処理の流れを、関連する全ログ行(アクセスログ・例外ログ等)を時系列で確認したい場合、そのリクエストの`traceId`でファイル全体を検索する。

```bash
grep "9bf23160-4ed8-444c-a3c9-911c151a7214" app.log
```

1リクエストにつき、最低でもアクセスログ(`"message":"http request completed"`)が1行出力される。エラーが発生していれば、それに加えて例外ログ(`GlobalExceptionHandler`または`UnhandledExceptionLoggingResolver`が出力する行)が同じ`traceId`で見つかる。

```
{"message":"request rejected with 400", ..., "traceId":"9bf23160-...", "userId":"65", "exceptionType":"InvalidPostContentException", "exceptionMessage":"a post must have a body or at least one image", ...}
{"message":"http request completed", ..., "traceId":"9bf23160-...", "userId":"65", "httpStatus":400, "endpoint":"/api/posts", "method":"POST", "duration_ms":64, ...}
```

traceIdはAPIレスポンス自体には含まれない(レスポンスボディを見ても分からない)ため、ユーザーからの問い合わせ調査では次のuserId・時刻・エンドポイントの組み合わせで対象のログ行を先に探し、そこからtraceIdを拾って関連ログを追う、という流れになることが多い。

### 2. 特定ユーザーの操作を追う(userId)

「あるユーザーの操作でエラーが起きた」といった調査では、`userId`フィールドで絞り込む。他のフィールド値の部分一致(例: `userId:"165"`が`userId:"65"`にもマッチしてしまう)を避けるため、前後のクォートまで含めて検索する。

```bash
grep '"userId":"65"' app.log
```

未ログイン状態のリクエスト(ログイン・登録処理など)には`userId`フィールド自体が含まれない([observability.md](./observability.md)参照)ため、この検索には出てこない。

### 3. エラーだけを抽出する(httpStatus・exceptionType)

特定期間に発生したエラーの傾向を把握したい場合は、`httpStatus`やレベル(`level`)、`exceptionType`で絞り込む。

```bash
# 4xx系のアクセスログのみ抽出
grep '"httpStatus":4' app.log

# 5xx系(予期しないサーバーエラー)のみ抽出
grep '"httpStatus":5' app.log

# WARN/ERRORレベルのログのみ抽出(例外ログはWARN以上で出力される)
grep -E '"level":"(WARN|ERROR)"' app.log

# 特定の例外種類が何回発生しているか集計
grep -o '"exceptionType":"[^"]*"' app.log | sort | uniq -c | sort -rn
```

`jq`が使える環境では、フィールドを指定した抽出・整形がより簡単に書ける。

```bash
# httpStatusが500以上の行だけを、endpoint・durationとあわせて見やすく表示する
jq -c 'select(.httpStatus >= 500) | {endpoint, httpStatus, duration_ms, traceId}' app.log
```

## よくあるエラーパターンと確認すべきログ項目

| 症状 | まず確認するログ項目 | 典型的な原因 |
|---|---|---|
| 特定APIが401を返す | `exceptionType`(`InvalidCredentialsException`/`InvalidRefreshTokenException`か)、`endpoint`が`permitAll`対象か | トークン期限切れ、誤ったエンドポイントへの未認証アクセス |
| 特定APIが400を返す | `exceptionType`/`exceptionMessage`、直前のリクエストバリデーション内容 | 不正なリクエストボディ、業務ルール違反(例: 画像枚数超過) |
| 特定APIが403/404を返す | `exceptionType`(`ForbiddenXxxAccessException`/`XxxNotFoundException`)、`userId`と対象リソースの所有者が一致するか | 他人のリソースへの操作、削除済み/存在しないIDの指定 |
| レスポンスが遅い | `duration_ms`の分布、同一`endpoint`の複数リクエストでの傾向 | N+1クエリの再発、外部サービス(S3等)呼び出しの遅延 |
| 原因不明の500 | `exceptionType`が業務例外(`XxxException`)ではなく標準の例外クラス名になっていないか、スタックトレース(`UnhandledExceptionLoggingResolver`がERRORレベルで出力) | 未処理の実装バグ、想定外のnull、DB制約違反 |

## 簡易インシデント対応の流れ

個人開発規模を想定し、大掛かりな体制を組まずに1人で完結できる範囲の流れとする。

1. **気づく**: ユーザー(自分)からの報告、または`httpStatus`5xx・ERRORレベルのログを定期的に(手動で)確認して気づく
2. **範囲を特定する**: 「特定ユーザーだけか、全員か」「特定APIだけか、複数か」「いつから発生しているか」を、`userId`/`endpoint`/`@timestamp`でログを絞り込んで把握する
3. **原因ログを1件特定する**: 該当する時間帯・エンドポイントのアクセスログから`traceId`を1つ拾い、そのtraceIdで関連ログ全体(上記「1. 特定のリクエストを1件だけ追う」参照)を確認する
4. **原因を切り分ける**: 上表の「よくあるエラーパターン」を参考に、`exceptionType`/`exceptionMessage`から原因のあたりをつける。業務例外(意図した4xx)なら仕様通りの可能性が高く、標準例外や予期しない500なら実装バグを疑う
5. **一時対応と恒久対応を分ける**: 影響が大きい場合はまず一時対応(該当機能の無効化、再起動等)を検討し、その後コードを修正して恒久対応する
6. **再発防止**: 原因がコードの不具合であれば、再発を防ぐテストを追加してから修正をリリースする

## 将来Datadog等を導入する場合の作業概要(参考情報)

現時点では、外部監視ツール(Datadog等)との連携は対象外とし、ログの構造化(JSON化)までを実施している。将来的に導入する場合、概ね以下の作業が必要になる見込み。

- **エージェント/ログ収集の導入**: Datadog Agentのインストール(EC2上)、またはログ転送先としてFireLens/CloudWatch Logs経由でDatadogに転送する構成の検討
- **ログのパース設定**: 現状のJSON構造(`@timestamp`, `level`, `service`, `traceId`, `userId`等)をDatadog側のログパイプラインでどう解釈させるかの設定(JSON形式であれば多くの場合自動認識されるが、`@timestamp`等のフィールド名マッピングの調整が必要になることがある)
- **分散トレーシングへの拡張**: 現状の`traceId`はリクエスト単位のシンプルなUUIDに過ぎず、Datadog APM等の分散トレーシング標準(W3C Trace Context等)には準拠していない。将来複数サービスにまたがるトレーシングが必要になった場合は、トレーシングライブラリ(OpenTelemetry等)の導入を検討する
- **アラート設定**: 5xxエラー率、レイテンシ(`duration_ms`)の閾値超過等に対するアラート・ダッシュボードの構築
- **コスト管理**: ログ量に応じた課金が発生するため、保持期間・サンプリング率の設計

これらは全て本ドキュメントの対象外であり、実際に導入する際に個別のIssueとして要件・設計を詰める。
