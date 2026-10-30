package app.pagefeed

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat

private val literataFamily = FontFamily(Font(R.font.literata))
private val merriweatherFamily = FontFamily(Font(R.font.merriweather))
private val goudyFamily = FontFamily(Font(R.font.goudy))
private val vollkornFamily = FontFamily(Font(R.font.vollkorn))

enum class ReaderFont(val label: String) {
    Original("Original"),
    Sans("Sans"),
    Literata("Literata"),
    Merriweather("Merriweather"),
    Goudy("Goudy"),
    Vollkorn("Vollkorn");

    val family: FontFamily
        get() = when (this) {
            Original -> FontFamily.Serif
            Sans -> FontFamily.SansSerif
            Literata -> literataFamily
            Merriweather -> merriweatherFamily
            Goudy -> goudyFamily
            Vollkorn -> vollkornFamily
        }

    fun typeface(ctx: Context): Typeface {
        val res = when (this) {
            Original -> return Typeface.SERIF
            Sans -> return Typeface.SANS_SERIF
            Literata -> R.font.literata
            Merriweather -> R.font.merriweather
            Goudy -> R.font.goudy
            Vollkorn -> R.font.vollkorn
        }
        return ResourcesCompat.getFont(ctx, res) ?: Typeface.SERIF
    }
}

enum class ReaderTheme(val bg: Color, val text: Color, val highlight: Color) {
    White(Color(0xFFFFFFFF), Color(0xFF1F1F1F), Color(0xFFFFE680)),
    Sepia(Color(0xFFEAE1CC), Color(0xFF3B2F1E), Color(0xFFF0D77A)),
    Black(Color(0xFF000000), Color(0xFFCCCCCC), Color(0xFF604E1E)),
}

enum class ReaderAlign(val label: String) { Default("Default"), Left("Left"), Justify("Justify") }

enum class ReadMode { Feed, Pages }

private inline fun <reified T : Enum<T>> enumOr(name: String?, def: T): T =
    try {
        if (name == null) def else enumValueOf<T>(name)
    } catch (e: Exception) {
        def
    }

class ReaderSettings(private val sp: SharedPreferences) {
    var font by mutableStateOf(enumOr(sp.getString("font", null), ReaderFont.Original))
        private set
    var textPct by mutableIntStateOf(sp.getInt("textPct", 100))
        private set
    var spacingPct by mutableIntStateOf(sp.getInt("spacingPct", 100))
        private set
    var align by mutableStateOf(enumOr(sp.getString("align", null), ReaderAlign.Default))
        private set
    var theme by mutableStateOf(enumOr(sp.getString("theme", null), ReaderTheme.Black))
        private set
    var brightnessOn by mutableStateOf(sp.getBoolean("brightnessOn", false))
        private set
    var brightness by mutableFloatStateOf(sp.getFloat("brightness", 0.5f))
        private set
    var nightLight by mutableStateOf(sp.getBoolean("nightLight", false))
        private set
    var mode by mutableStateOf(enumOr(sp.getString("mode", null), ReadMode.Feed))
        private set

    @JvmName("setFontExplicit")
    fun setFont(v: ReaderFont) { font = v; sp.edit().putString("font", v.name).apply() }
    
    @JvmName("setTextPctExplicit")
    fun setTextPct(v: Int) { textPct = v.coerceIn(60, 200); sp.edit().putInt("textPct", textPct).apply() }
    
    @JvmName("setSpacingPctExplicit")
    fun setSpacingPct(v: Int) { spacingPct = v.coerceIn(80, 200); sp.edit().putInt("spacingPct", spacingPct).apply() }
    
    @JvmName("setAlignExplicit")
    fun setAlign(v: ReaderAlign) { align = v; sp.edit().putString("align", v.name).apply() }
    
    @JvmName("setThemeExplicit")
    fun setTheme(v: ReaderTheme) { theme = v; sp.edit().putString("theme", v.name).apply() }
    
    @JvmName("setBrightnessOnExplicit")
    fun setBrightnessOn(v: Boolean) { brightnessOn = v; sp.edit().putBoolean("brightnessOn", v).apply() }
    
    @JvmName("setBrightnessExplicit")
    fun setBrightness(v: Float) { brightness = v.coerceIn(0.02f, 1f); sp.edit().putFloat("brightness", brightness).apply() }
    
    @JvmName("setNightLightExplicit")
    fun setNightLight(v: Boolean) { nightLight = v; sp.edit().putBoolean("nightLight", v).apply() }
    
    @JvmName("setModeExplicit")
    fun setMode(v: ReadMode) { mode = v; sp.edit().putString("mode", v.name).apply() }
}