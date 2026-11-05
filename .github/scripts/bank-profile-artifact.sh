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
  printf '%s\n' 'Profiling trace did not flush to a stable nonempty file.' >&2
  return 1
}

# Perfetto handles TERM by flushing, disabling producers, then reading buffers.
# Those phases can each wait for acknowledgements; wait for actual process exit.
bank_stop_perfetto_and_pull() {
  local recorder_pid=$1 remote=$2 destination=$3 attempts=${4:-30} interval=${5:-1}
  local attempt
  if adb shell kill -0 "$recorder_pid" >/dev/null 2>&1; then
    # A second TERM can hard-kill its reset signal handler during the flush.
    adb shell kill -TERM "$recorder_pid" || return 1
    for ((attempt=0; attempt<attempts; attempt++)); do
      if ! adb shell kill -0 "$recorder_pid" >/dev/null 2>&1; then
        bank_pull_flushed_profile "$remote" "$destination"
        return
      fi
      sleep "$interval"
    done
    printf '%s\n' 'Perfetto did not finish flushing within the bounded stop wait.' >&2
    return 1
  fi
  bank_pull_flushed_profile "$remote" "$destination"
}
