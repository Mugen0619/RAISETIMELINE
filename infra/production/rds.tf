resource "random_password" "db" {
  length  = 24
  special = false
}

resource "aws_db_subnet_group" "main" {
  name       = "${var.project_name}-db-subnet-group"
  subnet_ids = aws_subnet.private[*].id

  tags = {
    Name    = "${var.project_name}-db-subnet-group"
    Project = var.project_name
  }
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project_name}-db"
  engine         = "postgres"
  engine_version = "17"

  instance_class    = var.db_instance_class
  allocated_storage = var.db_allocated_storage_gb
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  # Single-AZ(コスト優先。個人開発規模の学習目的のため、可用性よりコストを優先した判断)
  multi_az = false

  # 学習用途のため最終スナップショットを作らずdestroy可能にする(本番運用では要見直し)
  skip_final_snapshot = true

  tags = {
    Name    = "${var.project_name}-db"
    Project = var.project_name
  }
}
