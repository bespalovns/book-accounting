from db import get_connection


def main():
    with get_connection() as connection:
        with connection.cursor() as cursor:
            cursor.execute("""
                SELECT 'authors' AS table_name, COUNT(*) AS total
                FROM public.authors
                UNION ALL
                SELECT 'publishers', COUNT(*)
                FROM public.publishers
                UNION ALL
                SELECT 'books', COUNT(*)
                FROM public.books;
            """)

            for row in cursor.fetchall():
                print(f"{row['table_name']}: {row['total']}")


if __name__ == "__main__":
    main()