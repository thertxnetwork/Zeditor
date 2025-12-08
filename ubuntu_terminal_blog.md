# Building a Full Ubuntu Terminal Emulator in Android with Kotlin and PRoot

## Introduction

Ever wanted to run a full Ubuntu Linux environment directly on your Android device? In this comprehensive guide, we'll build a professional terminal emulator app that runs Ubuntu 22.04 using PRoot - no root access required! This solution gives you the complete Ubuntu experience with access to all your favorite packages and tools.

## What We're Building

By the end of this tutorial, you'll have a fully functional terminal app that:
- Runs Ubuntu 22.04 LTS in a sandboxed environment
- Works without requiring root access (uses PRoot)
- Stores everything in internal storage (`/nexterminal` directory)
- Provides a proper terminal interface with keyboard input
- Shows installation progress with a beautiful UI
- Supports full package management with `apt`

## Why PRoot Over Other Solutions?

**PRoot** (Portable chroot) is the perfect solution because:
- ✅ No root access required
- ✅ Full Ubuntu compatibility
- ✅ Access to official Ubuntu repositories
- ✅ Supports all Ubuntu packages
- ✅ True Linux filesystem hierarchy
- ✅ Better than containers for this use case

## Architecture Overview

Our app consists of these key components:

1. **UbuntuBootstrap**: Handles downloading and setting up Ubuntu rootfs
2. **PRoot Manager**: Manages PRoot binary and execution
3. **Terminal View**: Custom terminal interface for user interaction
4. **Main Activity**: Orchestrates everything with progress tracking

## Prerequisites

- Android Studio Arctic Fox or later
- Minimum SDK 24 (Android 7.0)
- Basic knowledge of Kotlin and coroutines
- Understanding of Linux basics

## Step 1: Project Setup

### Create New Project

Start by creating a new Android project with Kotlin support.

### Add Dependencies (build.gradle.kts)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yourpackage.ubuntuterminal"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yourpackage.ubuntuterminal"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    
    // OkHttp for downloading files
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

### Configure AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Required permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" 
        android:maxSdkVersion="28" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" 
        android:maxSdkVersion="28" />
    
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/Theme.Material3.Dark"
        android:requestLegacyExternalStorage="true">
        
        <activity
            android:name=".MainActivity"
            android:windowSoftInputMode="adjustResize"
            android:screenOrientation="unspecified"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

## Step 2: Ubuntu Bootstrap Manager

Create `UbuntuBootstrap.kt` to handle Ubuntu installation:

