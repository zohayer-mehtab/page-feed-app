@file:OptIn(ExperimentalFoundationApi::class)

package app.pagefeed

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

@Composable
fun PbMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(PB.menu),
        content = content,
    )
}

@Composable
fun PbMenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(label, color = PB.text, fontSize = 17.sp) }, onClick = onClick)
}

@Composable
fun StepButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, PB.outlineStrong, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun TopBarSimple(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PB.text)
        }
        Text(
            title, color = PB.text, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        actions()
    }
}

@Composable
fun SimpleTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            titles.forEachIndexed { i, t ->
                Column(
                    Modifier.weight(1f).clickable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        t,
                        color = if (i == selected) PB.accent else PB.text2,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                    )
                    Box(
                        Modifier
                            .height(3.dp)
                            .width(if (i == selected) 56.dp else 0.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(PB.accent),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(PB.outline))
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    confirm: String,
    multiline: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PB.surface,
        title = { Text(title, color = PB.text) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = !multiline,
                minLines = if (multiline) 3 else 1,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PB.text,
                    unfocusedTextColor = PB.text,
                    focusedBorderColor = PB.accent,
                    unfocusedBorderColor = PB.outlineStrong,
                    cursorColor = PB.accent,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirm, color = PB.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PB.accent) } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PB.surface,
        title = { Text(title, color = PB.text) },
        text = { Text(message, color = PB.text2) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = PB.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = PB.accent) } },
    )
}

@Composable
fun PbFab(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = PB.fab,
        contentColor = PB.text,
        icon = { Icon(icon, null) },
        text = { Text(label, fontSize = 17.sp) },
    )
}

// ---------------------------------------------------------------------------------------------
// Scaffold + header
// ---------------------------------------------------------------------------------------------

@Composable
fun MainScaffold(vm: AppVm, selected: Int, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = PB.bg,
        bottomBar = {
            NavigationBar(containerColor = PB.surface, contentColor = PB.text) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = PB.text,
                    selectedTextColor = PB.text,
                    indicatorColor = PB.chip,
                    unselectedIconColor = PB.text2,
                    unselectedTextColor = PB.text2,
                )
                NavigationBarItem(
                    selected = selected == 0,
                    onClick = { if (selected != 0) vm.goTab(Screen.Home) },
                    icon = { Icon(if (selected == 0) Icons.Filled.Home else Icons.Outlined.Home, null) },
                    label = { Text("Home") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = selected == 1,
                    onClick = { if (selected != 1) vm.goTab(Screen.Library) },
                    icon = {
                        Icon(
                            if (selected == 1) Icons.Filled.LibraryBooks else Icons.Outlined.LibraryBooks,
                            null,
                        )
                    },
                    label = { Text("Library") },
                    colors = colors,
                )
            }
        },
        content = content,
    )
}

@Composable
fun HeaderBar(vm: AppVm) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(PB.searchBar)
            .clickable { vm.push(Screen.LibrarySearch) }
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, null, tint = PB.text2)
        Text(
            "Search your books", color = PB.text2, fontSize = 18.sp,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        Box {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(2.dp, PB.accent, CircleShape)
                    .clickable { menu = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, "Menu", tint = PB.text2)
            }
            PbMenu(menu, { menu = false }) {
                PbMenuItem("Settings") { menu = false; vm.push(Screen.Settings) }
            }
        }
    }
}

@Composable
fun EmptyLibrary(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.LibraryBooks, null, tint = PB.text2, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("No books yet", color = PB.text, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add EPUB, PDF, TXT or Markdown files here, or open them from your file manager.",
            color = PB.text2, fontSize = 16.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        PbFab("Add book", Icons.Filled.Add, onAdd)
    }
}

