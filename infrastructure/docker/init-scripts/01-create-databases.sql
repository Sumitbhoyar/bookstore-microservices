-- Database initialization script for Bookstore microservices
-- Run during docker-compose setup

-- Create databases for each service
CREATE DATABASE bookstore_auth;
CREATE DATABASE bookstore_users;
CREATE DATABASE bookstore_catalog;
CREATE DATABASE bookstore_orders;
CREATE DATABASE bookstore_search;

-- Create user for each service (in production, use IAM roles)
-- For local development, we'll use the same user but different databases

-- Grant permissions (in production, each service would have its own user)
GRANT ALL PRIVILEGES ON DATABASE bookstore_auth TO postgres;
GRANT ALL PRIVILEGES ON DATABASE bookstore_users TO postgres;
GRANT ALL PRIVILEGES ON DATABASE bookstore_catalog TO postgres;
GRANT ALL PRIVILEGES ON DATABASE bookstore_orders TO postgres;
GRANT ALL PRIVILEGES ON DATABASE bookstore_search TO postgres;

-- Create extensions in each database
\c bookstore_auth;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c bookstore_users;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c bookstore_catalog;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c bookstore_orders;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\c bookstore_search;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";