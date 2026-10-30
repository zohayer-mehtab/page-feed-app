package app.pagefeed

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val format: String,
    val added: Long,
    val lastOpened: Long,
    val pos: Int,
    val total: Int,
    val hasCover: Boolean,
) {
    val percent: Int
        get() = if (total <= 1) 0 else (pos * 100f / (total - 1)).roundToInt().coerceIn(0, 100)
}

data class Shelf(val name: String, val bookIds: List<String>)

data class Bookmark(val chunk: Int, val created: Long)

/** A highlight (whole chunk) with an optional typed note. */
data class Note(val id: String, val chunk: Int, val text: String, val note: String, val created: Long)

class BookAnnotations {
    val bookmarks = mutableStateListOf<Bookmark>()
    val notes = mutableStateListOf<Note>()
}

data class DayStat(val sec: Int, val words: Int)

class Stats(
    var goalMin: Int = 30,
    var unlocked: Boolean = false,
    val days: MutableMap<String, DayStat> = HashMap(),
)

class Repo(private val ctx: Context) {
    private val booksDir = File(ctx.filesDir, "books").apply { mkdirs() }
    private val parsedDir = File(ctx.filesDir, "parsed").apply { mkdirs() }
    private val coversDir = File(ctx.filesDir, "covers").apply { mkdirs() }
    private val annDir = File(ctx.filesDir, "annotations").apply { mkdirs() }
    private val libFile = File(ctx.filesDir, "library.json")
    private val shelvesFile = File(ctx.filesDir, "shelves.json")
    private val statsFile = File(ctx.filesDir, "stats.json")

    fun bookFile(id: String, format: String) = File(booksDir, "$id.$format")
    fun parsedFile(id: String) = File(parsedDir, "$id.pf")
    fun coverFile(id: String) = File(coversDir, "$id.jpg")

    private fun writeAtomic(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(f)) {
            f.writeText(text, Charsets.UTF_8)
            tmp.delete()
        }
    }

    // ---- library -------------------------------------------------------------------------

    fun loadBooks(): List<Book> {
        if (!libFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(libFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Book(
                    id = o.getString("id"),
                    title = o.optString("title"),
                    author = o.optString("author"),
                    format = o.optString("format", "txt"),
                    added = o.optLong("added"),
                    lastOpened = o.optLong("lastOpened"),
                    pos = o.optInt("pos"),
                    total = o.optInt("total"),
                    hasCover = o.optBoolean("hasCover"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveBooks(books: List<Book>) {
        val arr = JSONArray()
        for (b in books) {
            arr.put(
                JSONObject()
                    .put("id", b.id).put("title", b.title).put("author", b.author)
                    .put("format", b.format).put("added", b.added).put("lastOpened", b.lastOpened)
                    .put("pos", b.pos).put("total", b.total).put("hasCover", b.hasCover),
            )
        }
        writeAtomic(libFile, arr.toString())
    }

    fun deleteBookFiles(b: Book) {
        bookFile(b.id, b.format).delete()
        parsedFile(b.id).delete()
        coverFile(b.id).delete()
        File(annDir, "${b.id}.json").delete()
    }

    // ---- shelves -------------------------------------------------------------------------

    fun loadShelves(): List<Shelf> {
        if (!shelvesFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(shelvesFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ids = o.optJSONArray("ids") ?: JSONArray()
                Shelf(o.getString("name"), (0 until ids.length()).map { ids.getString(it) })
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveShelves(shelves: List<Shelf>) {
        val arr = JSONArray()
        for (s in shelves) {
            arr.put(JSONObject().put("name", s.name).put("ids", JSONArray(s.bookIds)))
        }
        writeAtomic(shelvesFile, arr.toString())
    }

    // ---- annotations ---------------------------------------------------------------------

    fun loadAnnotations(id: String): BookAnnotations {
        val out = BookAnnotations()
        val f = File(annDir, "$id.json")
        if (!f.exists()) return out
        try {
            val o = JSONObject(f.readText())
            val bm = o.optJSONArray("bookmarks") ?: JSONArray()
            for (i in 0 until bm.length()) {
                val b = bm.getJSONObject(i)
                out.bookmarks.add(Bookmark(b.getInt("chunk"), b.optLong("created")))
            }
            val nt = o.optJSONArray("notes") ?: JSONArray()
            for (i in 0 until nt.length()) {
                val n = nt.getJSONObject(i)
                out.notes.add(
                    Note(
                        n.getString("id"), n.getInt("chunk"), n.optString("text"),
                        n.optString("note"), n.optLong("created"),
                    ),
                )
            }
        } catch (e: Exception) {
            // ignore corrupt file
        }
        return out
    }

    fun saveAnnotations(id: String, a: BookAnnotations) {
        val bm = JSONArray()
        for (b in a.bookmarks.toList()) bm.put(JSONObject().put("chunk", b.chunk).put("created", b.created))
        val nt = JSONArray()
        for (n in a.notes.toList()) {
            nt.put(
                JSONObject().put("id", n.id).put("chunk", n.chunk).put("text", n.text)
                    .put("note", n.note).put("created", n.created),
            )
        }
        writeAtomic(File(annDir, "$id.json"), JSONObject().put("bookmarks", bm).put("notes", nt).toString())
    }

    // ---- stats ---------------------------------------------------------------------------

    fun loadStats(): Stats {
        val st = Stats()
        if (!statsFile.exists()) return st
        try {
            val o = JSONObject(statsFile.readText())
            st.goalMin = o.optInt("goal", 30)
            st.unlocked = o.optBoolean("unlocked", false)
            val d = o.optJSONObject("days")
            if (d != null) {
                for (k in d.keys()) {
                    val v = d.getJSONObject(k)
                    st.days[k] = DayStat(v.optInt("s"), v.optInt("w"))
                }
            }
        } catch (e: Exception) {
            // ignore corrupt file
        }
        return st
    }

    fun saveStats(st: Stats) {
        val d = JSONObject()
        for ((k, v) in st.days) d.put(k, JSONObject().put("s", v.sec).put("w", v.words))
        writeAtomic(
            statsFile,
            JSONObject().put("goal", st.goalMin).put("unlocked", st.unlocked).put("days", d).toString(),
        )
    }
}
