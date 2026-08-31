# インフラ構成

[要件定義書](./requirements.md)へ戻る

フロントエンド/バックエンド/DBの技術スタックは[tech-stack.md](./tech-stack.md)を参照。

## AWS / Terraform

| 技術 | バージョン | 備考 |
|---|---|---|
| AWS | EC2 + RDS + ALB + S3 | |
| Terraform | 1.15系 | 執筆時点の最新安定版は1.15.8 |

## 構成概要

- EC2: アプリケーションサーバー(フロントエンド/バックエンド)
- RDS: PostgreSQL(マネージドDB)
- ALB: ロードバランサー
- S3: 投稿画像・アイコン画像の保存先
- Terraform: 上記インフラのコード管理(IaC)

具体的なネットワーク構成(VPC/サブネット等)やCI/CDパイプラインは、[要件定義書 9. 今後扱う技術トピック](./requirements.md#9-今後扱う技術トピック予告)で扱うAWSデプロイ・CI/CDのフェーズで別途整理する。
