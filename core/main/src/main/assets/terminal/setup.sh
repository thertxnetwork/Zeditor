set -e

source "$LOCAL/bin/utils"

info "Extracting the Ubuntu container…"

# Extract the rootfs using proot with --link2symlink
# This converts hard links to symbolic links, which is necessary on Android
# where hard link creation may be restricted
cd "$LOCAL/sandbox" || exit 1

if [ "$FDROID" = "false" ]; then
    if ! $LINKER "$LOCAL/bin/proot" --link2symlink tar -xf "$TMP_DIR/sandbox.tar.gz"; then
        error "Failed to extract Ubuntu container"
        exit 1
    fi
else
    if ! "$LOCAL/bin/proot" --link2symlink tar -xf "$TMP_DIR/sandbox.tar.gz"; then
        error "Failed to extract Ubuntu container"
        exit 1
    fi
fi


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
    sh "$@"
else
    clear
    sh "$LOCAL/bin/sandbox"
fi