```kotlin
package com.yourpackage.ubuntuterminal

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.*
import java.util.concurrent.TimeUnit

class UbuntuBootstrap(private val context: Context) {
    
    companion object {
        private const val TAG = "UbuntuBootstrap"
        private const val UBUNTU_VERSION = "22.04"
        
        // Detect device architecture
        private val ARCH = when {
            Build.SUPPORTED_ABIS.contains("arm64-v8a") -> "arm64"
            Build.SUPPORTED_ABIS.contains("armeabi-v7a") -> "armhf"
            Build.SUPPORTED_ABIS.contains("x86_64") -> "amd64"
            Build.SUPPORTED_ABIS.contains("x86") -> "i386"
            else -> "arm64"
        }
        
        private const val UBUNTU_BASE_URL = 
            "https://cdimage.ubuntu.com/ubuntu-base/releases/$UBUNTU_VERSION/release"
        
        private val ROOTFS_URL = "$UBUNTU_BASE_URL/ubuntu-base-$UBUNTU_VERSION-base-$ARCH.tar.gz"
    }
    
    // Ubuntu will be installed in internal storage
    val rootfsPath: File
        get() = File(context.filesDir, "nexterminal/ubuntu")
    
    private val tarballPath: File
        get() = File(context.cacheDir, "ubuntu-rootfs.tar.gz")
    
    /**
     * Check if Ubuntu is already installed
     */
    suspend fun isInstalled(): Boolean = withContext(Dispatchers.IO) {
        val bashBinary = File(rootfsPath, "bin/bash")
        bashBinary.exists() && bashBinary.canExecute()
    }
    
    /**
     * Download and install Ubuntu
     */
    suspend fun install(
        progressCallback: (message: String, progress: Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Create directory structure
            progressCallback("Creating directories...", 5)
            rootfsPath.mkdirs()
            
            // Step 2: Download Ubuntu rootfs
            if (!tarballPath.exists()) {
                progressCallback("Downloading Ubuntu $UBUNTU_VERSION ($ARCH)...", 10)
                downloadRootfs(progressCallback)
            } else {
                progressCallback("Using cached Ubuntu rootfs...", 40)
            }
            
            // Step 3: Extract rootfs
            progressCallback("Extracting Ubuntu filesystem...", 45)
            extractRootfs(progressCallback)
            
            // Step 4: Setup environment
            progressCallback("Configuring environment...", 85)
            setupEnvironment()
            
            // Step 5: Setup PRoot
            progressCallback("Setting up PRoot...", 95)
            setupProot()
            
            // Cleanup
            progressCallback("Cleaning up...", 98)
            tarballPath.delete()
            
            progressCallback("Ubuntu installation complete!", 100)
            Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Download Ubuntu base rootfs
     */
    private suspend fun downloadRootfs(
        progressCallback: (String, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder()
            .url(ROOTFS_URL)
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download failed: ${response.code} ${response.message}")
            }
            
            val body = response.body ?: throw IOException("Empty response body")
            val totalSize = body.contentLength()
            
            body.byteStream().use { input ->
                FileOutputStream(tarballPath).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        if (totalSize > 0) {
                            val progress = (10 + (totalBytesRead * 35 / totalSize)).toInt()
                            val mbRead = totalBytesRead / 1024 / 1024
                            val mbTotal = totalSize / 1024 / 1024
                            
                            progressCallback(
                                "Downloading Ubuntu... ${mbRead}MB / ${mbTotal}MB",
                                progress
                            )
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Extract the tarball using tar command
     */
    private suspend fun extractRootfs(
        progressCallback: (String, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        progressCallback("Extracting files (this may take a while)...", 50)
        
        // Try using system tar command
        try {
            val process = ProcessBuilder()
                .command(
                    "tar",
                    "-xzf",
                    tarballPath.absolutePath,
                    "-C",
                    rootfsPath.absolutePath
                )
                .redirectErrorStream(true)
                .start()
            
            // Monitor progress
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                Log.d(TAG, "tar: $line")
            }
            
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw IOException("tar extraction failed with exit code: $exitCode")
            }
            
            progressCallback("Extraction complete", 80)
            
        } catch (e: Exception) {
            Log.e(TAG, "System tar failed, trying busybox", e)
            
            // Fallback: Try busybox tar
            val process = ProcessBuilder()
                .command(
                    "busybox",
                    "tar",
                    "-xzf",
                    tarballPath.absolutePath,
                    "-C",
                    rootfsPath.absolutePath
                )
                .redirectErrorStream(true)
                .start()
            
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw IOException("busybox tar extraction failed with exit code: $exitCode")
            }
            
            progressCallback("Extraction complete", 80)
        }
    }
    
    /**
     * Setup Ubuntu environment (configs, directories, etc.)
     */
    private fun setupEnvironment() {
        // Create essential directories
        val dirs = listOf(
            "dev", "proc", "sys", "tmp", 
            "home", "root", "var/tmp"
        )
        
        dirs.forEach { dir ->
            File(rootfsPath, dir).apply {
                mkdirs()
                setReadable(true, false)
                setWritable(true, false)
                setExecutable(true, false)
            }
        }
        
        // Setup DNS resolution
        val etcDir = File(rootfsPath, "etc")
        etcDir.mkdirs()
        
        File(etcDir, "resolv.conf").writeText("""
            nameserver 8.8.8.8
            nameserver 8.8.4.4
            nameserver 1.1.1.1
        """.trimIndent())
        
        // Setup hosts file
        File(etcDir, "hosts").writeText("""
            127.0.0.1 localhost
            ::1 localhost ip6-localhost ip6-loopback
            fe00::0 ip6-localnet
            ff00::0 ip6-mcastprefix
            ff02::1 ip6-allnodes
            ff02::2 ip6-allrouters
        """.trimIndent())
        
        // Create .bashrc for root user
        val rootHome = File(rootfsPath, "root")
        rootHome.mkdirs()
        
        File(rootHome, ".bashrc").writeText("""
            # Ubuntu environment
            export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
            export HOME=/root
            export TERM=xterm-256color
            export TMPDIR=/tmp
            export LANG=C.UTF-8
            export LC_ALL=C.UTF-8
            
            # Colorful prompt
            PS1='\[\e[0;32m\]\u@ubuntu\[\e[0m\]:\[\e[0;34m\]\w\[\e[0m\]\$ '
            
            # Useful aliases
            alias ll='ls -lah'
            alias la='ls -A'
            alias l='ls -CF'
            alias ..='cd ..'
            alias ...='cd ../..'
            
            # Welcome message
            if [ -f /etc/os-release ]; then
                . /etc/os-release
                echo "Welcome to Ubuntu $VERSION on Android!"
                echo "Type 'apt update' to refresh package lists"
                echo ""
            fi
        """.trimIndent())
        
        // Create startup script
        val startScript = File(rootfsPath, "start.sh")
        startScript.writeText("""
            #!/bin/bash
            cd /root
            exec /bin/bash --login
        """.trimIndent())
        startScript.setExecutable(true)
        
        // Fix permissions
        File(rootfsPath, "tmp").apply {
            setReadable(true, false)
            setWritable(true, false)
            setExecutable(true, false)
        }
    }
    
    /**
     * Extract and setup PRoot binary
     */
    private fun setupProot() {
        val prootFile = File(context.filesDir, "proot")
        
        if (!prootFile.exists()) {
            // Copy proot from assets
            try {
                context.assets.open("proot-$ARCH").use { input ->
                    FileOutputStream(prootFile).use { output ->
                        input.copyTo(output)
                    }
                }
                prootFile.setExecutable(true, false)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract proot", e)
                throw IOException("PRoot binary not found in assets. Please add proot-$ARCH to assets folder.")
            }
        }
    }
    
    /**
     * Get the PRoot command to launch Ubuntu
     */
    fun getLaunchCommand(): Array<String> {
        val prootPath = File(context.filesDir, "proot").absolutePath
        
        return arrayOf(
            prootPath,
            "--rootfs=${rootfsPath.absolutePath}",
            "--bind=/dev",
            "--bind=/proc",
            "--bind=/sys",
            "--bind=/data",
            "--bind=/storage",
            "--cwd=/root",
            "--link2symlink",
            "-0", // Fake root user
            "/start.sh"
        )
    }
    
    /**
     * Get environment variables for Ubuntu session
     */
    fun getEnvironment(): Map<String, String> {
        return mapOf(
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "HOME" to "/root",
            "TERM" to "xterm-256color",
            "TMPDIR" to "/tmp",
            "LANG" to "C.UTF-8",
            "LC_ALL" to "C.UTF-8"
        )
    }
}
```

