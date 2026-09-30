CREATE TABLE comment_upvotes (
    id          BIGSERIAL PRIMARY KEY,
    comment_id  BIGINT    NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    user_id     BIGINT    NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP,
    UNIQUE (comment_id, user_id)
);

CREATE INDEX idx_comment_upvotes_user_id ON comment_upvotes(user_id);