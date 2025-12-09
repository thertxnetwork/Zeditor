package com.rk.terminal

import org.junit.Test
import org.junit.Assert.*

class AnsiParserTest {
    
    private val parser = AnsiParser()
    
    @Test
    fun testSimpleText() {
        val result = parser.parse("Hello World")
        assertEquals(1, result.size)
        assertEquals("Hello World", result[0].text)
    }
    
    @Test
    fun testColoredText() {
        // Red text: ESC[31mRed TextESC[0m
        val result = parser.parse("\u001B[31mRed Text\u001B[0m")
        assertEquals(2, result.size)
        assertEquals("Red Text", result[0].text)
        assertTrue(result[0].style.fgColor != result[1].style.fgColor)
    }
    
    @Test
    fun testBoldText() {
        // Bold text: ESC[1mBold TextESC[0m
        val result = parser.parse("\u001B[1mBold Text\u001B[0m")
        assertEquals(2, result.size)
        assertEquals("Bold Text", result[0].text)
        assertTrue(result[0].style.bold)
        assertFalse(result[1].style.bold)
    }
    
    @Test
    fun testMultipleStyles() {
        // Bold red text: ESC[1;31mBold Red TextESC[0m
        val result = parser.parse("\u001B[1;31mBold Red Text\u001B[0m")
        assertEquals(2, result.size)
        assertEquals("Bold Red Text", result[0].text)
        assertTrue(result[0].style.bold)
    }
    
    @Test
    fun testMixedText() {
        // Normal text followed by colored text
        val result = parser.parse("Normal \u001B[32mGreen\u001B[0m Normal")
        assertTrue(result.size >= 3)
        assertEquals("Normal ", result[0].text)
        assertEquals("Green", result[1].text)
        assertEquals(" Normal", result[2].text)
    }
}
