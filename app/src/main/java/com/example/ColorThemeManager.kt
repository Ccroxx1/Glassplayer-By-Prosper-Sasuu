package com.example

import androidx.compose.ui.graphics.Color

/**
 * All available glassmorphic color themes for GlassPlayer.
 * DYNAMIC = extracted from album art via Palette (default behaviour).
 */
enum class GlassTheme(val displayName: String, val emoji: String) {
    DYNAMIC("Dynamic (Album Art)", "🎨"),
    PURPLE_HAZE("Purple Haze", "💜"),
    OCEAN_BLUE("Ocean Blue", "🌊"),
    ROSE_GOLD("Rose Gold", "🌸"),
    MIDNIGHT("Midnight Black", "🌑"),
    NEON_LIME("Neon Lime", "💚")
}

/**
 * A resolved set of colors for a given [GlassTheme].
 * Every composable that previously referenced [GlassDynamic] now uses this.
 */
data class GlassColors(
    val cyan: Color,
    val magenta: Color,
    val purple: Color,
    val accent: Color,
    val bgTint: Color,
    val glow: Color
)

object ColorThemeManager {

    fun resolve(theme: GlassTheme, dynamic: GlassColors): GlassColors = when (theme) {
        GlassTheme.DYNAMIC -> dynamic

        GlassTheme.PURPLE_HAZE -> GlassColors(
            cyan    = Color(0xFFBF5FFF),
            magenta = Color(0xFFE040FB),
            purple  = Color(0xFF7C4DFF),
            accent  = Color(0xFFBF5FFF),
            bgTint  = Color(0x221B0033),
            glow    = Color(0x99BF5FFF)
        )

        GlassTheme.OCEAN_BLUE -> GlassColors(
            cyan    = Color(0xFF00E5FF),
            magenta = Color(0xFF00B0FF),
            purple  = Color(0xFF0091EA),
            accent  = Color(0xFF00E5FF),
            bgTint  = Color(0x22001A33),
            glow    = Color(0x9900E5FF)
        )

        GlassTheme.ROSE_GOLD -> GlassColors(
            cyan    = Color(0xFFFF80AB),
            magenta = Color(0xFFF48FB1),
            purple  = Color(0xFFCE93D8),
            accent  = Color(0xFFFF80AB),
            bgTint  = Color(0x22330011),
            glow    = Color(0x99FF80AB)
        )

        GlassTheme.MIDNIGHT -> GlassColors(
            cyan    = Color(0xFF90A4AE),
            magenta = Color(0xFF78909C),
            purple  = Color(0xFF546E7A),
            accent  = Color(0xFFB0BEC5),
            bgTint  = Color(0x22000000),
            glow    = Color(0x6690A4AE)
        )

        GlassTheme.NEON_LIME -> GlassColors(
            cyan    = Color(0xFFCCFF00),
            magenta = Color(0xFF76FF03),
            purple  = Color(0xFF00E676),
            accent  = Color(0xFFCCFF00),
            bgTint  = Color(0x22001A00),
            glow    = Color(0x99CCFF00)
        )
    }

    /**
     * Returns display-ready chip info for every theme for the Settings picker.
     */
    fun allThemes(): List<GlassTheme> = GlassTheme.values().toList()
}
