package com.rk.settings.ssh

import android.graphics.Color

/**
 * Terminal color scheme with 16-color palette and special colors
 */
data class TerminalTheme(
    val id: String,
    val name: String,
    val backgroundColor: Int,
    val foregroundColor: Int,
    val cursorColor: Int,
    // ANSI 16 color palette
    val black: Int,
    val red: Int,
    val green: Int,
    val yellow: Int,
    val blue: Int,
    val magenta: Int,
    val cyan: Int,
    val white: Int,
    val brightBlack: Int,
    val brightRed: Int,
    val brightGreen: Int,
    val brightYellow: Int,
    val brightBlue: Int,
    val brightMagenta: Int,
    val brightCyan: Int,
    val brightWhite: Int
) {
    /**
     * Get the 16-color palette as an array for the terminal emulator
     */
    fun getColorPalette(): IntArray {
        return intArrayOf(
            black, red, green, yellow, blue, magenta, cyan, white,
            brightBlack, brightRed, brightGreen, brightYellow, brightBlue, brightMagenta, brightCyan, brightWhite
        )
    }
}

/**
 * Built-in terminal themes collection
 */
object TerminalThemes {
    
    val default = TerminalTheme(
        id = "default",
        name = "Default",
        backgroundColor = Color.parseColor("#1E1E1E"),
        foregroundColor = Color.parseColor("#D4D4D4"),
        cursorColor = Color.parseColor("#AEAFAD"),
        black = Color.parseColor("#000000"),
        red = Color.parseColor("#CD3131"),
        green = Color.parseColor("#0DBC79"),
        yellow = Color.parseColor("#E5E510"),
        blue = Color.parseColor("#2472C8"),
        magenta = Color.parseColor("#BC3FBC"),
        cyan = Color.parseColor("#11A8CD"),
        white = Color.parseColor("#E5E5E5"),
        brightBlack = Color.parseColor("#666666"),
        brightRed = Color.parseColor("#F14C4C"),
        brightGreen = Color.parseColor("#23D18B"),
        brightYellow = Color.parseColor("#F5F543"),
        brightBlue = Color.parseColor("#3B8EEA"),
        brightMagenta = Color.parseColor("#D670D6"),
        brightCyan = Color.parseColor("#29B8DB"),
        brightWhite = Color.parseColor("#FFFFFF")
    )
    
    val dracula = TerminalTheme(
        id = "dracula",
        name = "Dracula",
        backgroundColor = Color.parseColor("#282A36"),
        foregroundColor = Color.parseColor("#F8F8F2"),
        cursorColor = Color.parseColor("#F8F8F2"),
        black = Color.parseColor("#21222C"),
        red = Color.parseColor("#FF5555"),
        green = Color.parseColor("#50FA7B"),
        yellow = Color.parseColor("#F1FA8C"),
        blue = Color.parseColor("#BD93F9"),
        magenta = Color.parseColor("#FF79C6"),
        cyan = Color.parseColor("#8BE9FD"),
        white = Color.parseColor("#F8F8F2"),
        brightBlack = Color.parseColor("#6272A4"),
        brightRed = Color.parseColor("#FF6E6E"),
        brightGreen = Color.parseColor("#69FF94"),
        brightYellow = Color.parseColor("#FFFFA5"),
        brightBlue = Color.parseColor("#D6ACFF"),
        brightMagenta = Color.parseColor("#FF92DF"),
        brightCyan = Color.parseColor("#A4FFFF"),
        brightWhite = Color.parseColor("#FFFFFF")
    )
    
