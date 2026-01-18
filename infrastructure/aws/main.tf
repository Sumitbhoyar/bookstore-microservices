# AWS Infrastructure for Bookstore Microservices
# Terraform Configuration

terraform {
  required_version = ">= 1.0.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # In production, use S3 backend with DynamoDB locking
  # backend "s3" {
  #   bucket         = "bookstore-terraform-state"
  #   key            = "infrastructure/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "bookstore-terraform-locks"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region

  # Assume role in production
  # assume_role {
  #   role_arn = var.assume_role_arn
  # }

  default_tags {
    tags = {
      Project     = "Bookstore"
      Environment = var.environment
      ManagedBy   = "Terraform"
      Owner       = "Platform Team"
    }
  }
}

# Data sources
data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_caller_identity" "current" {}

# VPC Module
module "vpc" {
  source = "./modules/vpc"

  environment         = var.environment
  vpc_cidr           = var.vpc_cidr
  availability_zones = data.aws_availability_zones.available.names

  # Subnet configuration
  public_subnets  = var.public_subnets
  private_subnets = var.private_subnets
  database_subnets = var.database_subnets

  # Enable NAT Gateway for private subnet internet access
  enable_nat_gateway = true
  single_nat_gateway = var.environment == "dev" ? true : false
}

# Security Groups
module "security_groups" {
  source = "./modules/security-groups"

  environment = var.environment
  vpc_id      = module.vpc.vpc_id

  # Allow inbound traffic from ALB
  alb_security_group_id = module.alb.security_group_id
}

# RDS PostgreSQL
module "rds" {
  source = "./modules/rds"

  environment         = var.environment
  vpc_id             = module.vpc.vpc_id
  database_subnets   = module.vpc.database_subnets
  security_group_ids = [module.security_groups.rds_security_group_id]

  # Database configuration
  instance_class    = var.rds_instance_class
  allocated_storage = var.rds_allocated_storage
  engine_version    = var.rds_engine_version

  # Backup configuration
  backup_retention_period = var.backup_retention_period
  backup_window          = var.backup_window
  maintenance_window     = var.maintenance_window

  # Multi-AZ for production
  multi_az = var.environment == "prod"

  # Monitoring
  monitoring_interval = 60
  monitoring_role_arn = aws_iam_role.rds_enhanced_monitoring.arn
}

# ElastiCache Redis
module "redis" {
  source = "./modules/redis"

  environment       = var.environment
  vpc_id           = module.vpc.vpc_id
  private_subnets  = module.vpc.private_subnets
  security_group_ids = [module.security_groups.redis_security_group_id]

  # Redis configuration
  node_type      = var.redis_node_type
  num_cache_nodes = var.redis_num_cache_nodes

  # Maintenance
  maintenance_window = var.redis_maintenance_window
}

# Elasticsearch/OpenSearch
module "opensearch" {
  source = "./modules/opensearch"

  environment       = var.environment
  vpc_id           = module.vpc.vpc_id
  private_subnets  = module.vpc.private_subnets
  security_group_ids = [module.security_groups.opensearch_security_group_id]

  # OpenSearch configuration
  instance_type = var.opensearch_instance_type
  instance_count = var.opensearch_instance_count
  volume_size   = var.opensearch_volume_size
}

# DynamoDB Tables
module "dynamodb" {
  source = "./modules/dynamodb"

  environment = var.environment

