package app.pagefeed

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

sealed interface Screen {
    data object Home : Screen
    data object Library : Screen
    data class ShelfDetail(val name: String) : Screen
    data class Reader(val id: String) : Screen
    data object LibrarySearch : Screen
    data object Settings : Screen
    data object ReadingActivity : Screen
}

class AppVm(app: Application) : AndroidViewModel(app) {
    val repo = Repo(app)
    val books = mutableStateListOf<Book>()
    val shelves = mutableStateListOf<Shelf>()
    val settings = ReaderSettings(app.getSharedPreferences("reader", 0))

    val stack = mutableStateListOf<Screen>(Screen.Home)
    var libraryTab by mutableIntStateOf(0)

    /** Name of the file being imported right now (null = idle). */
    var importing by mutableStateOf<String?>(null)
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    var notesAccessMissing by mutableStateOf(false)

    private val contentCache = HashMap<String, BookContent>()
    private val annCache = HashMap<String, BookAnnotations>()

    // ---- reading statistics -------------------------------------------------------------
    private val statsData = repo.loadStats()
    var goalMin by mutableIntStateOf(statsData.goalMin)
        private set
    var activityUnlocked by mutableStateOf(statsData.unlocked)
        private set
    var statsVersion by mutableIntStateOf(0)
        private set

    init {
        books.addAll(repo.loadBooks())
        shelves.addAll(repo.loadShelves())
    }

    // ---- navigation ---------------------------------------------------------------------
    fun goTab(s: Screen) {
        stack.clear()
        stack.add(s)
    }

