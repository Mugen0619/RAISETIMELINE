resource "aws_ecs_cluster" "main" {
  name = "${var.project_name}-cluster"

  tags = {
    Name    = "${var.project_name}-cluster"
    Project = var.project_name
  }
}

resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/${var.project_name}-backend"
  retention_in_days = 14

  tags = {
    Project = var.project_name
  }
}

resource "aws_ecs_task_definition" "backend" {
  family                   = "${var.project_name}-backend"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.ecs_task_cpu
  memory                   = var.ecs_task_memory
  execution_role_arn       = aws_iam_role.ecs_task_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([
    {
      name      = "backend"
      image     = "${aws_ecr_repository.backend.repository_url}:${var.backend_image_tag}"
      essential = true

      portMappings = [
        {
          containerPort = var.container_port
          protocol      = "tcp"
        }
      ]

      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "DB_HOST", value = aws_db_instance.main.address },
        { name = "DB_PORT", value = tostring(aws_db_instance.main.port) },
        { name = "DB_NAME", value = var.db_name },
        { name = "DB_USER", value = var.db_username },
        { name = "CORS_ALLOWED_ORIGIN", value = "https://${aws_cloudfront_distribution.frontend.domain_name}" },
        { name = "JWT_EXPIRATION_MS", value = tostring(var.jwt_expiration_ms) },
        { name = "JWT_REFRESH_EXPIRATION_MS", value = tostring(var.jwt_refresh_expiration_ms) },
        { name = "AWS_REGION", value = var.aws_region },
        { name = "AWS_S3_BUCKET_NAME", value = var.s3_image_bucket_name },
        { name = "AWS_S3_PUBLIC_BASE_URL", value = var.s3_image_public_base_url },
        { name = "AWS_S3_ACCESS_KEY_ID", value = var.s3_access_key_id },
        { name = "AWS_S3_PRESIGN_EXPIRATION_SECONDS", value = tostring(var.s3_presign_expiration_seconds) },
      ]

      secrets = [
        { name = "DB_PASSWORD", valueFrom = aws_secretsmanager_secret.db_password.arn },
        { name = "JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt_secret.arn },
        { name = "AWS_S3_SECRET_ACCESS_KEY", valueFrom = aws_secretsmanager_secret.s3_secret_access_key.arn },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.backend.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "backend"
        }
      }
    }
  ])

  tags = {
    Name    = "${var.project_name}-backend"
    Project = var.project_name
  }
}

resource "aws_ecs_service" "backend" {
  name                              = "${var.project_name}-backend"
  cluster                           = aws_ecs_cluster.main.id
  task_definition                   = aws_ecs_task_definition.backend.arn
  desired_count                     = var.ecs_desired_count
  launch_type                       = "FARGATE"
  health_check_grace_period_seconds = 240

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "backend"
    container_port   = var.container_port
  }

  depends_on = [aws_lb_listener.http]

  tags = {
    Name    = "${var.project_name}-backend"
    Project = var.project_name
  }
}