  # Table configurations
  tables = {
    notifications_audit = {
      hash_key     = "id"
      range_key    = "timestamp"
      billing_mode = "PAY_PER_REQUEST"

      attributes = [
        { name = "id", type = "S" },
        { name = "timestamp", type = "N" },
        { name = "user_id", type = "S" },
        { name = "event_type", type = "S" }
      ]

      global_secondary_indexes = [
        {
          name               = "UserIdIndex"
          hash_key           = "user_id"
          range_key          = "timestamp"
          projection_type    = "ALL"
        },
        {
          name               = "EventTypeIndex"
          hash_key           = "event_type"
          range_key          = "timestamp"
          projection_type    = "ALL"
        }
      ]
    }

    payment_records = {
      hash_key     = "id"
      range_key    = "created_at"
      billing_mode = "PAY_PER_REQUEST"

      attributes = [
        { name = "id", type = "S" },
        { name = "created_at", type = "N" },
        { name = "order_id", type = "S" },
        { name = "user_id", type = "S" }
      ]

      global_secondary_indexes = [
        {
          name               = "OrderIdIndex"
          hash_key           = "order_id"
          projection_type    = "ALL"
        },
        {
          name               = "UserIdIndex"
          hash_key           = "user_id"
          range_key          = "created_at"
          projection_type    = "ALL"
        }
      ]
    }

    user_interactions = {
      hash_key     = "user_id"
      range_key    = "item_id"
      billing_mode = "PAY_PER_REQUEST"

      attributes = [
        { name = "user_id", type = "S" },
        { name = "item_id", type = "S" },
        { name = "interaction_type", type = "S" },
        { name = "timestamp", type = "N" }
      ]

      global_secondary_indexes = [
        {
          name               = "ItemInteractionsIndex"
          hash_key           = "item_id"
          range_key          = "timestamp"
          projection_type    = "ALL"
        }
      ]
    }
  }
}

# ECS Cluster
module "ecs" {
  source = "./modules/ecs"

  environment = var.environment
  vpc_id      = module.vpc.vpc_id

  # ECS configuration
  cluster_name = "${var.environment}-bookstore"

  # Capacity providers
  enable_fargate = true
  enable_ec2     = false
}

# Application Load Balancer
module "alb" {
  source = "./modules/alb"

  environment       = var.environment
  vpc_id           = module.vpc.vpc_id
  public_subnets   = module.vpc.public_subnets
  security_group_ids = [module.security_groups.alb_security_group_id]

  # ALB configuration
  internal = false

  # Health check settings
  health_check_path = "/actuator/health"
  health_check_timeout = 5
  health_check_interval = 30
}

# API Gateway
module "api_gateway" {
  source = "./modules/api-gateway"

  environment = var.environment

  # API Gateway configuration
  api_name = "${var.environment}-bookstore-api"

  # Authorizer configuration
  authorizer_uri = module.lambda_cognito_authorizer.invoke_arn
  authorizer_credentials = aws_iam_role.api_gateway_authorizer.arn
}

# Lambda Cognito Authorizer
module "lambda_cognito_authorizer" {
  source = "./modules/lambda"

  environment = var.environment

  # Lambda configuration
  function_name = "${var.environment}-cognito-authorizer"
  handler      = "index.handler"
  runtime      = "nodejs18.x"
  memory_size  = 256
  timeout      = 30

  # Environment variables
  environment_variables = {
    COGNITO_USER_POOL_ID = aws_cognito_user_pool.bookstore.id
    AWS_REGION          = var.aws_region
  }
}

# EventBridge
module "eventbridge" {
  source = "./modules/eventbridge"

  environment = var.environment

  # Event bus configuration
  bus_name = "${var.environment}-bookstore-events"

  # Event rules for different services
  event_rules = {
    order_events = {
      description = "Capture all order-related events"
      event_pattern = jsonencode({
        source = ["bookstore.order"]
      })
    }

    payment_events = {
      description = "Capture all payment-related events"
      event_pattern = jsonencode({
        source = ["bookstore.payment"]
      })
    }

    user_events = {
      description = "Capture all user-related events"
      event_pattern = jsonencode({
        source = ["bookstore.user"]
      })
    }
  }
}

# Cognito User Pool
resource "aws_cognito_user_pool" "bookstore" {
  name = "${var.environment}-bookstore-users"

  # Password policy
  password_policy {
    minimum_length    = 8
    require_uppercase = true
    require_lowercase = true
    require_numbers   = true
    require_symbols   = false
  }

  # MFA configuration
  mfa_configuration = var.environment == "prod" ? "OPTIONAL" : "OFF"

  # User attributes
  schema {
    name                = "email"
    attribute_data_type = "String"
    required           = true
    mutable            = true
  }

  # Auto-verified attributes
  auto_verified_attributes = ["email"]

  # Email configuration
  email_configuration {
    email_sending_account = "COGNITO_DEFAULT"
  }
}

