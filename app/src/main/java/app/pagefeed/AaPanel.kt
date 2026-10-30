package app.pagefeed

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The "Aa" popup: Text and Lighting tabs, laid out like the Play Books reader panel. */
@Composable
fun AaPanel(s: ReaderSettings, modifier: Modifier = Modifier) {
    var tab by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .width(284.dp)
            .clip(shape)
            .background(PB.surface)
            .border(1.dp, PB.outline, shape)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        SimpleTabs(listOf("Text", "Lighting"), tab) { tab = it }
        if (tab == 0) TextTab(s) else LightingTab(s)
    }
}

@Composable
private fun FontChip(font: ReaderFont, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (selected) PB.accent else Color.Transparent)
                .border(1.5.dp, if (selected) PB.accent else PB.outlineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "A",
                fontFamily = font.family,
                fontSize = 34.sp,
                color = if (selected) PB.onAccent else PB.text2,
            )
        }
        Text(
            font.label,
            color = if (selected) PB.accent else PB.text2,
            fontSize = 13.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun TextTab(s: ReaderSettings) {
    var alignMenu by remember { mutableStateOf(false) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (f in ReaderFont.entries) FontChip(f, s.font == f) { s.setFont(f) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(Modifier.weight(1f), { s.setTextPct(s.textPct - 4) }) {
                Text("T", color = PB.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                "${s.textPct}%", color = PB.text2, fontSize = 15.sp,
                textAlign = TextAlign.Center, modifier = Modifier.width(64.dp),
            )
            StepButton(Modifier.weight(1f), { s.setTextPct(s.textPct + 4) }) {
                Text("T", color = PB.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(Modifier.weight(1f), { s.setSpacingPct(s.spacingPct - 10) }) {
                Icon(Icons.Filled.VerticalAlignCenter, "Tighter lines", tint = PB.text)
            }
            Text(
                "${s.spacingPct}%", color = PB.text2, fontSize = 15.sp,
                textAlign = TextAlign.Center, modifier = Modifier.width(64.dp),
            )
            StepButton(Modifier.weight(1f), { s.setSpacingPct(s.spacingPct + 10) }) {
                Icon(Icons.Filled.FormatLineSpacing, "Looser lines", tint = PB.text)
            }
        }

        Box {
            Row(
                Modifier.fillMaxWidth().clickable { alignMenu = true }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.FormatAlignLeft, null, tint = PB.text2)
                Text(
                    s.align.label, color = PB.text, fontSize = 20.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                )
                Icon(Icons.Filled.ArrowDropDown, null, tint = PB.text2)
            }
            PbMenu(alignMenu, { alignMenu = false }) {
                for (a in ReaderAlign.entries) PbMenuItem(a.label) { s.setAlign(a); alignMenu = false }
            }
        }
    }
}

@Composable
private fun LightingTab(s: ReaderSettings) {
    val ctx = LocalContext.current
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = PB.onAccent,
        checkedTrackColor = PB.accent,
        uncheckedThumbColor = PB.outlineStrong,
        uncheckedTrackColor = PB.surface,
        uncheckedBorderColor = PB.outlineStrong,
    )
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reading brightness", color = PB.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Switch(checked = s.brightnessOn, onCheckedChange = { s.setBrightnessOn(it) }, colors = switchColors)
        }
        Slider(
            value = s.brightness,
            onValueChange = { s.setBrightness(it) },
            enabled = s.brightnessOn,
            colors = SliderDefaults.colors(
                thumbColor = PB.accent,
                activeTrackColor = PB.accent,
                inactiveTrackColor = PB.outline,
                disabledThumbColor = PB.outlineStrong,
                disabledActiveTrackColor = PB.outlineStrong,
                disabledInactiveTrackColor = PB.outline,
            ),
        )
        Text("Viewing theme", color = PB.text, fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (t in ReaderTheme.entries) {
                val sel = s.theme == t
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(shape)
                        .background(t.bg)
                        .border(if (sel) 2.dp else 1.dp, if (sel) PB.accent else PB.outlineStrong, shape)
                        .clickable { s.setTheme(t) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (sel) {
                        Icon(
                            Icons.Filled.Check, null,
                            tint = if (t == ReaderTheme.Black) Color.White else Color.Black,
                        )
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reading Night Light", color = PB.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Icon(
                Icons.Outlined.Info, "About night light", tint = PB.text2,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(24.dp)
                    .clickable {
                        Toast.makeText(ctx, "Warms the page colors to be easier on the eyes at night.", Toast.LENGTH_LONG).show()
                    },
            )
            Switch(checked = s.nightLight, onCheckedChange = { s.setNightLight(it) }, colors = switchColors)
        }
    }
}
