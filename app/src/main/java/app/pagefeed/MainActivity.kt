package app.pagefeed

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        if (savedInstanceState == null) handleIntent(intent)
        setContent { PagefeedTheme { AppRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshAccess()
    }

    override fun onPause() {
        super.onPause()
        vm.flushStats()
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_VIEW) {
            val uri = i.data
            if (uri != null) vm.importUris(listOf(uri), true)
            i.action = Intent.ACTION_MAIN
        }
    }
}

@Composable
fun AppRoot(vm: AppVm) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        vm.toasts.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.importUris(uris, false)
    }
    val pickBooks = remember {
        {
            picker.launch(
                arrayOf(
                    "application/epub+zip", "application/pdf", "text/plain",
                    "text/markdown", "text/x-markdown", "application/octet-stream",
                ),
            )
        }
    }

    BackHandler(enabled = vm.stack.size > 1) { vm.pop() }

    Box(Modifier.fillMaxSize().background(PB.bg)) {
        when (val top = vm.stack.last()) {
            Screen.Home -> MainScaffold(vm, 0) { pad -> HomeScreen(vm, pad, pickBooks) }
            Screen.Library -> MainScaffold(vm, 1) { pad -> LibraryScreen(vm, pad, pickBooks) }
            is Screen.ShelfDetail -> ShelfDetailScreen(vm, top.name)
            is Screen.Reader -> ReaderScreen(vm, top.id)
            Screen.LibrarySearch -> BookSearchScreen(vm)
            Screen.Settings -> SettingsScreen(vm)
            Screen.ReadingActivity -> ReadingActivityScreen(vm)
        }

        val importing = vm.importing
        if (importing != null) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth()
                    .background(PB.chip, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Importing $importing…", color = PB.text, fontSize = 15.sp, maxLines = 1)
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = PB.accent,
                    trackColor = PB.outline,
                )
            }
        }
    }
}
