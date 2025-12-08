set -e

source "$PRIVATE_DIR/local/bin/utils"

info "Extracting the Ubuntu container…"

# Extract the rootfs directly using tar without proot
# We don't need proot for extraction since we're just unpacking files on Android's filesystem
# Use busybox tar if available, otherwise try system tar
cd "$LOCAL/sandbox" || exit 1

# Try to extract with available tar implementation
if command -v busybox >/dev/null 2>&1; then
    info "Using busybox tar for extraction"
    if ! busybox tar -xzf "$TMP_DIR/sandbox.tar.gz" 2>&1; then
        error "Failed to extract Ubuntu container with busybox"
        exit 1
    fi
elif command -v tar >/dev/null 2>&1; then
    info "Using system tar for extraction"
    if ! tar -xzf "$TMP_DIR/sandbox.tar.gz" 2>&1; then
        error "Failed to extract Ubuntu container with system tar"
        exit 1
    fi
else
    error "No tar command available for extraction"
    exit 1
fi

cd - > /dev/null || exit 1


SANDBOX_DIR="$LOCAL/sandbox"

info "Setting up the Ubuntu container…"

# values you want written
nameserver="nameserver 8.8.8.8
nameserver 8.8.4.4"

hosts="127.0.0.1   localhost.localdomain localhost

# IPv6.
::1         localhost.localdomain localhost ip6-localhost ip6-loopback
fe00::0     ip6-localnet
ff00::0     ip6-mcastprefix
ff02::1     ip6-allnodes
ff02::2     ip6-allrouters
ff02::3     ip6-allhosts"

# ensure etc directory exists
mkdir -p "$SANDBOX_DIR/etc"

# write hostname
printf '%s\n' "Xed-Editor" > "$SANDBOX_DIR/etc/hostname"

# write resolv.conf (create file if not exists, then overwrite)
: > "$SANDBOX_DIR/etc/resolv.conf"
printf '%s\n' "$nameserver" > "$SANDBOX_DIR/etc/resolv.conf"

# write hosts
printf '%s\n' "$hosts" > "$SANDBOX_DIR/etc/hosts"

groupFile="$SANDBOX_DIR/etc/group"
aid="$(id -g)"

linesToAdd="
inet:x:3003
everybody:x:9997
android_app:x:20455
android_debug:x:50455
android_cache:x:$((10000 + aid))
android_storage:x:$((40000 + aid))
android_media:x:$((50000 + aid))
android_external_storage:x:1077
"

# create the file if it doesn't exist
[ -f "$groupFile" ] || : > "$groupFile"

existing="$(cat "$groupFile")"

# iterate through lines
echo "$linesToAdd" | while IFS= read -r line; do
    [ -z "$line" ] && continue
    gid="${line##*:}"  # get part after last colon
    case "$existing" in
        *:"$gid"*) : ;;   # already exists → skip
        *) printf '%s\n' "$line" >> "$groupFile" ;;
    esac
done


rm "$TMP_DIR"/sandbox.tar.gz
# DO NOT REMOVE THIS FILE JUST DON'T, TRUST ME
touch "$LOCAL/.terminal_setup_ok_DO_NOT_REMOVE"

if [ $# -gt 0 ]; then
    exec "$@"
else
    clear
    exec "$PRIVATE_DIR/local/bin/sandbox"
fi
