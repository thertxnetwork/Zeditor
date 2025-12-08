package com.rk.lsp.servers

import android.content.Context
import com.rk.exec.TerminalCommand
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.sandboxHomeDir
import com.rk.lsp.BaseLspConnector
import com.rk.lsp.BaseLspServer
import com.rk.lsp.LspConnectionConfig
import java.net.URI
import org.eclipse.lsp4j.DidChangeConfigurationParams

class Python() : BaseLspServer() {
    override val id: String = "python-lsp"
    override val languageName: String = "Python"
    override val serverName = "python-lsp-server"
    override val supportedExtensions: List<String> = FileType.PYTHON.extensions

    override fun isInstalled(context: Context): Boolean {
        // TODO: Implement installation check without terminal dependency
        return false
    }

    override fun install(context: Context) {
        // TODO: Implement LSP installation without terminal dependency
        // The terminal-based installation has been removed
        // User needs to implement a different way to install and run LSP servers
    }

    override fun getConnectionConfig(): LspConnectionConfig {
        // TODO: Update connection config for new LSP implementation
        return LspConnectionConfig.Process(arrayOf("/home/.local/share/pipx/venvs/python-lsp-server/bin/pylsp"))
    }

    override suspend fun beforeConnect() {}

    override suspend fun connectionSuccess(lspConnector: BaseLspConnector) {
        val requestManager = lspConnector.lspEditor!!.requestManager!!

        val params =
            DidChangeConfigurationParams(
                mapOf(
                    "pylsp" to
                        mapOf(
                            "plugins" to
                                mapOf(
                                    "pycodestyle" to
                                        mapOf(
                                            "enabled" to true,
                                            "ignore" to listOf("E501", "W291", "W293"),
                                            "maxLineLength" to 999,
                                        )
                                )
                        )
                )
            )

        requestManager.didChangeConfiguration(params)
    }

    override suspend fun connectionFailure(msg: String?) {}

    override fun isSupported(file: FileObject): Boolean {
        return supportedExtensions.contains(file.getName().substringAfterLast("."))
    }

    override fun getInitializationOptions(uri: URI?): Any? {
        return null
    }
}
