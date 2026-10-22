import hmac
import os
from urllib.parse import urlsplit

import psycopg
from flask import Flask, jsonify, render_template, request

from db import get_connection
from api import api_bp


app = Flask(__name__)
app.json.ensure_ascii = False
app.config["MAX_CONTENT_LENGTH"] = 1024 * 1024

app.register_blueprint(api_bp)

username = os.getenv("APP_USERNAME", "")
password = os.getenv("APP_PASSWORD", "")

production = (
    os.getenv("APP_ENV") == "production"
    or bool(os.getenv("RENDER"))
)

if bool(username) != bool(password):
    raise RuntimeError(
        "Укажите одновременно APP_USERNAME и APP_PASSWORD."
    )

if production and (not username or len(password) < 16):
    raise RuntimeError(
        "Для размещения нужен логин и пароль минимум 16 символов."
    )

if ":" in username:
    raise RuntimeError("Логин не должен содержать двоеточие.")


@app.before_request
def protect_access():
    if request.endpoint in ("static", "health"):
        return None

    if username:
        auth = request.authorization

        supplied_user = (
            (auth.username or "")
            if auth and auth.type == "basic"
            else ""
        )

        supplied_password = (
            (auth.password or "")
            if auth and auth.type == "basic"
            else ""
        )

        valid_user = hmac.compare_digest(
            supplied_user.encode("utf-8"),
            username.encode("utf-8"),
        )

        valid_password = hmac.compare_digest(
            supplied_password.encode("utf-8"),
            password.encode("utf-8"),
        )

        if not (valid_user and valid_password):
            response = jsonify(
                message="Введите правильный логин и пароль приложения."
            )
            response.status_code = 401
            response.headers["WWW-Authenticate"] = (
                'Basic realm="Book Accounting", charset="UTF-8"'
            )
            return response

    if request.method in ("POST", "PUT", "PATCH", "DELETE"):
        origin = request.headers.get("Origin")
        public_url = os.getenv("PUBLIC_URL", "").rstrip("/")

        if origin:
            if public_url:
                valid_origin = origin.rstrip("/") == public_url
            else:
                valid_origin = urlsplit(origin).netloc == request.host

            if not valid_origin:
                return jsonify(
                    message="Запрос с другого сайта запрещен."
                ), 403

        if request.headers.get("Sec-Fetch-Site") == "cross-site":
            return jsonify(
                message="Запрос с другого сайта запрещен."
            ), 403

        if (
            request.method in ("POST", "PUT", "PATCH")
            and not request.is_json
        ):
            return jsonify(message="Ожидается JSON."), 415


@app.get("/")
def index():
    return render_template("index.html")


@app.get("/healthz")
def health():
    return jsonify(status="ok")


@app.get("/api/health")
def check_database():
    try:
        with get_connection() as conn:
            conn.execute("SELECT 1").fetchone()

        return jsonify(status="ok")

    except psycopg.Error:
        app.logger.exception("Ошибка подключения к базе")
        return jsonify(
            status="error",
            message="Нет подключения к базе данных.",
        ), 503


@app.errorhandler(413)
def too_large(error):
    return jsonify(message="Слишком большой запрос."), 413


if __name__ == "__main__":
    app.run()