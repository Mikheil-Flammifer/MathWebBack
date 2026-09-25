-- USERS
CREATE TABLE users (
    id               BIGSERIAL PRIMARY KEY,
    first_name       VARCHAR(100)        NOT NULL,
    last_name        VARCHAR(100)        NOT NULL,
    email            VARCHAR(255) UNIQUE NOT NULL,
    password_hash    TEXT                NOT NULL,
    role             VARCHAR(20)         NOT NULL,
    email_verified   BOOLEAN             NOT NULL DEFAULT FALSE,
    active           BOOLEAN             NOT NULL DEFAULT TRUE,
    avatar_url       TEXT,
    stripe_customer_id VARCHAR(255) UNIQUE,
    created_at       TIMESTAMP           NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP
);

-- OTP CODES
CREATE TABLE otp_codes (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code        VARCHAR(10) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT FALSE,
    purpose     VARCHAR(50) NOT NULL DEFAULT 'EMAIL_VERIFICATION',
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP
);

-- REFRESH TOKENS
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(512) NOT NULL UNIQUE,
    expires_at  TIMESTAMP    NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP
);

-- SUBSCRIPTIONS
CREATE TABLE subscriptions (
    id                      BIGSERIAL PRIMARY KEY,
    user_id                 BIGINT      NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    status                  VARCHAR(30) NOT NULL,
    stripe_subscription_id  VARCHAR(255) UNIQUE,
    stripe_price_id         VARCHAR(255),
    amount_cents            INTEGER,
    currency                VARCHAR(10) DEFAULT 'usd',
    current_period_start    TIMESTAMP,
    current_period_end      TIMESTAMP,
    cancelled_at            TIMESTAMP,
    trial_end               TIMESTAMP,
    created_at              TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP
);

-- QUESTS
CREATE TABLE quests (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    icon_url         TEXT,
    difficulty_level VARCHAR(40)  NOT NULL,
    position_x       INTEGER,
    position_y       INTEGER,
    published        BOOLEAN      NOT NULL DEFAULT FALSE,
    xp_reward        INTEGER      DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP
);

-- QUEST PREREQUISITES (DAG graph)
CREATE TABLE quest_prerequisites (
    quest_id        BIGINT NOT NULL REFERENCES quests(id) ON DELETE CASCADE,
    prerequisite_id BIGINT NOT NULL REFERENCES quests(id) ON DELETE CASCADE,
    PRIMARY KEY (quest_id, prerequisite_id)
);

-- VIDEOS
CREATE TABLE videos (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    file_path        TEXT         NOT NULL,
    thumbnail_path   TEXT,
    duration_seconds BIGINT,
    file_size_bytes  BIGINT,
    mime_type        VARCHAR(50),
    status           VARCHAR(20)  NOT NULL DEFAULT 'UPLOADING',
    difficulty_level VARCHAR(40),
    view_count       BIGINT       DEFAULT 0,
    uploaded_by      BIGINT       NOT NULL REFERENCES users(id),
    quest_id         BIGINT       REFERENCES quests(id) ON DELETE SET NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP
);

-- COMMENTS
CREATE TABLE comments (
    id          BIGSERIAL PRIMARY KEY,
    content     TEXT        NOT NULL,
    depth       INTEGER     NOT NULL DEFAULT 0,
    edited      BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted     BOOLEAN     NOT NULL DEFAULT FALSE,
    upvotes     INTEGER     DEFAULT 0,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    video_id    BIGINT      NOT NULL REFERENCES videos(id) ON DELETE CASCADE,
    parent_id   BIGINT      REFERENCES comments(id) ON DELETE CASCADE,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP
);

-- PROBLEMS
CREATE TABLE problems (
    id                     BIGSERIAL PRIMARY KEY,
    question_text          TEXT        NOT NULL,
    question_image_path    TEXT,
    problem_type           VARCHAR(20) NOT NULL,
    difficulty_level       VARCHAR(40) NOT NULL,
    correct_answer         TEXT,
    explanation            TEXT,
    explanation_image_path TEXT,
    order_index            INTEGER     NOT NULL DEFAULT 0,
    max_attempts           INTEGER     NOT NULL DEFAULT 3,
    xp_reward              INTEGER     DEFAULT 10,
    quest_id               BIGINT      NOT NULL REFERENCES quests(id) ON DELETE CASCADE,
    created_at             TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP
);

-- ANSWER OPTIONS
CREATE TABLE answer_options (
    id                BIGSERIAL PRIMARY KEY,
    problem_id        BIGINT   NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    option_text       TEXT     NOT NULL,
    option_image_path TEXT,
    is_correct        BOOLEAN  NOT NULL,
    order_index       INTEGER  NOT NULL DEFAULT 0,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP
);

-- PROBLEM ATTEMPTS
CREATE TABLE problem_attempts (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    problem_id       BIGINT   NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    attempts_used    INTEGER  NOT NULL DEFAULT 0,
    solved           BOOLEAN  NOT NULL DEFAULT FALSE,
    answer_revealed  BOOLEAN  NOT NULL DEFAULT FALSE,
    last_answer_given TEXT,
    xp_earned        INTEGER  DEFAULT 0,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP,
    UNIQUE (user_id, problem_id)
);

-- USER QUEST PROGRESS
CREATE TABLE user_quest_progress (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    quest_id         BIGINT   NOT NULL REFERENCES quests(id) ON DELETE CASCADE,
    status           VARCHAR(20) NOT NULL DEFAULT 'LOCKED',
    problems_solved  INTEGER  DEFAULT 0,
    total_problems   INTEGER,
    xp_earned        INTEGER  DEFAULT 0,
    started_at       TIMESTAMP,
    completed_at     TIMESTAMP,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP,
    UNIQUE (user_id, quest_id)
);

-- INDEXES
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_videos_status ON videos(status);
CREATE INDEX idx_videos_quest_id ON videos(quest_id);
CREATE INDEX idx_comments_video_id ON comments(video_id);
CREATE INDEX idx_comments_parent_id ON comments(parent_id);
CREATE INDEX idx_problems_quest_id ON problems(quest_id);
CREATE INDEX idx_problem_attempts_user_id ON problem_attempts(user_id);
CREATE INDEX idx_user_quest_progress_user_id ON user_quest_progress(user_id);