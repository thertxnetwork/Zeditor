package com.rk.runner.runners

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.strings
import com.rk.runner.RunnerImpl
import com.rk.terminal.TerminalActivity
import com.rk.utils.errorDialog

class Shell : RunnerImpl() {
    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            errorDialog(msgRes = strings.native_runner)
            return
        }

        val filePath = fileObject.getAbsolutePath()
        val workDir = fileObject.getParentFile()?.getAbsolutePath() ?: context.filesDir.absolutePath
        
        // Launch terminal with shell script
        val intent = Intent(context, TerminalActivity::class.java).apply {
            putExtra(TerminalActivity.EXTRA_COMMAND, "/system/bin/sh")
            putExtra(TerminalActivity.EXTRA_ARGS, arrayOf(filePath))
            putExtra(TerminalActivity.EXTRA_WORKDIR, workDir)
            putExtra(TerminalActivity.EXTRA_TITLE, "Shell: ${fileObject.getName()}")
        }
        
        context.startActivity(intent)
    }

    override fun getName(): String {
        return "Shell Runner"
    }

    override fun getIcon(context: Context): Drawable? {
        return drawables.bash.getDrawable(context)
    }

    override suspend fun isRunning(): Boolean {
        return false
    }

    override suspend fun stop() {}
}
