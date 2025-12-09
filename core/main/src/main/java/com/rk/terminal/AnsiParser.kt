package com.rk.terminal

import android.graphics.Color

/**
 * Parser for ANSI escape sequences
 * Handles VT100/xterm control codes for terminal emulation
 */
class AnsiParser {
    
    companion object {
        // ANSI color codes
        private val ANSI_COLORS = mapOf(
            30 to Color.parseColor("#000000"), // Black
            31 to Color.parseColor("#FF0000"), // Red
            32 to Color.parseColor("#00FF00"), // Green
            33 to Color.parseColor("#FFFF00"), // Yellow
            34 to Color.parseColor("#0000FF"), // Blue
            35 to Color.parseColor("#FF00FF"), // Magenta
            36 to Color.parseColor("#00FFFF"), // Cyan
            37 to Color.parseColor("#FFFFFF"), // White
            
            90 to Color.parseColor("#808080"), // Bright Black (Gray)
            91 to Color.parseColor("#FF8080"), // Bright Red
            92 to Color.parseColor("#80FF80"), // Bright Green
            93 to Color.parseColor("#FFFF80"), // Bright Yellow
            94 to Color.parseColor("#8080FF"), // Bright Blue
            95 to Color.parseColor("#FF80FF"), // Bright Magenta
            96 to Color.parseColor("#80FFFF"), // Bright Cyan
            97 to Color.parseColor("#FFFFFF")  // Bright White
        )
        
        private val ANSI_BG_COLORS = mapOf(
            40 to Color.parseColor("#000000"), // Black background
            41 to Color.parseColor("#FF0000"), // Red background
            42 to Color.parseColor("#00FF00"), // Green background
            43 to Color.parseColor("#FFFF00"), // Yellow background
            44 to Color.parseColor("#0000FF"), // Blue background
            45 to Color.parseColor("#FF00FF"), // Magenta background
            46 to Color.parseColor("#00FFFF"), // Cyan background
            47 to Color.parseColor("#FFFFFF"), // White background
            
            100 to Color.parseColor("#808080"), // Bright Black background
            101 to Color.parseColor("#FF8080"), // Bright Red background
            102 to Color.parseColor("#80FF80"), // Bright Green background
            103 to Color.parseColor("#FFFF80"), // Bright Yellow background
            104 to Color.parseColor("#8080FF"), // Bright Blue background
            105 to Color.parseColor("#FF80FF"), // Bright Magenta background
            106 to Color.parseColor("#80FFFF"), // Bright Cyan background
            107 to Color.parseColor("#FFFFFF")  // Bright White background
        )
    }
    
    data class TextStyle(
        var fgColor: Int = Color.parseColor("#00FF00"),
        var bgColor: Int = Color.parseColor("#000000"),
        var bold: Boolean = false,
        var underline: Boolean = false,
        var reverse: Boolean = false
    ) {
        fun copy(): TextStyle {
            return TextStyle(fgColor, bgColor, bold, underline, reverse)
        }
        
        fun reset() {
            fgColor = Color.parseColor("#00FF00")
            bgColor = Color.parseColor("#000000")
            bold = false
            underline = false
            reverse = false
        }
    }
    
    data class ParsedText(
        val text: String,
        val style: TextStyle
    )
    
    /**
     * Parse text with ANSI escape sequences
     * Returns a list of text segments with their styles
     */
    fun parse(input: String): List<ParsedText> {
        val result = mutableListOf<ParsedText>()
        val currentStyle = TextStyle()
        val currentText = StringBuilder()
        
        var i = 0
        while (i < input.length) {
            when {
                // ESC sequence
                i < input.length - 1 && input[i] == '\u001B' && input[i + 1] == '[' -> {
                    // Save current text if any
                    if (currentText.isNotEmpty()) {
                        result.add(ParsedText(currentText.toString(), currentStyle.copy()))
                        currentText.clear()
                    }
                    
                    // Find the end of escape sequence
                    var j = i + 2
                    while (j < input.length && !input[j].isLetter()) {
                        j++
                    }
                    
                    if (j < input.length) {
                        val sequence = input.substring(i + 2, j)
                        val command = input[j]
                        processEscapeSequence(sequence, command, currentStyle)
                        i = j + 1
                    } else {
                        i++
                    }
                }
                // Regular character
                else -> {
                    currentText.append(input[i])
                    i++
                }
            }
        }
        
        // Add remaining text
        if (currentText.isNotEmpty()) {
            result.add(ParsedText(currentText.toString(), currentStyle.copy()))
        }
        
        return result
    }
    
    private fun processEscapeSequence(sequence: String, command: Char, style: TextStyle) {
        when (command) {
            'm' -> processSGR(sequence, style) // Select Graphic Rendition
            'K' -> {} // Erase in line (handled by terminal)
            'J' -> {} // Erase in display (handled by terminal)
            'H', 'f' -> {} // Cursor position (handled by terminal)
            'A', 'B', 'C', 'D' -> {} // Cursor movement (handled by terminal)
        }
    }
    
    private fun processSGR(sequence: String, style: TextStyle) {
        if (sequence.isEmpty()) {
            style.reset()
            return
        }
        
        val codes = sequence.split(';').mapNotNull { it.toIntOrNull() }
        
        for (code in codes) {
            when (code) {
                0 -> style.reset()
                1 -> style.bold = true
                4 -> style.underline = true
                7 -> style.reverse = true
                22 -> style.bold = false
                24 -> style.underline = false
                27 -> style.reverse = false
                in 30..37, in 90..97 -> {
                    style.fgColor = ANSI_COLORS[code] ?: style.fgColor
                }
                in 40..47, in 100..107 -> {
                    style.bgColor = ANSI_BG_COLORS[code] ?: style.bgColor
                }
            }
        }
    }
}
