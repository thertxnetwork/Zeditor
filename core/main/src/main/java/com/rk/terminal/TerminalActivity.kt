package com.rk.terminal

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.InputMethodManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.termux.shared.termux.extrakeys.ExtraKeyButton
import com.termux.shared.termux.extrakeys.ExtraKeysConstants
import com.termux.shared.termux.extrakeys.ExtraKeysInfo
import com.termux.shared.termux.extrakeys.ExtraKeysView
import com.termux.shared.termux.extrakeys.SpecialButton
import com.termux.shared.termux.terminal.io.TerminalExtraKeys
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import com.thertxnetwork.zeditor.core.main.R
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min

class TerminalActivity : AppCompatActivity() {
    
    private lateinit var terminalView: TerminalView
    private lateinit var extraKeysView: ExtraKeysView
    private lateinit var inputField: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var inputLayout: View
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var progressLayout: View
    private lateinit var toggleExtraKeysFab: FloatingActionButton
    
    private lateinit var bootstrap: UbuntuBootstrap
    private var isUbuntuMode = false
    private var terminalSession: TerminalSession? = null
    private var terminalExtraKeys: TerminalExtraKeys? = null
    
    // For draggable FAB
    private var dX = 0f
    private var dY = 0f
    private var lastAction = 0
    
    // Terminal text size and scale
    private var mTerminalTextSize = 14f
    private val MIN_FONTSIZE = 8f
    private val MAX_FONTSIZE = 32f
    
    companion object {
        const val EXTRA_COMMAND = "command"
        const val EXTRA_ARGS = "args"
        const val EXTRA_WORKDIR = "workdir"
        const val EXTRA_ENV = "env"
        const val EXTRA_TITLE = "title"
        const val EXTRA_UBUNTU_MODE = "ubuntu_mode"
        
        // Default extra keys configuration for the terminal
        // Two rows: top row has ESC, TAB, CTRL, ALT, special chars, and navigation
        // bottom row has SHIFT, more navigation, PASTE, and function keys
        private const val DEFAULT_EXTRA_KEYS = """
            [
              ["ESC", "/", "-", "HOME", "UP", "END", "PGUP", "DEL"],
              ["TAB", "CTRL", "ALT", "SHIFT", "LEFT", "DOWN", "RIGHT", "PGDN"]
            ]
        """
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)
        
        // Initialize views
        terminalView = findViewById(R.id.terminal_view)
        extraKeysView = findViewById(R.id.extra_keys)
        inputField = findViewById(R.id.input_field)
        sendButton = findViewById(R.id.send_button)
        inputLayout = findViewById(R.id.input_layout)
        progressBar = findViewById(R.id.progress_bar)
        progressText = findViewById(R.id.progress_text)
        progressLayout = findViewById(R.id.progress_layout)
        toggleExtraKeysFab = findViewById(R.id.toggle_extra_keys_fab)
        
        bootstrap = UbuntuBootstrap(this)
        isUbuntuMode = intent.getBooleanExtra(EXTRA_UBUNTU_MODE, false)
        
        // Setup toggle extra keys FAB
        setupToggleExtraKeysFab()
        
        // Setup extra keys early so they're ready when FAB is clicked
        setupExtraKeys()
        
        // Initialize TerminalRenderer with default text size
        // This must be done before attaching any session to prevent NullPointerException
        terminalView.setTextSize(14)
        
