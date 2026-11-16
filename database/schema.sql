-- MEESHO X — PostgreSQL schema (server side). Migration V1.
-- Money is stored as NUMERIC(12,2) in INR. Timestamps are timestamptz.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TYPE user_role       AS ENUM ('CUSTOMER','SELLER','ADMIN','SUPPORT');
CREATE TYPE order_status    AS ENUM ('PLACED','CONFIRMED','SHIPPED','OUT_FOR_DELIVERY','DELIVERED','CANCELLED','RETURN_REQUESTED','RETURNED');
CREATE TYPE payment_status  AS ENUM ('PENDING','PAID','FAILED','COD_PENDING','REFUND_INITIATED','REFUNDED');
CREATE TYPE payment_method  AS ENUM ('UPI','CREDIT_CARD','DEBIT_CARD','NET_BANKING','WALLET','COD');
CREATE TYPE ticket_status   AS ENUM ('OPEN','ESCALATED','RESOLVED');

CREATE TABLE users (
  id            BIGSERIAL PRIMARY KEY,
  name          TEXT NOT NULL,
  phone         VARCHAR(10) NOT NULL UNIQUE CHECK (phone ~ '^[6-9][0-9]{9}$'),
  email         CITEXT,
  password_hash TEXT,                       -- argon2id; NULL for OTP-only accounts
  role          user_role NOT NULL DEFAULT 'CUSTOMER',
  personalization_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  privacy_consent_at TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at    TIMESTAMPTZ                 -- soft delete, PII scrubbed by job
);

