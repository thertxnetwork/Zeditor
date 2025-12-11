package com.rk.runner.ssh

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.io.File

/**
 * SSH Connection manager for handling SSH sessions and command execution
 */
class SSHConnectionManager(private val config: SSHServerConfig) {
    
    private var session: Session? = null
    private val jsch = JSch()
    
    /**
     * Connection status
     */
    sealed class ConnectionStatus {
        data object Disconnected : ConnectionStatus()
        data object Connecting : ConnectionStatus()
        data object Connected : ConnectionStatus()
        data class Error(val message: String) : ConnectionStatus()
    }
    
    var status: ConnectionStatus = ConnectionStatus.Disconnected
        private set
    
    /**
     * Connect to the SSH server
     */
    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            status = ConnectionStatus.Connecting
            
            // Configure authentication
            when (config.authType) {
                SSHAuthType.PASSWORD -> {
                    // Password authentication
                }
                SSHAuthType.KEY -> {
                    // Key-based authentication using byte array (more secure)
                    config.privateKey?.let { key ->
                        jsch.addIdentity(
                            "key-${config.id}",
                            key.toByteArray(),
                            null,
                            null
                        )
                    }
                }
            }
            
            // Create session
            session = jsch.getSession(config.username, config.host, config.port).apply {
                if (config.authType == SSHAuthType.PASSWORD) {
                    setPassword(config.password)
                }
                
                // Disable strict host key checking
                // WARNING: This allows man-in-the-middle attacks. In production,
                // implement proper host key verification.
                setConfig("StrictHostKeyChecking", "no")
                
                // Set timeout
                timeout = 30000 // 30 seconds
                
                // Connect
                connect()
            }
            
            status = ConnectionStatus.Connected
            Result.success(Unit)
        } catch (e: Exception) {
            status = ConnectionStatus.Error(e.message ?: "Connection failed")
            disconnect()
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect from the SSH server
     */
    fun disconnect() {
        try {
            session?.disconnect()
        } catch (e: Exception) {
            // Ignore
        } finally {
            session = null
            status = ConnectionStatus.Disconnected
        }
    }
    
    /**
     * Execute a command on the remote server
     */
    suspend fun executeCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val currentSession = session ?: return@withContext Result.failure(
                IllegalStateException("Not connected")
            )
            
            val channel = currentSession.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            
            val outputStream = java.io.ByteArrayOutputStream()
            val errorStream = java.io.ByteArrayOutputStream()
            
            channel.outputStream = outputStream
            channel.setErrStream(errorStream)
            
            channel.connect()
            
            // Wait for command to complete
            while (!channel.isClosed) {
                kotlinx.coroutines.delay(100)
            }
            
            val exitStatus = channel.exitStatus
            channel.disconnect()
            
            val output = outputStream.toString("UTF-8")
            val error = errorStream.toString("UTF-8")
            
            val result = if (error.isNotEmpty()) {
                "$output\n$error"
            } else {
                output
            }
            
            if (exitStatus == 0) {
                Result.success(result)
            } else {
                Result.failure(Exception("Command failed with exit code $exitStatus\n$result"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Open an interactive shell channel
     */
    suspend fun openShell(): Result<ShellChannel> = withContext(Dispatchers.IO) {
        try {
            val currentSession = session ?: return@withContext Result.failure(
                IllegalStateException("Not connected")
            )
            
            val channel = currentSession.openChannel("shell") as ChannelShell
            channel.setPtyType("vt100")
            
            val inputStream = channel.inputStream
            val outputStream = channel.outputStream
            
            channel.connect()
            
            Result.success(ShellChannel(channel, inputStream, outputStream))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Upload a file to the remote server
     */
    suspend fun uploadFile(localPath: String, remotePath: String): Result<Unit> = 
        withContext(Dispatchers.IO) {
            try {
                val currentSession = session ?: return@withContext Result.failure(
                    IllegalStateException("Not connected")
                )
                
                val localFile = File(localPath)
                if (!localFile.exists()) {
                    return@withContext Result.failure(
                        IllegalStateException("Local file does not exist: $localPath")
                    )
                }
                
                // Use SCP to upload file
                val command = "scp -t $remotePath"
                val channel = currentSession.openChannel("exec") as ChannelExec
                channel.setCommand(command)
                
                val out = channel.outputStream
                val input = channel.inputStream
                
                channel.connect()
                
                // Wait for response
                checkAck(input)
                
                // Send file
                val fileSize = localFile.length()
                val cmd = "C0644 $fileSize ${localFile.name}\n"
                out.write(cmd.toByteArray())
                out.flush()
                checkAck(input)
                
                // Send file content
                localFile.inputStream().use { fis ->
                    val buffer = ByteArray(1024)
                    var len: Int
                    while (fis.read(buffer).also { len = it } > 0) {
                        out.write(buffer, 0, len)
                    }
                }
                
                // Send '\0'
                out.write(0)
                out.flush()
                checkAck(input)
                
                channel.disconnect()
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    private fun checkAck(input: InputStream): Int {
        val b = input.read()
        if (b == 0) return b
        if (b == -1) return b
        
        if (b == 1 || b == 2) {
            val sb = StringBuilder()
            var c: Int
            do {
                c = input.read()
                sb.append(c.toChar())
            } while (c != '\n'.code)
            throw Exception("SCP error: $sb")
        }
        return b
    }
    
    /**
     * Check if connected
     */
    fun isConnected(): Boolean {
        return session?.isConnected == true
    }
}

/**
 * Represents an interactive shell channel
 */
data class ShellChannel(
    val channel: ChannelShell,
    val inputStream: InputStream,
    val outputStream: OutputStream
) {
    fun isOpen(): Boolean = channel.isConnected
    
    fun close() {
        try {
            channel.disconnect()
        } catch (e: Exception) {
            // Ignore
        }
    }
}
