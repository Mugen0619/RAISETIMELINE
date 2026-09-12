output "bucket_name" {
  description = "投稿画像用S3バケット名。バックエンドのAWS_S3_BUCKET_NAMEに設定する。"
  value       = aws_s3_bucket.post_images.bucket
}

output "bucket_public_base_url" {
  description = "バケット内オブジェクトへのパブリックアクセスURLのベース。バックエンドのAWS_S3_PUBLIC_BASE_URLに設定する。"
  value       = "https://${aws_s3_bucket.post_images.bucket}.s3.${var.aws_region}.amazonaws.com"
}

output "iam_user_name" {
  description = "アップロード専用IAMユーザー名"
  value       = aws_iam_user.post_images_uploader.name
}

output "access_key_id" {
  description = "アップロード専用IAMユーザーのアクセスキーID。バックエンドのAWS_S3_ACCESS_KEY_IDに設定する。"
  value       = aws_iam_access_key.post_images_uploader.id
}

output "secret_access_key" {
  description = "アップロード専用IAMユーザーのシークレットアクセスキー。バックエンドのAWS_S3_SECRET_ACCESS_KEYに設定する。terraform output -raw secret_access_key で取得して.envへ手動転記し、Gitにはコミットしないこと。"
  value       = aws_iam_access_key.post_images_uploader.secret
  sensitive   = true
}
