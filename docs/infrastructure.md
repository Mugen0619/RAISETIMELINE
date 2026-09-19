# インフラ構成

[要件定義書](./requirements.md)へ戻る

フロントエンド/バックエンド/DBの技術スタックは[tech-stack.md](./tech-stack.md)を参照。

## AWS / Terraform

| 技術 | バージョン | 備考 |
|---|---|---|
| AWS | ECS Fargate + RDS + ALB + S3 + CloudFront | |
| Terraform | 1.15系 | 執筆時点の最新安定版は1.15.8。AWS provider ~> 6.0 |
| PostgreSQL(RDS) | 17系 | [tech-stack.md](./tech-stack.md)参照 |

## 構成概要

- フロントエンド: S3(静的ファイルホスティング)+ CloudFront(配信、SPAルーティング対応)
- バックエンド: ECS Fargate(Dockerコンテナ化したSpring Bootアプリ。CPU 512 / Memory 1024、desired_count 1。0.25vCPU/512MBではSpring Boot起動に約114秒かかりALBヘルスチェックに失敗したため引き上げ)
- ALB: ECS Fargateタスクの前段のロードバランサー(パブリックサブネット、HTTPのみ)
- RDS: PostgreSQL(マネージドDB、プライベートサブネット、外部非公開)
- S3(投稿画像用): 投稿画像・アイコン画像の保存先。フロントエンド用S3バケットとは別バケット
- ECR: バックエンドDockerイメージの保存先
- Terraform: 上記インフラのコード管理(IaC)。投稿画像用S3バケット(`infra/`)と本番環境一式(`infra/production/`)で状態(state)を分離している

## ネットワーク構成

既存のTASKMANAGEMENT用リソース(`taskmanagement-vpc`、CIDR `10.0.0.0/16`)とは独立した、RAISETIMELINE専用のVPC(CIDR `10.1.0.0/16`)を新規構築する。

- 複数AZ(2AZ)にまたがってパブリックサブネット(ALB用)・プライベートサブネット(ECS Fargateタスク・RDS用)を配置
- NAT Gatewayを1つ配置し、プライベートサブネットからのアウトバウンド通信(ECRイメージpull、Secrets Manager・CloudWatch Logsへのアクセス)を可能にする
- セキュリティグループはALB→ECS→RDSの一方向のみを許可する最小権限構成(RDSは外部から一切アクセス不可)

### CloudFrontとALBのオリジン構成

カスタムドメイン・HTTPS対応は今回のスコープ外のため、ALBはHTTPのみで公開される。CloudFront(HTTPS)からALB(HTTP)を直接呼び出すとブラウザのMixed Contentブロックに抵触するため、CloudFrontに`/api/*`のビヘビアを追加してALBを2つ目のオリジンとして内部的にHTTPでプロキシする構成とした。ブラウザは常にCloudFrontの単一HTTPSドメインとしか通信しない。

詳細な構築内容・デプロイ手順は[infra/production/README.md](../infra/production/README.md)を参照。
