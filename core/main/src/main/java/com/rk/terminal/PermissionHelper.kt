package com.rk.terminal

import android.util.Log
import com.rk.file.child
import com.rk.file.getActiveSandboxDir
import com.rk.file.getActiveHomeDir
import com.rk.file.localBinDir
import com.rk.file.localDir
import java.io.File

/**
 * PermissionHelper - Handles all permission-related setup for proot and Linux environment
 * 
 * This utility ensures that all directories and files have proper permissions
 * to avoid "Operation not permitted" and "Permission denied" errors.
 */
object PermissionHelper {
    private const val TAG = "PermissionHelper"
    
    /**
     * Set up all necessary permissions for proot and Linux environment
     */
    fun setupPermissions(): Boolean {
        return try {
            Log.i(TAG, "Setting up permissions...")
            
            // Fix sandbox directory permissions
            fixSandboxPermissions()
            
            // Fix home directory permissions
            fixHomePermissions()
            
            // Fix temp directory permissions
            fixTempPermissions()
            
            // Fix binary permissions
            fixBinaryPermissions()
            
            // Fix library permissions
            fixLibraryPermissions()
            
            Log.i(TAG, "Permission setup completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup permissions", e)
            false
        }
    }
    
    /**
     * Fix sandbox directory permissions
     * Ensures the rootfs has proper access rights
     */
    private fun fixSandboxPermissions() {
        val sandboxDir = getActiveSandboxDir()
        if (!sandboxDir.exists()) {
            Log.w(TAG, "Sandbox directory does not exist: ${sandboxDir.absolutePath}")
            return
        }
        
        Log.d(TAG, "Fixing sandbox permissions: ${sandboxDir.absolutePath}")
        
        // Make sandbox directory readable and executable
        sandboxDir.setReadable(true, false)
        sandboxDir.setExecutable(true, false)
        sandboxDir.setWritable(true, false)
        
        // Fix critical system directories in rootfs
        listOf("bin", "sbin", "usr/bin", "usr/sbin", "lib", "lib64", "usr/lib", "usr/lib64", "etc", "var", "tmp")
            .forEach { dir ->
                val dirFile = File(sandboxDir, dir)
                if (dirFile.exists()) {
                    dirFile.setReadable(true, false)
                    dirFile.setExecutable(true, false)
                    dirFile.setWritable(true, false)
                }
            }
        
        // Fix /tmp inside sandbox
        val tmpDir = File(sandboxDir, "tmp")
        if (!tmpDir.exists()) {
            tmpDir.mkdirs()
        }
        tmpDir.setReadable(true, false)
        tmpDir.setWritable(true, false)
        tmpDir.setExecutable(true, false)
        
        Log.d(TAG, "Sandbox permissions fixed")
    }
    
    /**
     * Fix home directory permissions
     */
    private fun fixHomePermissions() {
        val homeDir = getActiveHomeDir()
        if (!homeDir.exists()) {
            homeDir.mkdirs()
        }
        
        Log.d(TAG, "Fixing home permissions: ${homeDir.absolutePath}")
        
        homeDir.setReadable(true, false)
        homeDir.setWritable(true, false)
        homeDir.setExecutable(true, false)
        
        Log.d(TAG, "Home permissions fixed")
    }
    
    /**
     * Fix temporary directory permissions
     * This is critical for proot operation
     */
    private fun fixTempPermissions() {
        val localTmpDir = localDir().child("tmp")
        if (!localTmpDir.exists()) {
            localTmpDir.mkdirs()
        }
        
        Log.d(TAG, "Fixing temp permissions: ${localTmpDir.absolutePath}")
        
        // Temp directories need 777 permissions (rwxrwxrwx)
        localTmpDir.setReadable(true, false)
        localTmpDir.setWritable(true, false)
        localTmpDir.setExecutable(true, false)
        
        // Also fix sandbox tmp
        val sandboxTmpDir = getActiveSandboxDir().child("tmp")
        if (!sandboxTmpDir.exists()) {
            sandboxTmpDir.mkdirs()
        }
        sandboxTmpDir.setReadable(true, false)
        sandboxTmpDir.setWritable(true, false)
        sandboxTmpDir.setExecutable(true, false)
        
        Log.d(TAG, "Temp permissions fixed")
    }
    
