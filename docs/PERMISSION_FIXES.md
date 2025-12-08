# Permission Fixes for Proot Terminal

## Overview

This document explains the permission-related improvements made to the terminal implementation to solve common "Operation not permitted" and "Permission denied" errors when using proot.

## Common Permission Issues

### 1. Directory Access Issues
**Problem:** Proot needs to access various directories but may encounter permission errors.

**Solution:**
- Automatically set proper permissions (755) for sandbox directory
- Ensure home directory is readable, writable, and executable
- Set sticky bit (1777) on temp directories for proper multi-user access

### 2. Binary Execution Issues
**Problem:** Downloaded binaries may not have execute permission.

**Solution:**
- Automatically make all files in `local/bin` executable
- Set execute permission on proot binary
- Set execute permission on shared libraries

### 3. Temp Directory Issues
**Problem:** Applications fail when trying to write to /tmp or /var/tmp.

**Solution:**
- Create /tmp, /var/tmp with 1777 permissions (sticky bit)
- Create /run and /var/run directories
- Fix permissions on writable cache directories

### 4. SELinux Restrictions
**Problem:** Android's SELinux can block proot operations on some devices.

**Solution:**
- Detect SELinux enforcement status
- Log SELinux information for debugging
- Attempt to set appropriate contexts when possible
- Gracefully handle SELinux restrictions

## Implementation Details

### PermissionHelper.kt

The main permission management class that:

1. **setupPermissions()** - Main entry point, sets up all permissions
2. **fixSandboxPermissions()** - Fixes rootfs directory permissions
3. **fixHomePermissions()** - Ensures home directory is accessible
4. **fixTempPermissions()** - Sets proper temp directory permissions
5. **fixBinaryPermissions()** - Makes binaries executable
6. **fixLibraryPermissions()** - Ensures libraries are readable
7. **checkPermissions()** - Validates permission status

### SELinuxHelper.kt

Handles SELinux compatibility:

1. **isSELinuxEnforcing()** - Checks if SELinux is enforcing
2. **getSELinuxMode()** - Gets current SELinux mode
3. **trySetSELinuxContext()** - Attempts to set appropriate contexts
4. **logSELinuxInfo()** - Logs debugging information

### Integration Points

**MkSession.kt:**
```kotlin
// Setup permissions before creating session
PermissionHelper.setupPermissions()
```

**ubuntuProcess.kt:**
```kotlin
// Setup permissions before launching process
com.rk.terminal.PermissionHelper.setupPermissions()

// Ensure temp directory has proper permissions
tmpDir.setReadable(true, false)
tmpDir.setWritable(true, false)
tmpDir.setExecutable(true, false)
```

**Terminal.kt:**
```kotlin
// Setup all permissions after downloads complete
com.rk.terminal.PermissionHelper.setupPermissions()
```

### Script Improvements

**sandbox.sh:**
```bash
# Add permission-related proot flags
ARGS="$ARGS --kernel-release=5.10.0-android"

# Fix permissions with error suppression
chmod -R +x $PRIVATE_DIR/local/bin 2>/dev/null || true
chmod 755 $PRIVATE_DIR/local/bin 2>/dev/null || true
chmod 755 $LOCAL/sandbox 2>/dev/null || true
chmod 1777 $LOCAL/sandbox/tmp 2>/dev/null || true
```

**init.sh:**
```bash
# Ensure critical directories exist and have proper permissions
ensure_directory() {
    local dir="$1"
    local perms="${2:-755}"
    if [ ! -d "$dir" ]; then
        mkdir -p "$dir" 2>/dev/null || true
    fi
    chmod "$perms" "$dir" 2>/dev/null || true
}

# Create essential directories
ensure_directory "/tmp" 1777
ensure_directory "/var/tmp" 1777
ensure_directory "/run" 755
ensure_directory "/var/run" 755
ensure_directory "/home" 755
ensure_directory "/root" 700

# Fix permissions for writable locations
fix_permissions() {
    chmod 1777 /tmp 2>/dev/null || true
    chmod 1777 /var/tmp 2>/dev/null || true
    chmod 1777 /var/lock 2>/dev/null || true
    chmod 755 /var/cache 2>/dev/null || true
    chmod 755 /var/lib/dpkg 2>/dev/null || true
}
```

## Proot Configuration

Additional proot flags for better compatibility:

```kotlin
add("--kernel-release=5.10.0-android")  // Pretend to be Linux 5.10 kernel
add("-0")                                // Fake root user
add("--link2symlink")                    // Convert hard links to symlinks
add("--sysvipc")                         // Enable System V IPC
add("-L")                                // Use loader for better compatibility
```

## Permission Values Explained

- **755** (rwxr-xr-x): Owner can read/write/execute, others can read/execute
- **777** (rwxrwxrwx): Everyone can read/write/execute
- **1777** (rwxrwxrwt): Everyone can read/write/execute, but can only delete own files (sticky bit)
- **700** (rwx------): Only owner can read/write/execute

## Testing

To verify permissions are working:

```bash
# Check temp directory
ls -la /tmp
# Should show: drwxrwxrwt

# Check home directory
ls -la /home
# Should show: drwxr-xr-x

# Test file creation
echo "test" > /tmp/test.txt
cat /tmp/test.txt
rm /tmp/test.txt

# Test binary execution
which bash
bash --version
```

## Troubleshooting

### Still Getting Permission Errors?

1. **Check SELinux status:**
   ```bash
   getenforce
   ```

2. **Check directory permissions:**
   ```bash
   ls -la /tmp
   ls -la /home
   ```

3. **Check if binaries are executable:**
   ```bash
   ls -la $PRIVATE_DIR/local/bin/proot
   ```

4. **Check logs:**
   - Look for "PermissionHelper" logs in Android logcat
   - Look for "SELinuxHelper" logs in Android logcat

### Known Limitations

- Cannot change SELinux contexts without root access
- Some operations may still be restricted on highly locked-down devices
- File system permissions are emulated by proot, not native

## Future Improvements

- [ ] Add automatic permission repair on startup
- [ ] Detect and warn about severe permission issues
- [ ] Add user-friendly error messages for permission failures
- [ ] Implement permission caching to avoid repeated checks
- [ ] Add option to reset all permissions to defaults
