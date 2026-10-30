@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalPageCurlApi::class)

package app.pagefeed

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Toc
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.wewox.pagecurl.ExperimentalPageCurlApi
import eu.wewox.pagecurl.config.PageCurlConfig
import eu.wewox.pagecurl.config.rememberPageCurlConfig
import eu.wewox.pagecurl.page.PageCurl
import eu.wewox.pagecurl.page.rememberPageCurlState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

private val PAD_H = 28.dp
private val PAD_TOP = 36.dp
private val PAD_BOTTOM = 48.dp

private enum class Overlay { None, Contents, Search }

private class NoteEdit(val chunk: Int, val existing: Note?)

/** Text styling shared by the feed, the paged view and the page measurement. */
@Stable
class ReaderStyle(
    val font: ReaderFont,
    val sizeSp: Float,
    val lineSp: Float,
    val align: ReaderAlign,
    val theme: ReaderTheme,
) {
    private val lineHeightStyle =
        LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    val para = TextStyle(
        fontFamily = font.family,
        fontSize = sizeSp.sp,
        lineHeight = lineSp.sp,
        color = theme.text,
        textAlign = if (align == ReaderAlign.Justify) TextAlign.Justify else TextAlign.Start,
        textIndent = TextIndent(firstLine = (sizeSp * 1.5f).sp),
        lineHeightStyle = lineHeightStyle,
        lineBreak = LineBreak.Simple,
    )

    val heading = TextStyle(
        fontFamily = font.family,
        fontSize = (sizeSp * Paginator.HEADING_SCALE).sp,
        lineHeight = (lineSp * Paginator.HEADING_SCALE).sp,
        fontWeight = FontWeight.Bold,
        color = theme.text,
        textAlign = TextAlign.Start,
        lineHeightStyle = lineHeightStyle,
        lineBreak = LineBreak.Simple,
    )
}

/** Mutable reading position + counters for the reading record. */
@Stable
class ReaderState(val content: BookContent, initialPos: Int) {
    var pos by mutableIntStateOf(initialPos.coerceIn(0, (content.chunks.size - 1).coerceAtLeast(0)))
        private set
    var rangeEnd by mutableIntStateOf(pos)
        private set
    var jumpTick by mutableIntStateOf(0)
        private set

    var sec = 0
    var words = 0
    var lastActive = System.currentTimeMillis()
    private var lastCounted = pos
    var lastSaved = pos

    private fun moved(p: Int) {
        if (p > lastCounted && p - lastCounted <= 40) {
            for (k in lastCounted until p) words += content.wordsIn(k)
        }
        lastCounted = p
        lastActive = System.currentTimeMillis()
        pos = p
        rangeEnd = p
    }

    fun feedTo(index: Int) {
        moved(index.coerceIn(0, (content.chunks.size - 1).coerceAtLeast(0)))
    }

    fun pagedTo(items: List<PageItem>) {
        if (items.isEmpty()) return
        moved(items.first().chunk)
        rangeEnd = items.last().chunk
    }

    fun jumpTo(chunk: Int) {
        val c = chunk.coerceIn(0, (content.chunks.size - 1).coerceAtLeast(0))
        lastCounted = c
        lastActive = System.currentTimeMillis()
        pos = c
        rangeEnd = c
        jumpTick++
    }

    fun touch() {
        lastActive = System.currentTimeMillis()
    }
}

// ---------------------------------------------------------------------------------------------
// Entry
// ---------------------------------------------------------------------------------------------

@Composable
fun ReaderScreen(vm: AppVm, bookId: String) {
    var content by remember(bookId) { mutableStateOf<BookContent?>(null) }
    var failed by remember(bookId) { mutableStateOf(false) }
    val initial = remember(bookId) { vm.book(bookId)?.pos ?: 0 }
    val title = remember(bookId) { vm.book(bookId)?.title.orEmpty() }
    LaunchedEffect(bookId) {
        val c = vm.loadContent(bookId)
        if (c == null) {
            failed = true
        } else {
            content = c
            vm.saveProgress(bookId, initial, touchOpened = true)
        }
    }
    val c = content
    if (failed) {
        Box(Modifier.fillMaxSize().background(PB.bg), contentAlignment = Alignment.Center) {
            Text("Couldn't open this book", color = PB.text2, fontSize = 18.sp)
        }
        return
    }
    if (c == null) {
        Box(Modifier.fillMaxSize().background(PB.bg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PB.accent)
        }
        return
    }
    ReaderBody(vm, bookId, title, c, initial)
}

