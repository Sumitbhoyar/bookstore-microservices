# AWS Infrastructure for Bookstore Microservices

This directory contains Terraform configurations for deploying the Bookstore microservices system on AWS.

## Architecture Overview

The infrastructure follows AWS best practices for microservices deployment:

- **VPC**: Isolated network with public/private subnets across multiple AZs
- **ECS Fargate**: Serverless container orchestration
- **RDS PostgreSQL**: Managed relational database
- **ElastiCache Redis**: In-memory caching and session storage
- **OpenSearch**: Full-text search and analytics
- **DynamoDB**: NoSQL database for audit logs and high-throughput data
- **EventBridge**: Event-driven architecture for service communication
- **API Gateway**: Unified API entry point with JWT authentication
- **Cognito**: User authentication and authorization
- **CloudWatch**: Comprehensive monitoring and alerting

## Directory Structure

```
infrastructure/aws/
├── main.tf                 # Main Terraform configuration
├── variables.tf           # Input variables
├── outputs.tf            # Output values
├── terraform.tfvars      # Variable values (create per environment)
├── modules/              # Reusable Terraform modules
│   ├── vpc/             # VPC, subnets, NAT gateways
│   ├── security-groups/ # Security group definitions
│   ├── rds/             # PostgreSQL database
│   ├── redis/           # ElastiCache Redis
│   ├── opensearch/      # OpenSearch cluster
│   ├── dynamodb/        # DynamoDB tables
│   ├── ecs/             # ECS cluster and services
│   ├── alb/             # Application Load Balancer
│   ├── api-gateway/     # API Gateway configuration
│   ├── lambda/          # Lambda functions
│   ├── eventbridge/     # Event bus and rules
│   ├── sqs/             # SQS queues
│   ├── sns/             # SNS topics
│   └── cloudwatch/      # Monitoring and alerts
└── environments/        # Environment-specific configurations
    ├── dev/
    ├── staging/
    └── prod/
```

## Prerequisites

1. **AWS CLI**: Configure with appropriate credentials
   ```bash
   aws configure
   ```

2. **Terraform**: Version 1.0.0 or later
   ```bash
   terraform version
   ```

3. **AWS Account**: With necessary permissions for resource creation

## Quick Start

### 1. Environment Setup

Create environment-specific variable files:

```bash
# Copy and modify for each environment
cp terraform.tfvars.example environments/dev/terraform.tfvars
cp terraform.tfvars.example environments/staging/terraform.tfvars
cp terraform.tfvars.example environments/prod/terraform.tfvars
```

### 2. Initialize Terraform

```bash
cd environments/dev
terraform init
```

### 3. Plan Deployment

```bash
terraform plan -var-file="terraform.tfvars"
```

### 4. Apply Changes

```bash
terraform apply -var-file="terraform.tfvars"
```

## Environment Configuration

### Development Environment

- Single AZ deployment
- Minimal instance sizes
- Cost-optimized configuration
- Basic monitoring

### Staging Environment

- Multi-AZ deployment
- Production-like configuration
- Full monitoring and alerting
- Integration testing environment

### Production Environment

- Multi-AZ with high availability
- Maximum performance and reliability
- Comprehensive monitoring and alerting
- Security hardening
- Backup and disaster recovery

## Key Components

### Networking (VPC Module)

- **VPC**: /16 CIDR with DNS support
- **Subnets**: Public, private, and database subnets across 3 AZs
- **NAT Gateway**: For private subnet internet access
- **Internet Gateway**: For public subnet internet access
- **VPC Endpoints**: For AWS service access without internet

### Security (Security Groups Module)

- **ALB Security Group**: Allows HTTP/HTTPS from anywhere
- **ECS Security Group**: Allows traffic from ALB only
- **RDS Security Group**: Allows PostgreSQL traffic from ECS only
- **Redis Security Group**: Allows Redis traffic from ECS only
- **OpenSearch Security Group**: Allows HTTPS traffic from ECS only

### Database (RDS Module)

- **PostgreSQL 15**: Managed relational database
- **Multi-AZ**: Automatic failover in production
- **Automated Backups**: Configurable retention periods
- **Enhanced Monitoring**: CloudWatch metrics and logs
- **Encryption**: Data at rest encryption

### Caching (Redis Module)

- **ElastiCache Redis**: In-memory data store
- **Cluster Mode**: Disabled for simplicity
- **Automatic Failover**: In production environments
- **Backup**: Automated snapshots

### Search (OpenSearch Module)

- **OpenSearch 2.x**: Compatible with Elasticsearch 7.10+
- **Multi-AZ**: In production environments
- **Encryption**: Data at rest and in transit
- **Fine-grained Access Control**: For security

