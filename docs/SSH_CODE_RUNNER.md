# SSH Code Runner

## Overview

The SSH Code Runner feature allows you to execute code on remote VPS servers directly from Zeditor. Instead of relying on complex local interpreter libraries, you can configure one or more remote servers and run your code via SSH with an integrated terminal.

## Features

- **Multiple Server Support**: Configure multiple VPS servers with different Linux distributions
- **Flexible Authentication**: Support for both password and SSH key-based authentication
- **Interactive Terminal**: Real-time terminal interface for command execution and output
- **Automatic File Upload**: Files are automatically uploaded to the remote server before execution
- **Multi-distro Support**: Compatible with Ubuntu, CentOS, Fedora, Arch, Alpine, openSUSE, and custom distributions
- **Language Detection**: Automatically detects file type and uses appropriate interpreter on the remote server

## Configuration

### Adding an SSH Server

1. Open Zeditor
2. Navigate to **Settings** > **SSH Servers**
3. Tap the **+** (Add) button
4. Fill in the server details:
   - **Server Name**: A friendly name for your server (e.g., "My Ubuntu VPS")
   - **Host**: IP address or domain name of your server
   - **Port**: SSH port (default: 22)
   - **Username**: Your SSH username
   - **Authentication Type**: Choose between Password or Private Key
   - **Password/Private Key**: Your authentication credentials
   - **Distribution Type**: Select your server's Linux distribution
   - **Working Directory**: Default directory for code execution (default: ~)
5. Tap **Test Connection** to verify your configuration
6. Tap **Save**

### Authentication Options

#### Password Authentication
Simply enter your SSH password. This is the easiest method but less secure.

#### SSH Key Authentication
Paste your private key in PEM format. Example:
```
-----BEGIN OPENSSH PRIVATE KEY-----
b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAABlwAAAAdzc2gtcn
...
-----END OPENSSH PRIVATE KEY-----
```

## Usage

### Running Code

1. Open a code file in Zeditor
2. Tap the **Run** button or use the run shortcut
3. If multiple SSH servers are configured, select the server you want to use
4. The terminal will open automatically, showing:
   - Connection status
   - File upload progress
   - Code execution output
   - Any errors or warnings

### Terminal Features

- **Real-time Output**: See your code's output as it runs
- **Interactive Input**: Send commands directly to the terminal
- **Auto-scroll**: Terminal automatically scrolls to show latest output
- **Copy Output**: Copy terminal output to clipboard
- **Disconnect**: Close the SSH session at any time

## Supported Languages

The SSH runner automatically detects your file type and executes it with the appropriate interpreter:

| Language | Extension | Remote Command |
|----------|-----------|----------------|
| Python | `.py` | `python3` |
| JavaScript | `.js` | `node` |
| Shell Script | `.sh` | `bash` |
| Ruby | `.rb` | `ruby` |
| PHP | `.php` | `php` |
| Perl | `.pl` | `perl` |
| Go | `.go` | `go run` |
| Rust | `.rs` | `rustc` + execute |
| C | `.c` | `gcc` + execute |
| C++ | `.cpp`, `.cc` | `g++` + execute |
| Java | `.java` | `javac` + `java` |
| Kotlin | `.kt` | `kotlinc` + `java -jar` |

**Note**: The required interpreter/compiler must be installed on your remote server.

## Server Management

### Editing a Server

1. Go to **Settings** > **SSH Servers**
2. Tap on the server you want to edit
3. Modify the settings
4. Tap **Save**

### Deleting a Server

1. Go to **Settings** > **SSH Servers**
2. Tap the trash icon next to the server
3. Confirm deletion

### Testing Connection

Use the **Test Connection** button in the server configuration dialog to verify:
- Server is reachable
- Authentication credentials are correct
- SSH service is running
- Network connectivity is stable

## Troubleshooting

### Connection Failed

**Problem**: Cannot connect to the server

**Solutions**:
- Verify the host address and port are correct
- Check your internet connection
- Ensure the SSH service is running on the remote server
- Verify firewall rules allow SSH connections
- Check if the SSH port is correct (default: 22)

### Authentication Failed

**Problem**: Authentication error when connecting

**Solutions**:
- Verify username is correct
- Check password/private key is correct
- For key auth, ensure the key is in PEM format
- Verify the user has SSH access on the remote server
- Check if the SSH key is added to `~/.ssh/authorized_keys` on the server

### Command Not Found

**Problem**: Interpreter not found when running code

**Solutions**:
- Install the required interpreter on your remote server
- For Python: `sudo apt install python3` (Ubuntu/Debian)
- For Node.js: `sudo apt install nodejs` (Ubuntu/Debian)
- Verify the interpreter is in the system PATH
- Try running commands manually via terminal to test

### File Upload Failed

**Problem**: Cannot upload file to remote server

**Solutions**:
- Verify you have write permissions in the working directory
- Check available disk space on the remote server
- Ensure the working directory path exists
- Try changing the working directory to `~` (home directory)

## Security Considerations

⚠️ **Important Security Warnings**:

- **Host Key Verification**: Currently, the app disables strict host key checking for ease of use. This makes connections vulnerable to man-in-the-middle attacks. Only connect to servers you trust over secure networks.
- **SSH Keys**: Use SSH key authentication instead of passwords when possible for better security
- **Key Management**: Keep your private keys secure and never share them. Keys are stored in app data.
- **Server Access**: Only connect to servers you own or have permission to use
- **Network**: Always use secure, trusted networks when connecting to your servers
- **Passwords**: Use strong passwords and consider using a password manager
- **Firewall**: Configure your server's firewall to only allow SSH from trusted IPs
- **Credentials Storage**: Server credentials are stored locally in JSON format. Ensure your device is secured with a lock screen.

## Tips

1. **Multiple Servers**: Configure different servers for different purposes (development, testing, production)
2. **Distribution Types**: Select the correct distro type for better compatibility
3. **Working Directory**: Use absolute paths for the working directory to avoid issues
4. **Test First**: Always test the connection before running critical code
5. **Terminal Access**: Use the terminal for interactive debugging and testing
6. **File Management**: Files are uploaded to the working directory before execution

## Future Enhancements

Planned features for future releases:
- Support for SSH tunneling and port forwarding
- Session persistence and reconnection
- Multiple terminal tabs
- SFTP file browser
- Custom execution commands per server
- Terminal themes and customization
- Output filtering and search
- Command history

## Support

For issues, questions, or feature requests related to the SSH Code Runner, please:
1. Check this documentation first
2. Review the troubleshooting section
3. Open an issue on the project's GitHub repository
4. Include relevant error messages and server details (without credentials)
