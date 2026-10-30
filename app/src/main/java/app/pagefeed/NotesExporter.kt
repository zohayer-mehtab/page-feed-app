package app.pagefeed

import android.os.Build
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes one Markdown file per book into <phone root>/notes from books/. */
object NotesExporter {
    const val FOLDER = "notes from books"

    fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true

    fun folder(): File = File(Environment.getExternalStorageDirectory(), FOLDER)

    fun fileFor(book: Book): File = File(folder(), safeName(book.title.ifBlank { "Untitled" }) + ".md")

    private fun safeName(s: String): String {
        val cleaned = s.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trim('.')
        return cleaned.take(120).ifBlank { "Untitled" }
    }

    /** Returns true on success (or when there was nothing to do). */
    fun export(book: Book, content: BookContent, notes: List<Note>): Boolean {
        return try {
            val f = fileFor(book)
            if (notes.isEmpty()) {
                if (f.exists()) f.delete()
                return true
            }
            if (!hasAccess()) return false
            f.parentFile?.mkdirs()
            f.writeText(render(book, content, notes), Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun render(book: Book, content: BookContent, notes: List<Note>): String {
        val sorted = notes.sortedBy { it.chunk }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("# ").append(book.title.ifBlank { "Untitled" }).append('\n')
        if (book.author.isNotBlank()) sb.append('*').append(book.author).append("*\n")
        sb.append("\n_Updated ").append(stamp).append(" · ").append(sorted.size)
            .append(if (sorted.size == 1) " highlight_" else " highlights_").append("\n")
        var lastChapter = -1
        for (n in sorted) {
            val safeChunk = n.chunk.coerceIn(0, (content.chunks.size - 1).coerceAtLeast(0))
            val ch = if (content.chapters.isEmpty()) 0 else content.chapterIndexOf(safeChunk)
            if (ch != lastChapter) {
                sb.append("\n## ").append(content.chapterTitleOf(safeChunk)).append('\n')
                lastChapter = ch
            }
            sb.append("\n> ").append(n.text.replace("\n", "\n> ")).append('\n')
            sb.append("\n*Page ").append(content.virtualPage(safeChunk)).append("*\n")
            if (n.note.isNotBlank()) {
                sb.append("\n**Note:** ").append(n.note.trim()).append('\n')
            }
        }
        return sb.toString()
    }
}
