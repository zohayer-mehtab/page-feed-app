package app.pagefeed

// Pure Kotlin (no Android imports) so it can be unit-tested on a plain JVM.

import java.io.BufferedReader
import java.io.File

/** One paragraph produced by a parser, before chunking. */
data class Para(val text: String, val chapter: Int, val heading: Boolean)

/** Parser output: raw paragraphs plus chapter titles (index = Para.chapter). */
class RawBook(
    val title: String,
    val author: String,
    val chapters: List<String>,
    val paras: List<Para>,
)

data class Chunk(val text: String, val chapter: Int, val heading: Boolean)

data class ChapterInfo(val title: String, val startChunk: Int)

class BookContent(val chapters: List<ChapterInfo>, val chunks: List<Chunk>) {
    /** Cumulative character count before each chunk (size = chunks + 1). */
    val prefix: IntArray by lazy {
        val a = IntArray(chunks.size + 1)
        for (i in chunks.indices) a[i + 1] = a[i] + chunks[i].text.length + 1
        a
    }

    val totalVirtualPages: Int get() = (prefix[chunks.size] / CHARS_PER_PAGE) + 1

    fun virtualPage(chunk: Int): Int =
        (prefix[chunk.coerceIn(0, chunks.size)] / CHARS_PER_PAGE) + 1

    /** First chunk whose virtual page is >= [page] (1-based). */
    fun chunkOfVirtualPage(page: Int): Int {
        val target = (page - 1).coerceAtLeast(0) * CHARS_PER_PAGE
        var lo = 0
        var hi = chunks.size - 1
        var ans = chunks.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (prefix[mid] >= target) { ans = mid; hi = mid - 1 } else lo = mid + 1
        }
        return ans.coerceAtLeast(0)
    }

    fun chapterIndexOf(chunk: Int): Int {
        var lo = 0
        var hi = chapters.size - 1
        var ans = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (chapters[mid].startChunk <= chunk) { ans = mid; lo = mid + 1 } else hi = mid - 1
        }
        return ans
    }

    fun chapterTitleOf(chunk: Int): String =
        if (chapters.isEmpty()) "Start" else chapters[chapterIndexOf(chunk)].title

    fun wordsIn(chunk: Int): Int = countWords(chunks[chunk].text)

    companion object {
        const val CHARS_PER_PAGE = 1800
    }
}

fun countWords(s: String): Int {
    var n = 0
    var inWord = false
    for (ch in s) {
        if (ch.isWhitespace()) inWord = false else if (!inWord) { inWord = true; n++ }
    }
    return n
}

object Chunker {
    private const val SOFT = 280
    private const val HARD = 400

