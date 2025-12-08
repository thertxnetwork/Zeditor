package com.rk.terminal

import android.util.Log
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SELinuxHelper - Helps handle SELinux contexts for better proot compatibility
 * 
 * Android's SELinux can sometimes block proot operations. This helper attempts
 * to work around common SELinux issues.
 */
object SELinuxHelper {
    private const val TAG = "SELinuxHelper"
    
    /**
     * Check if SELinux is enforcing
     */
    suspend fun isSELinuxEnforcing(): Boolean = withContext(Dispatchers.IO) {
        try {
            val getenforceFile = File("/sys/fs/selinux/enforce")
            if (getenforceFile.exists()) {
                val content = getenforceFile.readText().trim()
                val isEnforcing = content == "1"
                Log.i(TAG, "SELinux enforcing: $isEnforcing")
                return@withContext isEnforcing
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check SELinux status", e)
        }
        return@withContext false
    }
    
    /**
     * Get SELinux mode (enforcing, permissive, or disabled)
     */
    suspend fun getSELinuxMode(): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("getenforce")
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()
            Log.i(TAG, "SELinux mode: $output")
            return@withContext output
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get SELinux mode", e)
            return@withContext "Unknown"
        }
    }
    
    /**
     * Try to set SELinux context for a file
     * This usually requires root, but we try anyway
     */
    suspend fun trySetSELinuxContext(file: File, context: String = "u:object_r:app_data_file:s0"): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("chcon", context, file.absolutePath))
                val exitCode = process.waitFor()
                val success = exitCode == 0
                if (success) {
                    Log.d(TAG, "Set SELinux context for ${file.absolutePath}: $context")
                } else {
                    Log.d(TAG, "Failed to set SELinux context (exit code: $exitCode)")
                }
                return@withContext success
            } catch (e: Exception) {
                Log.d(TAG, "Cannot set SELinux context (expected without root): ${e.message}")
                return@withContext false
            }
        }
    
    /**
     * Get SELinux context of a file
     */
    suspend fun getSELinuxContext(file: File): String? = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("ls", "-Z", file.absolutePath))
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()
            
            // Parse output: context filename
            val parts = output.split("\\s+".toRegex())
            if (parts.isNotEmpty()) {
                Log.d(TAG, "SELinux context for ${file.absolutePath}: ${parts[0]}")
                return@withContext parts[0]
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to get SELinux context: ${e.message}")
        }
        return@withContext null
    }
    
    /**
     * Log SELinux information for debugging
     */
    suspend fun logSELinuxInfo() = withContext(Dispatchers.IO) {
        val mode = getSELinuxMode()
        val enforcing = isSELinuxEnforcing()
        
        Log.i(TAG, "=== SELinux Information ===")
        Log.i(TAG, "Mode: $mode")
        Log.i(TAG, "Enforcing: $enforcing")
        
        // Try to get policy version
        try {
            val policyVersion = File("/sys/fs/selinux/policyvers").readText().trim()
            Log.i(TAG, "Policy version: $policyVersion")
        } catch (e: Exception) {
            Log.d(TAG, "Cannot read policy version")
        }
        
        Log.i(TAG, "=========================")
    }
}
