package app.pagefeed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ParsedFile(val raw: RawBook, val cover: ByteArray?)

object Parsers {
    fun parse(ctx: Context, file: File, format: String, fallbackTitle: String): ParsedFile =
        when (format) {
            "epub" -> EpubParser.parse(file, fallbackTitle)
            "pdf" -> parsePdf(ctx, file, fallbackTitle)
            "md" -> ParsedFile(PlainParsers.parseMarkdown(readText(file), fallbackTitle), null)
            else -> ParsedFile(PlainParsers.parseTxt(readText(file), fallbackTitle), null)
        }

    fun readText(file: File): String {
        val bytes = file.readBytes()
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }

    private fun parsePdf(ctx: Context, file: File, fallbackTitle: String): ParsedFile {
        PDFBoxResourceLoader.init(ctx.applicationContext)
        var title = fallbackTitle
        var author = ""
        val paras = ArrayList<Para>()
        val doc = PDDocument.load(file)
        try {
            val info = doc.documentInformation
            val t = info?.title
            if (!t.isNullOrBlank()) title = t.trim()
            author = info?.author?.trim().orEmpty()
            val text = PDFTextStripper().getText(doc)
            for (p in PdfReflow.reflow(text)) paras.add(Para(p, 0, false))
        } finally {
            doc.close()
        }
        val raw = RawBook(title, author, listOf("Start"), paras)
        return ParsedFile(raw, renderPdfCover(file))
    }

    private fun renderPdfCover(file: File): ByteArray? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) return null
            val page = renderer.openPage(0)
            try {
                val w = 480
                val h = (w * page.height.toFloat() / page.width.toFloat()).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            } finally {
                page.close()
            }
        } catch (e: Throwable) {
            null
        } finally {
            try { renderer?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
        }
    }

    /** Downsamples a cover image to at most ~480px wide and re-encodes it as JPEG. */
    fun scaleCover(bytes: ByteArray): ByteArray? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 480) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        } catch (e: Throwable) {
            null
        }
    }
}

object EpubParser {
    private val LEAF = setOf(
        "p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "pre", "dt", "dd",
        "figcaption", "caption", "th", "td", "summary",
    )
    private val SKIP = setOf("script", "style", "svg", "head", "nav", "img", "math", "noscript")

    private class Item(val id: String, val href: String, val type: String, val props: String)

