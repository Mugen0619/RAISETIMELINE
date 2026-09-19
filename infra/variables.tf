variable "aws_region" {
  description = "リソースを作成するAWSリージョン"
  type        = string
  default     = "ap-northeast-1"
}

variable "project_name" {
  description = "リソース名のプレフィックスとして使う識別子"
  type        = string
  default     = "raisetimeline"
}

variable "cors_allowed_origins" {
  description = "S3バケットへのPUTアップロードを許可するオリジン(ローカル開発用フロントエンド+本番CloudFrontドメイン)"
  type        = list(string)
  default = [
    "http://localhost:5173",
    "https://d13mtgfnf6x0ya.cloudfront.net",
  ]
}
