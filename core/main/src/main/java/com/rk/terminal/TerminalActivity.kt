package com.rk.terminal

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.thertxnetwork.zeditor.core.main.R

class TerminalActivity : AppCompatActivity() {
    
    private lateinit var terminalView: TerminalView
    private lateinit var inputField: EditText
    private lateinit var sendButton: ImageButton
    
    companion object {
        const val EXTRA_COMMAND = "command"
        const val EXTRA_ARGS = "args"
        const val EXTRA_WORKDIR = "workdir"
        const val EXTRA_ENV = "env"
        const val EXTRA_TITLE = "title"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)
        
        // Initialize views
        terminalView = findViewById(R.id.terminal_view)
        inputField = findViewById(R.id.input_field)
        sendButton = findViewById(R.id.send_button)
        
        // Setup input handling
        setupInputHandling()
        
        // Setup back press handling
        setupBackPressHandling()
        
        // Start terminal session
        startTerminalSession()
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
        environment["TERM"] = "xterm"
        
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