    fun parse(file: File, fallbackTitle: String): ParsedFile {
        ZipFile(file).use { zip ->
            val entries = HashMap<String, ZipEntry>()
            for (e in zip.entries()) entries[e.name] = e

            fun read(path: String): ByteArray? {
                val e = entries[path] ?: entries[decode(path)] ?: return null
                return zip.getInputStream(e).use { it.readBytes() }
            }

            fun xml(path: String) =
                read(path)?.let { Jsoup.parse(String(it, Charsets.UTF_8), "", Parser.xmlParser()) }

            // --- locate the OPF package document
            var opfPath = xml("META-INF/container.xml")
                ?.select("rootfile")?.firstOrNull()?.attr("full-path").orEmpty()
            if (opfPath.isBlank()) {
                opfPath = entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
                    ?: error("No OPF found")
            }
            val opf = xml(opfPath) ?: error("Cannot read OPF")
            val opfDir = opfPath.substringBeforeLast('/', "")

            val title = opf.select("dc|title").firstOrNull()?.text()?.trim().orEmpty()
                .ifBlank { fallbackTitle }
            val author = opf.select("dc|creator").joinToString(", ") { it.text().trim() }

            // --- manifest + spine
            val manifest = LinkedHashMap<String, Item>()
            for (it in opf.select("manifest > item")) {
                manifest[it.attr("id")] = Item(
                    it.attr("id"), it.attr("href"), it.attr("media-type"), it.attr("properties"),
                )
            }
            val spineEl = opf.select("spine").firstOrNull()
            val spine = opf.select("spine > itemref").map { it.attr("idref") }

            // --- cover
            var cover: ByteArray? = null
            val coverId = opf.select("meta[name=cover]").firstOrNull()?.attr("content")
            val coverItem = manifest[coverId]
                ?: manifest.values.firstOrNull { "cover-image" in it.props }
            if (coverItem != null) {
                cover = read(resolve(opfDir, coverItem.href))?.let { Parsers.scaleCover(it) }
            }

            // --- table of contents: path -> chapter title (first one wins)
            val toc = LinkedHashMap<String, String>()
            val navItem = manifest.values.firstOrNull { it.props.split(' ').contains("nav") }
            if (navItem != null) {
                val navPath = resolve(opfDir, navItem.href)
                val navDir = navPath.substringBeforeLast('/', "")
                val html = read(navPath)?.let { Jsoup.parse(String(it, Charsets.UTF_8)) }
                val nav = html?.select("nav")?.firstOrNull { it.attr("epub:type") == "toc" }
                    ?: html?.select("nav")?.firstOrNull()
                nav?.select("a[href]")?.forEach { a ->
                    val t = a.text().trim()
                    if (t.isNotEmpty()) toc.putIfAbsent(resolve(navDir, a.attr("href")), t)
                }
            }
            if (toc.isEmpty()) {
                val ncxItem = manifest[spineEl?.attr("toc")]
                    ?: manifest.values.firstOrNull { it.type == "application/x-dtbncx+xml" }
                if (ncxItem != null) {
                    val ncxPath = resolve(opfDir, ncxItem.href)
                    val ncxDir = ncxPath.substringBeforeLast('/', "")
                    xml(ncxPath)?.select("navPoint")?.forEach { np ->
                        val t = np.select("navLabel > text").firstOrNull()?.text()?.trim().orEmpty()
                        val src = np.select("content").firstOrNull()?.attr("src").orEmpty()
                        if (t.isNotEmpty() && src.isNotEmpty()) toc.putIfAbsent(resolve(ncxDir, src), t)
                    }
                }
            }

            // --- walk the spine
            val chapters = ArrayList<String>()
            val paras = ArrayList<Para>()
            var cur = -1
            for (idref in spine) {
                val item = manifest[idref] ?: continue
                if (item.props.split(' ').contains("nav")) continue
                if (!(item.type.contains("html") || item.type.contains("xml"))) continue
                val path = resolve(opfDir, item.href)
                val bytes = read(path) ?: continue
                val doc = Jsoup.parse(String(bytes, Charsets.UTF_8))
                val body = doc.body()

                val tocTitle = toc[path]
                if (tocTitle != null) {
                    chapters.add(tocTitle)
                    cur = chapters.size - 1
                } else if (toc.isEmpty()) {
                    val h = body.select("h1, h2, h3").firstOrNull()?.text()?.trim()
                    if (!h.isNullOrEmpty()) {
                        chapters.add(h)
                        cur = chapters.size - 1
                    }
                }

                walk(body) { text, heading ->
                    if (cur < 0) {
                        chapters.add("Start")
                        cur = 0
                    }
                    paras.add(Para(text, cur, heading))
                }
            }
            return ParsedFile(RawBook(title, author, chapters, paras), cover)
        }
    }

    private fun walk(el: Element, emit: (String, Boolean) -> Unit) {
        val tag = el.tagName().lowercase()
        if (tag in SKIP) return
        if (tag in LEAF) {
            val t = el.text()
            if (t.isNotBlank()) {
                emit(t, tag.length == 2 && tag[0] == 'h' && tag[1].isDigit())
            }
            return
        }
        val sb = StringBuilder()
        for (node in el.childNodes()) {
            if (node is TextNode) {
                sb.append(node.text())
            } else if (node is Element) {
                val ct = node.tagName().lowercase()
                if (ct == "br") {
                    sb.append(' ')
                } else if (isInline(ct)) {
                    sb.append(node.text())
                } else {
                    flush(sb, emit)
                    walk(node, emit)
                }
            }
        }
        flush(sb, emit)
    }

    private fun flush(sb: StringBuilder, emit: (String, Boolean) -> Unit) {
        val t = sb.toString().trim()
        if (t.isNotEmpty()) emit(t, false)
        sb.setLength(0)
    }

    private val INLINE = setOf(
        "a", "span", "em", "i", "b", "strong", "u", "sup", "sub", "small", "big", "cite", "q",
        "abbr", "code", "kbd", "mark", "font", "s", "strike", "del", "ins", "wbr", "bdi", "bdo",
        "dfn", "samp", "var", "time", "label", "ruby", "rt", "rp", "tt",
    )

    private fun isInline(tag: String) = tag in INLINE

    private fun decode(s: String): String = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (e: Exception) {
        s
    }

    /** Resolves [href] (may contain ../ and #fragment) relative to directory [base] inside the zip. */
    private fun resolve(base: String, href: String): String {
        val h = decode(href.substringBefore('#'))
        val parts = ArrayList<String>()
        if (!h.startsWith("/")) {
            if (base.isNotEmpty()) parts.addAll(base.split('/'))
        }
        for (seg in h.split('/')) {
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }
}
