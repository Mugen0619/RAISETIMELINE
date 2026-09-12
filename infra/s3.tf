# 投稿画像保存用のS3バケット。バケット名はグローバルに一意である必要があるため、
# ランダムなサフィックスを付与する。
resource "random_id" "post_images_bucket_suffix" {
  byte_length = 4
}

resource "aws_s3_bucket" "post_images" {
  bucket = "${var.project_name}-post-images-${random_id.post_images_bucket_suffix.hex}"

  tags = {
    Project = var.project_name
    Purpose = "post-images"
  }
}

# ACLによるパブリック公開は禁止しつつ、バケットポリシーによるパブリック読み取りのみ許可する。
resource "aws_s3_bucket_public_access_block" "post_images" {
  bucket = aws_s3_bucket.post_images.id

  block_public_acls       = true
  ignore_public_acls      = true
  block_public_policy     = false
  restrict_public_buckets = false
}

data "aws_iam_policy_document" "post_images_public_read" {
  statement {
    sid       = "PublicReadGetObject"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.post_images.arn}/*"]

    principals {
      type        = "AWS"
      identifiers = ["*"]
    }
  }
}

resource "aws_s3_bucket_policy" "post_images_public_read" {
  bucket = aws_s3_bucket.post_images.id
  policy = data.aws_iam_policy_document.post_images_public_read.json

  depends_on = [aws_s3_bucket_public_access_block.post_images]
}

# ローカル開発のフロントエンド(Vite dev server)からの直接PUTアップロードを許可する。
resource "aws_s3_bucket_cors_configuration" "post_images" {
  bucket = aws_s3_bucket.post_images.id

  cors_rule {
    allowed_methods = ["PUT"]
    allowed_origins = [var.cors_allowed_origin]
    allowed_headers = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}
