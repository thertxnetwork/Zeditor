package com.rk.terminal

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.thertxnetwork.zeditor.core.main.R
import kotlinx.coroutines.launch

class TerminalActivity : AppCompatActivity() {
    
    private lateinit var terminalView: TerminalView
    private lateinit var inputField: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var progressLayout: View
    
    private lateinit var bootstrap: UbuntuBootstrap
    private var isUbuntuMode = false
    
    companion object {
        const val EXTRA_COMMAND = "command"
        const val EXTRA_ARGS = "args"
        const val EXTRA_WORKDIR = "workdir"
        const val EXTRA_ENV = "env"
        const val EXTRA_TITLE = "title"
        const val EXTRA_UBUNTU_MODE = "ubuntu_mode"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)
        
        // Initialize views
        terminalView = findViewById(R.id.terminal_view)
        inputField = findViewById(R.id.input_field)
        sendButton = findViewById(R.id.send_button)
        progressBar = findViewById(R.id.progress_bar)
        progressText = findViewById(R.id.progress_text)
        progressLayout = findViewById(R.id.progress_layout)
        
        bootstrap = UbuntuBootstrap(this)
        isUbuntuMode = intent.getBooleanExtra(EXTRA_UBUNTU_MODE, false)
        
        // Setup input handling
        setupInputHandling()
        
        // Setup back press handling
        setupBackPressHandling()
        
        // Start terminal session
        if (isUbuntuMode) {
            checkAndStartUbuntu()
        } else {
            startTerminalSession()
        }
    }
    
    private fun checkAndStartUbuntu() {
        lifecycleScope.launch {
            if (bootstrap.isInstalled()) {
                startUbuntuSession()
            } else {
                showInstallDialog()
            }
        }
    }
    
    private fun showInstallDialog() {
        AlertDialog.Builder(this)
            .setTitle("Ubuntu Installation Required")
            .setMessage("Ubuntu is not installed. This will download approximately 100MB of data.\n\nDo you want to continue?")
            .setPositiveButton("Install") { _, _ ->
                installUbuntu()
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun installUbuntu() {
        progressLayout.visibility = View.VISIBLE
        terminalView.visibility = View.GONE
        inputField.visibility = View.GONE
        sendButton.visibility = View.GONE
        
        lifecycleScope.launch {
            val result = bootstrap.install { message, progress ->
                runOnUiThread {
                    progressText.text = message
                    progressBar.progress = progress
                }
            }
            
            result.onSuccess {
                runOnUiThread {
                    progressLayout.visibility = View.GONE
                    terminalView.visibility = View.VISIBLE
                    inputField.visibility = View.VISIBLE
                    sendButton.visibility = View.VISIBLE
                    startUbuntuSession()
                }
            }.onFailure { error ->
                runOnUiThread {
                    AlertDialog.Builder(this@TerminalActivity)
                        .setTitle("Installation Failed")
                        .setMessage("Failed to install Ubuntu:\n\n${error.message}\n\nPlease check your internet connection and try again.")
                        .setPositiveButton("Retry") { _, _ ->
                            installUbuntu()
                        }
                        .setNegativeButton("Exit") { _, _ ->
                            finish()
                        }
                        .show()
                }
            }
        }
    }
    
    private fun startUbuntuSession() {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Ubuntu Terminal"
        setTitle(title)
        
        val command = bootstrap.getLaunchCommand()
        val environment = bootstrap.getEnvironment()
        
        // Build environment map
        val envMap = mutableMapOf<String, String>()
        environment.forEach { envVar ->
            val parts = envVar.split("=", limit = 2)
            if (parts.size == 2) {
                envMap[parts[0]] = parts[1]
            }
        }
        
        terminalView.startSession(command, envMap)
        terminalView.requestFocus()
    }
    
    private fun setupInputHandling() {
        sendButton.setOnClickListener {
            sendCommand()
        }
        
        inputField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                sendCommand()
                true
            } else {
                false
            }
        }
    }
    
    private fun setupBackPressHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@TerminalActivity)
                    .setTitle("Exit Terminal")
                    .setMessage("Are you sure you want to exit? The terminal session will be terminated.")
                    .setPositiveButton("Exit") { _, _ ->
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })
    }
    
    private fun sendCommand() {
        val text = inputField.text.toString()
        if (text.isNotEmpty()) {
            terminalView.sendInput(text + "\n")
            inputField.text.clear()
        }
    }
    
    private fun startTerminalSession() {
        // Get intent extras
        val command = intent.getStringExtra(EXTRA_COMMAND) ?: "/system/bin/sh"
        val args = intent.getStringArrayExtra(EXTRA_ARGS) ?: arrayOf()
        val workdir = intent.getStringExtra(EXTRA_WORKDIR) ?: filesDir.absolutePath
        val env = intent.getStringArrayExtra(EXTRA_ENV) ?: arrayOf()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Terminal"
        
        // Set activity title
        setTitle(title)
        
        // Build command array - no shell interpolation, pass args directly
        val commandArray = if (workdir != filesDir.absolutePath) {
            // Use cd command with proper quoting, then exec to replace shell process
            arrayOf(command, "-c", "cd ${workdir.replace("\"", "\\\"")} && exec \"$command\" ${args.joinToString(" ") { "\"${it.replace("\"", "\\\"")}\"" }}")
        } else {
            arrayOf(command) + args
        }
        
        // Build environment map
        val environment = mutableMapOf<String, String>()
        environment["PATH"] = System.getenv("PATH") ?: "/system/bin"
        environment["HOME"] = filesDir.absolutePath
        environment["TMPDIR"] = cacheDir.absolutePath
        environment["TERM"] = "xterm-256color"
        
        // Add custom environment variables
        env.forEach { envVar ->
            val parts = envVar.split("=", limit = 2)
            if (parts.size == 2) {
                environment[parts[0]] = parts[1]
            }
        }
        
        // Start terminal session
        terminalView.startSession(commandArray, environment)
        
        // Request focus
        terminalView.requestFocus()
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (terminalView.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        terminalView.cleanup()
    }
}
