ALTER TABLE paddle_checkout_orders
    ADD COLUMN actual_subtotal_cents BIGINT,
    ADD COLUMN actual_total_cents    BIGINT;