## Step 3: Terminal View Component

Create `TerminalView.kt` for displaying terminal output:

```kotlin
package com.yourpackage.ubuntuterminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import java.io.*
import java.util.concurrent.LinkedBlockingQueue

class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    companion object {
        private const val MAX_LINES = 5000
        private const val CHAR_WIDTH = 22f
        private const val LINE_HEIGHT = 45f
    }
    
    private val textPaint = TextPaint().apply {
        color = Color.parseColor("#00FF00") // Terminal green
        typeface = Typeface.MONOSPACE
        textSize = 36f
        isAntiAlias = true
    }
    
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#000000") // Black background
        style = Paint.Style.FILL
    }
    
    private val cursorPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        style = Paint.Style.FILL
    }
    
    private val lines = mutableListOf<String>()
    private var currentLine = StringBuilder()
    private val inputQueue = LinkedBlockingQueue<String>()
    
    private var process: Process? = null
    private var outputReader: Thread? = null
    private var inputWriter: BufferedWriter? = null
    
    private var scrollY = 0f
    private val maxScrollY: Float
        get() = maxOf(0f, (lines.size * LINE_HEIGHT) - height)
    
    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.BLACK)
    }
    
    /**
     * Start a terminal session
     */
    fun startSession(command: Array<String>, environment: Map<String, String>) {
        try {
            val processBuilder = ProcessBuilder(*command)
                .redirectErrorStream(true)
            
            // Set environment variables
            processBuilder.environment().apply {
                clear()
                putAll(environment)
            }
            
            process = processBuilder.start()
            
            inputWriter = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            
            // Start output reader thread
            outputReader = Thread {
                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                
                try {
                    val buffer = CharArray(1024)
                    var count: Int
                    
                    while (reader.read(buffer).also { count = it } != -1) {
                        val text = String(buffer, 0, count)
                        post {
                            appendOutput(text)
                        }
                    }
                } catch (e: IOException) {
                    post {
                        if (process?.isAlive == true) {
                            appendOutput("\n[Session terminated unexpectedly]\n")
                        } else {
                            appendOutput("\n[Session ended]\n")
                        }
                    }
                }
            }.apply { start() }
            
            // Start input writer thread
            Thread {
                try {
                    while (process?.isAlive == true) {
                        val input = inputQueue.take()
                        inputWriter?.write(input)
                        inputWriter?.flush()
                    }
                } catch (e: InterruptedException) {
                    // Thread interrupted, exit gracefully
                } catch (e: IOException) {
                    post {
                        appendOutput("\n[Failed to write input]\n")
                    }
                }
            }.start()
            
        } catch (e: Exception) {
            appendOutput("Failed to start session: ${e.message}\n")
        }
    }
    
    /**
     * Send input to the terminal
     */
    fun sendInput(text: String) {
        inputQueue.offer(text)
    }
    
    /**
     * Append text to terminal output
     */
    private fun appendOutput(text: String) {
        for (char in text) {
            when (char) {
                '\n' -> {
                    lines.add(currentLine.toString())
                    currentLine.clear()
                    
                    // Limit number of lines
                    while (lines.size > MAX_LINES) {
                        lines.removeAt(0)
                    }
                }
                '\r' -> {
                    // Carriage return - move to start of line
                    currentLine.clear()
                }
                '\b' -> {
                    // Backspace
                    if (currentLine.isNotEmpty()) {
                        currentLine.deleteCharAt(currentLine.length - 1)
                    }
                }
                else -> {
                    if (char.isISOControl()) {
                        // Ignore other control characters
                        continue
                    }
                    currentLine.append(char)
                }
            }
        }
        
        // Auto-scroll to bottom
        scrollY = maxScrollY
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        
        // Calculate visible range
        val firstVisibleLine = (scrollY / LINE_HEIGHT).toInt()
        val lastVisibleLine = ((scrollY + height) / LINE_HEIGHT).toInt() + 1
        
        // Draw lines
        var y = LINE_HEIGHT - (scrollY % LINE_HEIGHT)
        
        for (i in firstVisibleLine until minOf(lastVisibleLine, lines.size)) {
            canvas.drawText(lines[i], 20f, y, textPaint)
            y += LINE_HEIGHT
        }
        
        // Draw current line
        if (lines.size >= firstVisibleLine && lines.size <= lastVisibleLine) {
            canvas.drawText(currentLine.toString(), 20f, y, textPaint)
            
            // Draw cursor
            val cursorX = 20f + (currentLine.length * CHAR_WIDTH)
            canvas.drawRect(
                cursorX, y - LINE_HEIGHT + 10,
                cursorX + CHAR_WIDTH, y - 5,
                cursorPaint
            )
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        event?.let {
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    sendInput("\n")
                    return true
                }
                KeyEvent.KEYCODE_DEL -> {
                    sendInput("\b")
                    return true
                }
                KeyEvent.KEYCODE_TAB -> {
                    sendInput("\t")
                    return true
                }
                else -> {
                    val char = it.unicodeChar
                    if (char != 0) {
                        sendInput(char.toChar().toString())
                        return true
                    }
                }
            }
        }
        
        return super.onKeyDown(keyCode, event)
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            outputReader?.interrupt()
            inputWriter?.close()
            process?.destroy()
            process?.waitFor()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}
```

