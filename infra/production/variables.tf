variable "aws_region" {
  description = "AWSリージョン"
  type        = string
  default     = "ap-northeast-1"
}

variable "project_name" {
  description = "リソース名のプレフィックスに使うプロジェクト名"
  type        = string
  default     = "raisetimeline"
}

# 既存のtaskmanagement-vpc(10.0.0.0/16)と衝突しない、RAISETIMELINE専用の新規VPC
variable "vpc_cidr" {
  description = "本番環境用VPCのCIDR"
  type        = string
  default     = "10.1.0.0/16"
}

variable "db_name" {
  type    = string
  default = "raisetimeline"
}

variable "db_username" {
  type    = string
  default = "raisetimeline"
}

variable "db_instance_class" {
  description = "無料利用枠の対象外になりうるが、フルマネージドDBの学習目的でdb.t4g.microを採用する"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_allocated_storage_gb" {
  type    = number
  default = 20
}

variable "container_port" {
  type    = number
  default = 8080
}

variable "ecs_task_cpu" {
  type    = number
  default = 512
}

variable "ecs_task_memory" {
  type    = number
  default = 1024
}

variable "ecs_desired_count" {
  type    = number
  default = 1
}

variable "backend_image_tag" {
  description = "ECSタスク定義に設定するバックエンドイメージのタグ。初回applyより前にECRへpushしておく必要がある"
  type        = string
  default     = "latest"
}

variable "jwt_expiration_ms" {
  type    = number
  default = 86400000
}

variable "jwt_refresh_expiration_ms" {
  type    = number
  default = 1209600000
}

variable "s3_presign_expiration_seconds" {
  type    = number
  default = 300
}

# 投稿画像用S3バケット(../infra/)は今回変更しない前提のため、既存の出力値を変数として受け取る
variable "s3_image_bucket_name" {
  description = "投稿画像用S3バケット名(../infra/の terraform output bucket_name)"
  type        = string
}

variable "s3_image_public_base_url" {
  description = "投稿画像用S3バケットの公開ベースURL(../infra/の terraform output bucket_public_base_url)"
  type        = string
}

variable "s3_access_key_id" {
  description = "投稿画像アップロード用IAMユーザーのアクセスキーID(../infra/の terraform output access_key_id)"
  type        = string
}

variable "s3_secret_access_key" {
  description = "投稿画像アップロード用IAMユーザーのシークレットアクセスキー(../infra/の terraform output -raw secret_access_key)。Secrets Managerに保存する"
  type        = string
  sensitive   = true
}
