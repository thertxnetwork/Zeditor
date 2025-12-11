package com.rk.runner.ssh

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Authentication type for SSH connections
 */
enum class SSHAuthType {
    PASSWORD,
    KEY
}

/**
 * Supported distribution types for remote servers
 */
enum class DistroType(val displayName: String) {
    UBUNTU("Ubuntu/Debian"),
    CENTOS("CentOS/RHEL"),
    FEDORA("Fedora"),
    ARCH("Arch Linux"),
    ALPINE("Alpine"),
    OPENSUSE("openSUSE"),
    CUSTOM("Custom");
    
    companion object {
        fun fromDisplayName(name: String): DistroType {
            return entries.find { it.displayName == name } ?: CUSTOM
        }
    }
}

/**
 * SSH Server configuration
 */
@Parcelize
data class SSHServerConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = 22,
    val username: String,
    val authType: SSHAuthType,
    val password: String? = null,
    val privateKey: String? = null,
    val distroType: DistroType = DistroType.UBUNTU,
    val workingDirectory: String = "~"
) : Parcelable {
    
    fun validate(): Boolean {
        if (name.isBlank() || host.isBlank() || username.isBlank()) {
            return false
        }
        
        if (port !in 1..65535) {
            return false
        }
        
        when (authType) {
            SSHAuthType.PASSWORD -> if (password.isNullOrBlank()) return false
            SSHAuthType.KEY -> if (privateKey.isNullOrBlank()) return false
        }
        
        return true
    }
    
    fun getDisplayInfo(): String {
        return "$username@$host:$port (${distroType.displayName})"
    }
}