## Step 4: Main Activity

Create `MainActivity.kt`:

```kotlin
package com.yourpackage.ubuntuterminal

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var terminalView: TerminalView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var progressLayout: LinearLayout
    private lateinit var inputField: EditText
    private lateinit var sendButton: ImageButton
    
    private lateinit var bootstrap: UbuntuBootstrap
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize views
        terminalView = findViewById(R.id.terminalView)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        progressLayout = findViewById(R.id.progressLayout)
        inputField = findViewById(R.id.inputField)
        sendButton = findViewById(R.id.sendButton)
        
        bootstrap = UbuntuBootstrap(this)
        
        // Setup input handling
        setupInputHandling()
        
        // Check installation status
        lifecycleScope.launch {
            if (bootstrap.isInstalled()) {
                showTerminal()
            } else {
                showInstallDialog()
            }
        }
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
    
    private fun sendCommand() {
        val text = inputField.text.toString()
        if (text.isNotEmpty()) {
            terminalView.sendInput(text + "\n")
            inputField.text.clear()
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
                    statusText.text = message
                    progressBar.progress = progress
                }
            }
            
            result.onSuccess {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "Ubuntu installed successfully!",
                        Toast.LENGTH_SHORT
                    ).show()
                    showTerminal()
                }
            }.onFailure { error ->
                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity)
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
    
    private fun showTerminal() {
        progressLayout.visibility = View.GONE
        terminalView.visibility = View.VISIBLE
        inputField.visibility = View.VISIBLE
        sendButton.visibility = View.VISIBLE
        
        // Start Ubuntu session
        val command = bootstrap.getLaunchCommand()
        val environment = bootstrap.getEnvironment()
        
        terminalView.startSession(command, environment)
        terminalView.requestFocus()
        
        Toast.makeText(
            this,
            "Ubuntu terminal started!",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        terminalView.cleanup()
    }
    
    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setTitle("Exit Terminal")
            .setMessage("Are you sure you want to exit? Any running processes will be terminated.")
            .setPositiveButton("Exit") { _, _ ->
                super.onBackPressed()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
```

