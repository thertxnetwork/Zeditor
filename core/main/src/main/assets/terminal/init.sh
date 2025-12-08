# shellcheck disable=SC2034
force_color_prompt=yes
shopt -s checkwinsize

source "$PRIVATE_DIR/local/bin/utils"

# Early PATH setup: Prioritize Ubuntu's binaries over Android's system binaries
# This prevents "Operation not permitted" errors when trying to use Android's /system/bin commands
# Put Ubuntu binaries FIRST so they're used instead of Android's
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PRIVATE_DIR/local/bin

# Ensure critical directories exist and have proper permissions
ensure_directory() {
    local dir="$1"
    local perms="${2:-755}"
    if [ ! -d "$dir" ]; then
        mkdir -p "$dir" 2>/dev/null || true
    fi
    chmod "$perms" "$dir" 2>/dev/null || true
}

# Create essential directories with proper permissions
ensure_directory "/tmp" 1777
ensure_directory "/var/tmp" 1777
ensure_directory "/run" 755
ensure_directory "/var/run" 755
ensure_directory "/home" 755
ensure_directory "/root" 700

# Set timezone - Use Ubuntu's ln command which is now in PATH
CONTAINER_TIMEZONE="UTC"  # or any timezone like "Asia/Kolkata"

# Symlink /etc/localtime to the desired timezone (using Ubuntu's ln, not Android's)
if [ -f "/usr/share/zoneinfo/$CONTAINER_TIMEZONE" ]; then
    ln -snf "/usr/share/zoneinfo/$CONTAINER_TIMEZONE" /etc/localtime 2>/dev/null || true
fi

# Write the timezone string to /etc/timezone
echo "$CONTAINER_TIMEZONE" > /etc/timezone 2>/dev/null || true

# Reconfigure tzdata to apply without prompts
DEBIAN_FRONTEND=noninteractive dpkg-reconfigure -f noninteractive tzdata >/dev/null 2>&1

ALPINE_DIR="$PRIVATE_DIR/local/alpine"
RETAINED_FILE="$ALPINE_DIR/.retained"

if [ -d "$ALPINE_DIR" ]; then
  if [ -f "$RETAINED_FILE" ]; then
    :
  else
    info "Detected existing Alpine installation"
    printf "\nXed-editor has now migrated from Alpine to Ubuntu for better compatibility and support.\n\n"

    if confirm "Do you want to migrate your home data from Alpine to Ubuntu?"; then
      info "Migrating data..."
      mkdir -p "/home/alpine-data"
      cp -r "$ALPINE_DIR/root" "/home/alpine-data/"
      cp -r "$ALPINE_DIR/home" "/home/alpine-data/"

      info "Data migration completed."
    else
      warn "Skipped data migration."
    fi

    if confirm "Do you want to delete the Alpine installation to free up space?"; then
      info "Deleting Alpine installation..."
      xed exec rm -rf "$ALPINE_DIR"
      info "Alpine has been removed."
    else
      warn "Alpine installation retained."
      touch "$RETAINED_FILE"
    fi
  fi
fi


if [[ -f ~/.bashrc ]]; then
    # shellcheck disable=SC1090
    source ~/.bashrc
fi


# Final PATH configuration: Prioritize Ubuntu binaries over Android system binaries
# This is the PATH that will be used for all commands in the container
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PRIVATE_DIR/local/bin
export SHELL="bash"
# Use a simpler PS1 that doesn't call external commands like groups
# \u in bash can sometimes trigger calls to getent or similar commands
export PS1="\[\e[1;32m\]root@zeditor\[\e[0m\]:\[\e[1;34m\]\w\[\e[0m\] \\$ "