    val solarizedDark = TerminalTheme(
        id = "solarized_dark",
        name = "Solarized Dark",
        backgroundColor = Color.parseColor("#002B36"),
        foregroundColor = Color.parseColor("#839496"),
        cursorColor = Color.parseColor("#93A1A1"),
        black = Color.parseColor("#073642"),
        red = Color.parseColor("#DC322F"),
        green = Color.parseColor("#859900"),
        yellow = Color.parseColor("#B58900"),
        blue = Color.parseColor("#268BD2"),
        magenta = Color.parseColor("#D33682"),
        cyan = Color.parseColor("#2AA198"),
        white = Color.parseColor("#EEE8D5"),
        brightBlack = Color.parseColor("#002B36"),
        brightRed = Color.parseColor("#CB4B16"),
        brightGreen = Color.parseColor("#586E75"),
        brightYellow = Color.parseColor("#657B83"),
        brightBlue = Color.parseColor("#839496"),
        brightMagenta = Color.parseColor("#6C71C4"),
        brightCyan = Color.parseColor("#93A1A1"),
        brightWhite = Color.parseColor("#FDF6E3")
    )
    
    val solarizedLight = TerminalTheme(
        id = "solarized_light",
        name = "Solarized Light",
        backgroundColor = Color.parseColor("#FDF6E3"),
        foregroundColor = Color.parseColor("#657B83"),
        cursorColor = Color.parseColor("#586E75"),
        black = Color.parseColor("#073642"),
        red = Color.parseColor("#DC322F"),
        green = Color.parseColor("#859900"),
        yellow = Color.parseColor("#B58900"),
        blue = Color.parseColor("#268BD2"),
        magenta = Color.parseColor("#D33682"),
        cyan = Color.parseColor("#2AA198"),
        white = Color.parseColor("#EEE8D5"),
        brightBlack = Color.parseColor("#002B36"),
        brightRed = Color.parseColor("#CB4B16"),
        brightGreen = Color.parseColor("#586E75"),
        brightYellow = Color.parseColor("#657B83"),
        brightBlue = Color.parseColor("#839496"),
        brightMagenta = Color.parseColor("#6C71C4"),
        brightCyan = Color.parseColor("#93A1A1"),
        brightWhite = Color.parseColor("#FDF6E3")
    )
    
    val monokai = TerminalTheme(
        id = "monokai",
        name = "Monokai",
        backgroundColor = Color.parseColor("#272822"),
        foregroundColor = Color.parseColor("#F8F8F2"),
        cursorColor = Color.parseColor("#F8F8F0"),
        black = Color.parseColor("#272822"),
        red = Color.parseColor("#F92672"),
        green = Color.parseColor("#A6E22E"),
        yellow = Color.parseColor("#F4BF75"),
        blue = Color.parseColor("#66D9EF"),
        magenta = Color.parseColor("#AE81FF"),
        cyan = Color.parseColor("#A1EFE4"),
        white = Color.parseColor("#F8F8F2"),
        brightBlack = Color.parseColor("#75715E"),
        brightRed = Color.parseColor("#F92672"),
        brightGreen = Color.parseColor("#A6E22E"),
        brightYellow = Color.parseColor("#F4BF75"),
        brightBlue = Color.parseColor("#66D9EF"),
        brightMagenta = Color.parseColor("#AE81FF"),
        brightCyan = Color.parseColor("#A1EFE4"),
        brightWhite = Color.parseColor("#F9F8F5")
    )
    
    val nord = TerminalTheme(
        id = "nord",
        name = "Nord",
        backgroundColor = Color.parseColor("#2E3440"),
        foregroundColor = Color.parseColor("#D8DEE9"),
        cursorColor = Color.parseColor("#D8DEE9"),
        black = Color.parseColor("#3B4252"),
        red = Color.parseColor("#BF616A"),
        green = Color.parseColor("#A3BE8C"),
        yellow = Color.parseColor("#EBCB8B"),
        blue = Color.parseColor("#81A1C1"),
        magenta = Color.parseColor("#B48EAD"),
        cyan = Color.parseColor("#88C0D0"),
        white = Color.parseColor("#E5E9F0"),
        brightBlack = Color.parseColor("#4C566A"),
        brightRed = Color.parseColor("#BF616A"),
        brightGreen = Color.parseColor("#A3BE8C"),
        brightYellow = Color.parseColor("#EBCB8B"),
        brightBlue = Color.parseColor("#81A1C1"),
        brightMagenta = Color.parseColor("#B48EAD"),
        brightCyan = Color.parseColor("#8FBCBB"),
        brightWhite = Color.parseColor("#ECEFF4")
    )
    
