# アカウント内で有効なAZをハードコードせず動的に取得する(アカウントによって使えるAZ名が異なるため)
data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  azs = slice(data.aws_availability_zones.available.names, 0, 2)
}

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name    = "${var.project_name}-vpc"
    Project = var.project_name
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = {
    Name    = "${var.project_name}-igw"
    Project = var.project_name
  }
}

# ALB用のパブリックサブネット(AZごとに1つ)
resource "aws_subnet" "public" {
  count                   = length(local.azs)
  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name    = "${var.project_name}-public-${local.azs[count.index]}"
    Project = var.project_name
  }
}

# Fargateタスク・RDS用のプライベートサブネット(AZごとに1つ)
resource "aws_subnet" "private" {
  count             = length(local.azs)
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 10)
  availability_zone = local.azs[count.index]

  tags = {
    Name    = "${var.project_name}-private-${local.azs[count.index]}"
    Project = var.project_name
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = {
    Name    = "${var.project_name}-public-rt"
    Project = var.project_name
  }
}

resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# NAT Gatewayは1つのみ作成する(コスト優先。AZ障害時の冗長性より個人開発規模の学習目的を優先した判断)。
#
# 検討: Fargateタスクが外部通信を必要とするのはECR pull・CloudWatch Logs・Secrets Managerの3用途のみで、
# NAT Gatewayの代わりにVPCエンドポイント(Interface×4種+S3 Gateway)で代替することも技術的には可能。
# ただし東京リージョンでコスト比較したところ、必要な4種のInterfaceエンドポイントを2AZに配置すると
# 約$82/月となりNAT Gateway(約$45/月)より高くなり、1AZに絞っても約$41/月とわずかな削減(約10%)に
# とどまる。将来的に他のAWS API・外部サービス呼び出しが増えても個別対応が不要なNAT Gatewayの
# 柔軟性を優先し、NAT Gateway方式を採用する。
resource "aws_eip" "nat" {
  domain = "vpc"

  tags = {
    Name    = "${var.project_name}-nat-eip"
    Project = var.project_name
  }
}

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id

  tags = {
    Name    = "${var.project_name}-nat"
    Project = var.project_name
  }

  depends_on = [aws_internet_gateway.main]
}

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main.id
  }

  tags = {
    Name    = "${var.project_name}-private-rt"
    Project = var.project_name
  }
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}
