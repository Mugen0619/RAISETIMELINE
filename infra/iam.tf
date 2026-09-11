# study-user(インフラ構築用の広い権限を持つIAMユーザー)とは分離した、
# アプリケーションが画像アップロードのためだけに使う最小権限のIAMユーザー。
resource "aws_iam_user" "post_images_uploader" {
  name = "${var.project_name}-s3-uploader"

  tags = {
    Project = var.project_name
    Purpose = "post-images-upload"
  }
}

data "aws_iam_policy_document" "post_images_put_object" {
  statement {
    sid       = "AllowPutObjectOnly"
    effect    = "Allow"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.post_images.arn}/*"]
  }
}

resource "aws_iam_policy" "post_images_put_object" {
  name        = "${var.project_name}-s3-put-object"
  description = "投稿画像バケットへのPutObjectのみを許可する最小権限ポリシー"
  policy      = data.aws_iam_policy_document.post_images_put_object.json
}

resource "aws_iam_user_policy_attachment" "post_images_uploader" {
  user       = aws_iam_user.post_images_uploader.name
  policy_arn = aws_iam_policy.post_images_put_object.arn
}

resource "aws_iam_access_key" "post_images_uploader" {
  user = aws_iam_user.post_images_uploader.name
}