# Cognito User Pool Client
resource "aws_cognito_user_pool_client" "bookstore" {
  name         = "${var.environment}-bookstore-client"
  user_pool_id = aws_cognito_user_pool.bookstore.id

  # OAuth configuration
  explicit_auth_flows = [
    "ALLOW_USER_SRP_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
    "ALLOW_USER_PASSWORD_AUTH"
  ]

  # Token validity
  access_token_validity  = 1   # 1 hour
  id_token_validity     = 1   # 1 hour
  refresh_token_validity = 30  # 30 days

  # OAuth flows
  supported_identity_providers = ["COGNITO"]
  allowed_oauth_flows          = ["code", "implicit"]
  allowed_oauth_scopes         = ["openid", "email", "profile"]
  callback_urls               = var.cognito_callback_urls
  logout_urls                 = var.cognito_logout_urls
}

# SQS Queues for async processing
module "sqs" {
  source = "./modules/sqs"

  environment = var.environment

  # Queue configurations
  queues = {
    notifications_dlq = {
      name = "${var.environment}-notifications-dlq"
      visibility_timeout_seconds = 30
      message_retention_seconds  = 1209600  # 14 days
    }

    notifications_queue = {
      name = "${var.environment}-notifications-queue"
      visibility_timeout_seconds = 300
      message_retention_seconds  = 345600  # 4 days
      redrive_policy = jsonencode({
        deadLetterTargetArn = module.sqs.notifications_dlq_arn
        maxReceiveCount     = 3
      })
    }

    search_queue = {
      name = "${var.environment}-search-queue"
      visibility_timeout_seconds = 300
      message_retention_seconds  = 345600
    }

    payment_webhooks = {
      name = "${var.environment}-payment-webhooks"
      visibility_timeout_seconds = 60
      message_retention_seconds  = 86400  # 1 day
    }
  }
}

# SNS Topics
module "sns" {
  source = "./modules/sns"

  environment = var.environment

  # Topic configurations
  topics = {
    order_notifications = {
      name = "${var.environment}-order-notifications"
    }

    payment_notifications = {
      name = "${var.environment}-payment-notifications"
    }

    system_alerts = {
      name = "${var.environment}-system-alerts"
    }
  }
}

# CloudWatch Alarms
module "cloudwatch_alarms" {
  source = "./modules/cloudwatch"

  environment = var.environment

  # RDS alarms
  rds_instance_id = module.rds.instance_id

  # ECS alarms
  ecs_cluster_name = module.ecs.cluster_name

  # ALB alarms
  alb_arn_suffix = module.alb.alb_arn_suffix

  # SNS topic for alerts
  alarm_sns_topic_arn = module.sns.system_alerts_arn
}

# IAM Roles
resource "aws_iam_role" "rds_enhanced_monitoring" {
  name = "${var.environment}-rds-enhanced-monitoring"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "monitoring.rds.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role" "api_gateway_authorizer" {
  name = "${var.environment}-api-gateway-authorizer"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "apigateway.amazonaws.com"
        }
      }
    ]
  })
}

# Outputs
output "vpc_id" {
  description = "VPC ID"
  value       = module.vpc.vpc_id
}

output "rds_endpoint" {
  description = "RDS endpoint"
  value       = module.rds.endpoint
  sensitive   = true
}

output "redis_endpoint" {
  description = "Redis endpoint"
  value       = module.redis.endpoint
  sensitive   = true
}

output "opensearch_endpoint" {
  description = "OpenSearch endpoint"
  value       = module.opensearch.endpoint
}

output "ecs_cluster_name" {
  description = "ECS cluster name"
  value       = module.ecs.cluster_name
}

output "alb_dns_name" {
  description = "ALB DNS name"
  value       = module.alb.dns_name
}

output "api_gateway_url" {
  description = "API Gateway URL"
  value       = module.api_gateway.api_url
}

output "cognito_user_pool_id" {
  description = "Cognito User Pool ID"
  value       = aws_cognito_user_pool.bookstore.id
}

output "cognito_client_id" {
  description = "Cognito Client ID"
  value       = aws_cognito_user_pool_client.bookstore.id
}