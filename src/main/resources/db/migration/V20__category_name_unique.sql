-- Sibling categories must have unique names (case-insensitive).
-- COALESCE makes top-level categories (parent_id IS NULL) compare equal to each other.
-- If this fails, two categories share a name under the same parent: rename one and re-run.
CREATE UNIQUE INDEX uq_categories_parent_name
    ON categories (COALESCE(parent_id, 0), LOWER(name));