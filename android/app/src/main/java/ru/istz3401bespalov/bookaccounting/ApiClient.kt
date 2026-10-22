package ru.istz3401bespalov.bookaccounting

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {

    private const val BASE_URL =
        "https://book-accounting.onrender.com"

    // Данные входа хранятся только в памяти приложения.
    @Volatile
    private var authorizationHeader: String? = null

    // Данные входа, которые уже были приняты сервером.
    @Volatile
    private var verifiedAuthorizationHeader: String? = null

    fun hasCredentials(): Boolean {
        return authorizationHeader != null
    }

    fun isAuthenticated(): Boolean {
        val current = authorizationHeader

        return current != null &&
                current == verifiedAuthorizationHeader
    }

    fun setCredentials(username: String, password: String) {
        require(username.isNotBlank()) {
            "Введите логин."
        }

        require(':' !in username) {
            "Логин не должен содержать двоеточие."
        }

        require(password.isNotEmpty()) {
            "Введите пароль."
        }

        val credentials = "$username:$password"
            .toByteArray(Charsets.UTF_8)

        // Новые данные ещё не проверены сервером.
        verifiedAuthorizationHeader = null

        authorizationHeader = "Basic " + Base64.encodeToString(
            credentials,
            Base64.NO_WRAP
        )
    }

    fun getList(entity: String): JSONArray {
        return JSONArray(request("GET", entity))
    }

    fun save(
        entity: String,
        id: Int?,
        data: JSONObject
    ) {
        request(
            method = if (id == null) "POST" else "PUT",
            entity = entity,
            id = id,
            data = data
        )
    }

    fun delete(entity: String, id: Int) {
        request(
            method = "DELETE",
            entity = entity,
            id = id
        )
    }

    private fun request(
        method: String,
        entity: String,
        id: Int? = null,
        data: JSONObject? = null
    ): String {
        require(entity in setOf("books", "authors", "publishers")) {
            "Неизвестный раздел."
        }

        val authorization = authorizationHeader
            ?: throw IOException(
                "Вернитесь на главный экран и введите логин и пароль."
            )

        val path = if (id == null) {
            entity
        } else {
            "$entity/$id"
        }

        val connection = URL("$BASE_URL/api/$path")
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.instanceFollowRedirects = false
            connection.useCaches = false

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "Authorization",
                authorization
            )

            if (data != null) {
                connection.doOutput = true

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
                )

                connection.outputStream
                    .bufferedWriter(Charsets.UTF_8)
                    .use { writer ->
                        writer.write(data.toString())
                    }
            }

            val status = connection.responseCode

            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                if (authorizationHeader == authorization) {
                    verifiedAuthorizationHeader = null
                }

                throw IOException(
                    "Неверный логин или пароль. " +
                            "Вернитесь на главный экран и введите данные заново."
                )
            }

            if (status in 300..399) {
                throw IOException(
                    "Сервер перенаправляет запрос. Проверьте адрес сервера."
                )
            }

            val stream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = stream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (status !in 200..299) {
                val message = runCatching {
                    JSONObject(response).optString("message")
                }.getOrDefault("")

                throw IOException(
                    message.ifBlank {
                        "Ошибка сервера: HTTP $status"
                    }
                )
            }

            // Сервер успешно выполнил защищённый запрос.
            // Не подтверждаем устаревшие данные, если их уже изменили.
            if (authorizationHeader == authorization) {
                verifiedAuthorizationHeader = authorization
            }

            return response
        } finally {
            connection.disconnect()
        }
    }
}