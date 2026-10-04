-- Map data lives on problems (each problem = one location)
ALTER TABLE problems
    ADD COLUMN position_x INTEGER,
    ADD COLUMN position_y INTEGER,
    ADD COLUMN is_start   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN node_icon  VARCHAR(50);

ALTER TABLE problem_attempts ADD COLUMN last_attempt_at TIMESTAMP;

-- Paths between locations (walkable both ways, stored once with a < b)
CREATE TABLE problem_links (
    id           BIGSERIAL PRIMARY KEY,
    quest_id     BIGINT    NOT NULL REFERENCES quests(id)   ON DELETE CASCADE,
    problem_a_id BIGINT    NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    problem_b_id BIGINT    NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP,
    CONSTRAINT chk_problem_links_order CHECK (problem_a_id < problem_b_id),
    CONSTRAINT uq_problem_links UNIQUE (problem_a_id, problem_b_id)
);
CREATE INDEX idx_problem_links_quest ON problem_links(quest_id);
CREATE INDEX idx_problem_links_b     ON problem_links(problem_b_id);

-- Backfill existing data so current quests keep working:
-- first problem = start node, problems chained in order_index order, laid out in a row.
UPDATE problems SET is_start = TRUE WHERE id IN (
    SELECT DISTINCT ON (quest_id) id FROM problems ORDER BY quest_id, order_index, id);

INSERT INTO problem_links (quest_id, problem_a_id, problem_b_id)
SELECT quest_id, LEAST(id, next_id), GREATEST(id, next_id)
FROM (SELECT quest_id, id,
             LEAD(id) OVER (PARTITION BY quest_id ORDER BY order_index, id) AS next_id
      FROM problems) t
WHERE next_id IS NOT NULL;

UPDATE problems p
SET position_x = 100 + (t.rn - 1) * 160, position_y = 200
FROM (SELECT id, ROW_NUMBER() OVER (PARTITION BY quest_id ORDER BY order_index, id) AS rn
      FROM problems) t
WHERE t.id = p.id;

-- Answers revealed under the old rules: give those players another go
UPDATE problem_attempts SET answer_revealed = FALSE, attempts_used = 0
WHERE solved = FALSE AND answer_revealed = TRUE;