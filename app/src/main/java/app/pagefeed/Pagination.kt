package app.pagefeed

import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min

/** One piece of a page: a whole chunk (or, for gigantic chunks, a run of whole lines of it). */
class PageItem(val chunk: Int, val text: String)

class Pagination(val pages: List<List<PageItem>>, val pageOfChunk: IntArray)

class PageSpec(
    val widthPx: Int,
    val heightPx: Int,
    val textPx: Float,
    val lineHeightPx: Float,
    val indentPx: Int,
)

/**
 * Packs chunks into pages. Chunks are never split, except a chunk taller than a whole page
 * (e.g. one huge unpunctuated "sentence" from a PDF table), which continues on the next page
 * at a line boundary.
 */
object Paginator {
    const val HEADING_SCALE = 1.2f
    const val HEADING_GAP_LINES = 0.8f

    suspend fun paginate(
        content: BookContent,
        spec: PageSpec,
        typeface: Typeface,
    ): Pagination {
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = spec.textPx
        }
        val headPaint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
            textSize = spec.textPx * HEADING_SCALE
        }
        val lh = spec.lineHeightPx
        val lhHead = lh * HEADING_SCALE
        val gapHead = lh * HEADING_GAP_LINES
        val avail = spec.heightPx - lh * 0.6f

        val chunks = content.chunks
        val pageOfChunk = IntArray(chunks.size) { -1 }
        val pages = ArrayList<List<PageItem>>()
        var cur = ArrayList<PageItem>()
        var y = 0f

        fun flush() {
            if (cur.isNotEmpty()) {
                pages.add(cur)
                cur = ArrayList()
                y = 0f
            }
        }

        for (i in chunks.indices) {
            if (i % 256 == 0) coroutineContext.ensureActive()
            val ch = chunks[i]
            val heading = ch.heading
            val lay = layoutFor(ch.text, if (heading) headPaint else paint, spec.widthPx, if (heading) 0 else spec.indentPx)
            val line = if (heading) lhHead else lh
            val gap = if (heading) gapHead else 0f
            val h = lay.lineCount * line + 2 * gap

            if (heading && y > 0f && y + h + 3 * lh > avail) flush()

            if (h <= avail) {
                if (y + h > avail) flush()
                if (pageOfChunk[i] < 0) pageOfChunk[i] = pages.size
                cur.add(PageItem(i, ch.text))
                y += h
            } else {
                flush()
                val perPage = max(1, ((avail - 2 * gap) / line).toInt())
                var start = 0
                while (start < lay.lineCount) {
                    val end = min(lay.lineCount, start + perPage)
                    val seg = ch.text.substring(lay.getLineStart(start), lay.getLineEnd(end - 1)).trim()
                    if (start == 0) pageOfChunk[i] = pages.size
                    cur.add(PageItem(i, seg))
                    flush()
                    start = end
                }
            }
        }
        flush()
        if (pages.isEmpty()) pages.add(emptyList())
        for (i in pageOfChunk.indices) if (pageOfChunk[i] < 0) pageOfChunk[i] = 0
        return Pagination(pages, pageOfChunk)
    }

    private fun layoutFor(text: String, paint: TextPaint, width: Int, indent: Int): StaticLayout {
        val b = StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, width))
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
        if (indent > 0) b.setIndents(intArrayOf(indent, 0), null)
        return b.build()
    }
}
