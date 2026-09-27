-- Performance indexes for common queries

-- Users
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_active ON users(active);
CREATE INDEX IF NOT EXISTS idx_users_email_verified ON users(email_verified);

-- OTP codes
CREATE INDEX IF NOT EXISTS idx_otp_user_id ON otp_codes(user_id);
CREATE INDEX IF NOT EXISTS idx_otp_expires_at ON otp_codes(expires_at);
CREATE INDEX IF NOT EXISTS idx_otp_used ON otp_codes(used);

-- Subscriptions
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions(status);
CREATE INDEX IF NOT EXISTS idx_subscriptions_period_end ON subscriptions(current_period_end);

-- Videos
CREATE INDEX IF NOT EXISTS idx_videos_difficulty ON videos(difficulty_level);
CREATE INDEX IF NOT EXISTS idx_videos_uploaded_by ON videos(uploaded_by);
CREATE INDEX IF NOT EXISTS idx_videos_view_count ON videos(view_count DESC);
CREATE INDEX IF NOT EXISTS idx_videos_title ON videos USING gin(to_tsvector('english', title));

-- Comments
CREATE INDEX IF NOT EXISTS idx_comments_user_id ON comments(user_id);
CREATE INDEX IF NOT EXISTS idx_comments_depth ON comments(depth);
CREATE INDEX IF NOT EXISTS idx_comments_deleted ON comments(deleted);
CREATE INDEX IF NOT EXISTS idx_comments_created_at ON comments(created_at DESC);

-- Problems
CREATE INDEX IF NOT EXISTS idx_problems_type ON problems(problem_type);
CREATE INDEX IF NOT EXISTS idx_problems_difficulty ON problems(difficulty_level);
CREATE INDEX IF NOT EXISTS idx_problems_order ON problems(quest_id, order_index);

-- Problem attempts
CREATE INDEX IF NOT EXISTS idx_attempts_solved ON problem_attempts(solved);

-- Quest prerequisites
CREATE INDEX IF NOT EXISTS idx_quest_prereq_prereq_id ON quest_prerequisites(prerequisite_id);

-- User quest progress
CREATE INDEX IF NOT EXISTS idx_uqp_status ON user_quest_progress(status);