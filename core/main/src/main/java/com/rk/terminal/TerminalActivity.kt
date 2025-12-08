package com.rk.terminal

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import com.thertxnetwork.zeditor.core.main.R

class TerminalActivity : AppCompatActivity() {
    
    private lateinit var terminalView: TerminalView
    private lateinit var inputField: EditText
    private lateinit var sendButton: ImageButton
    private var terminalSession: TerminalSession? = null
    
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
        
        // Setup terminal view
        setupTerminalView()
        
        // Setup input handling
        setupInputHandling()
        
        // Setup back press handling
        setupBackPressHandling()
        
        // Start terminal session
        startTerminalSession()
    }
    
    private fun setupTerminalView() {
        terminalView.setTerminalViewClient(object : TerminalViewClient {
            override fun onTextChanged(changedView: TerminalView) {
                changedView.post { changedView.invalidate() }
            }
            
            override fun onSingleTapUp(e: android.view.MotionEvent) {
                showSoftKeyboard()
            }
            
            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
            
            override fun shouldEnforceCharBasedInput(): Boolean = true
            
            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
            
            override fun onLongPress(event: android.view.MotionEvent): Boolean = false
            
            override fun readControlKey(): Boolean = false
            
            override fun readAltKey(): Boolean = false
            
            override fun readShiftKey(): Boolean = false
            
            override fun readFnKey(): Boolean = false
            
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = true
            
            override fun onEmulatorSet() {
                // Terminal emulator is ready
            }
            
            override fun logError(tag: String?, message: String?) {
                // Log error if needed
            }
            
            override fun logWarn(tag: String?, message: String?) {
                // Log warning if needed
            }
            
            override fun logInfo(tag: String?, message: String?) {
                // Log info if needed
            }
            
            override fun logDebug(tag: String?, message: String?) {
                // Log debug if needed
            }
            
            override fun logVerbose(tag: String?, message: String?) {
                // Log verbose if needed
            }
            
            override fun logStackTraceWithMessage(message: String?, e: Exception?) {
                // Log stack trace if needed
            }
            
            override fun logStackTrace(message: String?, e: Exception?) {
                // Log stack trace if needed
            }
        })
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
            terminalSession?.write(text + "\r")
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
        
        // Build environment array
        val environment = mutableListOf<String>()
        environment.add("PATH=${System.getenv("PATH") ?: "/system/bin"}")
        environment.add("HOME=${filesDir.absolutePath}")
        environment.add("TMPDIR=${cacheDir.absolutePath}")
        environment.add("TERM=xterm-256color")
        
        // Add custom environment variables
        env.forEach { envVar ->
            if (envVar.contains("=")) {
                environment.add(envVar)
            }
        }
        
        // Create terminal session client
        val sessionClient = object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                terminalView.post { terminalView.onScreenUpdated() }
            }
            
            override fun onTitleChanged(changedSession: TerminalSession) {
                runOnUiThread {
                    title = changedSession.title
                }
            }
            
            override fun onSessionFinished(finishedSession: TerminalSession) {
                runOnUiThread {
                    AlertDialog.Builder(this@TerminalActivity)
                        .setTitle("Session Ended")
                        .setMessage("The terminal session has ended.")
                        .setPositiveButton("OK") { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                }
            }
            
            override fun onBell(session: TerminalSession) {
                // Implement bell sound if needed
            }
            
            override fun onClipboardText(session: TerminalSession, text: String) {
                // Copy to clipboard if needed
            }
            
            override fun onColorsChanged(session: TerminalSession) {
                // Update colors if needed
            }
            
            override fun getTermuxActivityRootView() = terminalView
            
            override fun logError(tag: String?, message: String?) {}
            
            override fun logWarn(tag: String?, message: String?) {}
            
            override fun logInfo(tag: String?, message: String?) {}
            
            override fun logDebug(tag: String?, message: String?) {}
            
            override fun logVerbose(tag: String?, message: String?) {}
            
            override fun logStackTraceWithMessage(message: String?, e: Exception?) {}
            
            override fun logStackTrace(message: String?, e: Exception?) {}
        }
        
        // Create terminal session
        terminalSession = TerminalSession(
            command,
            workdir,
            args,
            environment.toTypedArray(),
            false,
            sessionClient
        )
        
        // Attach session to view
        terminalView.attachSession(terminalSession)
        
        // Request focus
        terminalView.requestFocus()
    }
    
    private fun showSoftKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT)
        inputField.requestFocus()
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (terminalView.onKeyDown(keyCode, event)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (terminalView.onKeyUp(keyCode, event)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        terminalSession?.finishIfRunning()
    }
}
