# shellcheck disable=SC2034
force_color_prompt=yes
shopt -s checkwinsize

source "$PRIVATE_DIR/local/bin/utils"

# Early PATH setup: Prioritize Ubuntu's binaries over Android's system binaries
# This prevents "Operation not permitted" errors when trying to use Android's /system/bin commands
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:$PRIVATE_DIR/local/bin

# Set timezone
CONTAINER_TIMEZONE="UTC"  # or any timezone like "Asia/Kolkata"

# Symlink /etc/localtime to the desired timezone (use absolute path to avoid Android system binary)
/bin/ln -snf "/usr/share/zoneinfo/$CONTAINER_TIMEZONE" /etc/localtime 2>/dev/null || true

# Write the timezone string to /etc/timezone
echo "$CONTAINER_TIMEZONE" > /etc/timezone

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


# Final PATH configuration: Set the complete PATH for the session
# This is the PATH that will be used for all commands in the container
export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/games:/usr/local/bin:/usr/local/sbin:$PRIVATE_DIR/local/bin
export SHELL="bash"
export PS1="\[\e[1;32m\]\u@\h\[\e[0m\]:\[\e[1;34m\]\w\[\e[0m\] \\$ "

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
    
    # Write DNS configuration to /etc/resolv.conf
    {
        echo "# DNS configuration for Ubuntu container"
        echo "# Generated dynamically from Android system or fallback"
        for dns in "${dns_servers[@]}"; do
            echo "nameserver $dns"
        done
        echo ""
        echo "options ndots:0"
    } > /etc/resolv.conf
    
    # Make sure resolv.conf is readable
    chmod 644 /etc/resolv.conf
}

# Setup DNS before attempting package operations
setup_dns

ensure_packages_once() {
    local marker_file="/.cache/.packages_ensured"
    local PACKAGES=("command-not-found" "sudo" "xkb-data")

    # Exit early if already done
    [[ -f "$marker_file" ]] && return 0

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

    if export DEBIAN_FRONTEND=noninteractive && \
       apt update -y && \
       apt install -y "${MISSING[@]}"; then
        touch "$marker_file"
        info "Packages installed."
    else
        error "Failed to install packages."
        return 1
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


