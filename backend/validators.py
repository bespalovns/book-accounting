import re
from decimal import Decimal, InvalidOperation
from urllib.parse import urlsplit


class ValidationError(ValueError):
    pass


def text_field(data, name, label, max_length, required=True):
    value = data.get(name, "")

    if not isinstance(value, str):
        raise ValidationError(f"{label}: ожидается текст.")

    value = value.strip()

    if "\x00" in value:
        raise ValidationError(
            f"{label}: обнаружен недопустимый символ."
        )

    if required and not value:
        raise ValidationError(f"Заполните поле «{label}».")

    if len(value) > max_length:
        raise ValidationError(
            f"{label}: максимум {max_length} символов."
        )

    return value


def integer_field(data, name, label, minimum, maximum):
    value = data.get(name)

    if type(value) is not int:
        raise ValidationError(f"{label}: требуется целое число.")

    if not minimum <= value <= maximum:
        raise ValidationError(
            f"{label}: допустимы значения "
            f"от {minimum} до {maximum}."
        )

    return value


def boolean_field(data, name, label):
    value = data.get(name)

    if type(value) is not bool:
        raise ValidationError(
            f"{label}: требуется значение true или false."
        )

    return value


def validate_isbn(value):
    # Удаляем пробелы и дефисы, приводим X к верхнему регистру.
    code = re.sub(r"[\s-]", "", value).upper()

    if re.fullmatch(r"[0-9]{13}", code):
        if not code.startswith(("978", "979")):
            raise ValidationError(
                "ISBN-13 должен начинаться с 978 или 979."
            )

        total = sum(
            int(digit) * (1 if index % 2 == 0 else 3)
            for index, digit in enumerate(code[:12])
        )

        check_digit = (10 - total % 10) % 10

        if check_digit != int(code[-1]):
            raise ValidationError(
                "Неверная контрольная цифра ISBN-13."
            )

        return code

    if re.fullmatch(r"[0-9]{9}[0-9X]", code):
        digits = [
            10 if symbol == "X" else int(symbol)
            for symbol in code
        ]

        total = sum(
            digit * (10 - index)
            for index, digit in enumerate(digits)
        )

        if total % 11 != 0:
            raise ValidationError(
                "Неверная контрольная цифра ISBN-10."
            )

        return code

    raise ValidationError(
        "ISBN должен содержать 13 цифр "
        "либо 10 символов с возможным X в конце."
    )


def validate_price(value):
    if isinstance(value, bool) or not isinstance(
        value, (str, int, float)
    ):
        raise ValidationError("Цена должна быть числом.")

    try:
        price = Decimal(str(value).strip().replace(",", "."))
    except InvalidOperation:
        raise ValidationError("Укажите корректную цену.")

    if not price.is_finite():
        raise ValidationError("Укажите корректную цену.")

    if price < 0 or price > Decimal("99999999.99"):
        raise ValidationError(
            "Цена должна быть от 0 до 99 999 999,99."
        )

    rounded = price.quantize(Decimal("0.01"))

    if price != rounded:
        raise ValidationError(
            "В цене допускается не более двух знаков после запятой."
        )

    return rounded


def validate_payload(entity, data):
    if not isinstance(data, dict):
        raise ValidationError("Ожидается JSON-объект.")

    if entity == "authors":
        return {
            "first_name": text_field(
                data, "first_name", "Имя", 100
            ),
            "last_name": text_field(
                data, "last_name", "Фамилия", 100
            ),
        }

    if entity == "publishers":
        site = text_field(
            data, "site", "Сайт", 300, required=False
        )

        if site:
            try:
                parsed = urlsplit(site)
                valid = (
                    parsed.scheme in ("http", "https")
                    and bool(parsed.hostname)
                    and not any(char.isspace() for char in site)
                )
            except ValueError:
                valid = False

            if not valid:
                raise ValidationError(
                    "Сайт: укажите адрес вида https://example.com."
                )

        return {
            "name_publisher": text_field(
                data, "name_publisher", "Название издательства", 200
            ),
            "address": text_field(
                data, "address", "Адрес", 300
            ),
            "site": site or None,
        }

    if entity == "books":
        raw_code = text_field(data, "code", "ISBN", 40)

        return {
            "author_id": integer_field(
                data, "author_id", "Автор", 1, 2147483647
            ),
            "publisher_id": integer_field(
                data, "publisher_id", "Издательство", 1, 2147483647
            ),
            "title": text_field(
                data, "title", "Название книги", 250
            ),
            "code": validate_isbn(raw_code),
            "year_publish": integer_field(
                data, "year_publish", "Год издания", 1, 9999
            ),
            "count_page": integer_field(
                data, "count_page", "Количество страниц",
                1, 2147483647
            ),
            "price": validate_price(data.get("price")),
            "hardcover": boolean_field(
                data, "hardcover", "Твердый переплет"
            ),
            "abstract": text_field(
                data, "abstract", "Аннотация",
                10000, required=False
            ),
            "status": boolean_field(
                data, "status", "Наличие"
            ),
        }

    raise ValidationError("Неизвестный раздел.")