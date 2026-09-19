resource "random_password" "jwt_secret" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "db_password" {
  name                    = "${var.project_name}/db-password"
  recovery_window_in_days = 0

  tags = {
    Project = var.project_name
  }
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db.result
}

resource "aws_secretsmanager_secret" "jwt_secret" {
  name                    = "${var.project_name}/jwt-secret"
  recovery_window_in_days = 0

  tags = {
    Project = var.project_name
  }
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id     = aws_secretsmanager_secret.jwt_secret.id
  secret_string = random_password.jwt_secret.result
}

# 投稿画像アップロード用IAMユーザー(../infra/で作成済み)のシークレットアクセスキーを、
# ECSタスクへ安全に渡すためにこちらのSecrets Managerにも保存する
resource "aws_secretsmanager_secret" "s3_secret_access_key" {
  name                    = "${var.project_name}/s3-secret-access-key"
  recovery_window_in_days = 0

  tags = {
    Project = var.project_name
  }
}

resource "aws_secretsmanager_secret_version" "s3_secret_access_key" {
  secret_id     = aws_secretsmanager_secret.s3_secret_access_key.id
  secret_string = var.s3_secret_access_key
}
