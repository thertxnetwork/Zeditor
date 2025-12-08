#!/system/bin/sh
# Native Linux Launcher Wrapper
# This script is executed once by Android shell, then immediately hands off to native Linux launcher
# This minimizes Android shell dependency to just the initial bootstrap

# This script should be as minimal as possible
exec "$@"
