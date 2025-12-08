package com.rk.file

import android.content.Context
import android.os.Environment
import com.rk.utils.application
import java.io.File
import kotlinx.coroutines.runBlocking

fun getPrivateDir(context: Context = application!!): File {
    // blocking thread is fine since we are always know it is just a java.io.File and we are not doing heavy stuff
    return runBlocking { context.filesDir.createDirIfNot().parentFile.createDirIfNot() }
}

fun getCacheDir(context: Context = application!!): File {
    return context.cacheDir.createDirIfNot()
}

fun localDir(context: Context = application!!): File {
    return getPrivateDir(context).child("local").also { it.createDirIfNot() }
}

fun localBinDir(context: Context = application!!): File {
    return localDir(context).child("bin").also { it.createDirIfNot() }
}

fun localLibDir(context: Context = application!!): File {
    return localDir(context).child("lib").also { it.createDirIfNot() }
}

fun sandboxDir(context: Context = application!!): File {
    return localDir(context).child("sandbox").also { it.createDirIfNot() }
}

fun sandboxHomeDir(context: Context = application!!): File {
    return localDir(context).child("home").createDirIfNot()
}

fun runnerDir(context: Context = application!!): File {
    return localDir(context).child("runners").createDirIfNot()
}

fun themeDir(context: Context = application!!): File {
    return localDir(context).child("themes").createDirIfNot()
}

fun persistentTempDir(context: Context = application!!): File {
    return getCacheDir(context).child("tempFiles").createDirIfNot()
}

/**
 * Gets the Zeditor directory in external storage.
 * This is the main directory where Ubuntu installation will be stored.
 * Location: /sdcard/Zeditor or equivalent external storage path
 */
fun getZeditorDir(context: Context = application!!): File {
    val externalStorage = Environment.getExternalStorageDirectory()
    return File(externalStorage, "Zeditor").also { it.createDirIfNot() }
}

/**
 * Gets the directory for Ubuntu rootfs in Zeditor external storage.
 * Falls back to internal storage if external storage is not available.
 */
fun getZeditorSandboxDir(context: Context = application!!): File {
    return try {
        getZeditorDir(context).child("sandbox").also { it.createDirIfNot() }
    } catch (e: Exception) {
        // Fallback to internal storage if external storage is not available
        sandboxDir(context)
    }
}

/**
 * Gets the home directory for Ubuntu in Zeditor external storage.
 * Falls back to internal storage if external storage is not available.
 */
fun getZeditorHomeDir(context: Context = application!!): File {
    return try {
        getZeditorDir(context).child("home").also { it.createDirIfNot() }
    } catch (e: Exception) {
        // Fallback to internal storage if external storage is not available
        sandboxHomeDir(context)
    }
}

/**
 * Checks if the external storage location has an existing Ubuntu installation.
 */
fun hasExternalInstallation(context: Context = application!!): Boolean {
    return try {
        val zeditorSandbox = getZeditorDir(context).child("sandbox")
        val setupMarker = getZeditorDir(context).child(".terminal_setup_ok_DO_NOT_REMOVE")
        zeditorSandbox.exists() && zeditorSandbox.listFiles()?.isNotEmpty() == true && setupMarker.exists()
    } catch (e: Exception) {
        false
    }
}

/**
 * Returns the active sandbox directory - either external or internal based on what exists.
 * Prioritizes external storage if it has an installation.
 */
fun getActiveSandboxDir(context: Context = application!!): File {
    return if (hasExternalInstallation(context)) {
        getZeditorSandboxDir(context)
    } else {
        sandboxDir(context)
    }
}

/**
 * Returns the active home directory - either external or internal based on what exists.
 * Prioritizes external storage if it has an installation.
 */
fun getActiveHomeDir(context: Context = application!!): File {
    return if (hasExternalInstallation(context)) {
        getZeditorHomeDir(context)
    } else {
        sandboxHomeDir(context)
    }
}
