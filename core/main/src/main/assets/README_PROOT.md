# PRoot Binaries Required

For the Ubuntu Terminal (Full) feature to work, you need to add PRoot binaries to this directory.

## Required Files

Add the following PRoot binaries to this `assets` folder:

- `proot-arm64` - For ARM64 devices (most modern Android devices)
- `proot-armhf` - For ARM32 devices
- `proot-amd64` - For x86_64 devices (emulators and some tablets)
- `proot-i386` - For x86 devices

## Where to Get PRoot Binaries

### Option 1: Download from PRoot GitHub Releases

Visit [PRoot GitHub Releases](https://github.com/proot-me/proot/releases) and download the appropriate binaries for Android.

### Option 2: Extract from Termux

1. Download the Termux APK from [F-Droid](https://f-droid.org/packages/com.termux/)
2. Extract the APK (it's a ZIP file)
3. Find PRoot binaries in the `lib/` folders:
   - `lib/arm64-v8a/` - Contains ARM64 binaries
   - `lib/armeabi-v7a/` - Contains ARM32 binaries
   - `lib/x86_64/` - Contains x86_64 binaries
   - `lib/x86/` - Contains x86 binaries
4. Copy the PRoot binary and rename it according to the architecture:
   - From `lib/arm64-v8a/libproot.so` → `proot-arm64`
   - From `lib/armeabi-v7a/libproot.so` → `proot-armhf`
   - From `lib/x86_64/libproot.so` → `proot-amd64`
   - From `lib/x86/libproot.so` → `proot-i386`

## Important Notes

- PRoot binaries must be executable (permissions are set automatically by the app)
- The app will detect the device architecture and use the appropriate binary
- Without PRoot binaries, the "Ubuntu Terminal (Full)" feature will not work
- The standard terminal will still function without PRoot binaries

## File Structure

```
assets/
├── proot-arm64
├── proot-armhf
├── proot-amd64
└── proot-i386
```

## Testing

After adding the binaries:
1. Build and run the app
2. Open the "Add" menu (+ button)
3. Select "Ubuntu Terminal (Full)"
4. The app will download Ubuntu rootfs (~100MB) on first launch
5. Subsequent launches will be instant

If PRoot binaries are missing, the app will show an error message asking you to add them.