    val gruvboxDark = TerminalTheme(
        id = "gruvbox_dark",
        name = "Gruvbox Dark",
        backgroundColor = Color.parseColor("#282828"),
        foregroundColor = Color.parseColor("#EBDBB2"),
        cursorColor = Color.parseColor("#EBDBB2"),
        black = Color.parseColor("#282828"),
        red = Color.parseColor("#CC241D"),
        green = Color.parseColor("#98971A"),
        yellow = Color.parseColor("#D79921"),
        blue = Color.parseColor("#458588"),
        magenta = Color.parseColor("#B16286"),
        cyan = Color.parseColor("#689D6A"),
        white = Color.parseColor("#A89984"),
        brightBlack = Color.parseColor("#928374"),
        brightRed = Color.parseColor("#FB4934"),
        brightGreen = Color.parseColor("#B8BB26"),
        brightYellow = Color.parseColor("#FABD2F"),
        brightBlue = Color.parseColor("#83A598"),
        brightMagenta = Color.parseColor("#D3869B"),
        brightCyan = Color.parseColor("#8EC07C"),
        brightWhite = Color.parseColor("#EBDBB2")
    )
    
    val oneDark = TerminalTheme(
        id = "one_dark",
        name = "One Dark",
        backgroundColor = Color.parseColor("#282C34"),
        foregroundColor = Color.parseColor("#ABB2BF"),
        cursorColor = Color.parseColor("#528BFF"),
        black = Color.parseColor("#282C34"),
        red = Color.parseColor("#E06C75"),
        green = Color.parseColor("#98C379"),
        yellow = Color.parseColor("#E5C07B"),
        blue = Color.parseColor("#61AFEF"),
        magenta = Color.parseColor("#C678DD"),
        cyan = Color.parseColor("#56B6C2"),
        white = Color.parseColor("#ABB2BF"),
        brightBlack = Color.parseColor("#5C6370"),
        brightRed = Color.parseColor("#E06C75"),
        brightGreen = Color.parseColor("#98C379"),
        brightYellow = Color.parseColor("#E5C07B"),
        brightBlue = Color.parseColor("#61AFEF"),
        brightMagenta = Color.parseColor("#C678DD"),
        brightCyan = Color.parseColor("#56B6C2"),
        brightWhite = Color.parseColor("#FFFFFF")
    )
    
    val tokyoNight = TerminalTheme(
        id = "tokyo_night",
        name = "Tokyo Night",
        backgroundColor = Color.parseColor("#1A1B26"),
        foregroundColor = Color.parseColor("#C0CAF5"),
        cursorColor = Color.parseColor("#C0CAF5"),
        black = Color.parseColor("#15161E"),
        red = Color.parseColor("#F7768E"),
        green = Color.parseColor("#9ECE6A"),
        yellow = Color.parseColor("#E0AF68"),
        blue = Color.parseColor("#7AA2F7"),
        magenta = Color.parseColor("#BB9AF7"),
        cyan = Color.parseColor("#7DCFFF"),
        white = Color.parseColor("#A9B1D6"),
        brightBlack = Color.parseColor("#414868"),
        brightRed = Color.parseColor("#F7768E"),
        brightGreen = Color.parseColor("#9ECE6A"),
        brightYellow = Color.parseColor("#E0AF68"),
        brightBlue = Color.parseColor("#7AA2F7"),
        brightMagenta = Color.parseColor("#BB9AF7"),
        brightCyan = Color.parseColor("#7DCFFF"),
        brightWhite = Color.parseColor("#C0CAF5")
    )
    