### NoSQL (DynamoDB Module)

- **On-demand Billing**: For variable workloads
- **Global Tables**: Cross-region replication in production
- **Point-in-time Recovery**: For disaster recovery
- **Streams**: For event-driven processing

### Container Orchestration (ECS Module)

- **Fargate**: Serverless container execution
- **Auto Scaling**: Based on CPU/memory utilization
- **Service Discovery**: CloudMap integration
- **Load Balancing**: Integration with ALB

### API Management (API Gateway Module)

- **REST API**: Traditional REST endpoints
- **JWT Authorizer**: Cognito-based authentication
- **Rate Limiting**: Per user and global limits
- **CORS**: Cross-origin resource sharing
- **Documentation**: OpenAPI specification

### Event-Driven Architecture (EventBridge Module)

- **Custom Event Bus**: Isolated event processing
- **Event Rules**: Pattern-based event routing
- **SQS Integration**: Dead letter queues for reliability
- **Cross-account**: Support for multi-account architectures

## Monitoring and Observability

### CloudWatch Integration

- **Metrics**: CPU, memory, network, disk utilization
- **Logs**: Centralized logging with structured format
- **Alarms**: Automated alerting for anomalies
- **Dashboards**: Visual monitoring interface

### X-Ray Integration

- **Distributed Tracing**: Request flow visualization
- **Performance Analysis**: Identify bottlenecks
- **Service Dependencies**: Map service interactions
- **Error Tracking**: Exception and error monitoring

## Security Considerations

### Network Security

- **Private Subnets**: Database and application isolation
- **Security Groups**: Least privilege access control
- **VPC Endpoints**: Secure AWS service access
- **NAT Gateway**: Controlled outbound traffic

### Data Security

- **Encryption at Rest**: All data stores encrypted
- **Encryption in Transit**: TLS 1.2+ for all communications
- **Key Management**: AWS KMS for encryption keys
- **Secret Management**: AWS Secrets Manager for credentials

### Access Control

- **IAM Roles**: Least privilege for service access
- **Cognito**: User authentication and authorization
- **API Gateway**: Request validation and rate limiting
- **CloudTrail**: Audit trail for all API calls

## Cost Optimization

### Development Environment

- **Spot Instances**: For non-critical workloads
- **Reserved Instances**: For predictable workloads
- **Auto Scaling**: Scale down during off-hours
- **Resource Tagging**: Cost allocation by environment

### Production Environment

- **Savings Plans**: For compute cost reduction
- **Auto Scaling**: Match capacity to demand
- **Storage Optimization**: Right-size storage volumes
- **Monitoring**: Identify and eliminate waste

## Deployment Strategy

### Blue-Green Deployment

- **API Gateway**: Route traffic between blue and green environments
- **Database**: Zero-downtime schema migrations
- **State Management**: Shared state between deployments
- **Rollback**: Instant rollback capability

### Canary Deployment

- **Traffic Shifting**: Gradual traffic migration
- **Monitoring**: Automated rollback on errors
- **A/B Testing**: Feature flag integration
- **Metrics**: Success criteria validation

## Backup and Recovery

### Database Backups

- **Automated Backups**: Daily snapshots with retention
- **Manual Backups**: On-demand snapshots
- **Cross-region**: Disaster recovery copies
- **Point-in-time**: Granular recovery options

### Infrastructure Backups

- **AMI Backups**: Golden images for quick recovery
- **Configuration**: Infrastructure as code versioning
- **State Management**: Terraform state backups
- **Documentation**: Runbooks and procedures

## Troubleshooting

### Common Issues

1. **Terraform State Lock**: Use `terraform force-unlock` if needed
2. **Resource Dependencies**: Check dependency graph with `terraform graph`
3. **Plan Differences**: Use `terraform plan -refresh=false` to debug
4. **Timeout Issues**: Increase timeout values in Terraform configuration

### Debugging Commands

```bash
# Check Terraform version and providers
terraform version

# Validate configuration
terraform validate

# Format configuration
terraform fmt -recursive

# Show current state
terraform show

# Import existing resources
terraform import aws_instance.example i-1234567890abcdef0
```

## Next Steps

1. **Implement Service Modules**: Create ECS service modules for each microservice
2. **Configure CI/CD**: Set up CodePipeline for automated deployments
3. **Add Monitoring**: Implement comprehensive monitoring and alerting
4. **Security Hardening**: Add WAF, Shield, and additional security controls
5. **Performance Testing**: Load testing and performance optimization
6. **Documentation**: Update runbooks and operational procedures

## Support

For issues and questions:
- Check the troubleshooting section above
- Review AWS documentation for service-specific guidance
- Create GitHub issues for bugs and feature requests
- Contact the platform team for architectural decisions