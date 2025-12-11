package com.rk.activities.settings

sealed class SettingsRoutes(val route: String) {
    data object Settings : SettingsRoutes("settings")

    data object AppSettings : SettingsRoutes("app_settings")

    data object EditorSettings : SettingsRoutes("editor_settings")

    data object About : SettingsRoutes("about")

    data object EditorFontScreen : SettingsRoutes("editor_font_screen")

    data object DefaultEncoding : SettingsRoutes("default_encoding")

    data object ToolbarActions : SettingsRoutes("toolbar_actions")

    data object ManageMutators : SettingsRoutes("manage_mutators")

    data object Extensions : SettingsRoutes("extensions")

    data object DeveloperOptions : SettingsRoutes("developer_options")

    data object Support : SettingsRoutes("support")

    data object LanguageScreen : SettingsRoutes("language")

    data object Themes : SettingsRoutes("theme")

    data object LspSettings : SettingsRoutes("lsp_settings")
    
    data object SSHServers : SettingsRoutes("ssh_servers")
}
