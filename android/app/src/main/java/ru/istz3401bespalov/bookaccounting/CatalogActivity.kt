package ru.istz3401bespalov.bookaccounting

import androidx.lifecycle.ViewModelProvider
import android.text.Editable
import android.text.TextWatcher
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal

class CatalogActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var onlyAvailable: CheckBox

    private var currentRecords = emptyList<JSONObject>()
    private data class Choice(
        val id: Int,
        val label: String
    ) {
        override fun toString(): String = label
    }

    private lateinit var entity: String

    private lateinit var root: ScrollView
    private lateinit var form: LinearLayout
    private lateinit var rows: LinearLayout
    private lateinit var status: TextView
    private lateinit var formTitle: TextView
    private lateinit var refreshButton: AppCompatButton
    private lateinit var saveButton: AppCompatButton
    private lateinit var deleteButton: AppCompatButton

    private lateinit var authorSpinner: Spinner
    private lateinit var publisherSpinner: Spinner

    private var editingId: Int? = null
    private var busy = false
    private var referencesReady = false

    private var pendingAuthorId = 0
    private var pendingPublisherId = 0

    private var authors = emptyList<Choice>()
    private var publishers = emptyList<Choice>()

    private lateinit var model: ScreenModel

    private var seenDataVersion = -1
    private var seenChangeVersion = 0

    private val isBooks: Boolean
        get() = entity == "books"

    private val inputIds = listOf(
        R.id.publisherName,
        R.id.publisherAddress,
        R.id.publisherSite,
        R.id.bookTitle,
        R.id.bookIsbn,
        R.id.bookYear,
        R.id.bookPages,
        R.id.bookPrice,
        R.id.bookAbstract
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        entity = intent.getStringExtra("entity") ?: "books"

        if (entity !in setOf("books", "publishers")) {
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_catalog)

        root = findViewById(R.id.catalogRoot)
        form = findViewById(R.id.catalogForm)
        rows = findViewById(R.id.catalogRows)
        status = findViewById(R.id.catalogStatus)
        formTitle = findViewById(R.id.catalogFormTitle)
        refreshButton = findViewById(R.id.catalogRefresh)
        saveButton = findViewById(R.id.catalogSave)
        deleteButton = findViewById(R.id.catalogDelete)

        authorSpinner = findViewById(R.id.bookAuthor)
        publisherSpinner = findViewById(R.id.bookPublisher)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            val keyboard = insets.getInsets(
                WindowInsetsCompat.Type.ime()
            )

            view.setPadding(
                bars.left,
                bars.top,
                bars.right,
                maxOf(bars.bottom, keyboard.bottom)
            )
            insets
        }

        findViewById<TextView>(R.id.catalogTitle).text =
            if (isBooks) "Книги" else "Издательства"

        findViewById<View>(R.id.bookFields).visibility =
            if (isBooks) View.VISIBLE else View.GONE

        findViewById<View>(R.id.publisherFields).visibility =
            if (isBooks) View.GONE else View.VISIBLE

        if (savedInstanceState?.containsKey("editing_id") == true) {
            editingId = savedInstanceState.getInt("editing_id")
        }

        pendingAuthorId =
            savedInstanceState?.getInt("author_id") ?: 0

        pendingPublisherId =
            savedInstanceState?.getInt("publisher_id") ?: 0

        updateFormTitle()

        refreshButton.setOnClickListener { loadData() }
        saveButton.setOnClickListener { saveRecord() }
        deleteButton.setOnClickListener { confirmDelete() }

        findViewById<AppCompatButton>(R.id.catalogNew)
            .setOnClickListener { clearForm() }

        searchInput = findViewById(R.id.catalogSearch)
        onlyAvailable = findViewById(R.id.catalogAvailableOnly)

        onlyAvailable.visibility =
            if (isBooks) View.VISIBLE else View.GONE

        if (!isBooks) {
            searchInput.hint = "Поиск издательства по названию или адресу"
        }

        searchInput.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(
                text: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(
                text: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                renderRows(currentRecords)
            }

            override fun afterTextChanged(text: Editable?) {
            }
        })

        onlyAvailable.setOnCheckedChangeListener { _, _ ->
            renderRows(currentRecords)
        }
        seenChangeVersion =
            savedInstanceState?.getInt("change_version") ?: 0

        model = ViewModelProvider(this)[ScreenModel::class.java]

        if (model.entity.isEmpty()) {
            seenChangeVersion = 0
        }

        model.initialize(
            entity,
            savedInstanceState?.getBoolean("pending_write") == true
        )
    }
    private fun loadData() {
        model.load()
    }

    private fun input(id: Int): EditText = findViewById(id)

    private fun text(id: Int): String =
        input(id).text.toString().trim()

    private fun checked(id: Int): Boolean =
        findViewById<CheckBox>(id).isChecked

    private fun selectedId(spinner: Spinner): Int =
        (spinner.selectedItem as? Choice)?.id ?: 0

    private fun objects(array: JSONArray): List<JSONObject> =
        (0 until array.length()).map { array.getJSONObject(it) }

    private fun setEnabledTree(view: View, enabled: Boolean) {
        view.isEnabled = enabled

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                setEnabledTree(view.getChildAt(index), enabled)
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        refreshButton.isEnabled = !value
        setEnabledTree(form, !value)
        setEnabledTree(rows, !value)

        saveButton.isEnabled =
            !value && (!isBooks || referencesReady)
    }

    private fun updateFormTitle() {
        formTitle.text = when {
            editingId != null -> "Редактирование записи"
            isBooks -> "Новая книга"
            else -> "Новое издательство"
        }

        deleteButton.visibility =
            if (editingId == null) View.GONE else View.VISIBLE
    }

    private fun fillSpinner(
        spinner: Spinner,
        choices: List<Choice>,
        prompt: String,
        selected: Int
    ) {
        val all = listOf(Choice(0, prompt)) + choices

        val adapter = ArrayAdapter(
            this,
            R.layout.item_catalog,
            all
        )
        adapter.setDropDownViewResource(R.layout.item_catalog)
        spinner.adapter = adapter

        val index = all.indexOfFirst { it.id == selected }
        spinner.setSelection(if (index >= 0) index else 0)
    }

    private fun renderRows(records: List<JSONObject>) {
        rows.removeAllViews()

        val query = searchInput.text.toString().trim().lowercase()

        val fields = if (isBooks) {
            listOf(
                "title",
                "author_name",
                "publisher_name",
                "code"
            )
        } else {
            listOf(
                "name_publisher",
                "address",
                "site"
            )
        }

        val filtered = records.filter { record ->
            val matchesSearch =
                query.isEmpty() || fields.any { field ->
                    val value = if (record.isNull(field)) {
                        ""
                    } else {
                        record.optString(field)
                    }

                    value.lowercase().contains(query)
                }

            val matchesAvailability =
                !isBooks ||
                        !onlyAvailable.isChecked ||
                        record.optBoolean("status")

            matchesSearch && matchesAvailability
        }

        findViewById<TextView>(R.id.catalogResultCount).text =
            getString(
                R.string.catalog_result_count,
                filtered.size,
                records.size
            )

        filtered.forEach { record ->
            val row = layoutInflater.inflate(
                R.layout.item_catalog,
                rows,
                false
            ) as TextView

            row.text = if (isBooks) {
                val available = if (record.optBoolean("status")) {
                    "В наличии"
                } else {
                    "Нет в наличии"
                }

                """
            ${record.optString("title")}
            Автор: ${record.optString("author_name")}
            Издательство: ${record.optString("publisher_name")}
            ISBN: ${record.optString("code")}
            Год: ${record.optInt("year_publish")}
            Цена: ${record.optString("price")}
            $available
            """.trimIndent()
            } else {
                val site = if (record.isNull("site")) {
                    "не указан"
                } else {
                    record.optString("site").ifBlank {
                        "не указан"
                    }
                }

                """
            ${record.optString("name_publisher")}
            Адрес: ${record.optString("address")}
            Сайт: $site
            """.trimIndent()
            }

            row.isEnabled = !busy

            row.setOnClickListener {
                if (!busy) {
                    editRecord(record)
                }
            }

            rows.addView(row)
        }
    }

    private fun editRecord(record: JSONObject) {
        editingId = record.getInt("id")

        inputIds.forEach { input(it).error = null }

        if (isBooks) {
            input(R.id.bookTitle).setText(record.optString("title"))
            input(R.id.bookIsbn).setText(record.optString("code"))
            input(R.id.bookYear).setText(record.optString("year_publish"))
            input(R.id.bookPages).setText(record.optString("count_page"))
            input(R.id.bookPrice).setText(record.optString("price"))
            input(R.id.bookAbstract).setText(
                if (record.isNull("abstract")) "" else record.optString("abstract")
            )

            findViewById<CheckBox>(R.id.bookHardcover).isChecked =
                record.optBoolean("hardcover")

            findViewById<CheckBox>(R.id.bookAvailable).isChecked =
                record.optBoolean("status")

            pendingAuthorId = record.getInt("author_id")
            pendingPublisherId = record.getInt("publisher_id")

            fillSpinner(
                authorSpinner,
                authors,
                "Выберите автора",
                pendingAuthorId
            )

            fillSpinner(
                publisherSpinner,
                publishers,
                "Выберите издательство",
                pendingPublisherId
            )
        } else {
            input(R.id.publisherName)
                .setText(record.optString("name_publisher"))

            input(R.id.publisherAddress)
                .setText(record.optString("address"))

            input(R.id.publisherSite).setText(
                if (record.isNull("site")) "" else record.optString("site")
            )
        }

        updateFormTitle()
        status.text = "Измените данные и нажмите «Сохранить»."
        root.post { root.smoothScrollTo(0, 0) }
    }

    private fun clearForm() {
        editingId = null

        inputIds.forEach {
            input(it).text.clear()
            input(it).error = null
        }

        pendingAuthorId = 0
        pendingPublisherId = 0

        if (isBooks) {
            authorSpinner.setSelection(0)
            publisherSpinner.setSelection(0)
            findViewById<CheckBox>(R.id.bookHardcover).isChecked = false
            findViewById<CheckBox>(R.id.bookAvailable).isChecked = true
        }

        updateFormTitle()
    }

    private fun required(id: Int, label: String): String {
        val value = text(id)

        if (value.isBlank()) {
            input(id).error = "Обязательное поле"
            input(id).requestFocus()
            throw IllegalArgumentException("Заполните поле «$label».")
        }

        return value
    }

    private fun positiveInt(
        id: Int,
        label: String,
        maximum: Int = Int.MAX_VALUE
    ): Int {
        val value = text(id).toIntOrNull()

        if (value == null || value < 1 || value > maximum) {
            input(id).error = "Введите число от 1 до $maximum"
            input(id).requestFocus()
            throw IllegalArgumentException(
                "$label: укажите целое число от 1 до $maximum."
            )
        }

        return value
    }

    private fun makePayload(): JSONObject {
        if (!isBooks) {
            return JSONObject()
                .put(
                    "name_publisher",
                    required(R.id.publisherName, "Название издательства")
                )
                .put(
                    "address",
                    required(R.id.publisherAddress, "Адрес")
                )
                .put("site", text(R.id.publisherSite))
        }

        val title = required(R.id.bookTitle, "Название книги")
        val authorId = selectedId(authorSpinner)
        val publisherId = selectedId(publisherSpinner)

        require(authorId > 0) { "Выберите автора." }
        require(publisherId > 0) { "Выберите издательство." }

        val isbn = required(R.id.bookIsbn, "ISBN")
        val year = positiveInt(R.id.bookYear, "Год издания", 9999)
        val pages = positiveInt(R.id.bookPages, "Количество страниц")

        val priceText = required(R.id.bookPrice, "Цена")
            .replace(',', '.')

        val price = priceText.toBigDecimalOrNull()

        require(
            price != null &&
                    price >= BigDecimal.ZERO &&
                    price <= BigDecimal("99999999.99") &&
                    price.stripTrailingZeros().scale() <= 2
        ) {
            "Цена: число от 0 до 99 999 999,99, " +
                    "не более двух знаков после запятой."
        }

        return JSONObject()
            .put("title", title)
            .put("author_id", authorId)
            .put("publisher_id", publisherId)
            .put("code", isbn)
            .put("year_publish", year)
            .put("count_page", pages)
            .put("price", priceText)
            .put("hardcover", checked(R.id.bookHardcover))
            .put("abstract", text(R.id.bookAbstract))
            .put("status", checked(R.id.bookAvailable))
    }

    private fun saveRecord() {
        if (busy) return

        inputIds.forEach { input(it).error = null }

        val payload = try {
            makePayload()
        } catch (error: IllegalArgumentException) {
            status.text = error.message
            root.smoothScrollTo(0, 0)
            return
        }

        val id = editingId

        model.save(id, payload)
    }

    private fun confirmDelete() {
        if (busy) return

        val id = editingId ?: return

        AlertDialog.Builder(this)
            .setTitle("Удалить запись?")
            .setMessage(
                "Удалить выбранную запись из базы? " +
                        "Отменить удаление будет нельзя."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                model.delete(id)
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        editingId?.let { outState.putInt("editing_id", it) }

        if (isBooks) {
            outState.putInt(
                "author_id",
                if (referencesReady) {
                    selectedId(authorSpinner)
                } else {
                    pendingAuthorId
                }
            )

            outState.putInt(
                "publisher_id",
                if (referencesReady) {
                    selectedId(publisherSpinner)
                } else {
                    pendingPublisherId
                }
            )
        }
        outState.putInt("change_version", seenChangeVersion)
        outState.putBoolean("pending_write", model.writing)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()

        if (!::model.isInitialized) return

        model.onChanged = { renderState() }
        renderState()
    }

    override fun onPause() {
        if (::model.isInitialized) {
            model.onChanged = null
        }

        super.onPause()
    }

    private fun renderState() {
        if (seenChangeVersion != model.changeVersion) {
            clearForm()
            seenChangeVersion = model.changeVersion
        }

        if (seenDataVersion != model.dataVersion) {
            if (isBooks && referencesReady) {
                pendingAuthorId = selectedId(authorSpinner)
                pendingPublisherId = selectedId(publisherSpinner)
            }

            authors = objects(model.authors).map { item ->
                Choice(
                    item.getInt("id"),
                    "${item.getString("last_name")} " +
                            item.getString("first_name")
                )
            }.sortedBy { it.label.lowercase() }

            publishers = objects(model.publishers).map { item ->
                Choice(
                    item.getInt("id"),
                    item.getString("name_publisher")
                )
            }.sortedBy { it.label.lowercase() }

            if (isBooks) {
                fillSpinner(
                    authorSpinner,
                    authors,
                    "Выберите автора",
                    pendingAuthorId
                )

                fillSpinner(
                    publisherSpinner,
                    publishers,
                    "Выберите издательство",
                    pendingPublisherId
                )

                referencesReady = model.dataVersion > 0
            }

            currentRecords = objects(model.records).sortedBy { item ->
                item.optString(
                    if (isBooks) "title" else "name_publisher"
                ).lowercase()
            }

            seenDataVersion = model.dataVersion
        }

        status.text = model.message

        if (
            isBooks &&
            model.dataVersion > 0 &&
            (authors.isEmpty() || publishers.isEmpty())
        ) {
            status.append(
                "\nСначала добавьте автора и издательство."
            )
        }

        renderRows(currentRecords)
        setBusy(model.busy)
    }

}