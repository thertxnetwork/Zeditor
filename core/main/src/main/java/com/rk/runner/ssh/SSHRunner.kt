package com.rk.runner.ssh

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.RunnerImpl
import com.rk.runner.currentRunner
import com.rk.utils.dialog
import java.lang.ref.WeakReference

/**
 * SSH-based code runner that executes code on remote VPS servers
 */
class SSHRunner(private val serverConfig: SSHServerConfig? = null) : RunnerImpl() {
    
    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(
                title = strings.attention.getString(),
                msg = strings.non_native_filetype.getString(),
                onOk = {}
            )
            return
        }
        
        currentRunner = WeakReference(this)
        
        // Get configured servers
        val servers = SSHServerManager.servers.toList()
        
        if (servers.isEmpty()) {
            dialog(
                title = "No SSH Servers",
                msg = "Please configure an SSH server in Settings > SSH Servers before running code.",
                onOk = {}
            )
            return
        }
        
        // Determine which server to use
        val serverToUse = serverConfig ?: servers.firstOrNull() ?: run {
            dialog(
                title = "No Server Selected",
                msg = "Please select a server to run code on.",
                onOk = {}
            )
            return
        }
        
        // Open terminal with file
        val intent = Intent(context, TerminalActivity::class.java).apply {
            putExtra(TerminalActivity.EXTRA_SERVER_ID, serverToUse.id)
            putExtra(TerminalActivity.EXTRA_FILE_PATH, fileObject.getAbsolutePath())
            putExtra(TerminalActivity.EXTRA_FILE_NAME, fileObject.getName())
        }
        context.startActivity(intent)
    }
    
    override fun getName(): String {
        return serverConfig?.let { "SSH: ${it.name}" } ?: "SSH Runner"
    }
    
    override fun getIcon(context: Context): Drawable? {
        return drawables.bash.getDrawable(context)
    }
    
    override suspend fun isRunning(): Boolean {
        return false
    }
    
    override suspend fun stop() {
        // Terminal sessions are managed independently
    }
}
