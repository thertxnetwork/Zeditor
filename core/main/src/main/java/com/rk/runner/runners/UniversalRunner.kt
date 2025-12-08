package com.rk.runner.runners

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Environment
import com.rk.DefaultScope
import com.rk.exec.TerminalCommand
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.localBinDir
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

        val path = fileObject.getAbsolutePath()
        if (
            path.startsWith("/sdcard") ||
                path.startsWith("/storage/") ||
                path.startsWith(Environment.getExternalStorageDirectory().absolutePath)
        ) {
            dialog(
                title = strings.attention.getString(),
                msg = strings.sdcard_filetype.getString(),
                okString = strings.continue_action,
                onCancel = {},
                onOk = { DefaultScope.launch { launchUniversalRunner(context, fileObject) } },
            )
            return
        }

        launchUniversalRunner(context, fileObject)
    }

    suspend fun launchUniversalRunner(context: Context, fileObject: FileObject) {
        // TODO: Implement universal runner without terminal dependency
        // The terminal-based runner has been removed
        // User needs to implement a different way to run code files
        errorDialog(msgRes = strings.unsupported_feature)
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
