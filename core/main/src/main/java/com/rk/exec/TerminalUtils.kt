package com.rk.exec

import android.content.Context
import android.content.Intent
import com.rk.activities.main.MainActivity
import com.rk.activities.terminal.Terminal
import com.rk.file.child
import com.rk.file.getActiveSandboxDir
import com.rk.file.getActiveHomeDir
import com.rk.file.getZeditorDir
import com.rk.file.hasExternalInstallation
import com.rk.file.localDir
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.utils.showTerminalNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun isTerminalInstalled(): Boolean {
    // Check external storage first
    if (hasExternalInstallation()) {
        val zeditorDir = getZeditorDir()
        val sandboxDir = zeditorDir.child("sandbox")
        val homeDir = zeditorDir.child("home")
        val tmpDir = sandboxDir.child("tmp")
        val rootfs =
            sandboxDir.listFiles()?.filter {
                it.absolutePath != homeDir.absolutePath &&
                    it.absolutePath != tmpDir.absolutePath
            } ?: emptyList()

        return zeditorDir.child(".terminal_setup_ok_DO_NOT_REMOVE").exists() && rootfs.isNotEmpty()
    }
    
    // Fall back to internal storage check
    val rootfs =
        sandboxDir().listFiles()?.filter {
            it.absolutePath != sandboxHomeDir().absolutePath &&
                it.absolutePath != sandboxDir().child("tmp").absolutePath
        } ?: emptyList()

    return localDir().child(".terminal_setup_ok_DO_NOT_REMOVE").exists() && rootfs.isNotEmpty()
}

suspend fun isTerminalWorking(): Boolean =
    withContext(Dispatchers.IO) {
        val process = ubuntuProcess(command = arrayOf("true"))
        return@withContext process.waitFor() == 0
    }

fun launchInternalTerminal(context: Context, terminalCommand: TerminalCommand) {
    showTerminalNotice(activity = MainActivity.instance!!) {
        pendingCommand = terminalCommand
        context.startActivity(Intent(context, Terminal::class.java))
    }
}