    private val ABBREV = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc", "e.g", "i.e", "no", "vol",
        "fig", "mt", "gen", "col", "capt", "lt", "sgt", "rev", "hon", "inc", "ltd", "co", "cf",
        "approx", "dept", "est", "jan", "feb", "mar", "apr", "jun", "jul", "aug", "sep", "sept",
        "oct", "nov", "dec", "p", "pp", "ch", "ed", "eds", "viz", "al",
    )
    private const val CLOSERS = "\"'”’)]»}"
    private const val OPENERS = "\"'“‘([«{—¿¡"

    fun build(raw: RawBook): BookContent {
        val chunks = ArrayList<Chunk>()
        val firstChunkOfChapter = HashMap<Int, Int>()
        for (p in raw.paras) {
            val text = normalize(p.text)
            if (text.isEmpty()) continue
            if (!firstChunkOfChapter.containsKey(p.chapter)) firstChunkOfChapter[p.chapter] = chunks.size
            if (p.heading) {
                chunks.add(Chunk(text, p.chapter, true))
            } else {
                for (piece in split(text)) chunks.add(Chunk(piece, p.chapter, false))
            }
        }
        val chapters = ArrayList<ChapterInfo>()
        for ((idx, title) in raw.chapters.withIndex()) {
            val start = firstChunkOfChapter[idx] ?: continue
            chapters.add(ChapterInfo(title.ifBlank { "Untitled" }, start))
        }
        chapters.sortBy { it.startChunk }
        if (chapters.isEmpty() || chapters[0].startChunk > 0) {
            chapters.add(0, ChapterInfo("Start", 0))
        }
        return BookContent(chapters, chunks)
    }

    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        var space = false
        for (ch in s) {
            if (ch.isWhitespace() || ch == ' ' || ch == '​') {
                space = true
            } else {
                if (space && sb.isNotEmpty()) sb.append(' ')
                space = false
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** Splits a paragraph into chunks. Never cuts inside a sentence. */
    fun split(text: String): List<String> {
        if (text.length <= HARD) return listOf(text)
        val sentences = splitSentences(text)
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for (s in sentences) {
            if (cur.isEmpty()) {
                cur.append(s)
            } else if (cur.length < SOFT && cur.length + 1 + s.length <= HARD) {
                cur.append(' ').append(s)
            } else {
                out.add(cur.toString())
                cur = StringBuilder(s)
            }
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    fun splitSentences(t: String): List<String> {
        val out = ArrayList<String>()
        val n = t.length
        var start = 0
        var i = 0
        while (i < n) {
            val c = t[i]
            if (c == '.' || c == '!' || c == '?' || c == '…') {
                var j = i + 1
                while (j < n && (t[j] == '.' || t[j] == '!' || t[j] == '?' || t[j] == '…')) j++
                val runLen = j - i
                while (j < n && CLOSERS.indexOf(t[j]) >= 0) j++
                if (j >= n) break
                if (t[j] == ' ') {
                    var k = j
                    while (k < n && t[k] == ' ') k++
                    if (k < n && startsSentence(t[k]) && !(c == '.' && runLen == 1 && isAbbrev(t, start, i))) {
                        val s = t.substring(start, j).trim()
                        if (s.isNotEmpty()) out.add(s)
                        start = k
                        i = k
                        continue
                    }
                }
                i = j
                continue
            }
            i++
        }
        if (start < n) {
            val s = t.substring(start).trim()
            if (s.isNotEmpty()) out.add(s)
        }
        return out
    }

    private fun startsSentence(ch: Char): Boolean =
        ch.isUpperCase() || ch.isDigit() || OPENERS.indexOf(ch) >= 0

    private fun isAbbrev(t: String, start: Int, dotIdx: Int): Boolean {
        var b = dotIdx
        while (b > start && (t[b - 1].isLetter() || t[b - 1] == '.')) b--
        val word = t.substring(b, dotIdx).lowercase()
        if (word.isEmpty()) return false
        if (word in ABBREV) return true
        // single capital initial such as "J. K. Rowling"
        if (word.length == 1 && t[b].isUpperCase()) return true
        return false
    }
}

object ContentIO {
    fun write(file: File, content: BookContent) {
        file.parentFile?.mkdirs()
        file.bufferedWriter(Charsets.UTF_8).use { w ->
            w.write("PF1\n")
            for (c in content.chapters) {
                w.write("C\t${c.startChunk}\t${clean(c.title)}\n")
            }
            w.write("---\n")
            for (ch in content.chunks) {
                w.write(if (ch.heading) "H" else "P")
                w.write("\t")
                w.write(ch.chapter.toString())
                w.write("\t")
                w.write(clean(ch.text))
                w.write("\n")
            }
        }
    }

    fun read(file: File): BookContent {
        val chapters = ArrayList<ChapterInfo>()
        val chunks = ArrayList<Chunk>()
        file.bufferedReader(Charsets.UTF_8).use { r: BufferedReader ->
            var line = r.readLine()
            if (line != "PF1") error("bad content file")
            var inChunks = false
            while (true) {
                line = r.readLine() ?: break
                if (!inChunks) {
                    if (line == "---") { inChunks = true; continue }
                    val parts = line.split('\t', limit = 3)
                    if (parts.size == 3 && parts[0] == "C") {
                        chapters.add(ChapterInfo(parts[2], parts[1].toInt()))
                    }
                } else {
                    val parts = line.split('\t', limit = 3)
                    if (parts.size == 3) {
                        chunks.add(Chunk(parts[2], parts[1].toInt(), parts[0] == "H"))
                    }
                }
            }
        }
        return BookContent(chapters, chunks)
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
}

object PlainParsers {
    private val CHAPTER_RE = Regex(
        "^(chapter|part|book|section|prologue|epilogue|preface|introduction|foreword|afterword|contents|act|canto)\\b.*",
        RegexOption.IGNORE_CASE,
    )
    private val WS = Regex("\\s+")

    fun parseTxt(raw: String, fallbackTitle: String): RawBook {
        val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val blocks = text.split(Regex("\n[ \t]*\n"))
        val chapters = ArrayList<String>()
        val paras = ArrayList<Para>()
        var chapter = -1
        for (block in blocks) {
            val t = block.trim()
            if (t.isEmpty()) continue
            val oneLine = t.replace(WS, " ")
            val isHeading = looksLikeHeading(oneLine)
            if (isHeading) {
                chapters.add(oneLine)
                chapter = chapters.size - 1
                paras.add(Para(oneLine, chapter, true))
            } else {
                if (chapter < 0) { chapters.add("Start"); chapter = 0 }
                paras.add(Para(oneLine, chapter, false))
            }
        }
        return RawBook(fallbackTitle, "", chapters, paras)
    }

    private fun looksLikeHeading(s: String): Boolean {
        if (s.length > 60 || s.endsWith(".") || s.endsWith(",")) return false
        if (CHAPTER_RE.matches(s)) return true
        val letters = s.count { it.isLetter() }
        if (letters >= 3 && s.length <= 50 && s == s.uppercase()) return true
        return false
    }

    private val H_RE = Regex("^(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val LIST_RE = Regex("^\\s*([-*+]|\\d+[.)])\\s+(.*)$")
    private val IMG_RE = Regex("!\\[([^\\]]*)]\\([^)]*\\)")
    private val LINK_RE = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val BOLD_RE = Regex("(\\*\\*|__)(.+?)\\1")
    private val EM_STAR_RE = Regex("\\*([^*\\n]+)\\*")
    private val EM_UND_RE = Regex("(?<!\\w)_([^_\\n]+)_(?!\\w)")
    private val CODE_RE = Regex("`([^`]*)`")
    private val TAG_RE = Regex("<[^>]+>")
    private val HR_RE = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")

    fun inline(s: String): String {
        var r = s
        r = IMG_RE.replace(r) { it.groupValues[1] }
        r = LINK_RE.replace(r) { it.groupValues[1] }
        r = BOLD_RE.replace(r) { it.groupValues[2] }
        r = EM_STAR_RE.replace(r) { it.groupValues[1] }
        r = EM_UND_RE.replace(r) { it.groupValues[1] }
        r = CODE_RE.replace(r) { it.groupValues[1] }
        r = TAG_RE.replace(r, "")
        return r.trim()
    }

    fun parseMarkdown(raw: String, fallbackTitle: String): RawBook {
        var text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        if (text.startsWith("---\n")) {
            val end = text.indexOf("\n---", 4)
            if (end > 0) text = text.substring(end + 4).trimStart('\n')
        }
        val chapters = ArrayList<String>()
        val paras = ArrayList<Para>()
        var chapter = -1
        var title = fallbackTitle
        var titleSet = false
        val buf = StringBuilder()
        var inFence = false

        fun ensureChapter() {
            if (chapter < 0) { chapters.add("Start"); chapter = 0 }
        }
        fun flush() {
            if (buf.isNotEmpty()) {
                val t = inline(buf.toString())
                if (t.isNotEmpty()) { ensureChapter(); paras.add(Para(t, chapter, false)) }
                buf.setLength(0)
            }
        }

        for (rawLine in text.split('\n')) {
            val line = rawLine.trimEnd()
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                flush(); inFence = !inFence; continue
            }
            if (inFence) {
                if (line.isNotBlank()) { ensureChapter(); paras.add(Para(line.trim(), chapter, false)) }
                continue
            }
            if (line.isBlank()) { flush(); continue }
            if (HR_RE.matches(line)) { flush(); continue }
            val h = H_RE.matchEntire(line)
            if (h != null) {
                flush()
                val level = h.groupValues[1].length
                val t = inline(h.groupValues[2])
                if (t.isEmpty()) continue
                if (level == 1 && !titleSet) { title = t; titleSet = true }
                if (level <= 2) {
                    chapters.add(t); chapter = chapters.size - 1
                } else ensureChapter()
                paras.add(Para(t, chapter, true))
                continue
            }
            val li = LIST_RE.matchEntire(line)
            if (li != null) {
                flush()
                val marker = li.groupValues[1]
                val bullet = if (marker[0].isDigit()) marker else "•"
                val t = inline(li.groupValues[2])
                if (t.isNotEmpty()) { ensureChapter(); paras.add(Para("$bullet $t", chapter, false)) }
                continue
            }
            var l = line
            if (l.trimStart().startsWith(">")) l = l.trimStart().trimStart('>').trim()
            if (buf.isNotEmpty()) buf.append(' ')
            buf.append(l.trim())
        }
        flush()
        return RawBook(title, "", chapters, paras)
    }
}

/** Turns raw PDF text (hard-wrapped lines) into paragraphs without ever splitting a sentence. */
object PdfReflow {
    private val PAGE_NUM = Regex("^\\s*(page\\s*)?\\d{1,4}\\s*$", RegexOption.IGNORE_CASE)

    fun reflow(text: String): List<String> {
        val lines = text.replace("\r", "").split('\n').map { it.trim() }
        val lens = lines.filter { it.length > 20 }.map { it.length }.sorted()
        val maxLen = if (lens.isEmpty()) 80 else lens[((lens.size - 1) * 0.9).toInt()]
        val out = ArrayList<String>()
        val buf = StringBuilder()
        fun flush() {
            if (buf.isNotEmpty()) { out.add(buf.toString()); buf.setLength(0) }
        }
        for (line in lines) {
            if (line.isEmpty()) { flush(); continue }
            if (PAGE_NUM.matches(line)) continue
            if (buf.isEmpty()) {
                buf.append(line)
            } else if (buf.endsWith("-") && line[0].isLowerCase()) {
                buf.setLength(buf.length - 1)
                buf.append(line)
            } else {
                buf.append(' ').append(line)
            }
            if (endsSentence(line) && line.length < maxLen * 0.72) flush()
        }
        flush()
        return out
    }

    private fun endsSentence(l: String): Boolean {
        var i = l.length - 1
        while (i >= 0 && "\"'\u201D\u2019)]".indexOf(l[i]) >= 0) i--
        return i >= 0 && (l[i] == '.' || l[i] == '!' || l[i] == '?' || l[i] == '\u2026')
    }
}
