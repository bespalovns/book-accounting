package ru.istz3401bespalov.bookaccounting

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
class MainActivity : AppCompatActivity() {

    private lateinit var loginInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var dataText: TextView
    private lateinit var authPanel: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        authPanel = findViewById(R.id.authPanel)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
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

        loginInput = findViewById(R.id.loginInput)
        passwordInput = findViewById(R.id.passwordInput)
        dataText = findViewById(R.id.dataText)

        findViewById<TextView>(R.id.sectionTitle).text =
            "Управление каталогом"

        findViewById<TextView>(R.id.sectionDescription).text =
            "Сайт и приложение используют общую базу данных."

        findViewById<AppCompatButton>(R.id.booksButton)
            .setOnClickListener {
                openSection("books")
            }

        findViewById<AppCompatButton>(R.id.authorsButton)
            .setOnClickListener {
                openSection("authors")
            }

        findViewById<AppCompatButton>(R.id.publishersButton)
            .setOnClickListener {
                openSection("publishers")
            }
    }

    override fun onResume() {
        super.onResume()

        val authenticated = ApiClient.isAuthenticated()

        authPanel.visibility = if (authenticated) {
            View.GONE
        } else {
            View.VISIBLE
        }

        dataText.text = if (authenticated) {
            "Вы вошли в приложение. Выберите раздел."
        } else {
            "Введите данные входа в приложение, затем выберите раздел. " +
                    "Первое подключение после простоя может занять около минуты."
        }
    }

    private fun prepareCredentials(): Boolean {
        val username = loginInput.text.toString().trim()
        val password = passwordInput.text.toString()

        loginInput.error = null
        passwordInput.error = null

        // При возврате или повороте можно использовать данные из памяти.
        if (
            username.isEmpty() &&
            password.isEmpty() &&
            ApiClient.hasCredentials()
        ) {
            return true
        }

        if (username.isBlank()) {
            loginInput.error = "Введите логин"
            loginInput.requestFocus()
            return false
        }

        if (':' in username) {
            loginInput.error = "Логин не должен содержать двоеточие"
            loginInput.requestFocus()
            return false
        }

        if (password.isEmpty()) {
            passwordInput.error = "Введите пароль"
            passwordInput.requestFocus()
            return false
        }

        ApiClient.setCredentials(username, password)

        loginInput.text.clear()
        passwordInput.text.clear()

        return true
    }

    private fun openSection(entity: String) {
        if (!prepareCredentials()) return

        val screen = if (entity == "authors") {
            Intent(this, AuthorsActivity::class.java)
        } else {
            Intent(this, CatalogActivity::class.java)
                .putExtra("entity", entity)
        }

        startActivity(screen)
    }
}