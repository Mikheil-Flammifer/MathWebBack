ALTER TABLE subscriptions DROP COLUMN IF EXISTS stripe_subscription_id;
ALTER TABLE subscriptions DROP COLUMN IF EXISTS stripe_price_id;
ALTER TABLE users DROP COLUMN IF EXISTS stripe_customer_id;