-- Product Catalog Service Database Schema
-- Version: 1.0.0
-- Description: Product catalog, inventory, and pricing tables

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Categories table (hierarchical product categories)
CREATE TABLE categories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    parent_id UUID REFERENCES categories(id),
    display_order INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Products table (main product catalog)
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sku VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL,
    subtitle VARCHAR(500),
    description TEXT,
    isbn VARCHAR(20) UNIQUE,
    isbn13 VARCHAR(20) UNIQUE,
    authors TEXT[], -- Array of author names
    publisher VARCHAR(255),
    publication_date DATE,
    edition VARCHAR(50),
    language VARCHAR(10) NOT NULL DEFAULT 'en',
    pages INTEGER,
    dimensions VARCHAR(100), -- Format: "length x width x height inches"
    weight DECIMAL(8,2), -- Weight in pounds
    format VARCHAR(50), -- hardcover, paperback, ebook, audiobook, etc.
    category_id UUID REFERENCES categories(id),
    tags TEXT[], -- Array of tags for search
    images TEXT[], -- Array of image URLs
    featured_image_url TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    is_featured BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Product variants (for different formats/editions of same book)
CREATE TABLE product_variants (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(50) NOT NULL UNIQUE,
    variant_type VARCHAR(50) NOT NULL, -- format, edition, etc.
    variant_value VARCHAR(100) NOT NULL, -- hardcover, paperback, etc.
    price DECIMAL(10,2) NOT NULL CHECK (price >= 0),
    compare_at_price DECIMAL(10,2), -- Original price for sales
    cost_price DECIMAL(10,2), -- Cost to bookstore
    inventory_quantity INTEGER NOT NULL DEFAULT 0 CHECK (inventory_quantity >= 0),
    low_stock_threshold INTEGER NOT NULL DEFAULT 10,
    is_available BOOLEAN NOT NULL DEFAULT true,
    requires_shipping BOOLEAN NOT NULL DEFAULT true,
    weight DECIMAL(8,2),
    dimensions VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(product_id, variant_type, variant_value)
);

-- Product pricing history
CREATE TABLE product_pricing_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    old_price DECIMAL(10,2),
    new_price DECIMAL(10,2) NOT NULL,
    changed_by VARCHAR(255), -- User who made the change
    change_reason TEXT,
    effective_date TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Product reviews and ratings
CREATE TABLE product_reviews (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL, -- References user service
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    title VARCHAR(200),
    review_text TEXT,
    verified_purchase BOOLEAN NOT NULL DEFAULT false,
    helpful_votes INTEGER NOT NULL DEFAULT 0,
    total_votes INTEGER NOT NULL DEFAULT 0,
    is_moderated BOOLEAN NOT NULL DEFAULT false,
    moderated_by VARCHAR(255),
    moderated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(product_id, user_id)
);

-- Inventory transactions (track stock movements)
CREATE TABLE inventory_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_variant_id UUID NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
    transaction_type VARCHAR(50) NOT NULL CHECK (transaction_type IN ('PURCHASE', 'SALE', 'ADJUSTMENT', 'RETURN', 'DAMAGED', 'LOST')),
    quantity INTEGER NOT NULL, -- Positive for additions, negative for reductions
    previous_quantity INTEGER NOT NULL,
    new_quantity INTEGER NOT NULL,
    reference_id UUID, -- Order ID, purchase order ID, etc.
    reference_type VARCHAR(50), -- ORDER, PURCHASE_ORDER, ADJUSTMENT, etc.
    notes TEXT,
    created_by VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Product attributes (for filtering and search)
CREATE TABLE product_attributes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    attribute_type VARCHAR(50) NOT NULL DEFAULT 'STRING' CHECK (attribute_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'DATE')),
    is_filterable BOOLEAN NOT NULL DEFAULT true,
    is_searchable BOOLEAN NOT NULL DEFAULT false,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Product attribute values
CREATE TABLE product_attribute_values (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    attribute_id UUID NOT NULL REFERENCES product_attributes(id) ON DELETE CASCADE,
    string_value TEXT,
    number_value DECIMAL(15,6),
    boolean_value BOOLEAN,
    date_value DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(product_id, attribute_id)
);

