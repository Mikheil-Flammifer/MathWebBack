-- ===== TABLE =====
CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    icon        VARCHAR(50),
    color       VARCHAR(20),
    order_index INTEGER      NOT NULL DEFAULT 0,
    parent_id   BIGINT       REFERENCES categories(id) ON DELETE RESTRICT,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP
);

CREATE INDEX idx_categories_parent_id ON categories(parent_id);

-- Quests are difficulty stages, so only problems and videos get a category
ALTER TABLE problems ADD COLUMN category_id BIGINT REFERENCES categories(id) ON DELETE SET NULL;
ALTER TABLE videos   ADD COLUMN category_id BIGINT REFERENCES categories(id) ON DELETE SET NULL;

CREATE INDEX idx_problems_category_id ON problems(category_id);
CREATE INDEX idx_videos_category_id   ON videos(category_id);

-- ===== MAIN CATEGORIES =====
INSERT INTO categories (name, slug, order_index, color) VALUES
  ('Algebra',        'algebra',        1, '#6366f1'),
  ('Geometry',       'geometry',       2, '#10b981'),
  ('Combinatorics',  'combinatorics',  3, '#f59e0b'),
  ('Number Theory',  'number-theory',  4, '#ef4444');

-- ===== SUBCATEGORIES (basic + advanced) =====
INSERT INTO categories (name, slug, order_index, parent_id)
SELECT s.name, s.slug, s.ord, p.id
FROM (VALUES
  ('Linear Equations',            'linear-equations',            1,  'algebra'),
  ('Systems of Equations',        'systems-of-equations',        2,  'algebra'),
  ('Quadratic Equations',         'quadratic-equations',         3,  'algebra'),
  ('Inequalities',                'inequalities',                4,  'algebra'),
  ('Functions',                   'functions',                   5,  'algebra'),
  ('Polynomials',                 'polynomials',                 6,  'algebra'),
  ('Sequences and Series',        'sequences-and-series',        7,  'algebra'),
  ('Exponents and Logarithms',    'exponents-and-logarithms',    8,  'algebra'),
  ('Classical Inequalities',      'classical-inequalities',      9,  'algebra'),
  ('Functional Equations',        'functional-equations',        10, 'algebra'),
  ('Polynomial Roots and Vieta',  'polynomial-roots-and-vieta',  11, 'algebra'),
  ('Recurrence Relations',        'recurrence-relations',        12, 'algebra'),
  ('Complex Numbers',             'complex-numbers',             13, 'algebra'),

  ('Triangles',                   'triangles',                   1,  'geometry'),
  ('Circles',                     'circles',                     2,  'geometry'),
  ('Polygons',                    'polygons',                    3,  'geometry'),
  ('Area and Perimeter',          'area-and-perimeter',          4,  'geometry'),
  ('Coordinate Geometry',         'coordinate-geometry',         5,  'geometry'),
  ('Solid Geometry',              'solid-geometry',              6,  'geometry'),
  ('Trigonometry',                'trigonometry',                7,  'geometry'),
  ('Similarity and Congruence',   'similarity-and-congruence',   8,  'geometry'),
  ('Cyclic Quadrilaterals',       'cyclic-quadrilaterals',       9,  'geometry'),
  ('Power of a Point',            'power-of-a-point',            10, 'geometry'),
  ('Geometric Transformations',   'geometric-transformations',   11, 'geometry'),
  ('Vectors',                     'vectors',                     12, 'geometry'),
  ('Geometric Inequalities',      'geometric-inequalities',      13, 'geometry'),

  ('Counting Principles',         'counting-principles',         1,  'combinatorics'),
  ('Permutations',                'permutations',                2,  'combinatorics'),
  ('Combinations',                'combinations',                3,  'combinatorics'),
  ('Probability',                 'probability',                 4,  'combinatorics'),
  ('Pigeonhole Principle',        'pigeonhole-principle',        5,  'combinatorics'),
  ('Inclusion-Exclusion',         'inclusion-exclusion',         6,  'combinatorics'),
  ('Binomial Coefficients',       'binomial-coefficients',       7,  'combinatorics'),
  ('Double Counting',             'double-counting',             8,  'combinatorics'),
  ('Graph Theory',                'graph-theory',                9,  'combinatorics'),
  ('Invariants and Monovariants', 'invariants-and-monovariants', 10, 'combinatorics'),
  ('Extremal Principle',          'extremal-principle',          11, 'combinatorics'),
  ('Combinatorial Games',         'combinatorial-games',         12, 'combinatorics'),
  ('Generating Functions',        'generating-functions',        13, 'combinatorics'),
  ('Expected Value',              'expected-value',              14, 'combinatorics'),

  ('Divisibility',                'divisibility',                1,  'number-theory'),
  ('Primes and Factorization',    'primes-and-factorization',    2,  'number-theory'),
  ('GCD and LCM',                 'gcd-and-lcm',                 3,  'number-theory'),
  ('Modular Arithmetic',          'modular-arithmetic',          4,  'number-theory'),
  ('Diophantine Equations',       'diophantine-equations',       5,  'number-theory'),
  ('Number Bases',                'number-bases',                6,  'number-theory'),
  ('Chinese Remainder Theorem',   'chinese-remainder-theorem',   7,  'number-theory'),
  ('Fermat and Euler Theorems',   'fermat-and-euler-theorems',   8,  'number-theory'),
  ('Arithmetic Functions',        'arithmetic-functions',        9,  'number-theory'),
  ('Orders and Primitive Roots',  'orders-and-primitive-roots',  10, 'number-theory'),
  ('Quadratic Residues',          'quadratic-residues',          11, 'number-theory'),
  ('Lifting the Exponent',        'lifting-the-exponent',        12, 'number-theory'),
  ('Pell Equations',              'pell-equations',              13, 'number-theory'),
  ('Floor and Ceiling Functions', 'floor-and-ceiling-functions', 14, 'number-theory')
) AS s(name, slug, ord, parent_slug)
JOIN categories p ON p.slug = s.parent_slug;