# Configure DNS for the container
# Android doesn't use traditional /etc/resolv.conf, DNS servers are in system properties
setup_dns() {
    # Try to get DNS servers from Android system properties
    local dns_servers=()
    
    # getprop is an Android command that may be accessible if /system is bind-mounted
    # It reads system properties where Android stores DNS configuration
    if command -v getprop >/dev/null 2>&1; then
        # Try different property names that might contain DNS info
        for prop in net.dns1 net.dns2 net.dns3 net.dns4; do
            local dns=$(getprop "$prop" 2>/dev/null)
            if [ -n "$dns" ] && [ "$dns" != "0.0.0.0" ]; then
                dns_servers+=("$dns")
            fi
        done
    fi
    
    # If no DNS servers found from Android properties, use reliable public DNS as fallback
    if [ ${#dns_servers[@]} -eq 0 ]; then
        dns_servers=("8.8.8.8" "8.8.4.4" "1.1.1.1" "1.0.0.1")
    fi
    
    # Ensure /etc directory exists and is writable
    mkdir -p /etc 2>/dev/null || true
    chmod 755 /etc 2>/dev/null || true
    
    # Write DNS configuration to /etc/resolv.conf
    {
        echo "# DNS configuration for Ubuntu container"
        echo "# Generated dynamically from Android system or fallback"
        for dns in "${dns_servers[@]}"; do
            echo "nameserver $dns"
        done
        echo ""
        echo "options ndots:0"
    } > /etc/resolv.conf 2>/dev/null || {
        # Fallback: try with sudo if available, or just create a minimal config
        echo "nameserver 8.8.8.8" > /etc/resolv.conf 2>/dev/null || true
        echo "nameserver 8.8.4.4" >> /etc/resolv.conf 2>/dev/null || true
    }
    
    # Make sure resolv.conf is readable
    chmod 644 /etc/resolv.conf 2>/dev/null || true
}

# Setup DNS before attempting package operations
setup_dns

# Fix permissions for writable locations
fix_permissions() {
    # Fix /tmp permissions
    if [ -d "/tmp" ]; then
        chmod 1777 /tmp 2>/dev/null || true
    fi
    
    # Fix /var/tmp permissions
    if [ -d "/var/tmp" ]; then
        chmod 1777 /var/tmp 2>/dev/null || true
    fi
    
    # Fix /var/lock permissions
    if [ -d "/var/lock" ]; then
        chmod 1777 /var/lock 2>/dev/null || true
    fi
    
    # Fix /var/cache permissions
    if [ -d "/var/cache" ]; then
        chmod 755 /var/cache 2>/dev/null || true
    fi
    
    # Fix /var/lib/dpkg if it exists
    if [ -d "/var/lib/dpkg" ]; then
        chmod 755 /var/lib/dpkg 2>/dev/null || true
    fi
}

# Apply permission fixes
fix_permissions

ensure_packages_once() {
    local marker_file="/.cache/.packages_ensured"
    local PACKAGES=("command-not-found" "sudo" "xkb-data")

    # Exit early if already done
    [[ -f "$marker_file" ]] && return 0

    # Ensure apt config directory exists
    mkdir -p /etc/apt/apt.conf.d 2>/dev/null || true
    
    echo 'APT::Install-Recommends "false";' > /etc/apt/apt.conf.d/99norecommends
    echo 'APT::Install-Suggests "false";' >> /etc/apt/apt.conf.d/99norecommends

    # Create cache dir
    mkdir -p "/.cache"

    # Check for missing packages
    local MISSING=()
    for pkg in "${PACKAGES[@]}"; do
        if ! dpkg -s "$pkg" >/dev/null 2>&1; then
            MISSING+=("$pkg")
        fi
    done

    # If nothing missing, just mark as done
    if [ ${#MISSING[@]} -eq 0 ]; then
        touch "$marker_file"
        return 0
    fi

    info "Installing missing packages: ${MISSING[*]}"
    
    # Test network connectivity first
    if ! ping -c 1 -W 2 8.8.8.8 >/dev/null 2>&1; then
        warn "No network connectivity detected. Package installation will be skipped."
        warn "Packages will be installed on next session when network is available."
        return 0
    fi

    if export DEBIAN_FRONTEND=noninteractive && \
       apt update -y && \
       apt install -y "${MISSING[@]}"; then
        touch "$marker_file"
        info "Packages installed."
    else
        warn "Failed to install packages. This may be due to network issues."
        warn "Terminal will work but some features may be limited."
        warn "Packages will be installed on next session when network is available."
        return 0
    fi

    # Update command-not-found database
    update-command-not-found 2>/dev/null || true
}


ensure_packages_once
unset -f ensure_packages_once

if [ -x /usr/lib/command-not-found -o -x /usr/share/command-not-found/command-not-found ]; then
	function command_not_found_handle {
	        # check because c-n-f could've been removed in the meantime
                if [ -x /usr/lib/command-not-found ]; then
		   /usr/lib/command-not-found -- "$1"
                   return $?
                elif [ -x /usr/share/command-not-found/command-not-found ]; then
		   /usr/share/command-not-found/command-not-found -- "$1"
                   return $?
		else
		   printf "%s: command not found\n" "$1" >&2
		   return 127
		fi
	}
fi


alias ls='ls --color=auto'
alias grep='grep --color=auto'
alias egrep='egrep --color=auto'
alias fgrep='fgrep --color=auto'
alias pkg='apt'

if [[ -f /initrc ]]; then
    # shellcheck disable=SC1090
    source /initrc
fi

# shellcheck disable=SC2164
cd "$WKDIR" || cd $HOME


