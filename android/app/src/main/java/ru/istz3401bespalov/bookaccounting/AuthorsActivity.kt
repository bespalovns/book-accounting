package ru.istz3401bespalov.bookaccounting

import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject

class AuthorsActivity : AppCompatActivity() {

    private lateinit var model: ScreenModel

    private var seenDataVersion = -1
    private var seenChangeVersion = 0

    private lateinit var firstNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var formTitle: TextView
    private lateinit var statusText: TextView

    private lateinit var saveButton: AppCompatButton
    private lateinit var clearButton: AppCompatButton
    private lateinit var deleteButton: AppCompatButton
    private lateinit var refreshButton: AppCompatButton

    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<Author>

    private var editingId: Int? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_authors)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.authorsRoot)
        ) { view, insets ->
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

        firstNameInput = findViewById(R.id.authorFirstName)
        lastNameInput = findViewById(R.id.authorLastName)
        formTitle = findViewById(R.id.authorFormTitle)
        statusText = findViewById(R.id.authorsStatus)

        saveButton = findViewById(R.id.saveAuthorButton)
        clearButton = findViewById(R.id.clearAuthorButton)
        deleteButton = findViewById(R.id.deleteAuthorButton)
        refreshButton = findViewById(R.id.refreshAuthorsButton)

        listView = findViewById(R.id.authorsList)

        adapter = ArrayAdapter(
            this,
            R.layout.item_author,
            mutableListOf<Author>()
        )
        listView.adapter = adapter

        if (savedInstanceState?.containsKey("editing_id") == true) {
            editingId = savedInstanceState.getInt("editing_id")
        }
        updateFormTitle()

        listView.setOnItemClickListener { _, _, position, _ ->
            if (!busy) {
                adapter.getItem(position)?.let { author ->
                    editingId = author.id
                    firstNameInput.setText(author.firstName)
                    lastNameInput.setText(author.lastName)
                    firstNameInput.error = null
                    lastNameInput.error = null
                    updateFormTitle()

                    statusText.text =
                        "Измените данные и нажмите «Сохранить»."
                }
            }
        }

        saveButton.setOnClickListener { saveAuthor() }
        clearButton.setOnClickListener { clearForm() }
        refreshButton.setOnClickListener { loadAuthors() }
        deleteButton.setOnClickListener { confirmDelete() }

        seenChangeVersion =
            savedInstanceState?.getInt("change_version") ?: 0

        model = ViewModelProvider(this)[ScreenModel::class.java]

        if (model.entity.isEmpty()) {
            seenChangeVersion = 0
        }

        model.initialize(
            "authors",
            savedInstanceState?.getBoolean("pending_write") == true
        )
    }

    private fun updateFormTitle() {
        formTitle.text = if (editingId == null) {
            "Новый автор"
        } else {
            "Редактирование автора"
        }

        deleteButton.visibility = if (editingId == null) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun clearForm() {
        editingId = null
        firstNameInput.text.clear()
        lastNameInput.text.clear()
        firstNameInput.error = null
        lastNameInput.error = null
        updateFormTitle()
    }

    private fun setBusy(value: Boolean) {
        busy = value

        saveButton.isEnabled = !value
        clearButton.isEnabled = !value
        deleteButton.isEnabled = !value
        refreshButton.isEnabled = !value
        firstNameInput.isEnabled = !value
        lastNameInput.isEnabled = !value
        listView.isEnabled = !value
    }

    override fun onResume() {
        super.onResume()

        model.onChanged = { renderState() }
        renderState()
    }

    override fun onPause() {
        model.onChanged = null
        super.onPause()
    }

    private fun renderState() {
        if (seenChangeVersion != model.changeVersion) {
            clearForm()
            seenChangeVersion = model.changeVersion
        }

        if (seenDataVersion != model.dataVersion) {
            val json = model.records

            val records = (0 until json.length()).map { index ->
                val item = json.getJSONObject(index)

                Author(
                    id = item.getInt("id"),
                    firstName = item.getString("first_name"),
                    lastName = item.getString("last_name")
                )
            }.sortedWith(
                compareBy<Author> { it.lastName.lowercase() }
                    .thenBy { it.firstName.lowercase() }
            )

            adapter.clear()
            adapter.addAll(records)

            seenDataVersion = model.dataVersion
        }

        setBusy(model.busy)
        statusText.text = model.message
    }

    private fun loadAuthors() {
        model.load()
    }

    private fun saveAuthor() {
        if (busy) return

        val firstName = firstNameInput.text.toString().trim()
        val lastName = lastNameInput.text.toString().trim()

        firstNameInput.error = null
        lastNameInput.error = null

        if (lastName.isBlank()) {
            lastNameInput.error = "Введите фамилию"
            lastNameInput.requestFocus()
            return
        }

        if (firstName.isBlank()) {
            firstNameInput.error = "Введите имя"
            firstNameInput.requestFocus()
            return
        }

        val id = editingId

        val data = JSONObject()
            .put("first_name", firstName)
            .put("last_name", lastName)

        model.save(id, data)
    }

    private fun confirmDelete() {
        if (busy) return

        val id = editingId ?: return
        val name = "${lastNameInput.text} ${firstNameInput.text}".trim()

        AlertDialog.Builder(this)
            .setTitle("Удалить автора?")
            .setMessage(
                "Удалить выбранную запись «$name»? " +
                        "Отменить удаление будет нельзя."
            )
            .setNegativeButton("Отмена", null)
            .setPositiveButton("Удалить") { _, _ ->
                model.delete(id)
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        editingId?.let { id ->
            outState.putInt("editing_id", id)
        }
        outState.putInt("change_version", seenChangeVersion)
        outState.putBoolean("pending_write", model.writing)
        super.onSaveInstanceState(outState)
    }

}