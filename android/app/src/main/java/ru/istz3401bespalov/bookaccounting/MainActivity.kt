package ru.istz3401bespalov.bookaccounting

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import android.content.Intent

class MainActivity : AppCompatActivity() {

    private lateinit var sectionTitle: TextView
    private lateinit var sectionDescription: TextView
    private lateinit var dataText: TextView
    private lateinit var sectionButtons: List<AppCompatButton>

    private var selectedSection = 0

    // Запросы выполняются вне главного потока интерфейса.
    private val executor = Executors.newSingleThreadExecutor()

    // Помогает игнорировать ответ от ранее выбранного раздела.
    private var requestVersion = 0

    companion object {
        private const val KEY_SELECTED_SECTION = "selected_section"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )

            insets
        }

        sectionTitle = findViewById(R.id.sectionTitle)
        sectionDescription = findViewById(R.id.sectionDescription)
        dataText = findViewById(R.id.dataText)

        sectionButtons = listOf(
            findViewById<AppCompatButton>(R.id.booksButton),
            findViewById<AppCompatButton>(R.id.authorsButton),
            findViewById<AppCompatButton>(R.id.publishersButton)
        )

        sectionButtons.forEachIndexed { index, button ->
            button.setOnClickListener {
                when (index) {
                    1 -> {
                        startActivity(
                            Intent(this, AuthorsActivity::class.java)
                        )
                    }

                    else -> {
                        val entity = if (index == 0) {
                            "books"
                        } else {
                            "publishers"
                        }

                        startActivity(
                            Intent(this, CatalogActivity::class.java)
                                .putExtra("entity", entity)
                        )
                    }
                }
            }
        }

        selectedSection = savedInstanceState
            ?.getInt(KEY_SELECTED_SECTION, 0)
            ?.coerceIn(0, 2)
            ?: 0

        sectionTitle.text = "Управление каталогом"
        sectionDescription.text =
            "Выберите раздел для просмотра, добавления и редактирования записей."
        dataText.text = "Книги, авторы и издательства используют общую базу данных."
    }

    private fun showSection(index: Int) {
        selectedSection = index

        sectionTitle.setText(
            when (index) {
                1 -> R.string.section_authors
                2 -> R.string.section_publishers
                else -> R.string.section_books
            }
        )

        sectionDescription.text =
            "Для обновления списка нажмите кнопку раздела."

        sectionButtons.forEachIndexed { buttonIndex, button ->
            val isSelected = buttonIndex == index

            val color = if (isSelected) {
                "#294B36"
            } else {
                "#426E52"
            }

            button.supportBackgroundTintList =
                ColorStateList.valueOf(Color.parseColor(color))

            button.isSelected = isSelected
        }

        loadData(index)
    }

    private fun loadData(section: Int) {
        val entity = when (section) {
            1 -> "authors"
            2 -> "publishers"
            else -> "books"
        }

        requestVersion += 1
        val currentRequest = requestVersion

        dataText.text = "Загрузка…"

        executor.execute {
            // Пропускаем устаревшие задания, ожидавшие в очереди.
            if (Thread.currentThread().isInterrupted) {
                return@execute
            }

            val result = try {
                val items = ApiClient.getList(entity)
                formatItems(items, section)
            } catch (error: SocketTimeoutException) {
                "Сервер не ответил вовремя. Повторите загрузку."
            } catch (error: ConnectException) {
                "Не удалось подключиться к серверу.\n\n" +
                        "Проверьте, что app.py запущен в PyCharm " +
                        "и использует порт 5000."
            } catch (error: Exception) {
                "Не удалось загрузить данные.\n\n" +
                        (error.message ?: "Неизвестная ошибка")
            }

            runOnUiThread {
                if (
                    !isDestroyed &&
                    !isFinishing &&
                    currentRequest == requestVersion
                ) {
                    dataText.text = result
                }
            }
        }
    }

    private fun formatItems(items: JSONArray, section: Int): String {
        if (items.length() == 0) {
            return "Записей пока нет."
        }

        return (0 until items.length()).joinToString("\n\n") { index ->
            val item = items.getJSONObject(index)

            when (section) {
                1 -> {
                    val lastName = item.optString("last_name")
                    val firstName = item.optString("first_name")

                    "${index + 1}. $lastName $firstName"
                }

                2 -> {
                    val name = item.optString("name_publisher")
                    val address = item.optString("address")

                    "${index + 1}. $name\nАдрес: $address"
                }

                else -> {
                    val title = item.optString("title")
                    val author = item.optString("author_name")
                    val publisher = item.optString("publisher_name")
                    val year = item.optInt("year_publish")
                    val price = item.optString("price")
                    val status = if (item.optBoolean("status")) {
                        "В наличии"
                    } else {
                        "Нет в наличии"
                    }

                    """
                    ${index + 1}. $title
                    Автор: $author
                    Издательство: $publisher
                    Год: $year
                    Цена: $price
                    Статус: $status
                    """.trimIndent()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_SELECTED_SECTION, selectedSection)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        // Ограниченные по времени запросы завершатся,
        // но результат уже не попадет в закрытый экран.
        requestVersion += 1
        executor.shutdownNow()
        super.onDestroy()
    }
}