## Step 5: Layout Design

Create `res/layout/activity_main.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#000000">

    <!-- Terminal Container -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">

        <!-- Terminal View -->
        <com.yourpackage.ubuntuterminal.TerminalView
            android:id="@+id/terminalView"
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_weight="1"
            android:visibility="gone" />

        <!-- Input Area -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:padding="8dp"
            android:background="#1A1A1A"
            android:visibility="gone"
            android:id="@+id/inputLayout">

            <EditText
                android:id="@+id/inputField"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:hint="Enter command..."
                android:imeOptions="actionDone"
                android:inputType="text"
                android:textColor="#00FF00"
                android:textColorHint="#006600"
                android:background="@android:color/transparent"
                android:padding="12dp"
                android:fontFamily="monospace" />

            <ImageButton
                android:id="@+id/sendButton"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:src="@android:drawable/ic_menu_send"
                android:background="?attr/selectableItemBackgroundBorderless"
                android:tint="#00FF00"
                android:contentDescription="Send command" />
        </LinearLayout>
    </LinearLayout>

    <!-- Installation Progress -->
    <LinearLayout
        android:id="@+id/progressLayout"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:gravity="center"
        android:padding="32dp"
        android:background="#000000">

        <ImageView
            android:layout_width="120dp"
            android:layout_height="120dp"
            android:src="@android:drawable/ic_dialog_info"
            android:tint="#00FF00"
            android:layout_marginBottom="32dp" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Installing Ubuntu"
            android:textSize="24sp"
            android:textColor="#00FF00"
            android:textStyle="bold"
            android:layout_marginBottom="16dp" />

        <ProgressBar
            android:id="@+id/progressBar"
            android:layout_width="300dp"
            android:layout_height="wrap_content"
            style="@style/Widget.AppCompat.ProgressBar.Horizontal"
            android:max="100"
            android:progress="0"
            android:progressTint="#00FF00"
            android:layout_marginBottom="16dp" />

        <TextView
            android:id="@+id/statusText"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Initializing..."
            android:textColor="#00FF00"
            android:textSize="14sp"
            android:gravity="center"
            android:fontFamily="monospace" />
    </LinearLayout>

</FrameLayout>
```

