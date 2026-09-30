CREATE TABLE comment_votes (
    id          BIGSERIAL PRIMARY KEY,
    comment_id  BIGINT    NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    user_id     BIGINT    NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    vote_value  SMALLINT  NOT NULL CHECK (vote_value IN (-1, 1)),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP,
    UNIQUE (comment_id, user_id)
);

CREATE INDEX idx_comment_votes_user_id ON comment_votes(user_id);

INSERT INTO comment_votes (comment_id, user_id, vote_value, created_at)
SELECT comment_id, user_id, 1, created_at FROM comment_upvotes;

DROP TABLE comment_upvotes;

ALTER TABLE comments ADD COLUMN downvotes INTEGER NOT NULL DEFAULT 0;