    val catppuccinMocha = TerminalTheme(
        id = "catppuccin_mocha",
        name = "Catppuccin Mocha",
        backgroundColor = Color.parseColor("#1E1E2E"),
        foregroundColor = Color.parseColor("#CDD6F4"),
        cursorColor = Color.parseColor("#F5E0DC"),
        black = Color.parseColor("#45475A"),
        red = Color.parseColor("#F38BA8"),
        green = Color.parseColor("#A6E3A1"),
        yellow = Color.parseColor("#F9E2AF"),
        blue = Color.parseColor("#89B4FA"),
        magenta = Color.parseColor("#F5C2E7"),
        cyan = Color.parseColor("#94E2D5"),
        white = Color.parseColor("#BAC2DE"),
        brightBlack = Color.parseColor("#585B70"),
        brightRed = Color.parseColor("#F38BA8"),
        brightGreen = Color.parseColor("#A6E3A1"),
        brightYellow = Color.parseColor("#F9E2AF"),
        brightBlue = Color.parseColor("#89B4FA"),
        brightMagenta = Color.parseColor("#F5C2E7"),
        brightCyan = Color.parseColor("#94E2D5"),
        brightWhite = Color.parseColor("#A6ADC8")
    )
    
    val ayu = TerminalTheme(
        id = "ayu",
        name = "Ayu Dark",
        backgroundColor = Color.parseColor("#0A0E14"),
        foregroundColor = Color.parseColor("#B3B1AD"),
        cursorColor = Color.parseColor("#E6B450"),
        black = Color.parseColor("#01060E"),
        red = Color.parseColor("#EA6C73"),
        green = Color.parseColor("#91B362"),
        yellow = Color.parseColor("#F9AF4F"),
        blue = Color.parseColor("#53BDFA"),
        magenta = Color.parseColor("#FAE994"),
        cyan = Color.parseColor("#90E1C6"),
        white = Color.parseColor("#C7C7C7"),
        brightBlack = Color.parseColor("#686868"),
        brightRed = Color.parseColor("#F07178"),
        brightGreen = Color.parseColor("#C2D94C"),
        brightYellow = Color.parseColor("#FFB454"),
        brightBlue = Color.parseColor("#59C2FF"),
        brightMagenta = Color.parseColor("#FFEE99"),
        brightCyan = Color.parseColor("#95E6CB"),
        brightWhite = Color.parseColor("#FFFFFF")
    )
    
    /**
     * All available built-in themes
     */
    val allThemes = listOf(
        default,
        dracula,
        solarizedDark,
        solarizedLight,
        monokai,
        nord,
        gruvboxDark,
        oneDark,
        tokyoNight,
        catppuccinMocha,
        ayu
    )
    
    /**
     * Get theme by ID
     */
    fun getThemeById(id: String): TerminalTheme {
        return allThemes.find { it.id == id } ?: default
    }
}

/**
 * Terminal font options
 */
data class TerminalFont(
    val id: String,
    val name: String,
    val isAsset: Boolean,
    val path: String // asset path or file path
)

/**
 * Built-in terminal fonts (monospace fonts)
 */
object TerminalFonts {
    val defaultFont = TerminalFont(
        id = "default",
        name = "System Default (Monospace)",
        isAsset = false,
        path = ""
    )
    
    // Note: These fonts would need to be added as assets
    val jetbrainsMono = TerminalFont(
        id = "jetbrains_mono",
        name = "JetBrains Mono",
        isAsset = true,
        path = "fonts/JetBrainsMono-Regular.ttf"
    )
    
    val firaCode = TerminalFont(
        id = "fira_code",
        name = "Fira Code",
        isAsset = true,
        path = "fonts/FiraCode-Regular.ttf"
    )
    
    val sourceCodePro = TerminalFont(
        id = "source_code_pro",
        name = "Source Code Pro",
        isAsset = true,
        path = "fonts/SourceCodePro-Regular.ttf"
    )
    
    val robotoMono = TerminalFont(
        id = "roboto_mono",
        name = "Roboto Mono",
        isAsset = true,
        path = "fonts/RobotoMono-Regular.ttf"
    )
    
    val inconsolata = TerminalFont(
        id = "inconsolata",
        name = "Inconsolata",
        isAsset = true,
        path = "fonts/Inconsolata-Regular.ttf"
    )
    
    val allFonts = listOf(
        defaultFont,
        jetbrainsMono,
        firaCode,
        sourceCodePro,
        robotoMono,
        inconsolata
    )
    
    fun getFontById(id: String): TerminalFont {
        return allFonts.find { it.id == id } ?: defaultFont
    }
}
