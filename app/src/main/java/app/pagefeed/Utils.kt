package app.pagefeed

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detects a long press without consuming anything, so taps / scrolls / page-curl drags keep working.
 */
@Composable
fun Modifier.longPressOnly(onLongPress: () -> Unit): Modifier {
    val current by rememberUpdatedState(onLongPress)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val timeout = viewConfiguration.longPressTimeoutMillis
            val slop = viewConfiguration.touchSlop
            val ended = withTimeoutOrNull(timeout) {
                var finished = false
                while (!finished) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || change.changedToUp() ||
                        (change.position - down.position).getDistance() > slop
                    ) {
                        finished = true
                    }
                }
                true
            }
            if (ended == null) current()
        }
    }
}

object CoverCache {
    val cache = LruCache<String, ImageBitmap>(48)
}

@Composable
fun rememberCover(repo: Repo, book: Book): ImageBitmap? {
    val state = produceState<ImageBitmap?>(CoverCache.cache.get(book.id), book.id, book.hasCover) {
        if (value == null && book.hasCover) {
            value = withContext(Dispatchers.IO) {
                try {
                    val bmp = BitmapFactory.decodeFile(repo.coverFile(book.id).absolutePath)?.asImageBitmap()
                    if (bmp != null) CoverCache.cache.put(book.id, bmp)
                    bmp
                } catch (e: Throwable) {
                    null
                }
            }
        }
    }
    return state.value
}

@Composable
fun BookCover(repo: Repo, book: Book, modifier: Modifier, corner: Dp = 6.dp) {
    val img = rememberCover(repo, book)
    Box(modifier.clip(RoundedCornerShape(corner)).background(PB.placeholder)) {
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = book.title,
                color = PB.text2,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.Center).padding(6.dp),
            )
        }
    }
}

fun fmtDuration(sec: Int): String {
    val m = sec / 60
    return when {
        sec < 60 -> "${sec}s"
        m < 60 -> "$m min"
        else -> "${m / 60} h ${"%02d".format(m % 60)} min"
    }
}
