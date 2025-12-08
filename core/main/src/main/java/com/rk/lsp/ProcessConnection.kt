package com.rk.lsp

import android.util.Log
import com.rk.utils.toast
import com.thertxnetwork.zeditor.core.main.BuildConfig
import io.github.rosemoe.sora.lsp.client.connection.StreamConnectionProvider
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ProcessConnection(private val cmd: Array<String>) : StreamConnectionProvider {

    private var process: Process? = null

    override val inputStream: InputStream
        get() = process?.inputStream ?: throw IllegalStateException("Process not running")

    override val outputStream: OutputStream
        get() = process?.outputStream ?: throw IllegalStateException("Process not running")
    
    private suspend fun Process.readStderr(): String =
        withContext(Dispatchers.IO) {
            try {
                errorStream.bufferedReader().use { reader ->
                    if (errorStream.available() <= 0) return@use ""
                    reader.readText()
                }
            } catch (e: Exception) {
                ""
            }
        }

    override fun start() {
        if (process != null) return
        runBlocking {
            // TODO: Implement process execution without terminal dependency
            // The ubuntuProcess has been removed along with terminal infrastructure
            // User needs to implement a different way to spawn LSP server processes
            // For now, try to use regular ProcessBuilder
            try {
                val processBuilder = ProcessBuilder(*cmd)
                process = processBuilder.start()
                
                if (BuildConfig.DEBUG && process?.waitFor(110, TimeUnit.MILLISECONDS) == true) {
                    val exitCode = process?.exitValue() ?: -1
                    if (exitCode != 0) {
                        val stderr = process?.readStderr().orEmpty()
                        Log.e(this@ProcessConnection::class.java.simpleName, stderr)
                        toast(stderr)
                    }
                }
            } catch (e: Exception) {
                Log.e(this@ProcessConnection::class.java.simpleName, "Failed to start LSP process", e)
                toast("Failed to start LSP process: ${e.message}")
            }
        }
    }

    override fun close() {
        runBlocking { Log.e(this@ProcessConnection::class.java.simpleName, process?.readStderr().toString()) }

        process?.destroy()
        process = null
    }
}