CREATE TABLE user_addresses (
  id        BIGSERIAL PRIMARY KEY,
  user_id   BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  full_name TEXT NOT NULL, phone VARCHAR(10) NOT NULL,
  line1 TEXT NOT NULL, line2 TEXT, city TEXT NOT NULL, state TEXT NOT NULL,
  pincode   CHAR(6) NOT NULL CHECK (pincode ~ '^[1-8][0-9]{5}$'),
  is_default BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX ON user_addresses(user_id);
CREATE UNIQUE INDEX one_default_address ON user_addresses(user_id) WHERE is_default;

CREATE TABLE sellers (
  id            BIGSERIAL PRIMARY KEY,
  user_id       BIGINT UNIQUE REFERENCES users(id),
  business_name TEXT NOT NULL,
  gstin         CHAR(15) NOT NULL UNIQUE CHECK (gstin ~ '^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$'),
  pickup_city   TEXT,
  bank_account_enc BYTEA,                  -- encrypted with KMS data key
  bank_ifsc     CHAR(11),
  rating        NUMERIC(2,1) DEFAULT 0,
  verification_status TEXT NOT NULL DEFAULT 'PENDING',
  commission_pct NUMERIC(5,2) NOT NULL DEFAULT 0,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE seller_documents (
  id         BIGSERIAL PRIMARY KEY,
  seller_id  BIGINT NOT NULL REFERENCES sellers(id) ON DELETE CASCADE,
  doc_type   TEXT NOT NULL,                -- GST_CERT, PAN, CANCELLED_CHEQUE
  storage_key TEXT NOT NULL,               -- object storage key, never a public URL
  status     TEXT NOT NULL DEFAULT 'PENDING',
  reviewed_by BIGINT REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE categories (
  id        BIGSERIAL PRIMARY KEY,
  parent_id BIGINT REFERENCES categories(id),
  name      TEXT NOT NULL, slug TEXT NOT NULL UNIQUE, icon TEXT, sort_order INT DEFAULT 0
);

CREATE TABLE products (
  id          BIGSERIAL PRIMARY KEY,
  seller_id   BIGINT NOT NULL REFERENCES sellers(id),
  category_id BIGINT NOT NULL REFERENCES categories(id),
  name TEXT NOT NULL, brand TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
  specs       JSONB NOT NULL DEFAULT '{}',
  tags        TEXT[] NOT NULL DEFAULT '{}',
  return_days INT NOT NULL DEFAULT 7,
  rating_avg  NUMERIC(2,1) NOT NULL DEFAULT 0, rating_count INT NOT NULL DEFAULT 0,
  popularity  INT NOT NULL DEFAULT 0,
  status      TEXT NOT NULL DEFAULT 'PENDING_REVIEW',  -- PENDING_REVIEW, LIVE, PAUSED, REJECTED
  search_tsv  TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', name || ' ' || brand || ' ' || description)) STORED,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON products(category_id, status);
CREATE INDEX ON products(seller_id);
CREATE INDEX ON products USING GIN(search_tsv);
CREATE INDEX ON products USING GIN(name gin_trgm_ops);
CREATE INDEX ON products USING GIN(tags);

CREATE TABLE product_variants (
  id         BIGSERIAL PRIMARY KEY,
  product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  sku        TEXT NOT NULL UNIQUE,
  size TEXT, colour TEXT, colour_hex CHAR(7),
  price NUMERIC(12,2) NOT NULL CHECK (price > 0),
  mrp   NUMERIC(12,2) NOT NULL CHECK (mrp >= price)
);
CREATE INDEX ON product_variants(product_id);

CREATE TABLE product_images (
  id BIGSERIAL PRIMARY KEY,
  product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  variant_id BIGINT REFERENCES product_variants(id) ON DELETE CASCADE,
  storage_key TEXT NOT NULL, width INT, height INT, sort_order INT DEFAULT 0, is_video BOOLEAN DEFAULT FALSE
);

CREATE TABLE inventory (
  variant_id BIGINT PRIMARY KEY REFERENCES product_variants(id) ON DELETE CASCADE,
  on_hand  INT NOT NULL DEFAULT 0 CHECK (on_hand >= 0),
  reserved INT NOT NULL DEFAULT 0 CHECK (reserved >= 0),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CHECK (reserved <= on_hand)
);

CREATE TABLE carts (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE, updated_at TIMESTAMPTZ DEFAULT now());
CREATE TABLE cart_items (
  id BIGSERIAL PRIMARY KEY,
  cart_id BIGINT NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
  variant_id BIGINT NOT NULL REFERENCES product_variants(id),
  quantity INT NOT NULL CHECK (quantity BETWEEN 1 AND 10),
  saved_for_later BOOLEAN NOT NULL DEFAULT FALSE,
  UNIQUE (cart_id, variant_id, saved_for_later)
);

CREATE TABLE wishlists (id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, name TEXT NOT NULL DEFAULT 'Wishlist', share_code TEXT UNIQUE);
CREATE TABLE wishlist_items (
  wishlist_id BIGINT NOT NULL REFERENCES wishlists(id) ON DELETE CASCADE,
  product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  added_by BIGINT REFERENCES users(id), added_at TIMESTAMPTZ DEFAULT now(),
  PRIMARY KEY (wishlist_id, product_id)
);

CREATE TABLE coupons (
  code TEXT PRIMARY KEY, description TEXT NOT NULL,
  percent_off INT CHECK (percent_off BETWEEN 0 AND 90), flat_off NUMERIC(12,2) DEFAULT 0,
  min_order NUMERIC(12,2) NOT NULL DEFAULT 0, max_discount NUMERIC(12,2) NOT NULL,
  starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ, usage_limit INT, per_user_limit INT DEFAULT 1, active BOOLEAN DEFAULT TRUE
);

CREATE TABLE orders (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id),
  shipping_address JSONB NOT NULL,          -- snapshot at order time
  subtotal NUMERIC(12,2) NOT NULL, discount NUMERIC(12,2) NOT NULL DEFAULT 0,
  delivery_charge NUMERIC(12,2) NOT NULL DEFAULT 0, tax NUMERIC(12,2) NOT NULL DEFAULT 0, total NUMERIC(12,2) NOT NULL,
  coupon_code TEXT REFERENCES coupons(code),
  status order_status NOT NULL DEFAULT 'PLACED',
  delivery_option TEXT NOT NULL DEFAULT 'STANDARD', expected_delivery DATE,
  risk_score INT NOT NULL DEFAULT 0, risk_flags TEXT[] DEFAULT '{}', risk_review_status TEXT,
  idempotency_key UUID UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON orders(user_id, created_at DESC);
CREATE INDEX ON orders(status);
CREATE INDEX ON orders(created_at);
CREATE INDEX ON orders(risk_score) WHERE risk_score >= 50;

CREATE TABLE order_items (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  variant_id BIGINT NOT NULL REFERENCES product_variants(id),
  seller_id BIGINT NOT NULL REFERENCES sellers(id),
  name_snapshot TEXT NOT NULL, unit_price NUMERIC(12,2) NOT NULL, quantity INT NOT NULL CHECK (quantity > 0)
);
CREATE INDEX ON order_items(order_id);
CREATE INDEX ON order_items(seller_id);

CREATE TABLE payments (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES orders(id),
  provider TEXT NOT NULL, method payment_method NOT NULL,
  provider_order_id TEXT, provider_payment_id TEXT UNIQUE,
  amount NUMERIC(12,2) NOT NULL, status payment_status NOT NULL DEFAULT 'PENDING',
  signature_verified BOOLEAN NOT NULL DEFAULT FALSE,   -- webhook/HMAC verified server-side
  raw_webhook JSONB, created_at TIMESTAMPTZ DEFAULT now(), updated_at TIMESTAMPTZ DEFAULT now()
  -- NOTE: no card number / CVV columns by design (PCI-DSS scope stays with the gateway)
);
CREATE INDEX ON payments(order_id);

CREATE TABLE refunds (
  id BIGSERIAL PRIMARY KEY,
  payment_id BIGINT NOT NULL REFERENCES payments(id),
  amount NUMERIC(12,2) NOT NULL CHECK (amount > 0), reason TEXT,
  provider_refund_id TEXT UNIQUE, status TEXT NOT NULL DEFAULT 'INITIATED',
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE shipments (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES orders(id),
  carrier TEXT, awb TEXT UNIQUE, partner_name TEXT, partner_phone TEXT,
  status TEXT NOT NULL DEFAULT 'CREATED', events JSONB NOT NULL DEFAULT '[]',
  shipped_at TIMESTAMPTZ, delivered_at TIMESTAMPTZ
);
CREATE INDEX ON shipments(order_id);

CREATE TABLE reviews (
  id BIGSERIAL PRIMARY KEY,
  product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id),
  order_item_id BIGINT REFERENCES order_items(id),   -- non-null => verified purchase
  rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  body TEXT, fit TEXT CHECK (fit IN ('SMALL','TRUE','LARGE')), photo_keys TEXT[] DEFAULT '{}',
  status TEXT NOT NULL DEFAULT 'PUBLISHED', created_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (product_id, user_id)
);
CREATE INDEX ON reviews(product_id, created_at DESC);

CREATE TABLE product_questions (
  id BIGSERIAL PRIMARY KEY,
  product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id),
  question TEXT NOT NULL, answer TEXT, answered_by BIGINT REFERENCES users(id),
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE notifications (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, data JSONB,
  read_at TIMESTAMPTZ, created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX ON notifications(user_id, created_at DESC);

CREATE TABLE support_tickets (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
  order_id BIGINT REFERENCES orders(id), subject TEXT NOT NULL, message TEXT NOT NULL,
  status ticket_status NOT NULL DEFAULT 'OPEN', assigned_to BIGINT REFERENCES users(id),
  created_at TIMESTAMPTZ DEFAULT now(), updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE ai_conversations (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  channel TEXT NOT NULL, role TEXT NOT NULL CHECK (role IN ('user','assistant')),
  content TEXT NOT NULL, product_ids BIGINT[] DEFAULT '{}',
  engine TEXT NOT NULL,                      -- 'rules' | 'local-model' | 'cloud:<provider>' (provenance)
  created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE search_history (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  query TEXT NOT NULL, created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX ON search_history(user_id, created_at DESC);

CREATE TABLE price_history (
  id BIGSERIAL PRIMARY KEY,
  variant_id BIGINT NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
  price NUMERIC(12,2) NOT NULL, recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON price_history(variant_id, recorded_at);

CREATE TABLE seller_payouts (
  id BIGSERIAL PRIMARY KEY, seller_id BIGINT NOT NULL REFERENCES sellers(id),
  period_start DATE NOT NULL, period_end DATE NOT NULL,
  gross NUMERIC(12,2) NOT NULL, commission NUMERIC(12,2) NOT NULL, net NUMERIC(12,2) NOT NULL,
  status TEXT NOT NULL DEFAULT 'PENDING', provider_transfer_id TEXT, created_at TIMESTAMPTZ DEFAULT now(),
  UNIQUE (seller_id, period_start, period_end)
);

CREATE TABLE audit_logs (
  id BIGSERIAL PRIMARY KEY, actor_id BIGINT REFERENCES users(id), action TEXT NOT NULL,
  entity TEXT, entity_id TEXT, detail JSONB, ip INET, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON audit_logs(created_at);
CREATE INDEX ON audit_logs(actor_id, created_at);
