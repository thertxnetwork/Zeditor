package com.rk.runner.runners

import android.annotation.SuppressLint
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
import com.rk.runner.runners.code.CodeExecutor
import com.rk.runner.runners.code.CodeRunnerActivity
import com.rk.utils.dialog

class UniversalRunner : RunnerImpl() {
    
    @SuppressLint("SdCardPath")
    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        // Check if the file type is supported
        if (!CodeExecutor.canExecute(fileObject)) {
            dialog(
                title = strings.attention.getString(),
                msg = "Unsupported file type: ${fileObject.getName()}",
                onOk = {}
            )
            return
        }

        // Launch the CodeRunnerActivity to execute the file
        val intent = Intent(context, CodeRunnerActivity::class.java).apply {
            putExtra(CodeRunnerActivity.EXTRA_FILE_PATH, fileObject.getAbsolutePath())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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