@Composable
private fun ReaderBody(vm: AppVm, bookId: String, title: String, content: BookContent, initialPos: Int) {
    val ctx = LocalContext.current
    val activity = ctx as ComponentActivity
    val view = LocalView.current
    val settings = vm.settings
    val style = remember(settings.font, settings.textPct, settings.spacingPct, settings.align, settings.theme) {
        ReaderStyle(
            font = settings.font,
            sizeSp = 17f * settings.textPct / 100f,
            lineSp = 17f * settings.textPct / 100f * 1.4f * settings.spacingPct / 100f,
            align = settings.align,
            theme = settings.theme,
        )
    }

    val rs = remember(bookId) { ReaderState(content, initialPos) }
    val ann = remember(bookId) { vm.annotations(bookId) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current

    var chrome by remember { mutableStateOf(false) }
    var showAa by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(Overlay.None) }
    var contentsTab by remember { mutableIntStateOf(0) }
    var actionChunk by remember { mutableStateOf<Int?>(null) }
    var noteEdit by remember { mutableStateOf<NoteEdit?>(null) }
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var accessPrompt by remember { mutableStateOf(false) }
    var promptedAccess by remember { mutableStateOf(false) }

    val highlighted by remember { derivedStateOf { ann.notes.map { it.chunk }.toHashSet() as Set<Int> } }
    val bookmarked by remember { derivedStateOf { ann.bookmarks.any { it.chunk in rs.pos..rs.rangeEnd } } }

    fun persist() = vm.persistAnnotations(bookId, content)

    fun flushStats() {
        val sec = rs.sec
        val words = rs.words
        if (sec == 0 && words == 0) return
        rs.sec = 0
        rs.words = 0
        val goalSec = vm.goalMin * 60
        val before = vm.todayStat().sec
        vm.addReading(sec, words)
        val after = vm.todayStat().sec
        vm.flushStats()
        if (before < goalSec && after >= goalSec) {
            scope.launch { snackbar.showSnackbar("Daily reading goal reached") }
        }
    }

    fun saveProgress() {
        val p = rs.pos
        if (rs.lastSaved != p) {
            rs.lastSaved = p
            vm.saveProgress(bookId, p, touchOpened = false)
        }
    }

    fun toggleBookmark() {
        val inRange = ann.bookmarks.filter { it.chunk in rs.pos..rs.rangeEnd }
        if (inRange.isNotEmpty()) {
            ann.bookmarks.removeAll(inRange)
        } else if (ann.bookmarks.none { it.chunk == rs.pos }) {
            ann.bookmarks.add(Bookmark(rs.pos, System.currentTimeMillis()))
        }
        persist()
    }

    fun toggleHighlight(chunk: Int) {
        val existing = ann.notes.firstOrNull { it.chunk == chunk }
        if (existing != null) {
            ann.notes.removeAll { it.chunk == chunk }
        } else {
            ann.notes.add(
                Note(UUID.randomUUID().toString(), chunk, content.chunks[chunk].text, "", System.currentTimeMillis()),
            )
        }
        persist()
    }

    fun saveNote(edit: NoteEdit, text: String) {
        val existing = edit.existing ?: ann.notes.firstOrNull { it.chunk == edit.chunk }
        if (existing != null) {
            val i = ann.notes.indexOfFirst { it.id == existing.id }
            if (i >= 0) ann.notes[i] = existing.copy(note = text.trim())
        } else {
            ann.notes.add(
                Note(
                    UUID.randomUUID().toString(), edit.chunk, content.chunks[edit.chunk].text,
                    text.trim(), System.currentTimeMillis(),
                ),
            )
        }
        persist()
    }

    // --- system bars: hidden while reading, shown with the toolbars / overlays
    val barsVisible = chrome || overlay != Overlay.None
    LaunchedEffect(barsVisible) {
        val ctrl = WindowCompat.getInsetsController(activity.window, view)
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (barsVisible) ctrl.show(WindowInsetsCompat.Type.systemBars())
        else ctrl.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(Unit) {
        onDispose {
            WindowCompat.getInsetsController(activity.window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // --- reading brightness
    LaunchedEffect(settings.brightnessOn, settings.brightness) {
        val lp = activity.window.attributes
        lp.screenBrightness =
            if (settings.brightnessOn) settings.brightness else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = lp
    }
    DisposableEffect(Unit) {
        onDispose {
            val lp = activity.window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = lp
        }
    }

    // --- reading time
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(Unit) {
        var ticks = 0
        while (true) {
            delay(1000)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                if (System.currentTimeMillis() - rs.lastActive < 180_000L) rs.sec++
                ticks++
                if (rs.sec >= 10) flushStats()
                if (ticks % 10 == 0) saveProgress()
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        flushStats()
        saveProgress()
    }
    DisposableEffect(Unit) {
        onDispose {
            flushStats()
            saveProgress()
        }
    }

    // --- notes need "All files access" the first time
    LaunchedEffect(vm.notesAccessMissing) {
        if (vm.notesAccessMissing && !promptedAccess) {
            promptedAccess = true
            accessPrompt = true
        }
    }

    BackHandler(enabled = overlay != Overlay.None || showAa || chrome) {
        when {
            overlay != Overlay.None -> overlay = Overlay.None
            showAa -> showAa = false
            chrome -> chrome = false
        }
    }

    val feedStart = remember(settings.mode) { rs.pos }

    Box(Modifier.fillMaxSize().background(PB.bg)) {
        // ---- the page "card"
        val scale by animateFloatAsState(if (chrome) 0.7f else 1f, label = "cardScale")
        val corner by animateDpAsState(if (chrome) 14.dp else 0.dp, label = "cardCorner")
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationY = -(1f - scale) / 0.3f * 0.025f * size.height
                    shape = RoundedCornerShape(corner)
                    clip = true
                },
        ) {
            when (settings.mode) {
                ReadMode.Feed -> FeedView(
                    content = content,
                    style = style,
                    initialIndex = feedStart,
                    jumpTick = rs.jumpTick,
                    currentChunk = { rs.pos },
                    highlighted = highlighted,
                    onIndex = { rs.feedTo(it) },
                    onLongPress = { actionChunk = it; rs.touch() },
                    onTap = {
                        showAa = false
                        chrome = !chrome
                        rs.touch()
                    },
                )

                ReadMode.Pages -> PagedHost(
                    content = content,
                    style = style,
                    jumpTick = rs.jumpTick,
                    currentChunk = { rs.pos },
                    highlighted = highlighted,
                    onPage = { rs.pagedTo(it) },
                    onLongPress = { actionChunk = it; rs.touch() },
                    onCenterTap = {
                        showAa = false
                        chrome = !chrome
                        rs.touch()
                    },
                )
            }
            if (settings.nightLight) {
                Box(Modifier.fillMaxSize().background(Color(0x33FF8A00)))
            }
            if (bookmarked) {
                Canvas(
                    Modifier
                        .padding(start = 22.dp)
                        .size(width = 22.dp, height = 34.dp)
                        .align(Alignment.TopStart),
                ) {
                    val p = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, size.height)
                        lineTo(size.width / 2f, size.height * 0.72f)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(p, PB.accent)
                }
            }
        }

        // ---- top bar
        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            Row(
                Modifier.fillMaxWidth().background(PB.bg).statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PB.text)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { overlay = Overlay.Search }) {
                    Icon(Icons.Filled.Search, "Search in book", tint = PB.text)
                }
                IconButton(onClick = { showAa = true; chrome = false }) {
                    Text("Aa", color = PB.text, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = PB.text) }
                    PbMenu(menu, { menu = false }) {
                        PbMenuItem(if (bookmarked) "Remove bookmark" else "Add bookmark") {
                            menu = false
                            toggleBookmark()
                        }
                        PbMenuItem(if (settings.mode == ReadMode.Feed) "Read in pages" else "Read as feed") {
                            menu = false
                            settings.setMode(if (settings.mode == ReadMode.Feed) ReadMode.Pages else ReadMode.Feed)
                        }
                    }
                }
            }
        }

        // ---- bottom bar
        AnimatedVisibility(
            visible = chrome,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
        ) {
            val total = content.totalVirtualPages
            val page = content.virtualPage(rs.pos)
            val shown = dragValue ?: page.toFloat()
            Column(
                Modifier.fillMaxWidth().background(PB.bg).navigationBarsPadding().padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val left = (total - shown.roundToInt()).coerceAtLeast(0)
                Text(
                    "$left pages left in book", color = PB.text2, fontSize = 17.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { contentsTab = 0; overlay = Overlay.Contents }) {
                        Icon(Icons.Filled.Toc, "Contents", tint = PB.text)
                    }
                    Slider(
                        value = shown.coerceIn(1f, maxOf(total, 2).toFloat()),
                        onValueChange = { dragValue = it },
                        onValueChangeFinished = {
                            val v = dragValue
                            dragValue = null
                            if (v != null) rs.jumpTo(content.chunkOfVirtualPage(v.roundToInt()))
                        },
                        valueRange = 1f..maxOf(total, 2).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = PB.accent,
                            activeTrackColor = PB.accent,
                            inactiveTrackColor = PB.outline,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${shown.roundToInt()} / $total", color = PB.text2, fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }

        // ---- Aa panel
        if (showAa) {
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { showAa = false } },
            )
            AaPanel(
                settings,
                Modifier.align(Alignment.TopEnd).padding(top = 48.dp, end = 16.dp),
            )
        }

        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        )

        // ---- overlays
        if (overlay == Overlay.Contents) {
            ContentsOverlay(
                title = title,
                content = content,
                ann = ann,
                pos = rs.pos,
                initialTab = contentsTab,
                onJump = { c ->
                    rs.jumpTo(c)
                    overlay = Overlay.None
                    chrome = false
                },
                onClose = { overlay = Overlay.None },
                onDeleteBookmark = { b -> ann.bookmarks.remove(b); persist() },
                onDeleteNote = { n -> ann.notes.removeAll { it.id == n.id }; persist() },
                onEditNote = { n -> noteEdit = NoteEdit(n.chunk, n) },
            )
        }
        if (overlay == Overlay.Search) {
            SearchInBook(
                content = content,
                onJump = { c ->
                    rs.jumpTo(c)
                    overlay = Overlay.None
                    chrome = false
                },
                onClose = { overlay = Overlay.None },
            )
        }
    }

    // ---- long-press actions
    val ac = actionChunk
    if (ac != null && ac in content.chunks.indices) {
        val existing = ann.notes.firstOrNull { it.chunk == ac }
        ModalBottomSheet(
            onDismissRequest = { actionChunk = null },
            containerColor = PB.surface,
            contentColor = PB.text,
        ) {
            Text(
                content.chunks[ac].text, color = PB.text2, fontSize = 15.sp, maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SheetAction(Icons.Filled.Highlight, if (existing != null) "Remove highlight" else "Highlight") {
                actionChunk = null
                toggleHighlight(ac)
            }
            SheetAction(Icons.Filled.Edit, if (existing != null && existing.note.isNotBlank()) "Edit note" else "Add note") {
                actionChunk = null
                noteEdit = NoteEdit(ac, existing)
            }
            SheetAction(Icons.Filled.ContentCopy, "Copy") {
                actionChunk = null
                clipboard.setText(AnnotatedString(content.chunks[ac].text))
            }
            val hasBm = ann.bookmarks.any { it.chunk == ac }
            SheetAction(if (hasBm) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (hasBm) "Remove bookmark" else "Bookmark") {
                actionChunk = null
                if (hasBm) ann.bookmarks.removeAll { it.chunk == ac } else ann.bookmarks.add(Bookmark(ac, System.currentTimeMillis()))
                persist()
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }

    noteEdit?.let { e ->
        TextInputDialog(
            title = "Note",
            initial = e.existing?.note.orEmpty(),
            confirm = "Save",
            multiline = true,
            onDismiss = { noteEdit = null },
            onConfirm = { text ->
                saveNote(e, text)
                noteEdit = null
            },
        )
    }

    if (accessPrompt) NotesAccessDialog { accessPrompt = false }
}

@Composable
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = PB.text2)
        Text(label, color = PB.text, fontSize = 18.sp, modifier = Modifier.padding(start = 20.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Text rendering
// ---------------------------------------------------------------------------------------------

@Composable
private fun ChunkText(
    text: String,
    heading: Boolean,
    highlighted: Boolean,
    style: ReaderStyle,
    modifier: Modifier = Modifier,
) {
    val ts = if (heading) style.heading else style.para
    if (highlighted) {
        val a = remember(text, style.theme) {
            buildAnnotatedString {
                withStyle(SpanStyle(background = style.theme.highlight)) { append(text) }
            }
        }
        Text(a, style = ts, modifier = modifier)
    } else {
        Text(text, style = ts, modifier = modifier)
    }
}

// ---------------------------------------------------------------------------------------------
// Feed mode: vertical scrolling chunks
// ---------------------------------------------------------------------------------------------

@Composable
private fun FeedView(
    content: BookContent,
    style: ReaderStyle,
    initialIndex: Int,
    jumpTick: Int,
    currentChunk: () -> Int,
    highlighted: Set<Int>,
    onIndex: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    onTap: () -> Unit,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val tap by rememberUpdatedState(onTap)
    val density = LocalDensity.current
    val headingGap = with(density) { (style.lineSp * Paginator.HEADING_GAP_LINES).sp.toDp() }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { onIndex(it) }
    }
    LaunchedEffect(jumpTick) {
        if (jumpTick > 0) listState.scrollToItem(currentChunk().coerceIn(0, (content.chunks.size - 1).coerceAtLeast(0)))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(style.theme.bg)
            .pointerInput(Unit) { detectTapGestures(onTap = { tap() }) },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = PAD_H, end = PAD_H, top = PAD_TOP, bottom = PAD_BOTTOM),
        ) {
            items(
                count = content.chunks.size,
                key = { it },
                contentType = { if (content.chunks[it].heading) 1 else 0 },
            ) { i ->
                val ch = content.chunks[i]
                Column(Modifier.fillMaxWidth().longPressOnly { onLongPress(i) }) {
                    if (ch.heading) Spacer(Modifier.height(headingGap))
                    ChunkText(
                        text = ch.text,
                        heading = ch.heading,
                        highlighted = i in highlighted,
                        style = style,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (ch.heading) Spacer(Modifier.height(headingGap))
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(style.theme.text.copy(alpha = 0.08f)))
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Page mode: real page-curl
// ---------------------------------------------------------------------------------------------

@Composable
private fun PagedHost(
    content: BookContent,
    style: ReaderStyle,
    jumpTick: Int,
    currentChunk: () -> Int,
    highlighted: Set<Int>,
    onPage: (List<PageItem>) -> Unit,
    onLongPress: (Int) -> Unit,
    onCenterTap: () -> Unit,
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth
        val hPx = constraints.maxHeight
        var pagination by remember { mutableStateOf<Pagination?>(null) }

        LaunchedEffect(style.font, style.sizeSp, style.lineSp, wPx, hPx, density.density, density.fontScale) {
            pagination = null
            val padH = with(density) { PAD_H.roundToPx() }
            val padTop = with(density) { PAD_TOP.roundToPx() }
            val padBottom = with(density) { PAD_BOTTOM.roundToPx() }
            val spec = PageSpec(
                widthPx = wPx - 2 * padH,
                heightPx = hPx - padTop - padBottom,
                textPx = with(density) { style.sizeSp.sp.toPx() },
                lineHeightPx = with(density) { style.lineSp.sp.toPx() },
                indentPx = with(density) { (style.sizeSp * 1.5f).sp.toPx() }.roundToInt(),
            )
            val tf = style.font.typeface(ctx)
            pagination = withContext(Dispatchers.Default) { Paginator.paginate(content, spec, tf) }
        }

        val pag = pagination
        if (pag == null) {
            Box(Modifier.fillMaxSize().background(style.theme.bg), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PB.accent)
                    Text(
                        "Preparing pages…", color = style.theme.text.copy(alpha = 0.7f), fontSize = 15.sp,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        } else {
            androidx.compose.runtime.key(pag) {
                PagedView(
                    content = content,
                    style = style,
                    pag = pag,
                    startPage = pag.pageOfChunk[currentChunk().coerceIn(0, pag.pageOfChunk.size - 1)],
                    jumpTick = jumpTick,
                    currentChunk = currentChunk,
                    highlighted = highlighted,
                    onPage = onPage,
                    onLongPress = onLongPress,
                    onCenterTap = onCenterTap,
                )
            }
        }
    }
}

@Composable
private fun PagedView(
    content: BookContent,
    style: ReaderStyle,
    pag: Pagination,
    startPage: Int,
    jumpTick: Int,
    currentChunk: () -> Int,
    highlighted: Set<Int>,
    onPage: (List<PageItem>) -> Unit,
    onLongPress: (Int) -> Unit,
    onCenterTap: () -> Unit,
) {
    val pageState = rememberPageCurlState(initialCurrent = startPage)
    val centerTap by rememberUpdatedState(onCenterTap)
    val config = rememberPageCurlConfig(
        backPageColor = style.theme.bg,
        backPageContentAlpha = 0.1f,
        shadowAlpha = 0.25f,
        dragInteraction = PageCurlConfig.GestureDragInteraction(),
        tapInteraction = PageCurlConfig.TargetTapInteraction(
            forward = PageCurlConfig.TargetTapInteraction.Config(Rect(0.7f, 0f, 1f, 1f)),
            backward = PageCurlConfig.TargetTapInteraction.Config(Rect(0f, 0f, 0.3f, 1f)),
        ),
        onCustomTap = { size, offset ->
            if (offset.x > size.width * 0.3f && offset.x < size.width * 0.7f) {
                centerTap()
                true
            } else {
                false
            }
        },
    )
    SideEffect { config.backPageColor = style.theme.bg }

    LaunchedEffect(pageState) {
        snapshotFlow { pageState.current }.collect { p ->
            pag.pages.getOrNull(p)?.let(onPage)
        }
    }
    LaunchedEffect(jumpTick) {
        if (jumpTick > 0) {
            val c = currentChunk().coerceIn(0, pag.pageOfChunk.size - 1)
            pageState.snapTo(pag.pageOfChunk[c])
        }
    }

    PageCurl(
        count = pag.pages.size,
        state = pageState,
        config = config,
        modifier = Modifier.fillMaxSize(),
    ) { idx ->
        PageView(pag.pages[idx], content, style, highlighted, onLongPress)
    }
}

@Composable
private fun PageView(
    items: List<PageItem>,
    content: BookContent,
    style: ReaderStyle,
    highlighted: Set<Int>,
    onLongPress: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val gap = with(density) { (style.lineSp * Paginator.HEADING_GAP_LINES).sp.toDp() }
    Column(
        Modifier
            .fillMaxSize()
            .background(style.theme.bg)
            .padding(start = PAD_H, end = PAD_H, top = PAD_TOP, bottom = PAD_BOTTOM),
    ) {
        for (item in items) {
            val ch = content.chunks[item.chunk]
            ChunkText(
                text = item.text,
                heading = ch.heading,
                highlighted = item.chunk in highlighted,
                style = style,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (ch.heading) Modifier.padding(vertical = gap) else Modifier)
                    .longPressOnly { onLongPress(item.chunk) },
            )
        }
    }
}
