package com.rk.lsp.servers

import android.content.Context
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.lsp.BaseLspConnector
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspConnectionConfig
import java.net.URI

class TypeScript() : BaseLspServer() {
    override val id: String = "typescript-lsp"
    override val languageName: String = "TypeScript"
    override val serverName = "typescript-language-server"
    override val supportedExtensions: List<String> =
        FileType.JAVASCRIPT.extensions +
            FileType.TYPESCRIPT.extensions +
            FileType.JSX.extensions +
            FileType.TSX.extensions

    override fun isInstalled(context: Context): Boolean {
        // TODO: Implement installation check without terminal dependency
        return false
    }

    override suspend fun beforeConnect() {}

    override suspend fun connectionSuccess(lspConnector: BaseLspConnector) {}

    override suspend fun connectionFailure(msg: String?) {}

    override fun install(context: Context) {
        // TODO: Implement LSP installation without terminal dependency
        // The terminal-based installation has been removed
        // User needs to implement a different way to install and run LSP servers
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        // TODO: Update connection config for new LSP implementation
        return LspConnectionConfig.Process(arrayOf("/usr/bin/node", "/usr/bin/typescript-language-server", "--stdio"))
    }

    override fun isSupported(file: FileObject): Boolean {
        return supportedExtensions.contains(file.getName().substringAfterLast("."))
    }

    override fun getInitializationOptions(uri: URI?): Any? {
        return null
    }
}
