package ru.istz3401bespalov.bookaccounting

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {

    private const val BASE_URL = "http://10.0.2.2:5000"

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
        request("DELETE", entity, id)
    }

    private fun request(
        method: String,
        entity: String,
        id: Int? = null,
        data: JSONObject? = null
    ): String {
        require(entity in setOf("books", "authors", "publishers"))

        val path = if (id == null) entity else "$entity/$id"

        val connection = URL("$BASE_URL/api/$path")
            .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", "application/json")

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
                throw IOException(
                    "Сервер требует входа. Необходимо настроить авторизацию."
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
                    message.ifBlank { "Ошибка сервера: HTTP $status" }
                )
            }

            return response
        } finally {
            connection.disconnect()
        }
    }
}