    fun push(s: Screen) {
        stack.add(s)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.size - 1)
    }

    fun openBook(id: String) {
        if (stack.lastOrNull() is Screen.Reader) pop()
        stack.add(Screen.Reader(id))
    }

    // ---- import -------------------------------------------------------------------------
    fun importUris(uris: List<Uri>, openAfter: Boolean) {
        viewModelScope.launch {
            var lastId: String? = null
            for (u in uris) {
                val id = withContext(Dispatchers.IO) { importOne(u) }
                if (id != null) lastId = id
            }
            importing = null
            if (openAfter && lastId != null) openBook(lastId)
        }
    }

    private fun displayName(uri: Uri): String {
        val cr = getApplication<Application>().contentResolver
        try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) {
                    val n = it.getString(0)
                    if (!n.isNullOrBlank()) return n
                }
            }
        } catch (_: Exception) {
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "book"
    }

    private fun formatOf(name: String, mime: String?): String? {
        when (name.substringAfterLast('.', "").lowercase()) {
            "epub" -> return "epub"
            "pdf" -> return "pdf"
            "md", "markdown" -> return "md"
            "txt", "text" -> return "txt"
        }
        return when (mime) {
            "application/epub+zip" -> "epub"
            "application/pdf" -> "pdf"
            "text/markdown", "text/x-markdown" -> "md"
            "text/plain" -> "txt"
            else -> null
        }
    }

    private suspend fun importOne(uri: Uri): String? {
        val app = getApplication<Application>()
        val cr = app.contentResolver
        val name = displayName(uri)
        val ext = formatOf(name, cr.getType(uri))
        if (ext == null) {
            toasts.tryEmit("Unsupported file: $name")
            return null
        }
        importing = name
        val tmp = File(app.cacheDir, "import-${UUID.randomUUID()}.tmp")
        val md = MessageDigest.getInstance("SHA-1")
        try {
            val input = cr.openInputStream(uri)
            if (input == null) {
                toasts.tryEmit("Couldn't open $name")
                return null
            }
            input.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        md.update(buf, 0, n)
                        out.write(buf, 0, n)
                    }
                }
            }
        } catch (e: Exception) {
            tmp.delete()
            toasts.tryEmit("Couldn't open $name")
            return null
        }
        val id = md.digest().joinToString("") { "%02x".format(it) }.take(12)
        if (books.any { it.id == id }) {
            tmp.delete()
            toasts.tryEmit("Already in your library")
            return id
        }
        val dest = repo.bookFile(id, ext)
        try {
            tmp.copyTo(dest, overwrite = true)
        } finally {
            tmp.delete()
        }
        return try {
            val fallback = name.substringBeforeLast('.', name)
            val parsed = Parsers.parse(app, dest, ext, fallback)
            val content = Chunker.build(parsed.raw)
            if (content.chunks.isEmpty()) {
                dest.delete()
                toasts.tryEmit("No readable text found in $name")
                return null
            }
            ContentIO.write(repo.parsedFile(id), content)
            val cover = parsed.cover
            if (cover != null) repo.coverFile(id).writeBytes(cover)
            val book = Book(
                id = id,
                title = parsed.raw.title.ifBlank { fallback },
                author = parsed.raw.author,
                format = ext,
                added = System.currentTimeMillis(),
                lastOpened = 0L,
                pos = 0,
                total = content.chunks.size,
                hasCover = cover != null,
            )
            contentCache[id] = content
            withContext(Dispatchers.Main) {
                books.add(0, book)
                repo.saveBooks(books.toList())
            }
            id
        } catch (e: Throwable) {
            dest.delete()
            repo.parsedFile(id).delete()
            toasts.tryEmit("Couldn't read $name")
            null
        }
    }

    // ---- library operations ----------------------------------------------------------------
    fun removeBook(id: String) {
        val b = books.firstOrNull { it.id == id } ?: return
        books.removeAll { it.id == id }
        for (i in shelves.indices) {
            val s = shelves[i]
            if (id in s.bookIds) shelves[i] = s.copy(bookIds = s.bookIds - id)
        }
        contentCache.remove(id)
        annCache.remove(id)
        repo.saveBooks(books.toList())
        repo.saveShelves(shelves.toList())
        viewModelScope.launch(Dispatchers.IO) { repo.deleteBookFiles(b) }
    }

    fun createShelf(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty() || shelves.any { it.name.equals(n, ignoreCase = true) }) return false
        shelves.add(Shelf(n, emptyList()))
        repo.saveShelves(shelves.toList())
        return true
    }

    fun renameShelf(old: String, new: String): Boolean {
        val n = new.trim()
        if (n.isEmpty() || shelves.any { it.name.equals(n, ignoreCase = true) && it.name != old }) return false
        val i = shelves.indexOfFirst { it.name == old }
        if (i < 0) return false
        shelves[i] = shelves[i].copy(name = n)
        repo.saveShelves(shelves.toList())
        for (k in stack.indices) {
            val s = stack[k]
            if (s is Screen.ShelfDetail && s.name == old) stack[k] = Screen.ShelfDetail(n)
        }
        return true
    }

    fun deleteShelf(name: String) {
        shelves.removeAll { it.name == name }
        repo.saveShelves(shelves.toList())
    }

    fun addToShelf(shelf: String, bookId: String) {
        val i = shelves.indexOfFirst { it.name == shelf }
        if (i < 0) return
        val s = shelves[i]
        if (bookId !in s.bookIds) {
            shelves[i] = s.copy(bookIds = s.bookIds + bookId)
            repo.saveShelves(shelves.toList())
        }
    }

    fun removeFromShelf(shelf: String, bookId: String) {
        val i = shelves.indexOfFirst { it.name == shelf }
        if (i < 0) return
        val s = shelves[i]
        shelves[i] = s.copy(bookIds = s.bookIds - bookId)
        repo.saveShelves(shelves.toList())
    }

    fun book(id: String): Book? = books.firstOrNull { it.id == id }

    // ---- reading content / progress ---------------------------------------------------------
    suspend fun loadContent(id: String): BookContent? {
        contentCache[id]?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val c = ContentIO.read(repo.parsedFile(id))
                contentCache[id] = c
                c
            } catch (e: Exception) {
                null
            }
        }
    }

    fun saveProgress(id: String, pos: Int, touchOpened: Boolean = true) {
        val i = books.indexOfFirst { it.id == id }
        if (i < 0) return
        val b = books[i]
        val updated = b.copy(
            pos = pos.coerceAtLeast(0),
            lastOpened = if (touchOpened) System.currentTimeMillis() else b.lastOpened,
        )
        books[i] = updated
        repo.saveBooks(books.toList())
    }

    // ---- annotations / notes export -----------------------------------------------------------
    fun annotations(id: String): BookAnnotations = annCache.getOrPut(id) { repo.loadAnnotations(id) }

    fun persistAnnotations(bookId: String, content: BookContent) {
        val ann = annotations(bookId)
        val book = book(bookId) ?: return
        val notes = ann.notes.toList()
        viewModelScope.launch(Dispatchers.IO) {
            repo.saveAnnotations(bookId, ann)
            val ok = NotesExporter.export(book, content, notes)
            withContext(Dispatchers.Main) {
                notesAccessMissing = !ok && notes.isNotEmpty() && !NotesExporter.hasAccess()
            }
        }
    }

    /** Called when the app returns to the foreground (e.g. after the user granted file access). */
    fun refreshAccess() {
        val has = NotesExporter.hasAccess()
        if (has && notesAccessMissing) {
            notesAccessMissing = false
            exportAll()
        }
    }

    fun exportAll() {
        val list = books.toList()
        viewModelScope.launch(Dispatchers.IO) {
            for (b in list) {
                val ann = repo.loadAnnotations(b.id)
                if (ann.notes.isEmpty()) continue
                val content = loadContentBlocking(b.id) ?: continue
                NotesExporter.export(b, content, ann.notes.toList())
            }
        }
    }

    private fun loadContentBlocking(id: String): BookContent? =
        contentCache[id] ?: try {
            ContentIO.read(repo.parsedFile(id)).also { contentCache[id] = it }
        } catch (e: Exception) {
            null
        }

    // ---- stats ------------------------------------------------------------------------------
    private fun dayKey(cal: Calendar = Calendar.getInstance()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)

    fun todayStat(): DayStat = statsData.days[dayKey()] ?: DayStat(0, 0)

    fun addReading(sec: Int, words: Int) {
        if (sec <= 0 && words <= 0) return
        val k = dayKey()
        val d = statsData.days[k] ?: DayStat(0, 0)
        statsData.days[k] = DayStat(d.sec + sec, d.words + words)
        statsVersion++
    }

    fun flushStats() {
        statsData.goalMin = goalMin
        statsData.unlocked = activityUnlocked
        repo.saveStats(statsData)
    }

    fun setGoal(minutes: Int) {
        goalMin = minutes.coerceIn(5, 480)
        flushStats()
    }

    fun unlockActivity() {
        activityUnlocked = true
        flushStats()
    }

    fun allDays(): Map<String, DayStat> {
        statsVersion.let { }
        return statsData.days.toMap()
    }

    /** Consecutive days (ending today, or yesterday if today isn't done yet) that met the goal. */
    fun streak(): Int {
        statsVersion.let { }
        val goalSec = goalMin * 60
        val cal = Calendar.getInstance()
        var n = 0
        if ((statsData.days[dayKey(cal)]?.sec ?: 0) < goalSec) cal.add(Calendar.DAY_OF_YEAR, -1)
        while ((statsData.days[dayKey(cal)]?.sec ?: 0) >= goalSec && goalSec > 0) {
            n++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return n
    }
}