    /**
     * Fix binary permissions
     * Ensures all binaries in local/bin are executable
     */
    private fun fixBinaryPermissions() {
        val binDir = localBinDir()
        if (!binDir.exists()) {
            Log.w(TAG, "Binary directory does not exist: ${binDir.absolutePath}")
            return
        }
        
        Log.d(TAG, "Fixing binary permissions: ${binDir.absolutePath}")
        
        binDir.setReadable(true, false)
        binDir.setExecutable(true, false)
        
        // Make all files in bin directory executable
        binDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.setReadable(true, false)
                file.setExecutable(true, false)
                Log.d(TAG, "Made executable: ${file.name}")
            }
        }
        
        Log.d(TAG, "Binary permissions fixed")
    }
    
    /**
     * Fix library permissions
     * Ensures shared libraries are readable
     */
    private fun fixLibraryPermissions() {
        val libDir = localDir().child("lib")
        if (!libDir.exists()) {
            Log.w(TAG, "Library directory does not exist: ${libDir.absolutePath}")
            return
        }
        
        Log.d(TAG, "Fixing library permissions: ${libDir.absolutePath}")
        
        libDir.setReadable(true, false)
        libDir.setExecutable(true, false)
        
        // Make all .so files readable and executable
        libDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".so") || file.name.contains(".so.")) {
                file.setReadable(true, false)
                file.setExecutable(true, false)
            }
        }
        
        Log.d(TAG, "Library permissions fixed")
    }
    
    /**
     * Fix permissions for a specific file
     */
    fun fixFilePermissions(file: File, makeExecutable: Boolean = false) {
        if (!file.exists()) {
            Log.w(TAG, "File does not exist: ${file.absolutePath}")
            return
        }
        
        file.setReadable(true, false)
        file.setWritable(true, false)
        
        if (makeExecutable || file.name.endsWith(".sh") || file.parent?.endsWith("bin") == true) {
            file.setExecutable(true, false)
        }
        
        Log.d(TAG, "Fixed permissions for: ${file.absolutePath}")
    }
    
    /**
     * Fix permissions recursively for a directory
     * Use with caution - can be slow for large directories
     */
    fun fixDirectoryPermissionsRecursive(dir: File, maxDepth: Int = 3, currentDepth: Int = 0) {
        if (!dir.exists() || !dir.isDirectory || currentDepth > maxDepth) {
            return
        }
        
        dir.setReadable(true, false)
        dir.setWritable(true, false)
        dir.setExecutable(true, false)
        
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                fixDirectoryPermissionsRecursive(file, maxDepth, currentDepth + 1)
            } else {
                fixFilePermissions(file)
            }
        }
    }
    
    /**
     * Check if we have necessary permissions
     */
    fun checkPermissions(): PermissionStatus {
        val status = PermissionStatus()
        
        // Check sandbox
        val sandboxDir = getActiveSandboxDir()
        status.sandboxReadable = sandboxDir.canRead()
        status.sandboxWritable = sandboxDir.canWrite()
        status.sandboxExecutable = sandboxDir.canExecute()
        
        // Check home
        val homeDir = getActiveHomeDir()
        status.homeReadable = homeDir.canRead()
        status.homeWritable = homeDir.canWrite()
        status.homeExecutable = homeDir.canExecute()
        
        // Check binaries
        val binDir = localBinDir()
        status.binReadable = binDir.canRead()
        status.binExecutable = binDir.canExecute()
        
        Log.i(TAG, "Permission check: $status")
        return status
    }
}

/**
 * Data class to hold permission status
 */
data class PermissionStatus(
    var sandboxReadable: Boolean = false,
    var sandboxWritable: Boolean = false,
    var sandboxExecutable: Boolean = false,
    var homeReadable: Boolean = false,
    var homeWritable: Boolean = false,
    var homeExecutable: Boolean = false,
    var binReadable: Boolean = false,
    var binExecutable: Boolean = false
) {
    fun isAllGood(): Boolean {
        return sandboxReadable && sandboxExecutable &&
                homeReadable && homeWritable && homeExecutable &&
                binReadable && binExecutable
    }
}
