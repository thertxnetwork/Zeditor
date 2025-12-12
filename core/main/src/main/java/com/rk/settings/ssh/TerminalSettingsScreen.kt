package com.rk.settings.ssh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.resources.strings
import com.rk.settings.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalSettingsScreen() {
    val context = LocalContext.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var showFontDialog by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    
    val currentThemeId by remember { mutableStateOf(Settings.terminal_theme) }
    val currentTheme = TerminalThemes.getThemeById(currentThemeId)
    
    val currentFontId by remember { mutableStateOf(Settings.terminal_font) }
    val currentFont = TerminalFonts.getFontById(currentFontId)
    
    val fontSize by remember { mutableIntStateOf(Settings.terminal_font_size) }
    
    PreferenceLayout(
        label = "Terminal Settings",
        backArrowVisible = true
    ) {
        PreferenceGroup(heading = "Appearance") {
            // Theme selection
            PreferenceTemplate(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showThemeDialog = true }
                    .padding(horizontal = 16.dp),
                title = { Text("Theme") },
                description = { Text(currentTheme.name) },
                endWidget = {
                    // Color preview
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(2.dp)
                        ) {
                            Surface(
                                color = Color(currentTheme.backgroundColor),
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.fillMaxSize()
                            ) {}
                        }
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .padding(2.dp)
                        ) {
                            Surface(
                                color = Color(currentTheme.foregroundColor),
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.fillMaxSize()
                            ) {}
                        }
                    }
                }
            )
            
            // Font selection
            PreferenceTemplate(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFontDialog = true }
                    .padding(horizontal = 16.dp),
                title = { Text("Font") },
                description = { Text(currentFont.name) }
            )
            
            // Font size
            PreferenceTemplate(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFontSizeDialog = true }
                    .padding(horizontal = 16.dp),
                title = { Text("Font Size") },
                description = { Text("${fontSize}sp") }
            )
        }
        
        PreferenceGroup(heading = "Behavior") {
            var scrollbackLines by remember { mutableIntStateOf(Settings.terminal_scrollback_lines) }
            
            SettingsToggle(
                label = "Bell Sound",
                description = "Play sound on terminal bell",
                default = Settings.terminal_bell,
                sideEffect = { Settings.terminal_bell = it }
            )
            
            SettingsToggle(
                label = "Vibrate on Bell",
                description = "Vibrate device on terminal bell",
                default = Settings.terminal_vibrate,
                sideEffect = { Settings.terminal_vibrate = it }
            )
            
            PreferenceTemplate(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                title = { Text("Scrollback Lines") },
                description = { Text("$scrollbackLines lines") },
                endWidget = {
                    Slider(
                        value = scrollbackLines.toFloat(),
                        onValueChange = { 
                            scrollbackLines = it.toInt()
                            Settings.terminal_scrollback_lines = scrollbackLines
                        },
                        valueRange = 500f..10000f,
                        steps = 18,
                        modifier = Modifier.width(150.dp)
                    )
                }
            )
        }
        
        PreferenceGroup(heading = "Extra Keys") {
            SettingsToggle(
                label = "Show Extra Keys",
                description = "Show additional keys row (ESC, CTRL, etc.)",
                default = Settings.terminal_show_extra_keys,
                sideEffect = { Settings.terminal_show_extra_keys = it }
            )
            
            SettingsToggle(
                label = "Show Arrow Keys",
                description = "Show arrow keys in extra keys row",
                default = Settings.terminal_show_arrow_keys,
                sideEffect = { Settings.terminal_show_arrow_keys = it }
            )
        }
    }
    
    // Theme selection dialog
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentThemeId = currentThemeId,
            onDismiss = { showThemeDialog = false },
            onSelect = { theme ->
                Settings.terminal_theme = theme.id
                showThemeDialog = false
            }
        )
    }
    
    // Font selection dialog
    if (showFontDialog) {
        FontSelectionDialog(
            currentFontId = currentFontId,
            onDismiss = { showFontDialog = false },
            onSelect = { font ->
                Settings.terminal_font = font.id
                showFontDialog = false
            }
        )
    }
    
    // Font size dialog
    if (showFontSizeDialog) {
        FontSizeDialog(
            currentSize = fontSize,
            onDismiss = { showFontSizeDialog = false },
            onSelect = { size ->
                Settings.terminal_font_size = size
                showFontSizeDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectionDialog(
    currentThemeId: String,
    onDismiss: () -> Unit,
    onSelect: (TerminalTheme) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Theme") },
        text = {
            LazyColumn {
                items(TerminalThemes.allThemes) { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(theme) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = theme.id == currentThemeId,
                            onClick = { onSelect(theme) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(theme.name)
                        }
                        // Color preview
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Surface(
                                color = Color(theme.backgroundColor),
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.size(16.dp)
                            ) {}
                            Surface(
                                color = Color(theme.foregroundColor),
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.size(16.dp)
                            ) {}
                            Surface(
                                color = Color(theme.red),
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.size(16.dp)
                            ) {}
                            Surface(
                                color = Color(theme.green),
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.size(16.dp)
                            ) {}
                            Surface(
                                color = Color(theme.blue),
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.size(16.dp)
                            ) {}
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FontSelectionDialog(
    currentFontId: String,
    onDismiss: () -> Unit,
    onSelect: (TerminalFont) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Font") },
        text = {
            LazyColumn {
                items(TerminalFonts.allFonts) { font ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(font) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = font.id == currentFontId,
                            onClick = { onSelect(font) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(font.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FontSizeDialog(
    currentSize: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    var size by remember { mutableIntStateOf(currentSize) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Font Size") },
        text = {
            Column {
                Text("${size}sp", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = size.toFloat(),
                    onValueChange = { size = it.toInt() },
                    valueRange = 8f..32f,
                    steps = 23
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("8sp", style = MaterialTheme.typography.bodySmall)
                    Text("32sp", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(size) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
