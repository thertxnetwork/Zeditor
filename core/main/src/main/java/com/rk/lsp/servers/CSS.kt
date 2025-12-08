package com.rk.lsp.servers

import android.content.Context
import com.rk.exec.TerminalCommand
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxDir
import com.rk.lsp.BaseLspConnector
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspConnectionConfig
import java.net.URI

class CSS() : BaseLspServer() {
    override val id: String = "css-lsp"
    override val languageName: String = "CSS"
    override val serverName = "vscode-langservers-extracted"
    override val supportedExtensions: List<String> =
        FileType.CSS.extensions + FileType.SCSS.extensions + FileType.LESS.extensions

    override fun isInstalled(context: Context): Boolean {
        // TODO: Implement installation check without terminal dependency
        return false
    }

        return sandboxDir().child("/usr/bin/vscode-css-language-server").exists()
    }

    override fun install(context: Context) {
        // TODO: Implement LSP installation without terminal dependency
        // The terminal-based installation has been removed
        // User needs to implement a different way to install and run LSP servers
    }

    override suspend fun beforeConnect() {}

    override suspend fun connectionSuccess(lspConnector: BaseLspConnector) {}

    override suspend fun connectionFailure(msg: String?) {}

    override fun getConnectionConfig(): LspConnectionConfig {
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/vscode-css-language-server", "--stdio"))
    }

    override fun isSupported(file: FileObject): Boolean {
        return supportedExtensions.contains(file.getName().substringAfterLast("."))
    }

    override fun getInitializationOptions(uri: URI?): Any? {
        return null
    }
}
