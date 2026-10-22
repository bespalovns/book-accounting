package ru.istz3401bespalov.bookaccounting

data class Author(
    val id: Int,
    val firstName: String,
    val lastName: String
) {
    override fun toString(): String {
        return "$lastName $firstName"
    }
}