ALTER TABLE problems ADD COLUMN answer_tolerance DOUBLE PRECISION;

-- If problem_type has a CHECK constraint, drop it so 'NUMERIC' is allowed
DO $$
DECLARE c record;
BEGIN
  FOR c IN SELECT conname FROM pg_constraint
           WHERE conrelid = 'problems'::regclass AND contype = 'c'
             AND pg_get_constraintdef(oid) ILIKE '%problem_type%'
  LOOP
    EXECUTE format('ALTER TABLE problems DROP CONSTRAINT %I', c.conname);
  END LOOP;
END $$;