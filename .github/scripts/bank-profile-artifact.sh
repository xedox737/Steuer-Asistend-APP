#!/usr/bin/env bash
# am profile stop schedules work in the app; its return does not mean ART flushed.
bank_pull_flushed_profile() {
  local remote=$1 destination=$2 attempts=${3:-20} interval=${4:-0.25}
  local previous_size=-1 profile_size attempt
  for ((attempt=0; attempt<attempts; attempt++)); do
    profile_size=$(adb shell stat -c %s "$remote" 2>/dev/null | tr -d '\r') || profile_size=
    if [[ "$profile_size" =~ ^[0-9]+$ ]] && [ "$profile_size" -gt 0 ] && [ "$profile_size" = "$previous_size" ]; then
      adb pull "$remote" "$destination" || return 1
      test -s "$destination"
      return
    fi
    previous_size=$profile_size
    sleep "$interval"
  done
  printf '%s\n' 'Startup method profile did not flush to a stable nonempty file.' >&2
  return 1
}