        // Setup terminal view client
        terminalView.setTerminalViewClient(object : TerminalViewClient {
            override fun onScale(scale: Float): Float {
                // Clamp the scale to reasonable limits
                mTerminalTextSize = max(MIN_FONTSIZE, min(scale, MAX_FONTSIZE))
                terminalView.setTextSize(mTerminalTextSize.toInt())
                return mTerminalTextSize
            }
            
            override fun onSingleTapUp(e: android.view.MotionEvent?) {
                // Show soft keyboard when terminal is tapped
                terminalView.requestFocus()
                showSoftKeyboard()
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
                // Show context menu for copy/paste on long press
                showTextActionMenu()
                return true
            }
            
            override fun readControlKey(): Boolean {
                // Read CTRL state from ExtraKeysView
                return extraKeysView.readSpecialButton(SpecialButton.CTRL, false) ?: false
            }
            
            override fun readAltKey(): Boolean {
                // Read ALT state from ExtraKeysView
                return extraKeysView.readSpecialButton(SpecialButton.ALT, false) ?: false
            }
            
            override fun readShiftKey(): Boolean {
                // Read SHIFT state from ExtraKeysView
                return extraKeysView.readSpecialButton(SpecialButton.SHIFT, false) ?: false
            }
            
            override fun readFnKey(): Boolean {
                // Read FN state from ExtraKeysView
                return extraKeysView.readSpecialButton(SpecialButton.FN, false) ?: false
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
        extraKeysView.visibility = View.GONE
        inputLayout.visibility = View.GONE
        
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
                    extraKeysView.visibility = View.VISIBLE
                    // Keep input layout hidden - using extra keys instead
                    inputLayout.visibility = View.GONE
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
        
        // Create terminal session with proper parameters
        val shellPath = command[0]
        val args = command.drop(1).toTypedArray()
        val env = environment // Already in "KEY=VALUE" format from getEnvironment()
        
        createTerminalSession(shellPath, args, env, bootstrap.rootfsPath.absolutePath)
    }
    
    private fun setupExtraKeys() {
        try {
            val extraKeysInfoObj = ExtraKeysInfo(
                DEFAULT_EXTRA_KEYS,
                ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY,
                ExtraKeysConstants.CONTROL_CHARS_ALIASES
            )
            
            // Create the TerminalExtraKeys instance once
            terminalExtraKeys = TerminalExtraKeys(terminalView)
            
            // Setup the extra keys view client
            extraKeysView.setExtraKeysViewClient(object : ExtraKeysView.IExtraKeysView {
                override fun onExtraKeyButtonClick(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton) {
                    // Delegate to TerminalExtraKeys for proper key handling
                    terminalExtraKeys?.onExtraKeyButtonClick(view, buttonInfo, button)
                }
                
                override fun performExtraKeyButtonHapticFeedback(view: View, buttonInfo: ExtraKeyButton, button: MaterialButton): Boolean {
                    // Provide haptic feedback on key press
                    button.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    return true
                }
            })
            
            // Load the extra keys
            extraKeysView.reload(extraKeysInfoObj, 1.0f)
            
            // Start with extra keys hidden - user can show via FAB
            extraKeysView.visibility = View.GONE
        } catch (e: Exception) {
            android.util.Log.e("TerminalActivity", "Failed to setup extra keys", e)
        }
    }
    
    private fun setupToggleExtraKeysFab() {
        // Make FAB draggable with click support
        toggleExtraKeysFab.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    lastAction = MotionEvent.ACTION_DOWN
                    false // Return false to allow click event to be processed
                }
                MotionEvent.ACTION_MOVE -> {
                    view.x = event.rawX + dX
                    view.y = event.rawY + dY
                    lastAction = MotionEvent.ACTION_MOVE
                    true // Consume move event
                }
                MotionEvent.ACTION_UP -> {
                    // If it was a drag (not just a tap), consume the event
                    if (lastAction == MotionEvent.ACTION_MOVE) {
                        true // Consume to prevent click after drag
                    } else {
                        false // Allow click to be processed
                    }
                }
                else -> false
            }
        }
        
        // Toggle extra keys visibility on click
        toggleExtraKeysFab.setOnClickListener {
            android.util.Log.d("TerminalActivity", "FAB clicked, current visibility: ${extraKeysView.visibility}")
            if (extraKeysView.visibility == View.VISIBLE) {
                extraKeysView.visibility = View.GONE
                android.util.Log.d("TerminalActivity", "Extra keys hidden")
            } else {
                extraKeysView.visibility = View.VISIBLE
                android.util.Log.d("TerminalActivity", "Extra keys shown")
                // Request layout to ensure view is properly displayed
                extraKeysView.requestLayout()
            }
        }
    }
    
    private fun showTextActionMenu() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val hasClipboard = clipboard.hasPrimaryClip()
        
        val options = if (hasClipboard) {
            arrayOf("Copy All", "Paste")
        } else {
            arrayOf("Copy All")
        }
        
        AlertDialog.Builder(this)
            .setTitle("Text Actions")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Copy all text from terminal
                        terminalSession?.let { session ->
                            val text = session.emulator.screen.transcriptText
                            if (text.isNotEmpty()) {
                                clipboard.setPrimaryClip(ClipData.newPlainText("Terminal", text))
                                android.widget.Toast.makeText(this, "Text copied", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(this, "No text to copy", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    1 -> {
                        // Paste
                        clipboard.primaryClip?.let { clip ->
                            if (clip.itemCount > 0) {
                                val text = clip.getItemAt(0).text?.toString()
                                text?.let { terminalSession?.write(it) }
                            }
                        }
                    }
                }
            }
            .show()
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
        
        createTerminalSession(command, args, environment.toTypedArray(), workdir)
    }
    
    private fun createTerminalSession(shellPath: String, args: Array<String>, environment: Array<String>, cwd: String) {
        val sessionClient = object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession) {
                terminalView.onScreenUpdated()
            }
            
            override fun onTitleChanged(changedSession: TerminalSession) {
                // Update title if needed
            }
            
            override fun setTerminalShellPid(session: TerminalSession, pid: Int) {
                // Store shell PID if needed
            }
            
            override fun onSessionFinished(finishedSession: TerminalSession) {
                runOnUiThread {
                    // Check if activity is still valid before showing dialog
                    if (!isFinishing && !isDestroyed) {
                        AlertDialog.Builder(this@TerminalActivity)
                            .setTitle("Session Ended")
                            .setMessage("The terminal session has ended.")
                            .setPositiveButton("OK") { _, _ ->
                                finish()
                            }
                            .show()
                    } else {
                        // Activity is not valid, just finish it
                        finish()
                    }
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
                return com.termux.terminal.TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
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
            shellPath,
            cwd,
            args,
            environment,
            com.termux.terminal.TerminalEmulator.DEFAULT_TERMINAL_TRANSCRIPT_ROWS,
            sessionClient
        )
        
        // Post to ensure TerminalView layout is complete before attaching session
        terminalView.post {
            terminalView.attachSession(terminalSession)
            terminalView.requestFocus()
            
            // Note: Keyboard is not shown automatically to keep extra keys visible.
            // Users can tap the terminal to show the keyboard when needed.
        }
    }
    
    private fun showSoftKeyboard() {
        // Post to message queue to ensure view is ready and has focus
        terminalView.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        terminalSession?.finishIfRunning()
    }
}
