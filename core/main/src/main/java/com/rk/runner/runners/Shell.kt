package com.rk.runner.runners

import android.content.Context
import android.graphics.drawable.Drawable
import com.rk.exec.TerminalCommand
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.strings
import com.rk.runner.RunnerImpl
import com.rk.utils.errorDialog

class Shell : RunnerImpl() {
    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            errorDialog(msgRes = strings.native_runner)
            return
        }

        // TODO: Implement shell runner without terminal dependency
        // The terminal-based runner has been removed
        // User needs to implement a different way to run shell scripts
        errorDialog(msgRes = strings.unsupported_feature)
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
