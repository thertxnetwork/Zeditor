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
    
    /**
     * Escape a string for safe use in shell commands
     * Replaces single quotes with '\'' to prevent command injection
     */
    private fun shellEscape(str: String): String {
        return "'${str.replace("'", "'\\''")}'"
    }
    
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
        
        // Escape paths for safe shell usage
        val escapedFilePath = shellEscape(filePath)
        val escapedFileName = shellEscape(fileName)
        val escapedWorkDir = shellEscape(workDir)
        val escapedClassName = shellEscape(fileName.substringBeforeLast('.'))
        
        // Determine the interpreter based on file extension
        val extension = fileName.substringAfterLast('.', "")
        val (command, args) = when (extension.lowercase()) {
            "py" -> "/system/bin/sh" to arrayOf("-c", "python3 $escapedFilePath || python $escapedFilePath")
            "js" -> "/system/bin/sh" to arrayOf("-c", "node $escapedFilePath")
            "java" -> "/system/bin/sh" to arrayOf("-c", "cd $escapedWorkDir && javac $escapedFileName && java $escapedClassName")
            "kt" -> "/system/bin/sh" to arrayOf("-c", "cd $escapedWorkDir && kotlinc $escapedFileName -include-runtime -d $escapedClassName.jar && java -jar $escapedClassName.jar")
            "rs" -> "/system/bin/sh" to arrayOf("-c", "cd $escapedWorkDir && rustc $escapedFileName && ./$escapedClassName")
            "rb" -> "/system/bin/sh" to arrayOf("-c", "ruby $escapedFilePath")
            "php" -> "/system/bin/sh" to arrayOf("-c", "php $escapedFilePath")
            "c" -> "/system/bin/sh" to arrayOf("-c", "cd $escapedWorkDir && gcc $escapedFileName -o $escapedClassName && ./$escapedClassName")
            "cpp", "cc", "cxx" -> "/system/bin/sh" to arrayOf("-c", "cd $escapedWorkDir && g++ $escapedFileName -o $escapedClassName && ./$escapedClassName")
            "cs" -> "/system/bin/sh" to arrayOf("-c", "cd $escapedWorkDir && mcs $escapedFileName && mono $escapedClassName.exe")
            "sh", "bash", "zsh", "fish" -> "/system/bin/sh" to arrayOf(filePath)
            "pl" -> "/system/bin/sh" to arrayOf("-c", "perl $escapedFilePath")
            "lua" -> "/system/bin/sh" to arrayOf("-c", "lua $escapedFilePath")
            "r", "R" -> "/system/bin/sh" to arrayOf("-c", "Rscript $escapedFilePath")
            "go" -> "/system/bin/sh" to arrayOf("-c", "cd $escapedWorkDir && go run $escapedFileName")
            "ts" -> "/system/bin/sh" to arrayOf("-c", "ts-node $escapedFilePath")
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