-- Product collections (featured collections, recommendations)
CREATE TABLE product_collections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    collection_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL' CHECK (collection_type IN ('MANUAL', 'AUTOMATED', 'SMART')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    display_order INTEGER NOT NULL DEFAULT 0,
    rules JSONB, -- For automated collections
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Product collection items
CREATE TABLE product_collection_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    collection_id UUID NOT NULL REFERENCES product_collections(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(collection_id, product_id)
);

-- Indexes for performance
CREATE INDEX idx_categories_parent_id ON categories(parent_id);
CREATE INDEX idx_categories_active ON categories(is_active);
CREATE INDEX idx_categories_display_order ON categories(display_order);

CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_is_active ON products(is_active);
CREATE INDEX idx_products_is_featured ON products(is_featured);
CREATE INDEX idx_products_created_at ON products(created_at DESC);
CREATE INDEX idx_products_title ON products USING gin(to_tsvector('english', title));
CREATE INDEX idx_products_authors ON products USING gin(authors);
CREATE INDEX idx_products_tags ON products USING gin(tags);

CREATE INDEX idx_product_variants_product_id ON product_variants(product_id);
CREATE INDEX idx_product_variants_sku ON product_variants(sku);
CREATE INDEX idx_product_variants_available ON product_variants(is_available);
CREATE INDEX idx_product_variants_price ON product_variants(price);

CREATE INDEX idx_product_pricing_history_variant_id ON product_pricing_history(product_variant_id);
CREATE INDEX idx_product_pricing_history_effective_date ON product_pricing_history(effective_date DESC);

CREATE INDEX idx_product_reviews_product_id ON product_reviews(product_id);
CREATE INDEX idx_product_reviews_user_id ON product_reviews(user_id);
CREATE INDEX idx_product_reviews_rating ON product_reviews(rating);
CREATE INDEX idx_product_reviews_created_at ON product_reviews(created_at DESC);

CREATE INDEX idx_inventory_transactions_variant_id ON inventory_transactions(product_variant_id);
CREATE INDEX idx_inventory_transactions_type ON inventory_transactions(transaction_type);
CREATE INDEX idx_inventory_transactions_created_at ON inventory_transactions(created_at DESC);

CREATE INDEX idx_product_attributes_name ON product_attributes(name);
CREATE INDEX idx_product_attributes_filterable ON product_attributes(is_filterable);

CREATE INDEX idx_product_attribute_values_product_id ON product_attribute_values(product_id);
CREATE INDEX idx_product_attribute_values_attribute_id ON product_attribute_values(attribute_id);

CREATE INDEX idx_product_collections_active ON product_collections(is_active);
CREATE INDEX idx_product_collections_type ON product_collections(collection_type);

CREATE INDEX idx_product_collection_items_collection_id ON product_collection_items(collection_id);
CREATE INDEX idx_product_collection_items_display_order ON product_collection_items(display_order);

-- Triggers for updated_at timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_categories_updated_at BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_products_updated_at BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_product_variants_updated_at BEFORE UPDATE ON product_variants
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_product_reviews_updated_at BEFORE UPDATE ON product_reviews
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_product_collections_updated_at BEFORE UPDATE ON product_collections
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Default categories
INSERT INTO categories (name, description, display_order) VALUES
    ('Fiction', 'Fictional literature and novels', 1),
    ('Non-Fiction', 'Factual books and biographies', 2),
    ('Science', 'Science and technology books', 3),
    ('History', 'Historical books and accounts', 4),
    ('Biography', 'Biographies and memoirs', 5),
    ('Children', 'Books for children', 6);

-- Default product attributes
INSERT INTO product_attributes (name, display_name, attribute_type, is_filterable, is_searchable) VALUES
    ('genre', 'Genre', 'STRING', true, true),
    ('age_range', 'Age Range', 'STRING', true, false),
    ('series', 'Series', 'STRING', true, true),
    ('award_winner', 'Award Winner', 'BOOLEAN', true, false),
    ('bestseller', 'Bestseller', 'BOOLEAN', true, false);

-- Row Level Security (RLS) policies
ALTER TABLE products ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_variants ENABLE ROW LEVEL SECURITY;
ALTER TABLE product_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE inventory_transactions ENABLE ROW LEVEL SECURITY;

-- RLS policies will be managed by application logic
-- Products can be read by all users
-- Write operations restricted to admin users