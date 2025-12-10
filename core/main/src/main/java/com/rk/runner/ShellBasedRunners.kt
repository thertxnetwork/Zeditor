package com.rk.runner

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.DefaultScope
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.localDir
import com.rk.file.runnerDir
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.runner.runners.code.CodeRunnerActivity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ShellBasedRunners {
    val runners = mutableStateListOf<ShellBasedRunner>()

    init {
        DefaultScope.launch { indexRunners() }
    }

    suspend fun newRunner(runner: ShellBasedRunner): Boolean {
        return withContext(Dispatchers.IO) {
            if (runners.find { it.getName() == runner.getName() } == null) {
                withContext(Dispatchers.Main) { runners.add(runner) }
                runnerDir()
                    .child("${runner.getName()}.sh")
                    .createFileIfNot()
                    .writeText("echo \"This runner has no implementation. Click the runner and add your own script.\"")
                saveRunners()
                true
            } else {
                false
            }
        }
    }

    suspend fun saveRunners() {
        val json = Gson().toJson(runners)
        localDir().child("runners.json").writeText(json)
    }

    suspend fun deleteRunner(runner: ShellBasedRunner, deleteScript: Boolean = true) {
        runners.remove(runner)
        saveRunners()
        runnerDir().child("${runner.getName()}.sh").createFileIfNot().delete()
    }

    suspend fun indexRunners() {
        withContext(Dispatchers.IO) {
            val file = localDir().child("runners.json")
            if (file.exists()) {
                val content = file.readText()
                val type = object : TypeToken<List<ShellBasedRunner>>() {}.type
                runners.clear()
                runners.addAll(Gson().fromJson<List<ShellBasedRunner>>(content, type))
            }
        }
    }
}

data class ShellBasedRunner(private val name: String, val regex: String) : RunnerImpl() {
    override suspend fun run(context: Context, fileObject: FileObject) {
        // Get the script file for this runner
        val scriptFile = getScript()
        
        // Launch CodeRunnerActivity to execute the script
        val intent = Intent(context, CodeRunnerActivity::class.java).apply {
            putExtra(CodeRunnerActivity.EXTRA_FILE_PATH, scriptFile.absolutePath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun getName(): String {
        return name
    }

    fun getScript(): File {
        return runnerDir().child("${getName()}.sh").createFileIfNot()
    }

    override fun getIcon(context: Context): Drawable? {
        return drawables.bash.getDrawable(context)
    }

    override suspend fun isRunning(): Boolean {
        return false
    }

    override suspend fun stop() {}
}
