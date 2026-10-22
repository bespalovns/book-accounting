BEGIN;

SET LOCAL ROLE book_app;

CREATE TABLE authors (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    first_name VARCHAR(100) NOT NULL
        CHECK (length(btrim(first_name)) > 0),

    last_name VARCHAR(100) NOT NULL
        CHECK (length(btrim(last_name)) > 0)
);

CREATE TABLE publishers (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    name_publisher VARCHAR(200) NOT NULL
        CHECK (length(btrim(name_publisher)) > 0),

    address VARCHAR(300) NOT NULL
        CHECK (length(btrim(address)) > 0),

    site VARCHAR(300)
);

CREATE TABLE books (
    id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    author_id INTEGER NOT NULL
        REFERENCES authors(id) ON DELETE RESTRICT,

    publisher_id INTEGER NOT NULL
        REFERENCES publishers(id) ON DELETE RESTRICT,

    title VARCHAR(250) NOT NULL
        CHECK (length(btrim(title)) > 0),

    code VARCHAR(13) NOT NULL UNIQUE
        CHECK (
            code ~ '^([0-9]{13}|[0-9]{9}[0-9X])$'
        ),

    year_publish INTEGER NOT NULL
        CHECK (year_publish BETWEEN 1 AND 9999),

    count_page INTEGER NOT NULL
        CHECK (count_page > 0),

    price NUMERIC(10, 2) NOT NULL
        CHECK (price >= 0),

    hardcover BOOLEAN NOT NULL DEFAULT FALSE,

    abstract TEXT NOT NULL DEFAULT '',

    status BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_books_author_id
    ON books(author_id);

CREATE INDEX idx_books_publisher_id
    ON books(publisher_id);

COMMIT;