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
        if (fileObject !is FileWrapper) {
            errorDialog(msgRes = strings.native_runner)
            return
        }
        
        val filePath = fileObject.getAbsolutePath()
        val fileName = fileObject.getName()
        val workDir = fileObject.getParentFile()?.getAbsolutePath() ?: context.filesDir.absolutePath
        
        // Determine the interpreter based on file extension
        val extension = fileName.substringAfterLast('.', "")
        val className = fileName.substringBeforeLast('.')
        val (command, args) = when (extension.lowercase()) {
            "py" -> "/system/bin/sh" to arrayOf("-c", "python3 \"$filePath\" || python \"$filePath\"")
            "js" -> "/system/bin/sh" to arrayOf("-c", "node \"$filePath\"")
            "java" -> "/system/bin/sh" to arrayOf("-c", "cd \"$workDir\" && javac \"$fileName\" && java \"$className\"")
            "kt" -> "/system/bin/sh" to arrayOf("-c", "cd \"$workDir\" && kotlinc \"$fileName\" -include-runtime -d \"${className}.jar\" && java -jar \"${className}.jar\"")
            "rs" -> "/system/bin/sh" to arrayOf("-c", "cd \"$workDir\" && rustc \"$fileName\" && ./\"${fileName.substringBeforeLast('.')}\"")
            "rb" -> "/system/bin/sh" to arrayOf("-c", "ruby \"$filePath\"")
            "php" -> "/system/bin/sh" to arrayOf("-c", "php \"$filePath\"")
            "c" -> "/system/bin/sh" to arrayOf("-c", "cd \"$workDir\" && gcc \"$fileName\" -o \"${fileName.substringBeforeLast('.')}\" && ./\"${fileName.substringBeforeLast('.')}\"")
            "cpp", "cc", "cxx" -> "/system/bin/sh" to arrayOf("-c", "cd \"$workDir\" && g++ \"$fileName\" -o \"${fileName.substringBeforeLast('.')}\" && ./\"${fileName.substringBeforeLast('.')}\"")
            "cs" -> "/system/bin/sh" to arrayOf("-c", "cd \"$workDir\" && mcs \"$fileName\" && mono \"${fileName.substringBeforeLast('.')}.exe\"")
            "sh", "bash", "zsh", "fish" -> "/system/bin/sh" to arrayOf(filePath)
            "pl" -> "/system/bin/sh" to arrayOf("-c", "perl \"$filePath\"")
            "lua" -> "/system/bin/sh" to arrayOf("-c", "lua \"$filePath\"")
            "r", "R" -> "/system/bin/sh" to arrayOf("-c", "Rscript \"$filePath\"")
            "go" -> "/system/bin/sh" to arrayOf("-c", "cd \"$workDir\" && go run \"$fileName\"")
            "ts" -> "/system/bin/sh" to arrayOf("-c", "ts-node \"$filePath\"")
            else -> {
                errorDialog("Unsupported file type: $extension")
                return
            }
        }
        
        // Launch terminal with the appropriate command
        val intent = android.content.Intent(context, com.rk.terminal.TerminalActivity::class.java).apply {
            putExtra(com.rk.terminal.TerminalActivity.EXTRA_COMMAND, command)
            putExtra(com.rk.terminal.TerminalActivity.EXTRA_ARGS, args)
            putExtra(com.rk.terminal.TerminalActivity.EXTRA_WORKDIR, workDir)
            putExtra(com.rk.terminal.TerminalActivity.EXTRA_TITLE, "Run: $fileName")
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
