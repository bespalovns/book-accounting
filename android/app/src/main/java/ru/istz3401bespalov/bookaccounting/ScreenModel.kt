package ru.istz3401bespalov.bookaccounting

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class ScreenModel : ViewModel() {

    var entity = ""
        private set

    var busy = false
        private set

    var writing = false
        private set

    var records = JSONArray()
        private set

    var authors = JSONArray()
        private set

    var publishers = JSONArray()
        private set

    var dataVersion = 0
        private set

    var changeVersion = 0
        private set

    var message = ""
        private set

    var onChanged: (() -> Unit)? = null

    private var cleared = false

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun initialize(
        section: String,
        uncertainWrite: Boolean = false
    ) {
        if (entity.isNotEmpty()) return

        entity = section

        load(
            if (uncertainWrite) {
                "Предыдущая операция могла завершиться. " +
                        "Проверьте список перед повторной отправкой."
            } else {
                ""
            }
        )
    }

    fun load(prefix: String = "") {
        runRequest(prefix, null)
    }

    fun save(id: Int?, payload: JSONObject) {
        val section = entity
        val body = JSONObject(payload.toString())

        runRequest("Изменения сохранены.") {
            ApiClient.save(section, id, body)
        }
    }

    fun delete(id: Int) {
        val section = entity

        runRequest("Запись удалена.") {
            ApiClient.delete(section, id)
        }
    }

    private fun runRequest(
        successMessage: String,
        mutation: (() -> Unit)?
    ) {
        if (busy || cleared) return

        busy = true
        writing = mutation != null

        message = if (writing) {
            "Сохранение…"
        } else {
            "Загрузка…"
        }

        onChanged?.invoke()

        val section = entity

        executor.execute {
            var committed = false

            var loaded: Triple<
                    JSONArray,
                    JSONArray,
                    JSONArray
                    >? = null

            var failure: Exception? = null

            try {
                mutation?.invoke()
                committed = mutation != null

                val list = ApiClient.getList(section)

                val loadedAuthors = if (section == "books") {
                    ApiClient.getList("authors")
                } else {
                    JSONArray()
                }

                val loadedPublishers = if (section == "books") {
                    ApiClient.getList("publishers")
                } else {
                    JSONArray()
                }

                loaded = Triple(
                    list,
                    loadedAuthors,
                    loadedPublishers
                )
            } catch (error: Exception) {
                failure = error
            }

            main.post {
                if (!cleared) {
                    if (committed) {
                        changeVersion++
                    }

                    loaded?.let { data ->
                        records = data.first
                        authors = data.second
                        publishers = data.third
                        dataVersion++
                    }

                    val error = failure

                    message = when {
                        error == null -> {
                            listOf(
                                successMessage,
                                "Записей: ${records.length()}."
                            )
                                .filter { it.isNotBlank() }
                                .joinToString("\n")
                        }

                        committed -> {
                            "$successMessage\n" +
                                    "Не удалось обновить список: " +
                                    error.message
                        }

                        mutation != null -> {
                            "${error.message ?: "Ошибка запроса."}\n" +
                                    "При потере связи обновите список " +
                                    "перед повторной отправкой."
                        }

                        else -> {
                            listOf(
                                successMessage,
                                error.message ?: "Ошибка подключения."
                            )
                                .filter { it.isNotBlank() }
                                .joinToString("\n")
                        }
                    }

                    busy = false
                    writing = false

                    onChanged?.invoke()
                }
            }
        }
    }

    override fun onCleared() {
        cleared = true
        onChanged = null
        executor.shutdown()
        super.onCleared()
    }
}