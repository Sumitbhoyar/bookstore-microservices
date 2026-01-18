# Terraform Variables for Bookstore Infrastructure

variable "aws_region" {
  description = "AWS region for resources"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: dev, staging, prod"
  }
}

variable "assume_role_arn" {
  description = "ARN of IAM role to assume for deployment"
  type        = string
  default     = null
}

# VPC Configuration
variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnets" {
  description = "Public subnet CIDR blocks"
  type        = list(string)
  default = [
    "10.0.1.0/24",
    "10.0.2.0/24",
    "10.0.3.0/24"
  ]
}

variable "private_subnets" {
  description = "Private subnet CIDR blocks"
  type        = list(string)
  default = [
    "10.0.10.0/24",
    "10.0.11.0/24",
    "10.0.12.0/24"
  ]
}

variable "database_subnets" {
  description = "Database subnet CIDR blocks"
  type        = list(string)
  default = [
    "10.0.20.0/24",
    "10.0.21.0/24",
    "10.0.22.0/24"
  ]
}

# RDS Configuration
variable "rds_instance_class" {
  description = "RDS instance class"
  type        = string
  default = {
    dev  = "db.t3.micro"
    staging = "db.t3.small"
    prod = "db.t3.medium"
  }[var.environment]
}

variable "rds_allocated_storage" {
  description = "RDS allocated storage in GB"
  type        = number
  default     = 20
}

variable "rds_engine_version" {
  description = "PostgreSQL engine version"
  type        = string
  default     = "15.4"
}

variable "backup_retention_period" {
  description = "Backup retention period in days"
  type        = number
  default = {
    dev  = 7
    staging = 14
    prod = 30
  }[var.environment]
}

variable "backup_window" {
  description = "Preferred backup window"
  type        = string
  default     = "03:00-04:00"
}

variable "maintenance_window" {
  description = "Preferred maintenance window"
  type        = string
  default     = "sun:04:00-sun:05:00"
}

# Redis Configuration
variable "redis_node_type" {
  description = "Redis node type"
  type        = string
  default = {
    dev  = "cache.t3.micro"
    staging = "cache.t3.small"
    prod = "cache.t3.medium"
  }[var.environment]
}

variable "redis_num_cache_nodes" {
  description = "Number of Redis cache nodes"
  type        = number
  default     = 1
}

variable "redis_maintenance_window" {
  description = "Redis maintenance window"
  type        = string
  default     = "sun:05:00-sun:06:00"
}

# OpenSearch Configuration
variable "opensearch_instance_type" {
  description = "OpenSearch instance type"
  type        = string
  default = {
    dev  = "t3.small.elasticsearch"
    staging = "t3.medium.elasticsearch"
    prod = "t3.medium.elasticsearch"
  }[var.environment]
}

variable "opensearch_instance_count" {
  description = "Number of OpenSearch instances"
  type        = number
  default = {
    dev  = 1
    staging = 2
    prod = 3
  }[var.environment]
}

variable "opensearch_volume_size" {
  description = "OpenSearch EBS volume size in GB"
  type        = number
  default     = 10
}

# Cognito Configuration
variable "cognito_callback_urls" {
  description = "Cognito callback URLs"
  type        = list(string)
  default = [
    "http://localhost:3000/auth/callback",
    "https://bookstore-dev.example.com/auth/callback"
  ]
}

variable "cognito_logout_urls" {
  description = "Cognito logout URLs"
  type        = list(string)
  default = [
    "http://localhost:3000",
    "https://bookstore-dev.example.com"
  ]
}

# Tagging
variable "tags" {
  description = "Common tags for all resources"
  type        = map(string)
  default = {
    Project     = "Bookstore"
    ManagedBy   = "Terraform"
    Owner       = "Platform Team"
  }
}

# Cost Allocation
variable "cost_center" {
  description = "Cost center for billing"
  type        = string
  default     = "bookstore-platform"
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "bookstore"
}

# Monitoring and Alerting
variable "enable_cloudtrail" {
  description = "Enable CloudTrail for auditing"
  type        = bool
  default     = true
}

variable "enable_config" {
  description = "Enable AWS Config for compliance"
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days"
  type        = number
  default = {
    dev  = 30
    staging = 90
    prod = 365
  }[var.environment]
}

# Security
variable "enable_encryption" {
  description = "Enable encryption for data at rest"
  type        = bool
  default     = true
}

variable "kms_key_rotation" {
  description = "Enable KMS key rotation"
  type        = bool
  default     = true
}

# High Availability
variable "multi_az" {
  description = "Enable Multi-AZ deployment for high availability"
  type        = bool
  default = {
    dev  = false
    staging = true
    prod = true
  }[var.environment]
}

# Performance
variable "rds_max_connections" {
  description = "Maximum connections for RDS"
  type        = number
  default = {
    dev  = 100
    staging = 200
    prod = 500
  }[var.environment]
}

# Scaling
variable "ecs_min_capacity" {
  description = "Minimum ECS capacity"
  type        = number
  default = {
    dev  = 1
    staging = 2
    prod = 3
  }[var.environment]
}

variable "ecs_max_capacity" {
  description = "Maximum ECS capacity"
  type        = number
  default = {
    dev  = 5
    staging = 10
    prod = 20
  }[var.environment]
}

# Budget
variable "monthly_budget_limit" {
  description = "Monthly budget limit in USD"
  type        = number
  default = {
    dev  = 100
    staging = 500
    prod = 2000
  }[var.environment]
}