package app.pagefeed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Chapters / Bookmarks / Notes, modelled on the Play Books "contents" screen. */
@Composable
fun ContentsOverlay(
    title: String,
    content: BookContent,
    ann: BookAnnotations,
    pos: Int,
    initialTab: Int,
    onJump: (Int) -> Unit,
    onClose: () -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onEditNote: (Note) -> Unit,
) {
    var tab by remember { mutableIntStateOf(initialTab) }
    Column(Modifier.fillMaxSize().background(PB.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PB.text) }
            Text(
                title, color = PB.text, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        SimpleTabs(listOf("Chapters", "Bookmarks", "Notes"), tab) { tab = it }
        when (tab) {
            0 -> ChaptersList(content, pos, onJump)
            1 -> BookmarksList(content, ann, onJump, onDeleteBookmark)
            else -> NotesList(content, ann, onJump, onDeleteNote, onEditNote)
        }
    }
}

@Composable
private fun ChaptersList(content: BookContent, pos: Int, onJump: (Int) -> Unit) {
    val current = content.chapterIndexOf(pos)
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(content.chapters) { i, ch ->
            val isCurrent = i == current
            Row(
                Modifier.fillMaxWidth().clickable { onJump(ch.startChunk) }.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(36.dp)) {
                    if (isCurrent) Box(Modifier.size(12.dp).clip(CircleShape).background(PB.accent))
                }
                Column {
                    Text(
                        ch.title, color = if (isCurrent) PB.accent else PB.text, fontSize = 18.sp,
                        fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    val page = if (isCurrent) content.virtualPage(pos) else content.virtualPage(ch.startChunk)
                    Text(
                        if (isCurrent) "currently on page $page" else "at page $page",
                        color = if (isCurrent) PB.accent else PB.text2, fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarksList(
    content: BookContent,
    ann: BookAnnotations,
    onJump: (Int) -> Unit,
    onDelete: (Bookmark) -> Unit,
) {
    val list = ann.bookmarks.sortedBy { it.chunk }
    if (list.isEmpty()) {
        EmptyNote("No bookmarks yet", "Use the ⋮ menu in the reader to bookmark a page.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(list, key = { it.chunk }) { b ->
            var menu by remember { mutableStateOf(false) }
            val chunk = b.chunk.coerceIn(0, content.chunks.lastIndex)
            Row(
                Modifier.fillMaxWidth().clickable { onJump(chunk) }.padding(start = 24.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            ) {
                Box(Modifier.width(36.dp).padding(top = 4.dp)) {
                    Icon(Icons.Filled.Bookmark, null, tint = PB.text2)
                }
                Column(Modifier.weight(1f)) {
                    Text(content.chapterTitleOf(chunk), color = PB.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("at page ${content.virtualPage(chunk)}", color = PB.text2, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(content.chunks[chunk].text, color = PB.text2, fontSize = 17.sp, lineHeight = 26.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = PB.text) }
                    PbMenu(menu, { menu = false }) { PbMenuItem("Delete bookmark") { menu = false; onDelete(b) } }
                }
            }
        }
    }
}

@Composable
private fun NotesList(
    content: BookContent,
    ann: BookAnnotations,
    onJump: (Int) -> Unit,
    onDelete: (Note) -> Unit,
    onEdit: (Note) -> Unit,
) {
    val list = ann.notes.sortedBy { it.chunk }
    if (list.isEmpty()) {
        EmptyNote("No highlights yet", "Long-press a paragraph in the reader to highlight it or add a note.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(list, key = { it.id }) { n ->
            var menu by remember { mutableStateOf(false) }
            val chunk = n.chunk.coerceIn(0, content.chunks.lastIndex)
            Row(
                Modifier.fillMaxWidth().clickable { onJump(chunk) }.padding(start = 24.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
            ) {
                Box(Modifier.width(36.dp).padding(top = 4.dp)) {
                    Icon(Icons.Filled.Description, null, tint = PB.noteIcon)
                }
                Column(Modifier.weight(1f)) {
                    Text(content.chapterTitleOf(chunk), color = PB.text, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("at page ${content.virtualPage(chunk)}", color = PB.text2, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(background = PB.noteHighlight)) { append(n.text) }
                        },
                        color = PB.text, fontSize = 17.sp, lineHeight = 26.sp,
                    )
                    if (n.note.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(n.note, color = PB.text2, fontSize = 16.sp, lineHeight = 24.sp)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = PB.text) }
                    PbMenu(menu, { menu = false }) {
                        PbMenuItem(if (n.note.isBlank()) "Add note" else "Edit note") { menu = false; onEdit(n) }
                        PbMenuItem("Delete") { menu = false; onDelete(n) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNote(title: String, hint: String) {
    Column(Modifier.fillMaxWidth().padding(32.dp)) {
        Text(title, color = PB.text, fontSize = 20.sp)
        Spacer(Modifier.height(8.dp))
        Text(hint, color = PB.text2, fontSize = 16.sp)
    }
}

/** Full-text search inside the open book. */
@Composable
fun SearchInBook(content: BookContent, onJump: (Int) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Int>>(emptyList()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(q) {
        val needle = q.trim()
        if (needle.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(250)
        results = withContext(Dispatchers.Default) {
            val out = ArrayList<Int>()
            for (i in content.chunks.indices) {
                if (content.chunks[i].text.contains(needle, ignoreCase = true)) {
                    out.add(i)
                    if (out.size >= 300) break
                }
            }
            out
        }
    }
    Column(Modifier.fillMaxSize().background(PB.bg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PB.text) }
            BasicTextField(
                value = q,
                onValueChange = { q = it },
                singleLine = true,
                textStyle = TextStyle(color = PB.text, fontSize = 19.sp),
                cursorBrush = SolidColor(PB.accent),
                modifier = Modifier.weight(1f).focusRequester(focus).focusable(),
                decorationBox = { inner ->
                    Box {
                        if (q.isEmpty()) Text("Search in book", color = PB.text2, fontSize = 19.sp)
                        inner()
                    }
                },
            )
            if (q.isNotEmpty()) {
                IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, "Clear", tint = PB.text2) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(PB.outline))
        val needle = q.trim()
        if (needle.length >= 2 && results.isEmpty()) {
            Text("No results", color = PB.text2, fontSize = 16.sp, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(results, key = { it }) { i ->
                val text = content.chunks[i].text
                val at = text.indexOf(needle, ignoreCase = true).coerceAtLeast(0)
                val from = (at - 60).coerceAtLeast(0)
                val to = (at + needle.length + 100).coerceAtMost(text.length)
                val snippet = buildAnnotatedString {
                    if (from > 0) append("…")
                    val before = text.substring(from, at)
                    val mid = text.substring(at, (at + needle.length).coerceAtMost(text.length))
                    val after = text.substring((at + needle.length).coerceAtMost(text.length), to)
                    append(before)
                    withStyle(SpanStyle(color = PB.accent, fontWeight = FontWeight.Bold)) { append(mid) }
                    append(after)
                    if (to < text.length) append("…")
                }
                Column(Modifier.fillMaxWidth().clickable { onJump(i) }.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(
                        "${content.chapterTitleOf(i)} · page ${content.virtualPage(i)}",
                        color = PB.text2, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(snippet, color = PB.text, fontSize = 17.sp, lineHeight = 25.sp)
                }
            }
        }
    }
}
