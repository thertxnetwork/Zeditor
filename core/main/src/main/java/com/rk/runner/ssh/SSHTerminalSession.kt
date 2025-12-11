package com.rk.runner.ssh

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.jcraft.jsch.ChannelShell
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSessionClient
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * A terminal session that bridges SSH I/O with the Termux TerminalEmulator.
 * 
 * This class provides SSH-based terminal emulation by:
 * 1. Reading data from SSH channel and feeding it to TerminalEmulator
 * 2. Writing user input from TerminalEmulator to SSH channel
 */
class SSHTerminalSession(
    private val sshChannel: ChannelShell,
    transcriptRows: Int?,
    private var client: TerminalSessionClient
) : TerminalOutput() {

    companion object {
        private const val MSG_NEW_INPUT = 1
        private const val MSG_SESSION_FINISHED = 2
        private const val DEFAULT_TRANSCRIPT_ROWS = 2000
    }

    val handle: String = UUID.randomUUID().toString()
    
    var emulator: TerminalEmulator? = null
        private set
    
    private var sshInputStream: InputStream? = null
    private var sshOutputStream: OutputStream? = null
    
    private val transcriptRowCount = transcriptRows ?: DEFAULT_TRANSCRIPT_ROWS
    
    @Volatile
    private var isRunning = false
    
    @Volatile
    private var exitStatus = 0
    
    private val mainThreadHandler = MainThreadHandler()
    
    // Buffer for writing UTF-8 encoded data
    private val utf8InputBuffer = ByteArray(5)
    
    // Queue for process -> terminal data
    private val processToTerminalQueue = ByteQueue(4096)
    
    // Queue for terminal -> process data  
    private val terminalToProcessQueue = ByteQueue(4096)

    /**
     * Initialize the terminal emulator and start I/O threads.
     */
    fun initializeEmulator(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        emulator = TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, transcriptRowCount, client)
        
        try {
            sshInputStream = sshChannel.inputStream
            sshOutputStream = sshChannel.outputStream
            
            // Set PTY size on SSH channel
            sshChannel.setPtySize(columns, rows, cellWidthPixels, cellHeightPixels)
            
            isRunning = true
            
            // Start SSH input reader thread (reads from SSH, writes to terminal)
            Thread("SSHTerminalInputReader") {
                readFromSSH()
            }.start()
            
            // Start SSH output writer thread (reads from terminal, writes to SSH)
            Thread("SSHTerminalOutputWriter") {
                writeToSSH()
            }.start()
            
            // Start session monitor thread
            Thread("SSHTerminalMonitor") {
                monitorSession()
            }.start()
            
        } catch (e: Exception) {
            val errorMsg = "\r\n[SSH connection error: ${e.message}]\r\n"
            emulator?.append(errorMsg.toByteArray(StandardCharsets.UTF_8), errorMsg.length)
            notifyScreenUpdate()
        }
    }
    
    /**
     * Update the terminal size.
     */
    fun updateSize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        if (emulator == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels)
        } else {
            // Update SSH PTY size
            try {
                sshChannel.setPtySize(columns, rows, cellWidthPixels, cellHeightPixels)
            } catch (e: Exception) {
                // Ignore PTY resize errors
            }
            emulator?.resize(columns, rows, cellWidthPixels, cellHeightPixels)
        }
    }
    
    /**
     * Update the terminal session client.
     */
    fun updateTerminalSessionClient(newClient: TerminalSessionClient) {
        client = newClient
        emulator?.updateTerminalSessionClient(newClient)
    }
    
    /**
     * Read data from SSH channel and queue it for the terminal emulator.
     */
    private fun readFromSSH() {
        val buffer = ByteArray(4096)
        try {
            while (isRunning && sshChannel.isConnected) {
                val inputStream = sshInputStream ?: break
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) {
                    break
                }
                if (bytesRead > 0) {
                    if (!processToTerminalQueue.write(buffer, 0, bytesRead)) {
                        break
                    }
                    mainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT)
                }
            }
        } catch (e: IOException) {
            // Connection closed or error
        } finally {
            if (isRunning) {
                isRunning = false
                mainThreadHandler.sendMessage(mainThreadHandler.obtainMessage(MSG_SESSION_FINISHED, 0))
            }
        }
    }
    
    /**
     * Write data from terminal to SSH channel.
     */
    private fun writeToSSH() {
        val buffer = ByteArray(4096)
        try {
            while (isRunning) {
                val bytesToWrite = terminalToProcessQueue.read(buffer, true)
                if (bytesToWrite == -1) {
                    break
                }
                sshOutputStream?.write(buffer, 0, bytesToWrite)
                sshOutputStream?.flush()
            }
        } catch (e: IOException) {
            // Connection closed or error
        }
    }
    
    /**
     * Monitor the SSH session for disconnection.
     */
    private fun monitorSession() {
        try {
            while (isRunning && sshChannel.isConnected) {
                Thread.sleep(500)
            }
        } catch (e: InterruptedException) {
            // Interrupted
        } finally {
            if (isRunning) {
                isRunning = false
                mainThreadHandler.sendMessage(mainThreadHandler.obtainMessage(MSG_SESSION_FINISHED, sshChannel.exitStatus))
            }
        }
    }
    
    /**
     * Write data to the SSH channel (called by TerminalEmulator).
     */
    override fun write(data: ByteArray, offset: Int, count: Int) {
        if (isRunning) {
            terminalToProcessQueue.write(data, offset, count)
        }
    }
    
    /**
     * Write a Unicode code point to the terminal encoded in UTF-8.
     */
    fun writeCodePoint(prependEscape: Boolean, codePoint: Int) {
        require(!(codePoint > 1114111 || codePoint in 0xD800..0xDFFF)) {
            "Invalid code point: $codePoint"
        }
        
        var bufferPosition = 0
        if (prependEscape) utf8InputBuffer[bufferPosition++] = 27
        
        when {
            codePoint <= 0x7F -> {
                utf8InputBuffer[bufferPosition++] = codePoint.toByte()
            }
            codePoint <= 0x7FF -> {
                utf8InputBuffer[bufferPosition++] = (0xC0 or (codePoint shr 6)).toByte()
                utf8InputBuffer[bufferPosition++] = (0x80 or (codePoint and 0x3F)).toByte()
            }
            codePoint <= 0xFFFF -> {
                utf8InputBuffer[bufferPosition++] = (0xE0 or (codePoint shr 12)).toByte()
                utf8InputBuffer[bufferPosition++] = (0x80 or ((codePoint shr 6) and 0x3F)).toByte()
                utf8InputBuffer[bufferPosition++] = (0x80 or (codePoint and 0x3F)).toByte()
            }
            else -> {
                utf8InputBuffer[bufferPosition++] = (0xF0 or (codePoint shr 18)).toByte()
                utf8InputBuffer[bufferPosition++] = (0x80 or ((codePoint shr 12) and 0x3F)).toByte()
                utf8InputBuffer[bufferPosition++] = (0x80 or ((codePoint shr 6) and 0x3F)).toByte()
                utf8InputBuffer[bufferPosition++] = (0x80 or (codePoint and 0x3F)).toByte()
            }
        }
        write(utf8InputBuffer, 0, bufferPosition)
    }
    
    override fun titleChanged(oldTitle: String?, newTitle: String?) {
        client.onTitleChanged(null) // We don't have a TerminalSession reference
    }
    
    override fun onCopyTextToClipboard(text: String?) {
        client.onCopyTextToClipboard(null, text)
    }
    
    override fun onPasteTextFromClipboard() {
        client.onPasteTextFromClipboard(null)
    }
    
    override fun onBell() {
        client.onBell(null)
    }
    
    override fun onColorsChanged() {
        client.onColorsChanged(null)
    }
    
    /**
     * Get the terminal title.
     */
    fun getTitle(): String? = emulator?.title
    
    /**
     * Reset the terminal emulator state.
     */
    fun reset() {
        emulator?.reset()
        notifyScreenUpdate()
    }
    
    /**
     * Finish the session by closing the SSH channel.
     */
    fun finishIfRunning() {
        if (isRunning) {
            isRunning = false
            try {
                sshChannel.disconnect()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    /**
     * Clean up resources.
     */
    fun cleanupResources(status: Int) {
        synchronized(this) {
            isRunning = false
            exitStatus = status
        }
        
        terminalToProcessQueue.close()
        processToTerminalQueue.close()
        
        try {
            sshInputStream?.close()
            sshOutputStream?.close()
            sshChannel.disconnect()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
    
    /**
     * Check if the session is still running.
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * Get the exit status (only valid if not running).
     */
    fun getExitStatus(): Int = exitStatus
    
    /**
     * Notify the client that the screen has changed.
     */
    private fun notifyScreenUpdate() {
        client.onTextChanged(null)
    }
    
    /**
     * Handler for processing messages on the main thread.
     */
    private inner class MainThreadHandler : Handler(Looper.getMainLooper()) {
        private val receiveBuffer = ByteArray(4 * 1024)
        
        override fun handleMessage(msg: Message) {
            val bytesRead = processToTerminalQueue.read(receiveBuffer, false)
            if (bytesRead > 0) {
                emulator?.append(receiveBuffer, bytesRead)
                notifyScreenUpdate()
            }
            
            if (msg.what == MSG_SESSION_FINISHED) {
                val status = msg.obj as? Int ?: 0
                cleanupResources(status)
                
                val exitDescription = "\r\n[SSH session ended - press Enter]\r\n"
                val bytesToWrite = exitDescription.toByteArray(StandardCharsets.UTF_8)
                emulator?.append(bytesToWrite, bytesToWrite.size)
                notifyScreenUpdate()
                
                client.onSessionFinished(null)
            }
        }
    }
}

/**
 * Simple byte queue for inter-thread communication.
 */
class ByteQueue(capacity: Int) {
    private val buffer = ByteArray(capacity)
    private var head = 0
    private var storedBytes = 0
    private var isClosed = false
    
    @Synchronized
    fun write(data: ByteArray, offset: Int, count: Int): Boolean {
        if (isClosed) return false
        
        var written = 0
        while (written < count && !isClosed) {
            while (storedBytes >= buffer.size && !isClosed) {
                try {
                    (this as Object).wait()
                } catch (e: InterruptedException) {
                    return false
                }
            }
            if (isClosed) return false
            
            val tail = (head + storedBytes) % buffer.size
            val toWrite = minOf(count - written, buffer.size - storedBytes, buffer.size - tail)
            System.arraycopy(data, offset + written, buffer, tail, toWrite)
            storedBytes += toWrite
            written += toWrite
            (this as Object).notifyAll()
        }
        return written == count
    }
    
    @Synchronized
    fun read(data: ByteArray, block: Boolean): Int {
        while (storedBytes == 0 && !isClosed) {
            if (!block) return 0
            try {
                (this as Object).wait()
            } catch (e: InterruptedException) {
                return -1
            }
        }
        if (storedBytes == 0 && isClosed) return -1
        
        val toRead = minOf(data.size, storedBytes, buffer.size - head)
        System.arraycopy(buffer, head, data, 0, toRead)
        head = (head + toRead) % buffer.size
        storedBytes -= toRead
        (this as Object).notifyAll()
        return toRead
    }
    
    @Synchronized
    fun close() {
        isClosed = true
        (this as Object).notifyAll()
    }
}