## Step 6: Adding PRoot Binary

This is crucial! Download PRoot binaries for different architectures:

### Option 1: Download from Official Source

Visit [PRoot GitHub Releases](https://github.com/proot-me/proot/releases) and download:
- `proot-arm64` for ARM64 devices
- `proot-arm` for ARM devices
- `proot-x86_64` for x86_64 devices
- `proot-i686` for x86 devices

Place these files in `app/src/main/assets/` directory.

### Option 2: Use Termux PRoot

Alternatively, extract PRoot from Termux APK:
1. Download Termux APK
2. Extract the APK (it's just a zip file)
3. Find PRoot binaries in `lib/` folders
4. Rename and copy to your assets folder

## Step 7: Testing the App

### Build and Run

1. Connect your Android device or start an emulator
2. Click "Run" in Android Studio
3. The app will:
   - Check if Ubuntu is installed
   - Show installation dialog if not installed
   - Download Ubuntu rootfs (~100MB)
   - Extract and configure the environment
   - Launch the terminal

### First Commands to Try

After installation completes, try these commands:

```bash
# Check Ubuntu version
cat /etc/os-release

# Update package lists
apt update

# Install some useful packages
apt install -y nano vim git python3 nodejs

# Check Python
python3 --version

# Create a test file
echo "Hello from Ubuntu on Android!" > test.txt
cat test.txt

# List files
ls -la

# Check system info
uname -a
```

## Advanced Features

### Adding File Browser Integration

You can extend the app to browse and edit files stored in the Ubuntu filesystem:

```kotlin
// Add this to MainActivity
private fun openFileBrowser() {
    val intent = Intent(this, FileBrowserActivity::class.java)
    intent.putExtra("rootPath", bootstrap.rootfsPath.absolutePath)
    startActivity(intent)
}
```

### Adding Package Manager UI

Create a simple UI for managing packages:

```kotlin
class PackageManagerActivity : AppCompatActivity() {
    private fun installPackage(packageName: String) {
        terminalView.sendInput("apt install -y $packageName\n")
    }
    
    private fun searchPackages(query: String) {
        terminalView.sendInput("apt search $query\n")
    }
}
```

### Adding Shortcuts

Create common command shortcuts:

```kotlin
private fun setupShortcuts() {
    findViewById<Button>(R.id.btnUpdate).setOnClickListener {
        terminalView.sendInput("apt update && apt upgrade -y\n")
    }
    
    findViewById<Button>(R.id.btnClear).setOnClickListener {
        terminalView.sendInput("clear\n")
    }
}
```

## Troubleshooting Common Issues

### Issue 1: "tar: command not found"

**Solution**: Install busybox or include a static tar binary in your assets.

### Issue 2: PRoot fails to start

**Solution**: 
- Verify PRoot binary has executable permissions
- Check architecture matches device (arm64 vs arm)
- Try different PRoot versions

### Issue 3: DNS not working

**Solution**: Update `/etc/resolv.conf`:
```bash
echo "nameserver 8.8.8.8" > /etc/resolv.conf
```

### Issue 4: Package installation fails

**Solution**:
```bash
apt update
apt --fix-broken install
apt upgrade
```

### Issue 5: Storage space issues

**Solution**: 
- Clear cache: `apt clean`
- Remove unnecessary packages: `apt autoremove`
- Use SD card if available

## Performance Optimization

### 1. Reduce Download Size

Use Ubuntu minimal base instead:
```kotlin
private const val ROOTFS_URL = 
    "https://cdimage.ubuntu.com/ubuntu-base/releases/$UBUNTU_VERSION/release/ubuntu-base-$UBUNTU_VERSION-minimal-$ARCH.tar.gz"
```

### 2. Cache Downloaded Files

```kotlin
private fun shouldRedownload(): Boolean {
    if (!tarballPath.exists()) return true
    
    // Check if file is complete (size matches expected)
    val expectedSize = getExpectedFileSize()
    return tarballPath.length() != expectedSize
}
```

### 3. Optimize Terminal Rendering

```kotlin
// Use dirty regions for redrawing
private var dirtyRegion = Rect()

override fun onDraw(canvas: Canvas) {
    if (dirtyRegion.isEmpty) return
    
    // Only redraw changed area
    canvas.clipRect(dirtyRegion)
    // ... draw code ...
    dirtyRegion.setEmpty()
}
```

## Security Considerations

### 1. Validate Downloads

```kotlin
private suspend fun verifyChecksum(file: File, expectedChecksum: String): Boolean {
    val md = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { fis ->
        val buffer = ByteArray(8192)
        var bytesRead: Int
        while (fis.read(buffer).also { bytesRead = it } != -1) {
            md.update(buffer, 0, bytesRead)
        }
    }
    
    val checksum = md.digest().joinToString("") { "%02x".format(it) }
    return checksum == expectedChecksum
}
```

### 2. Sandbox Environment

PRoot already provides isolation, but you can add additional restrictions:
- Limit network access
- Restrict file system access
- Disable certain system calls

### 3. User Permissions

Request only necessary permissions:
```xml
<!-- Only request storage for Android 10 and below -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="29" />
```

## Future Enhancements

### 1. Multi-Session Support

Allow running multiple terminal sessions:
```kotlin
class SessionManager {
    private val sessions = mutableListOf<TerminalSession>()
    
    fun createSession(): TerminalSession {
        val session = TerminalSession(bootstrap)
        sessions.add(session)
        return session
    }
}
```

### 2. Split Screen

Implement split-screen terminal views for multitasking.

### 3. Custom Themes

Add theme support:
```kotlin
data class TerminalTheme(
    val backgroundColor: Int,
    val textColor: Int,
    val cursorColor: Int,
    val selectionColor: Int
)
```

### 4. SSH Support

Add SSH client functionality to connect to remote servers.

### 5. Script Automation

Allow users to create and run automated scripts:
```kotlin
class ScriptManager {
    fun saveScript(name: String, commands: String) {
        val scriptFile = File(bootstrap.rootfsPath, "scripts/$name.sh")
        scriptFile.writeText(commands)
        scriptFile.setExecutable(true)
    }
}
```

## Conclusion

You've now built a fully functional Ubuntu terminal emulator for Android! This app provides:

✅ Complete Ubuntu 22.04 environment  
✅ No root access required (thanks to PRoot)  
✅ Full package management with APT  
✅ Professional terminal interface  
✅ Storage in internal storage for persistence  
✅ Progress tracking during installation  

### Key Takeaways

1. **PRoot is powerful**: It allows running Linux distributions without root access
2. **Coroutines are essential**: For handling long-running operations smoothly
3. **Custom views provide control**: Building your own terminal view gives flexibility
4. **Error handling matters**: Always handle edge cases in file operations

### Next Steps

- Add more features from the enhancement list
- Optimize performance for low-end devices
- Create a settings UI for customization
- Publish to Google Play Store
- Add support for other Linux distributions

### Resources

- [PRoot Documentation](https://proot-me.github.io/)
- [Ubuntu Base Images](https://cloud-images.ubuntu.com/)
- [Android Process Management](https://developer.android.com/reference/java/lang/Process)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)

### Full Source Code

The complete source code for this project is available on GitHub:
[github.com/yourname/ubuntu-terminal-android](https://github.com)

## Support & Contributions

Found a bug? Have a feature request? Please open an issue on GitHub!

Pull requests are welcome. For major changes, please open an issue first to discuss what you would like to change.

---

**Happy coding! Enjoy your Ubuntu terminal on Android! 🚀**