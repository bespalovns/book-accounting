from decimal import Decimal

import psycopg
from flask import (
    Blueprint,
    current_app,
    jsonify,
    request,
)
from psycopg import sql

from db import get_connection
from validators import ValidationError, validate_payload


api_bp = Blueprint("api", __name__, url_prefix="/api")


CONFIG = {
    "authors": {
        "table": "authors",
        "select": """
            SELECT t.*
            FROM authors AS t
        """,
        "order": "t.last_name, t.first_name, t.id",
    },
    "publishers": {
        "table": "publishers",
        "select": """
            SELECT t.*
            FROM publishers AS t
        """,
        "order": "t.name_publisher, t.id",
    },
    "books": {
        "table": "books",
        "select": """
            SELECT
                t.*,
                a.last_name || ' ' || a.first_name AS author_name,
                p.name_publisher AS publisher_name
            FROM books AS t
            JOIN authors AS a ON a.id = t.author_id
            JOIN publishers AS p ON p.id = t.publisher_id
        """,
        "order": "t.id",
    },
}


def serialize_row(row):
    return {
        key: str(value) if isinstance(value, Decimal) else value
        for key, value in row.items()
    }


def not_found():
    return jsonify({"message": "Запись не найдена."}), 404


@api_bp.errorhandler(ValidationError)
def handle_validation_error(error):
    return jsonify({"message": str(error)}), 400


@api_bp.errorhandler(psycopg.Error)
def handle_database_error(error):
    current_app.logger.error(
        "Ошибка PostgreSQL: SQLSTATE=%s",
        error.sqlstate,
        exc_info=(type(error), error, error.__traceback__),
    )

    # Нарушение уникальности.
    if error.sqlstate == "23505":
        return jsonify({
            "message": "Книга с таким ISBN уже существует."
        }), 409

    # Нарушение внешнего ключа.
    if error.sqlstate == "23503":
        if request.method == "DELETE":
            entity = (request.view_args or {}).get("entity")

            if entity == "authors":
                message = (
                    "Невозможно удалить автора: он связан с книгами. "
                    "Сначала назначьте книгам другого автора "
                    "или удалите связанные записи книг."
                )
            elif entity == "publishers":
                message = (
                    "Невозможно удалить издательство: "
                    "оно связано с книгами. "
                    "Сначала назначьте книгам другое издательство "
                    "или удалите связанные записи книг."
                )
            else:
                message = (
                    "Невозможно удалить запись: "
                    "на нее ссылаются другие данные."
                )

            return jsonify({"message": message}), 409

        return jsonify({
            "message": (
                "Выбранный автор или издательство не существует. "
                "Обновите страницу и выберите их заново."
            )
        }), 400

    if (
        error.sqlstate in ("23502", "23514")
        or isinstance(error, psycopg.DataError)
    ):
        return jsonify({
            "message": (
                "Данные не соответствуют ограничениям базы. "
                "Проверьте заполненные поля."
            )
        }), 400

    return jsonify({
        "message": (
            "Не удалось выполнить операцию с базой данных. "
            "Подробности находятся в терминале сервера."
        )
    }), 500


@api_bp.get("/<entity>")
def list_records(entity):
    config = CONFIG.get(entity)

    if config is None:
        return not_found()

    conditions = []
    params = []

    if entity == "books":
        search = request.args.get("search", "").strip()
        status = request.args.get("status", "")

        if len(search) > 250:
            raise ValidationError(
                "Поисковый запрос не должен превышать 250 символов."
            )

        if "\x00" in search:
            raise ValidationError(
                "Поисковый запрос содержит недопустимый символ."
            )

        if search:
            # % и _ трактуем как обычные символы поиска.
            escaped = (
                search.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            )

            pattern = f"%{escaped}%"

            conditions.append("""
                (
                    t.title ILIKE %s
                    OR t.code ILIKE %s
                    OR a.last_name ILIKE %s
                    OR a.first_name ILIKE %s
                )
            """)

            params.extend([pattern, pattern, pattern, pattern])

        if status:
            if status not in ("true", "false"):
                raise ValidationError(
                    "Некорректный фильтр наличия."
                )

            conditions.append("t.status = %s")
            params.append(status == "true")

    query = config["select"]

    if conditions:
        query += " WHERE " + " AND ".join(conditions)

    query += " ORDER BY " + config["order"]

    with get_connection() as conn:
        rows = conn.execute(query, params).fetchall()

    return jsonify([serialize_row(row) for row in rows])


