package com.rk.terminal

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
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
     * Extract the tarball using Apache Commons Compress
     */
    private suspend fun extractRootfs(
        progressCallback: (String, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        progressCallback("Extracting files (this may take a while)...", 50)
        
        try {
            FileInputStream(tarballPath).use { fis ->
                BufferedInputStream(fis).use { bis ->
                    GzipCompressorInputStream(bis).use { gzis ->
                        TarArchiveInputStream(gzis).use { tais ->
                            var entry = tais.nextTarEntry
                            var filesExtracted = 0
                            
                            while (entry != null) {
                                val outputFile = File(rootfsPath, entry.name)
                                
                                if (entry.isDirectory) {
                                    outputFile.mkdirs()
                                } else {
                                    // Ensure parent directory exists
                                    outputFile.parentFile?.mkdirs()
                                    
                                    // Extract file
                                    FileOutputStream(outputFile).use { fos ->
                                        val buffer = ByteArray(8192)
                                        var len: Int
                                        while (tais.read(buffer).also { len = it } != -1) {
                                            fos.write(buffer, 0, len)
                                        }
                                    }
                                    
                                    // Set file permissions (if available)
                                    if (entry.mode and 0x100 != 0) { // Check if executable
                                        outputFile.setExecutable(true, false)
                                    }
                                    outputFile.setReadable(true, false)
                                    if (entry.mode and 0x80 != 0) { // Check if writable
                                        outputFile.setWritable(true, false)
                                    }
                                }
                                
                                // Update progress periodically
                                filesExtracted++
                                if (filesExtracted % 100 == 0) {
                                    val progress = 50 + (filesExtracted / 100).coerceAtMost(30)
                                    progressCallback("Extracting... ($filesExtracted files)", progress)
                                }
                                
                                entry = tais.nextTarEntry
                            }
                            
                            progressCallback("Extraction complete", 80)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed", e)
            throw IOException("Failed to extract Ubuntu rootfs: ${e.message}", e)
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
                echo "Welcome to Ubuntu ${'$'}VERSION on Android!"
                echo "Type 'apt update' to refresh package lists"
                echo ""
            fi
        """.trimIndent())
        
        // Create startup script
        val startScript = File(rootfsPath, "start.sh")
        startScript.writeText("""
            #!/bin/bash
            # Ensure we're in the root home directory, fallback to / if it doesn't exist
            cd /root 2>/dev/null || cd /
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
    fun getEnvironment(): Array<String> {
        return arrayOf(
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "HOME=/root",
            "TERM=xterm-256color",
            "TMPDIR=/tmp",
            "LANG=C.UTF-8",
            "LC_ALL=C.UTF-8"
        )
    }
}
