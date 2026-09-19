# infra/production

RAISETIMELINEの本番環境用Terraform構成。ECS Fargate + RDS(PostgreSQL) + S3/CloudFrontで、既存の[TASKMANAGEMENT用EC2/RDS](../../docs/infrastructure.md)とは完全に独立したVPC・リソース一式を新規構築する。

投稿画像用S3バケット・アップロード専用IAMユーザー(`../`で管理)はここでは変更しない。既存の出力値を`terraform.tfvars`経由で受け取る。

- Terraform 1.15系 / AWS provider ~> 6.0(詳細は[../../docs/tech-stack.md](../../docs/tech-stack.md)参照)
- 状態(state)は`../`とは別に管理する(誤って画像アップロード用の既存リソースに影響しないため)

## 構成

- VPC(`10.1.0.0/16`、2AZ、パブリック/プライベートサブネット、NAT Gateway1つ)
- RDS(PostgreSQL 17、`db.t4g.micro`、プライベートサブネット、外部非公開)
- ECR(バックエンドDockerイメージ)
- ECS Fargate(クラスター/タスク定義/サービス)
- ALB(パブリックサブネット、HTTPのみ)
- S3(フロントエンド静的ファイル、OAC経由のみアクセス可)+ CloudFront

### CloudFrontとALBのオリジン構成について

ALBはカスタムドメイン・HTTPS対応が今回のスコープ外のためHTTPのみで公開される。CloudFront(HTTPS)からALB(HTTP)を直接叩く構成にすると、ブラウザがMixed Contentとしてブロックしてしまう。

そのため、CloudFrontに`/api/*`のビヘビアを追加し、ALBを2つ目のオリジンとして内部的にHTTPでプロキシする構成にした。ブラウザは常にCloudFrontの単一HTTPSドメインとしか通信しないため、Mixed Contentは発生せず、実質的に同一オリジンとなるためCORSも本質的には不要になる(ただし柔軟性のため`CORS_ALLOWED_ORIGIN`によるCORS設定自体は入れてある)。

### 費用面の判断

個人開発規模の学習目的のため、以下はコストを優先した判断とした(可用性より優先):

- NAT Gatewayは1つのみ(AZ冗長化しない)
- RDSはSingle-AZ
- ECSタスクは小サイズ(CPU 512 / Memory 1024)、desired_count 1。CPU 256 / Memory 512ではSpring Bootの起動に約114秒かかりALBのヘルスチェックに失敗したため引き上げ、あわせてサービスに`health_check_grace_period_seconds = 240`を設定している
- RDSは`db.t4g.micro`(無料利用枠を超える可能性があるが、フルマネージドDB構成を学ぶ目的で許容)

### NAT Gateway代替の検討結果

Fargateタスクが外部通信を必要とするのはECR pull・CloudWatch Logs・Secrets Managerの3用途のみのため、NAT GatewayをVPCエンドポイント(Interface型×4種 + S3 Gateway型)に置き換えられないか検討した。実際にTerraform構成を変更し`terraform plan`が通ることまで確認したが、東京リージョンの料金(NAT: $0.062/h+$0.062/GB、Interfaceエンドポイント: $0.014/AZ/h、AWS公式bulk pricing APIで確認)で試算すると、必要な4種を2AZに配置した場合は約$82/月とNAT Gateway(約$45/月)より高くなり、1AZに絞っても約$41/月とわずかな削減(約10%)にとどまった。将来的に他のAWS API・外部サービス呼び出しが増えても個別対応が不要なNAT Gatewayの柔軟性を優先し、NAT Gateway方式を維持することとした。

## 使い方

### 1. 既存インフラの出力値を確認する

```
cd ../
terraform output bucket_name
terraform output bucket_public_base_url
terraform output access_key_id
terraform output -raw secret_access_key
```

### 2. terraform.tfvarsを作成する

```
cd production
cp terraform.tfvars.example terraform.tfvars
```

上記1で取得した値を`terraform.tfvars`に転記する(Git管理外)。

### 3. init / plan

```
terraform init
terraform plan
```

**`.tf`ファイル作成・変更後は`terraform apply`を実行せず、plan結果とファイル内容をユーザーに報告し、明示的な承認を得てから`apply`を実行する。**

### 4. 初回apply(バックエンドイメージ未pushの状態)

初回applyの時点ではECRリポジトリが空のため、ECSタスクは起動に失敗する(イメージが存在しない)。`aws_ecs_service`のapply自体はエラーにならず完了する。イメージをpushした後、下記「デプロイ手順」の3〜4を実行してサービスを安定させる。

```
terraform apply   # 内容確認・ユーザー承認後に実行する
```

## デプロイ手順

### バックエンド(Dockerイメージ build → ECR push → ECSサービス更新)

1. ECRへログインする
   ```
   aws ecr get-login-password --region ap-northeast-1 | docker login --username AWS --password-stdin <ecr_repository_urlのアカウント部分>.dkr.ecr.ap-northeast-1.amazonaws.com
   ```
2. イメージをbuildしてpushする(`backend/`ディレクトリで実行)
   ```
   docker build -t raisetimeline-backend .
   docker tag raisetimeline-backend:latest <ecr_repository_url>:latest
   docker push <ecr_repository_url>:latest
   ```
3. ECSサービスに新しいイメージを反映する(タスク定義のイメージタグは`latest`固定のため、強制的に新デプロイを走らせる)
   ```
   aws ecs update-service --cluster raisetimeline-cluster --service raisetimeline-backend --force-new-deployment --region ap-northeast-1
   ```
4. デプロイが安定するまで待つ
   ```
   aws ecs wait services-stable --cluster raisetimeline-cluster --services raisetimeline-backend --region ap-northeast-1
   ```

### フロントエンド(build → S3 upload → CloudFrontキャッシュ無効化)

1. API接続先をCloudFront経由の相対パス(`/api`)のままにする場合、ビルド時の環境変数指定は不要(デフォルトで`/api`を使う)。ALBへ直接接続する等、接続先を明示的に切り替えたい場合のみ`VITE_API_BASE_URL`を指定する
   ```
   cd frontend
   npm run build
   # 例: VITE_API_BASE_URL=http://<alb_dns_name> npm run build
   ```
2. S3へアップロードする
   ```
   aws s3 sync dist/ s3://<frontend_bucket_name>/ --delete
   ```
3. CloudFrontのキャッシュを無効化する
   ```
   aws cloudfront create-invalidation --distribution-id <cloudfront_distribution_id> --paths "/*"
   ```

## 既存の投稿画像用S3バケットのCORS設定について

投稿画像は署名付きURLでブラウザから直接S3へPUTアップロードする方式のため、`../`の`aws_s3_bucket_cors_configuration`の`allowed_origins`に、本番のCloudFrontドメイン(`https://<cloudfront_domain_name>`)を含める必要がある。

`../variables.tf`の`cors_allowed_origins`(リスト)に、ローカル開発用(`http://localhost:5173`)とCloudFrontドメインを設定している。CloudFrontドメインはapplyのたびに変わるため、`infra/production`をapplyし直した場合は、新しいドメインに更新して`../`で`terraform apply`すること(現在の値は動作確認に使った後に破棄したドメインのままになっている)。

## 後片付け

```
terraform destroy
```
