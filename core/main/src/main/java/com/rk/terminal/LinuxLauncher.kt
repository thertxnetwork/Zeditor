package com.rk.terminal

import android.util.Log
import java.io.File

/**
 * LinuxLauncher - Native library to execute Linux binaries directly without Android shell.
 * 
 * This class provides a way to launch Linux binaries (like bash) from the Ubuntu rootfs
 * without going through Android's /system/bin/sh. It uses JNI to call exec() family functions
 * directly, bypassing all Android shell dependencies.
 */
object LinuxLauncher {
    private const val TAG = "LinuxLauncher"
    
    init {
        try {
            System.loadLibrary("linuxlauncher")
            Log.i(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }
    
    /**
     * Execute a Linux binary directly using execve()
     * 
     * @param binary Absolute path to the Linux binary
     * @param args Array of arguments (argv), including argv[0]
     * @param env Array of environment variables in KEY=VALUE format
     * @return Process ID of the child process, or -1 on error
     */
    external fun nativeExec(binary: String, args: Array<String>, env: Array<String>): Int
    
    /**
     * Wait for a process to complete
     * 
     * @param pid Process ID to wait for
     * @return Exit code of the process
     */
    external fun nativeWaitFor(pid: Int): Int
    
    /**
     * Kill a process
     * 
     * @param pid Process ID to kill
     * @param signal Signal to send (default SIGTERM = 15)
     * @return true if successful
     */
    external fun nativeKill(pid: Int, signal: Int = 15): Boolean
    
    /**
     * Execute a Linux binary using the dynamic linker directly
     * This is the preferred method as it properly handles library dependencies
     * 
     * @param linker Path to ld-linux dynamic linker
     * @param libraryPath Colon-separated list of library search paths
     * @param binary Path to the binary to execute
     * @param args Arguments for the binary
     * @param env Environment variables
     * @return Process ID of the child process, or -1 on error
     */
    external fun nativeExecWithLinker(
        linker: String,
        libraryPath: String,
        binary: String,
        args: Array<String>,
        env: Array<String>
    ): Int
    
    /**
     * High-level function to launch bash from a Linux rootfs
     * 
     * @param rootfsPath Path to the Linux rootfs directory
     * @param workingDir Working directory inside the rootfs
     * @param environment Map of environment variables
     * @param command Optional command to execute
     * @return Process ID or -1 on error
     */
    fun launchBash(
        rootfsPath: File,
        workingDir: String = "/home",
        environment: Map<String, String> = emptyMap(),
        command: String? = null
    ): Int {
        // Find bash binary
        val bashBinary = File(rootfsPath, "bin/bash")
        if (!bashBinary.exists()) {
            Log.e(TAG, "Bash not found: ${bashBinary.absolutePath}")
            return -1
        }
        
        // Find dynamic linker
        val linker = findDynamicLinker(rootfsPath)
        if (linker == null) {
            Log.e(TAG, "Dynamic linker not found in rootfs")
            return -1
        }
        
        // Build library path
        val libraryPath = buildLibraryPath(rootfsPath)
        
        // Build environment
        val envList = buildEnvironment(environment, workingDir, libraryPath)
        
        // Build arguments
        val args = mutableListOf("-i")  // Interactive shell
        if (command != null) {
            args.add("-c")
            args.add(command)
        }
        
        Log.i(TAG, "Launching bash with linker: ${linker.absolutePath}")
        Log.i(TAG, "Binary: ${bashBinary.absolutePath}")
        Log.i(TAG, "Library path: $libraryPath")
        Log.i(TAG, "Args: $args")
        
        return nativeExecWithLinker(
            linker.absolutePath,
            libraryPath,
            bashBinary.absolutePath,
            args.toTypedArray(),
            envList
        )
    }
    
    /**
     * Find the dynamic linker in a rootfs
     */
    private fun findDynamicLinker(rootfsPath: File): File? {
        val possiblePaths = listOf(
            "lib64/ld-linux-x86-64.so.2",
            "lib/ld-linux-x86-64.so.2",
            "lib/ld-linux-aarch64.so.1",
            "lib64/ld-linux-aarch64.so.1",
            "lib/aarch64-linux-gnu/ld-linux-aarch64.so.1",
            "lib/x86_64-linux-gnu/ld-linux-x86-64.so.2",
            "lib/ld-linux-armhf.so.3",
            "lib/arm-linux-gnueabihf/ld-linux-armhf.so.3"
        )
        
        for (path in possiblePaths) {
            val linker = File(rootfsPath, path)
            if (linker.exists()) {
                linker.setExecutable(true, false)
                Log.i(TAG, "Found linker: ${linker.absolutePath}")
                return linker
            }
        }
        
        return null
    }
    
    /**
     * Build library search path from rootfs
     */
    private fun buildLibraryPath(rootfsPath: File): String {
        val libDirs = listOf(
            "lib",
            "lib64",
            "usr/lib",
            "usr/lib64",
            "lib/aarch64-linux-gnu",
            "lib/x86_64-linux-gnu",
            "lib/arm-linux-gnueabihf",
            "usr/lib/aarch64-linux-gnu",
            "usr/lib/x86_64-linux-gnu",
            "usr/lib/arm-linux-gnueabihf"
        )
        
        return libDirs
            .map { File(rootfsPath, it) }
            .filter { it.exists() && it.isDirectory }
            .joinToString(":") { it.absolutePath }
    }
    
    /**
     * Build environment variable array
     */
    private fun buildEnvironment(
        customEnv: Map<String, String>,
        workingDir: String,
        libraryPath: String
    ): Array<String> {
        val env = mutableMapOf(
            "PATH" to "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "HOME" to "/home",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "LANG" to "C.UTF-8",
            "LC_ALL" to "C.UTF-8",
            "PWD" to workingDir,
            "SHELL" to "/bin/bash",
            "USER" to "root",
            "LOGNAME" to "root",
            "LD_LIBRARY_PATH" to libraryPath,
            "TMPDIR" to "/tmp"
        )
        
        // Add custom environment variables
        env.putAll(customEnv)
        
        return env.map { "${it.key}=${it.value}" }.toTypedArray()
    }
}
