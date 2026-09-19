output "alb_dns_name" {
  description = "ALBのDNS名(CloudFrontの/api/*オリジンとして使用。直接アクセスもHTTPで可能だがブラウザから叩く用途は想定しない)"
  value       = aws_lb.main.dns_name
}

output "cloudfront_domain_name" {
  description = "CloudFrontのドメイン名。フロントエンド公開URL(https://<この値>)"
  value       = aws_cloudfront_distribution.frontend.domain_name
}

output "cloudfront_distribution_id" {
  description = "デプロイ時のキャッシュ無効化(invalidation)に使うCloudFrontディストリビューションID"
  value       = aws_cloudfront_distribution.frontend.id
}

output "ecr_repository_url" {
  description = "バックエンドDockerイメージのpush先ECRリポジトリURL"
  value       = aws_ecr_repository.backend.repository_url
}

output "frontend_bucket_name" {
  description = "フロントエンド静的ファイルのアップロード先S3バケット名"
  value       = aws_s3_bucket.frontend.bucket
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.backend.name
}

output "rds_endpoint" {
  description = "RDSのエンドポイント(プライベートサブネット内のみアクセス可能)"
  value       = aws_db_instance.main.address
}

output "db_password_secret_arn" {
  value = aws_secretsmanager_secret.db_password.arn
}

output "jwt_secret_arn" {
  value = aws_secretsmanager_secret.jwt_secret.arn
}
