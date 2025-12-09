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
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import com.thertxnetwork.zeditor.core.main.R
import kotlinx.coroutines.launch
import java.io.File

class TerminalActivity : AppCompatActivity() {
    
    private lateinit var terminalView: TerminalView
    private lateinit var inputField: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var progressLayout: View
    
    private lateinit var bootstrap: UbuntuBootstrap
    private var isUbuntuMode = false
    private var terminalSession: TerminalSession? = null
    
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
        
        // Setup terminal view client
        terminalView.setTerminalViewClient(object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                return scale
            }
            
            override fun onSingleTapUp(e: android.view.MotionEvent?) {
                // Show keyboard
                inputField.requestFocus()
            }
            
            override fun shouldBackButtonBeMappedToEscape(): Boolean {
                return false
            }
            
            override fun shouldEnforceCharBasedInput(): Boolean {
                return true
            }
            
            override fun shouldUseCtrlSpaceWorkaround(): Boolean {
                return false
            }
            
            override fun isTerminalViewSelected(): Boolean {
                return true
            }
            
            override fun copyModeChanged(copyMode: Boolean) {
                // Handle copy mode
            }
            
            override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean {
                return false
            }
            
            override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean {
                return false
            }
            
            override fun onLongPress(event: android.view.MotionEvent?): Boolean {
                return false
            }
            
            override fun readControlKey(): Boolean {
                return false
            }
            
            override fun readAltKey(): Boolean {
                return false
            }
            
            override fun readShiftKey(): Boolean {
                return false
            }
            
            override fun readFnKey(): Boolean {
                return false
            }
            
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean {
                return false
            }
            
            override fun onEmulatorSet() {
                // Terminal emulator ready
            }
            
            override fun logError(tag: String?, message: String?) {
                android.util.Log.e(tag ?: "Terminal", message ?: "Unknown error")
            }
            
            override fun logWarn(tag: String?, message: String?) {
                android.util.Log.w(tag ?: "Terminal", message ?: "Unknown warning")
            }
            
            override fun logInfo(tag: String?, message: String?) {
                android.util.Log.i(tag ?: "Terminal", message ?: "Unknown info")
            }
            
            override fun logDebug(tag: String?, message: String?) {
                android.util.Log.d(tag ?: "Terminal", message ?: "Unknown debug")
            }
            
            override fun logVerbose(tag: String?, message: String?) {
                android.util.Log.v(tag ?: "Terminal", message ?: "Unknown verbose")
            }
            
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
                android.util.Log.e(tag ?: "Terminal", message ?: "Unknown error", e)
            }
            
            override fun logStackTrace(tag: String?, e: Exception?) {
                android.util.Log.e(tag ?: "Terminal", "Exception", e)
            }
        })
        
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
        
        createTerminalSession(command, environment.toTypedArray(), bootstrap.getPrefix())
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
        if (text.isNotEmpty() && terminalSession != null) {
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
        environment.addAll(env)
        
        // Build command array
        val fullCommand = arrayOf(command) + args
        
        createTerminalSession(fullCommand.joinToString(" "), environment.toTypedArray(), workdir)
    }
    
    private fun createTerminalSession(executablePath: String, environment: Array<String>, cwd: String) {
        val sessionClient = object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                terminalView.onScreenUpdated()
            }
            
            override fun onTitleChanged(changedSession: TerminalSession) {
                // Update title if needed
            }
            
            override fun onSessionFinished(finishedSession: TerminalSession) {
                runOnUiThread {
                    AlertDialog.Builder(this@TerminalActivity)
                        .setTitle("Session Ended")
                        .setMessage("The terminal session has ended.")
                        .setPositiveButton("OK") { _, _ ->
                            finish()
                        }
                        .show()
                }
            }
            
            override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
                // Handle clipboard
                text?.let {
                    val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Terminal", it)
                    clipboard.setPrimaryClip(clip)
                }
            }
            
            override fun onPasteTextFromClipboard(session: TerminalSession?) {
                // Handle paste
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.primaryClip?.let { clip ->
                    if (clip.itemCount > 0) {
                        val text = clip.getItemAt(0).text?.toString()
                        text?.let { terminalSession?.write(it) }
                    }
                }
            }
            
            override fun onBell(session: TerminalSession) {
                // Handle bell sound/vibration
            }
            
            override fun onColorsChanged(session: TerminalSession) {
                terminalView.onScreenUpdated()
            }
            
            override fun onTerminalCursorStateChange(state: Boolean) {
                // Handle cursor state
            }
            
            override fun getTerminalCursorStyle(): Int {
                return TerminalSessionClient.TERMINAL_CURSOR_STYLE_BLOCK
            }
            
            override fun logError(tag: String?, message: String?) {
                android.util.Log.e(tag ?: "Terminal", message ?: "")
            }
            
            override fun logWarn(tag: String?, message: String?) {
                android.util.Log.w(tag ?: "Terminal", message ?: "")
            }
            
            override fun logInfo(tag: String?, message: String?) {
                android.util.Log.i(tag ?: "Terminal", message ?: "")
            }
            
            override fun logDebug(tag: String?, message: String?) {
                android.util.Log.d(tag ?: "Terminal", message ?: "")
            }
            
            override fun logVerbose(tag: String?, message: String?) {
                android.util.Log.v(tag ?: "Terminal", message ?: "")
            }
            
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
                android.util.Log.e(tag ?: "Terminal", message ?: "", e)
            }
            
            override fun logStackTrace(tag: String?, e: Exception?) {
                android.util.Log.e(tag ?: "Terminal", "", e)
            }
        }
        
        terminalSession = TerminalSession(
            executablePath,
            cwd,
            arrayOf(),
            environment,
            TerminalSession.TERMINAL_TRANSCRIPT_ROWS_DEFAULT,
            sessionClient
        )
        
        terminalView.attachSession(terminalSession)
        terminalView.requestFocus()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        terminalSession?.finishIfRunning()
    }
}
