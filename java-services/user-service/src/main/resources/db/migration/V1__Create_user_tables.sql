-- User Service Database Schema
-- Version: 1.0.0
-- Description: User profile management tables

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- User profiles table (extends auth service user data)
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL UNIQUE, -- References auth service user ID
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    display_name VARCHAR(150),
    phone VARCHAR(20),
    date_of_birth DATE,
    gender VARCHAR(20) CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY')),
    avatar_url TEXT,
    bio TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User addresses table
CREATE TABLE user_addresses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    address_type VARCHAR(50) NOT NULL CHECK (address_type IN ('HOME', 'WORK', 'SHIPPING', 'BILLING')),
    is_default BOOLEAN NOT NULL DEFAULT false,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    company VARCHAR(255),
    street_address VARCHAR(255) NOT NULL,
    apartment VARCHAR(100),
    city VARCHAR(100) NOT NULL,
    state VARCHAR(100),
    postal_code VARCHAR(20) NOT NULL,
    country VARCHAR(100) NOT NULL DEFAULT 'US',
    phone VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Ensure only one default address per type per user
    UNIQUE(user_id, address_type, is_default)
);

-- User preferences table
CREATE TABLE user_preferences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    preference_key VARCHAR(100) NOT NULL,
    preference_value TEXT,
    preference_type VARCHAR(50) NOT NULL DEFAULT 'STRING' CHECK (preference_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(user_id, preference_key)
);

-- User notifications settings
CREATE TABLE user_notification_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    notification_type VARCHAR(50) NOT NULL CHECK (notification_type IN ('ORDER_STATUS', 'PROMOTIONS', 'NEW_RELEASES', 'ACCOUNT_ACTIVITY', 'MARKETING')),
    email_enabled BOOLEAN NOT NULL DEFAULT true,
    sms_enabled BOOLEAN NOT NULL DEFAULT false,
    push_enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(user_id, notification_type)
);

-- User reading preferences (for recommendations)
CREATE TABLE user_reading_preferences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    category VARCHAR(100) NOT NULL,
    weight DECIMAL(3,2) NOT NULL DEFAULT 1.0 CHECK (weight >= 0 AND weight <= 5.0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(user_id, category)
);

-- User wishlist
CREATE TABLE user_wishlist (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    product_id UUID NOT NULL, -- References product catalog service
    added_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,

    UNIQUE(user_id, product_id)
);

-- User reading history (for analytics and recommendations)
CREATE TABLE user_reading_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES user_profiles(id) ON DELETE CASCADE,
    product_id UUID NOT NULL, -- References product catalog service
    action VARCHAR(50) NOT NULL CHECK (action IN ('VIEWED', 'ADDED_TO_CART', 'PURCHASED', 'REVIEWED')),
    action_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    session_id VARCHAR(255), -- For session tracking
    metadata JSONB -- Additional context (search terms, referrer, etc.)
);

-- Indexes for performance
CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);
CREATE INDEX idx_user_profiles_email ON user_profiles(email);
CREATE INDEX idx_user_profiles_created_at ON user_profiles(created_at);

CREATE INDEX idx_user_addresses_user_id ON user_addresses(user_id);
CREATE INDEX idx_user_addresses_type_default ON user_addresses(user_id, address_type, is_default);
CREATE INDEX idx_user_addresses_country ON user_addresses(country);

CREATE INDEX idx_user_preferences_user_id ON user_preferences(user_id);
CREATE INDEX idx_user_preferences_key ON user_preferences(preference_key);

CREATE INDEX idx_user_notification_settings_user_id ON user_notification_settings(user_id);

CREATE INDEX idx_user_reading_preferences_user_id ON user_reading_preferences(user_id);
CREATE INDEX idx_user_reading_preferences_category ON user_reading_preferences(category);

CREATE INDEX idx_user_wishlist_user_id ON user_wishlist(user_id);
CREATE INDEX idx_user_wishlist_product_id ON user_wishlist(product_id);
CREATE INDEX idx_user_wishlist_added_at ON user_wishlist(added_at DESC);

CREATE INDEX idx_user_reading_history_user_id ON user_reading_history(user_id);
CREATE INDEX idx_user_reading_history_product_id ON user_reading_history(product_id);
CREATE INDEX idx_user_reading_history_action ON user_reading_history(action);
CREATE INDEX idx_user_reading_history_timestamp ON user_reading_history(action_timestamp DESC);
CREATE INDEX idx_user_reading_history_session ON user_reading_history(session_id);

-- Triggers for updated_at timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_user_profiles_updated_at BEFORE UPDATE ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_addresses_updated_at BEFORE UPDATE ON user_addresses
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_preferences_updated_at BEFORE UPDATE ON user_preferences
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_notification_settings_updated_at BEFORE UPDATE ON user_notification_settings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_reading_preferences_updated_at BEFORE UPDATE ON user_reading_preferences
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Default preferences for new users
CREATE OR REPLACE FUNCTION create_default_user_preferences()
RETURNS TRIGGER AS $$
BEGIN
    -- Insert default notification settings
    INSERT INTO user_notification_settings (user_id, notification_type)
    VALUES
        (NEW.id, 'ORDER_STATUS'),
        (NEW.id, 'PROMOTIONS'),
        (NEW.id, 'NEW_RELEASES'),
        (NEW.id, 'ACCOUNT_ACTIVITY'),
        (NEW.id, 'MARKETING');

    -- Insert default reading preferences
    INSERT INTO user_reading_preferences (user_id, category)
    VALUES
        (NEW.id, 'Fiction'),
        (NEW.id, 'Non-Fiction'),
        (NEW.id, 'Science'),
        (NEW.id, 'Technology'),
        (NEW.id, 'History'),
        (NEW.id, 'Biography');

    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER create_default_preferences_after_user_insert
    AFTER INSERT ON user_profiles
    FOR EACH ROW EXECUTE FUNCTION create_default_user_preferences();

-- Row Level Security (RLS) policies
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_addresses ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_notification_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_reading_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_wishlist ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_reading_history ENABLE ROW LEVEL SECURITY;

-- RLS policies will be managed by application logic
-- Users can only see their own data
-- Admins can see all data