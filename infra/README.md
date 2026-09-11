# infra

RAISETIMELINE用のTerraform構成。投稿画像アップロード(S3 + 専用IAMユーザー)を管理する。

- Terraform 1.15系 / AWS provider ~> 6.0(詳細は[../docs/tech-stack.md](../docs/tech-stack.md)参照)
- `study-user`(EC2/RDS等インフラ構築用の広い権限を持つIAMユーザー)とは別に、S3バケットへの`PutObject`のみを許可する専用IAMユーザーをここで作成する

## 使い方

```
cd infra
terraform init
terraform plan
terraform apply   # 内容確認後、ユーザーの承認を得てから実行する
```

## apply後のセットアップ

1. アクセスキーIDとシークレットアクセスキーを取得する(シークレットはsensitive出力のため個別に取得する)
   ```
   terraform output access_key_id
   terraform output -raw secret_access_key
   ```
2. `backend/.env`(Git管理外)に以下を設定する(値は`backend/.env.example`のテンプレートを参照)
   - `AWS_S3_BUCKET_NAME`(`terraform output bucket_name`)
   - `AWS_S3_PUBLIC_BASE_URL`(`terraform output bucket_public_base_url`)
   - `AWS_S3_ACCESS_KEY_ID` / `AWS_S3_SECRET_ACCESS_KEY`(上記手順1で取得した値)

## 後片付け

```
terraform destroy
```