@api_bp.get("/<entity>/<int:record_id>")
def get_record(entity, record_id):
    config = CONFIG.get(entity)

    if config is None:
        return not_found()

    query = config["select"] + " WHERE t.id = %s"

    with get_connection() as conn:
        row = conn.execute(query, (record_id,)).fetchone()

    if row is None:
        return not_found()

    return jsonify(serialize_row(row))


@api_bp.post("/<entity>")
def create_record(entity):
    config = CONFIG.get(entity)

    if config is None:
        return not_found()

    data = validate_payload(
        entity,
        request.get_json(silent=True),
    )

    columns = list(data.keys())

    query = sql.SQL("""
        INSERT INTO {} ({})
        VALUES ({})
        RETURNING *
    """).format(
        sql.Identifier(config["table"]),
        sql.SQL(", ").join(
            sql.Identifier(column) for column in columns
        ),
        sql.SQL(", ").join(
            sql.Placeholder() for _ in columns
        ),
    )

    with get_connection() as conn:
        row = conn.execute(
            query,
            [data[column] for column in columns],
        ).fetchone()

    return jsonify(serialize_row(row)), 201


@api_bp.put("/<entity>/<int:record_id>")
def update_record(entity, record_id):
    config = CONFIG.get(entity)

    if config is None:
        return not_found()

    data = validate_payload(
        entity,
        request.get_json(silent=True),
    )

    columns = list(data.keys())

    assignments = sql.SQL(", ").join(
        sql.SQL("{} = %s").format(sql.Identifier(column))
        for column in columns
    )

    query = sql.SQL("""
        UPDATE {}
        SET {}
        WHERE id = %s
        RETURNING *
    """).format(
        sql.Identifier(config["table"]),
        assignments,
    )

    values = [data[column] for column in columns]
    values.append(record_id)

    with get_connection() as conn:
        row = conn.execute(query, values).fetchone()

    if row is None:
        return not_found()

    return jsonify(serialize_row(row))


@api_bp.delete("/<entity>/<int:record_id>")
def delete_record(entity, record_id):
    config = CONFIG.get(entity)

    if config is None:
        return not_found()

    with get_connection() as conn:
        # Сначала проверяем существование записи.
        existing = conn.execute(
            sql.SQL("SELECT id FROM {} WHERE id = %s").format(
                sql.Identifier(config["table"])
            ),
            (record_id,),
        ).fetchone()

        if existing is None:
            return not_found()

        # Проверяем, используется ли автор или издательство.
        relation_fields = {
            "authors": "author_id",
            "publishers": "publisher_id",
        }

        if entity in relation_fields:
            result = conn.execute(
                sql.SQL("""
                    SELECT COUNT(*) AS book_count
                    FROM books
                    WHERE {} = %s
                """).format(
                    sql.Identifier(relation_fields[entity])
                ),
                (record_id,),
            ).fetchone()

            book_count = result["book_count"]

            if book_count > 0:
                if entity == "authors":
                    message = (
                        "Невозможно удалить автора: "
                        f"с ним связаны записи книг — {book_count}. "
                        "Сначала назначьте этим книгам другого автора "
                        "или удалите связанные записи книг."
                    )
                else:
                    message = (
                        "Невозможно удалить издательство: "
                        f"с ним связаны записи книг — {book_count}. "
                        "Сначала назначьте этим книгам другое издательство "
                        "или удалите связанные записи книг."
                    )

                return jsonify({
                    "message": message
                }), 409

        # Если связей нет, удаляем запись.
        query = sql.SQL("""
            DELETE FROM {}
            WHERE id = %s
            RETURNING id
        """).format(
            sql.Identifier(config["table"])
        )

        deleted = conn.execute(
            query,
            (record_id,),
        ).fetchone()

    if deleted is None:
        return not_found()

    return "", 204