import psycopg
from flask import Blueprint, current_app, jsonify, request

from db import get_connection


authors_bp = Blueprint(
    "authors",
    __name__,
    url_prefix="/api/authors",
)


@authors_bp.get("")
def get_authors():
    try:
        with get_connection() as conn:
            authors = conn.execute(
                """
                SELECT id, first_name, last_name
                FROM authors
                ORDER BY last_name, first_name, id
                """
            ).fetchall()

        return jsonify(authors)

    except psycopg.Error:
        current_app.logger.exception("Ошибка загрузки авторов")

        return jsonify({
            "message": "Не удалось загрузить авторов."
        }), 500


@authors_bp.post("")
def add_author():
    data = request.get_json(silent=True)

    if not isinstance(data, dict):
        return jsonify({
            "message": "Ожидается JSON-объект с данными автора."
        }), 400

    first_name = data.get("first_name")
    last_name = data.get("last_name")

    if not isinstance(first_name, str):
        return jsonify({
            "message": "Имя должно быть строкой."
        }), 400

    if not isinstance(last_name, str):
        return jsonify({
            "message": "Фамилия должна быть строкой."
        }), 400

    first_name = first_name.strip()
    last_name = last_name.strip()

    if not first_name or not last_name:
        return jsonify({
            "message": "Заполните имя и фамилию."
        }), 400

    if len(first_name) > 100 or len(last_name) > 100:
        return jsonify({
            "message": "Имя и фамилия не должны превышать 100 символов."
        }), 400

    if "\x00" in first_name or "\x00" in last_name:
        return jsonify({
            "message": "Имя или фамилия содержат недопустимый символ."
        }), 400

    try:
        with get_connection() as conn:
            author = conn.execute(
                """
                INSERT INTO authors (first_name, last_name)
                VALUES (%s, %s)
                RETURNING id, first_name, last_name
                """,
                (first_name, last_name),
            ).fetchone()

        return jsonify(author), 201

    except psycopg.Error:
        current_app.logger.exception("Ошибка добавления автора")

        return jsonify({
            "message": "Не удалось сохранить автора."
        }), 500