// ---------------------------------------------------------------------------------------------
// Home
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(vm: AppVm, pad: PaddingValues, pickBooks: () -> Unit) {
    Column(Modifier.padding(pad).fillMaxSize()) {
        HeaderBar(vm)
        if (vm.books.isEmpty()) {
            EmptyLibrary(pickBooks)
        } else {
            val opened = vm.books.filter { it.lastOpened > 0 }.sortedByDescending { it.lastOpened }
            val list = if (opened.isEmpty()) vm.books.sortedByDescending { it.added } else opened
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(list.take(12), key = { it.id }) { b ->
                    Column(
                        Modifier.width(156.dp).clickable { vm.openBook(b.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BookCover(vm.repo, b, Modifier.width(156.dp).height(240.dp), corner = 16.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            b.title, color = PB.text, fontSize = 17.sp, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        )
                        Text("${b.percent}% complete", color = PB.text2, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Library
// ---------------------------------------------------------------------------------------------

@Composable
fun BookRow(vm: AppVm, book: Book, menuItems: List<Pair<String, () -> Unit>>) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = { vm.openBook(book.id) }, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            BookCover(vm.repo, book, Modifier.width(52.dp).height(80.dp))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(book.title, color = PB.text, fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) {
                    Text(book.author, color = PB.text2, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("${book.percent}% complete", color = PB.text2, fontSize = 17.sp)
            }
            Icon(Icons.Filled.Check, null, tint = PB.accent, modifier = Modifier.padding(top = 4.dp))
        }
        PbMenu(menu, { menu = false }) {
            for ((label, action) in menuItems) PbMenuItem(label) { menu = false; action() }
        }
    }
}

@Composable
fun LibraryScreen(vm: AppVm, pad: PaddingValues, pickBooks: () -> Unit) {
    Box(Modifier.padding(pad).fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HeaderBar(vm)
            SimpleTabs(listOf("Your books", "Shelves"), vm.libraryTab) { vm.libraryTab = it }
            if (vm.libraryTab == 0) YourBooks(vm, pickBooks) else ShelvesTab(vm)
        }
        if (vm.libraryTab == 0) {
            PbFab(
                "Add book", Icons.Filled.Add, pickBooks,
                Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

@Composable
private fun YourBooks(vm: AppVm, pickBooks: () -> Unit) {
    var sort by remember { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var shelfPickFor by remember { mutableStateOf<Book?>(null) }
    var removeFor by remember { mutableStateOf<Book?>(null) }
    val labels = listOf("Recent", "Title", "Author")

    if (vm.books.isEmpty()) {
        EmptyLibrary(pickBooks)
        return
    }
    val sorted = when (sort) {
        1 -> vm.books.sortedBy { it.title.lowercase() }
        2 -> vm.books.sortedBy { it.author.lowercase() }
        else -> vm.books.sortedByDescending { maxOf(it.lastOpened, it.added) }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(PB.chip)
                    .clickable { sortMenu = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.Sort, null, tint = PB.text, modifier = Modifier.size(20.dp))
                Text(labels[sort], color = PB.text, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Icon(Icons.Filled.ArrowDropDown, null, tint = PB.text, modifier = Modifier.size(20.dp))
            }
            PbMenu(sortMenu, { sortMenu = false }) {
                labels.forEachIndexed { i, l -> PbMenuItem(l) { sort = i; sortMenu = false } }
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            items(sorted, key = { it.id }) { b ->
                BookRow(
                    vm, b,
                    listOf(
                        "Add to shelf" to { shelfPickFor = b },
                        "Remove from library" to { removeFor = b },
                    ),
                )
            }
        }
    }

    shelfPickFor?.let { b -> ShelfPickerDialog(vm, b) { shelfPickFor = null } }
    removeFor?.let { b ->
        ConfirmDialog(
            title = "Remove book?",
            message = "“${b.title}” will be removed from your library. Its notes file in “notes from books” stays.",
            confirm = "Remove",
            onDismiss = { removeFor = null },
            onConfirm = { vm.removeBook(b.id); removeFor = null },
        )
    }
}

@Composable
fun ShelfPickerDialog(vm: AppVm, book: Book, onDismiss: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        val ctx = LocalContext.current
        TextInputDialog(
            title = "New shelf", confirm = "Create",
            onDismiss = { creating = false },
            onConfirm = { name ->
                if (vm.createShelf(name)) {
                    vm.addToShelf(name.trim(), book.id)
                    creating = false
                    onDismiss()
                } else {
                    Toast.makeText(ctx, "Pick a different shelf name", Toast.LENGTH_SHORT).show()
                }
            },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PB.surface,
        title = { Text("Add to shelf", color = PB.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (s in vm.shelves) {
                    val has = book.id in s.bookIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (has) vm.removeFromShelf(s.name, book.id) else vm.addToShelf(s.name, book.id)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.name, color = PB.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        if (has) Icon(Icons.Filled.Check, null, tint = PB.accent)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { creating = true }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, null, tint = PB.accent)
                    Text("New shelf", color = PB.accent, fontSize = 18.sp, modifier = Modifier.padding(start = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = PB.accent) } },
    )
}

@Composable
private fun ShelvesTab(vm: AppVm) {
    var creating by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize()) {
        if (vm.shelves.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No shelves yet", color = PB.text, fontSize = 22.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Create a shelf to group your books.",
                    color = PB.text2, fontSize = 16.sp, textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                items(vm.shelves.toList(), key = { it.name }) { s ->
                    val first = s.bookIds.firstNotNullOfOrNull { vm.book(it) }
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.push(Screen.ShelfDetail(s.name)) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(80.dp).height(84.dp)) {
                                Box(
                                    Modifier.padding(start = 22.dp, top = 6.dp).width(56.dp).height(76.dp)
                                        .clip(RoundedCornerShape(6.dp)).background(PB.surface),
                                )
                                if (first != null) {
                                    BookCover(vm.repo, first, Modifier.width(56.dp).height(80.dp))
                                } else {
                                    Box(Modifier.width(56.dp).height(80.dp).clip(RoundedCornerShape(6.dp)).background(PB.placeholder))
                                }
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(s.name, color = PB.text, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val n = s.bookIds.count { vm.book(it) != null }
                                Text(if (n == 1) "1 book" else "$n books", color = PB.text2, fontSize = 17.sp)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = PB.accent)
                        }
                        Box(Modifier.fillMaxWidth().height(1.dp).background(PB.outline))
                    }
                }
            }
        }
        PbFab(
            "Create new", Icons.Filled.Edit, { creating = true },
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
    if (creating) {
        TextInputDialog(
            title = "Create shelf", confirm = "Create",
            onDismiss = { creating = false },
            onConfirm = { name ->
                if (vm.createShelf(name)) creating = false
                else Toast.makeText(ctx, "Pick a different shelf name", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
fun ShelfDetailScreen(vm: AppVm, name: String) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val shelf = vm.shelves.firstOrNull { it.name == name }
    Column(Modifier.fillMaxSize()) {
        TopBarSimple(name, { vm.pop() }) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = PB.text) }
                PbMenu(menu, { menu = false }) {
                    PbMenuItem("Rename shelf") { menu = false; renaming = true }
                    PbMenuItem("Delete shelf") { menu = false; deleting = true }
                }
            }
        }
        val items = shelf?.bookIds?.mapNotNull { vm.book(it) }.orEmpty()
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("This shelf is empty", color = PB.text, fontSize = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "In Your books, long-press a book and choose “Add to shelf”.",
                    color = PB.text2, fontSize = 16.sp, textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn {
                items(items, key = { it.id }) { b ->
                    BookRow(vm, b, listOf("Remove from shelf" to { vm.removeFromShelf(name, b.id) }))
                }
            }
        }
    }
    if (renaming) {
        TextInputDialog(
            title = "Rename shelf", initial = name, confirm = "Rename",
            onDismiss = { renaming = false },
            onConfirm = { n ->
                if (vm.renameShelf(name, n)) renaming = false
                else Toast.makeText(ctx, "Pick a different shelf name", Toast.LENGTH_SHORT).show()
            },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete shelf?",
            message = "The books stay in your library.",
            confirm = "Delete",
            onDismiss = { deleting = false },
            onConfirm = { deleting = false; vm.deleteShelf(name); vm.pop() },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Search (library)
// ---------------------------------------------------------------------------------------------

@Composable
fun BookSearchScreen(vm: AppVm) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = PB.text) }
            BasicTextField(
                value = q,
                onValueChange = { q = it },
                singleLine = true,
                textStyle = TextStyle(color = PB.text, fontSize = 19.sp),
                cursorBrush = SolidColor(PB.accent),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box {
                        if (q.isEmpty()) Text("Search your books", color = PB.text2, fontSize = 19.sp)
                        inner()
                    }
                },
            )
            if (q.isNotEmpty()) {
                IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, "Clear", tint = PB.text2) }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(PB.outline))
        val query = q.trim()
        val results = if (query.isEmpty()) emptyList() else vm.books.filter {
            it.title.contains(query, ignoreCase = true) || it.author.contains(query, ignoreCase = true)
        }
        if (query.isNotEmpty() && results.isEmpty()) {
            Text("No books match “$query”", color = PB.text2, fontSize = 16.sp, modifier = Modifier.padding(24.dp))
        }
        LazyColumn {
            items(results, key = { it.id }) { b -> BookRow(vm, b, emptyList()) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------------------------

private fun openAllFilesAccess(ctx: android.content.Context) {
    if (Build.VERSION.SDK_INT < 30) return
    try {
        ctx.startActivity(
            Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${ctx.packageName}"),
            ),
        )
    } catch (e: Exception) {
        try {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        } catch (_: Exception) {
        }
    }
}

@Composable
fun NotesAccessDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PB.surface,
        title = { Text("Allow file access", color = PB.text) },
        text = {
            Text(
                "To save your highlights and notes into “${NotesExporter.FOLDER}” on your phone, " +
                    "Pagefeed needs “All files access”. Your notes are kept inside the app until you allow it.",
                color = PB.text2,
            )
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); openAllFilesAccess(ctx) }) { Text("Open settings", color = PB.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now", color = PB.accent) } },
    )
}

@Composable
fun SettingsScreen(vm: AppVm) {
    val ctx = LocalContext.current
    var taps by remember { mutableIntStateOf(0) }
    val granted = NotesExporter.hasAccess()
    Column(Modifier.fillMaxSize()) {
        TopBarSimple("Settings", { vm.pop() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Daily reading goal", color = PB.text, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StepButton(Modifier.width(88.dp), { vm.setGoal(vm.goalMin - 5) }) {
                    Text("−", color = PB.text, fontSize = 24.sp)
                }
                Text(
                    "${vm.goalMin} min", color = PB.text, fontSize = 18.sp,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                StepButton(Modifier.width(88.dp), { vm.setGoal(vm.goalMin + 5) }) {
                    Text("+", color = PB.text, fontSize = 24.sp)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(PB.outline))

            Text("Notes folder", color = PB.text, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp))
            Text(
                "${NotesExporter.folder().path}\nOne Markdown file per book, updated whenever you highlight or add a note.",
                color = PB.text2, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                if (granted) "All files access: allowed" else "All files access: not allowed",
                color = if (granted) PB.accent else PB.noteIcon, fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (!granted && Build.VERSION.SDK_INT >= 30) {
                TextButton(onClick = { openAllFilesAccess(ctx) }) { Text("Allow access", color = PB.accent) }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(PB.outline))

            if (vm.activityUnlocked) {
                Row(
                    Modifier.fillMaxWidth().clickable { vm.push(Screen.ReadingActivity) }.padding(vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Reading activity", color = PB.text, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = PB.accent)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(PB.outline))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (vm.activityUnlocked) return@clickable
                        taps++
                        val left = 5 - taps
                        if (left <= 0) {
                            vm.unlockActivity()
                            Toast.makeText(ctx, "Reading activity unlocked", Toast.LENGTH_SHORT).show()
                        } else if (taps >= 2) {
                            Toast.makeText(ctx, "$left more taps…", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .padding(vertical = 18.dp),
            ) {
                Text("About", color = PB.text, fontSize = 18.sp)
                Text("Pagefeed 1.0", color = PB.text2, fontSize = 15.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Reading activity (hidden record)
// ---------------------------------------------------------------------------------------------

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = PB.text2, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(value, color = PB.text, fontSize = 17.sp)
    }
}

@Composable
fun ReadingActivityScreen(vm: AppVm) {
    val days = vm.allDays()
    val today = vm.todayStat()
    val goalSec = vm.goalMin * 60
    val dateFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val labelFmt = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }
    val totalSec = days.values.sumOf { it.sec }
    val totalWords = days.values.sumOf { it.words }
    fun pages(words: Int) = (words / 250f).roundToInt()

    Column(Modifier.fillMaxSize()) {
        TopBarSimple("Reading activity", { vm.pop() })
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            item {
                Text("Today", color = PB.text, fontSize = 20.sp)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { if (goalSec == 0) 0f else (today.sec.toFloat() / goalSec).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = PB.accent,
                    trackColor = PB.outline,
                )
                Spacer(Modifier.height(6.dp))
                StatRow("Time", "${fmtDuration(today.sec)} of ${vm.goalMin} min")
                StatRow("Words", "%,d".format(today.words))
                StatRow("Pages (≈ 250 words)", "${pages(today.words)}")
                StatRow("Goal streak", "${vm.streak()} days")
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(PB.outline))
                Spacer(Modifier.height(16.dp))
                Text("All time", color = PB.text, fontSize = 20.sp)
                Spacer(Modifier.height(6.dp))
                StatRow("Time", fmtDuration(totalSec))
                StatRow("Words", "%,d".format(totalWords))
                StatRow("Pages (≈ 250 words)", "${pages(totalWords)}")
                StatRow("Days read", "${days.values.count { it.sec > 0 || it.words > 0 }}")
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(PB.outline))
                Spacer(Modifier.height(16.dp))
                Text("Recent days", color = PB.text, fontSize = 20.sp)
                Spacer(Modifier.height(6.dp))
            }
            val recent = days.entries.sortedByDescending { it.key }.take(30)
            if (recent.isEmpty()) {
                item { Text("Nothing recorded yet.", color = PB.text2, fontSize = 16.sp) }
            }
            items(recent, key = { it.key }) { e ->
                val label = try {
                    labelFmt.format(dateFmt.parse(e.key)!!)
                } catch (ex: Exception) {
                    e.key
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(label, color = PB.text, fontSize = 16.sp, modifier = Modifier.weight(1.2f))
                    Text(fmtDuration(e.value.sec), color = PB.text2, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(
                        "%,d w · ${pages(e.value.words)} p".format(e.value.words),
                        color = PB.text2, fontSize = 16.sp, modifier = Modifier.weight(1.3f), textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}
