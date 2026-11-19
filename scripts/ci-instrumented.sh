#!/usr/bin/env bash
# Runs the instrumented tests inside the emulator action (PoC task 43). The action executes each
# `script:` line in a bare sh, so the Gradle call lives here: it keeps the full log and turns the
# first failure into a readable annotation, as the other android-ci steps do.
set +e
./gradlew --no-daemon --console=plain --stacktrace :app:connectedDebugAndroidTest 2>&1 | tee gradle-instrumented.log
status=${PIPESTATUS[0]}
if [ "$status" -ne 0 ]; then
  msg=$(grep -E -A8 "What went wrong|FAILED|^e: |error:|INSTRUMENTATION_|Process crashed|No connected devices" gradle-instrumented.log | head -50 | sed ':a;N;$!ba;s/\n/%0A/g')
  echo "::error title=Gradle instrumented tests failed::${msg}"
fi
exit "$status"
