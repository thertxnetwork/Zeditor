package com.rk.runner.runners

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Environment
import com.rk.DefaultScope
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.RunnerImpl
import com.rk.utils.dialog
import com.rk.utils.errorDialog
import kotlinx.coroutines.launch

class UniversalRunner : RunnerImpl() {
    
    @SuppressLint("SdCardPath")
    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        // Terminal feature has been removed
        errorDialog("Terminal feature is not available. Code execution requires a terminal.")
    }

    override fun getName(): String {
        return "Universal Runner"
    }

    override fun getIcon(context: Context): Drawable? {
        return drawables.run.getDrawable(context)
    }

    override suspend fun isRunning(): Boolean {
        return false
    }

    override suspend fun stop() {}
}
