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

variable "cors_allowed_origin" {
  description = "S3バケットへのPUTアップロードを許可するオリジン(ローカル開発用フロントエンド)"
  type        = string
  default     = "http://localhost:5173"
}
