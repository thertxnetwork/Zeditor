# PRoot Binaries Included

✅ **PRoot binaries are now included in this assets folder!**

The following PRoot binaries (v5.3.0) have been added for the Ubuntu Terminal (Full) feature:

- ✅ `proot-arm64` - For ARM64 devices (most modern Android devices)
- ✅ `proot-armhf` - For ARM32 devices  
- ✅ `proot-amd64` - For x86_64 devices (emulators and some tablets)
- ✅ `proot-i386` - For x86 devices (fallback to x86_64 binary)

## No Additional Setup Required

The PRoot binaries are ready to use. The Ubuntu Terminal (Full) feature will work out of the box:

1. Open the "Add" menu (+ button)
2. Select "Ubuntu Terminal (Full)"
3. On first launch, the app will download Ubuntu rootfs (~100MB)
4. Subsequent launches will be instant

The app automatically detects your device architecture and uses the appropriate PRoot binary.

## Binary Information

- **Version**: PRoot v5.3.0
- **Type**: Statically linked executables
- **Source**: [PRoot GitHub Releases](https://github.com/proot-me/proot/releases/tag/v5.3.0)
- **Architectures**: ARM64, ARM32, x86_64, x86
- **Permissions**: Set to executable automatically by the app

## What is PRoot?

PRoot is a user-space implementation of chroot that doesn't require root access. It allows running a full Linux distribution in a sandboxed environment on Android by:

- Faking root privileges (uid 0) within the container
- Binding essential Android directories (/dev, /proc, /sys)
- Translating filesystem paths transparently
- Providing a complete Linux environment without kernel modifications

## File Structure

```
assets/
├── proot-arm64    (1.5MB - ARM 64-bit)
├── proot-armhf    (1.2MB - ARM 32-bit)
├── proot-amd64    (1.9MB - x86 64-bit)
├── proot-i386     (1.9MB - x86 32-bit fallback)
└── README_PROOT.md (this file)
```

## Testing

After building the app:
1. Open the "Add" menu (+ button in top bar)
2. Select "Ubuntu Terminal (Full)"
3. The app will download Ubuntu rootfs (~100MB) on first launch
4. After installation, you'll have a full Ubuntu environment with apt, gcc, python3, node, vim, etc.
5. All installations and configurations persist across app restarts

## Troubleshooting

If the Ubuntu Terminal fails to start:
- Check device architecture compatibility
- Ensure sufficient storage space (~200MB for full installation)
- Check internet connection for first-time rootfs download
- Review app logs for PRoot execution errors

The standard terminal will continue to work regardless of PRoot binary status.
