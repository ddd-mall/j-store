#!/usr/bin/env sh
set -eu

# PR analysis imports the pre-move revision first. Its generated IDE model can
# still link the repository root when Qodana switches to the relocated build.
test -f backend/settings.gradle.kts
if [ -f .idea/gradle.xml ]; then
    sed 's|name="externalProjectPath" value="$PROJECT_DIR$"|name="externalProjectPath" value="$PROJECT_DIR$/backend"|g' \
        .idea/gradle.xml > .idea/gradle.xml.qodana
    mv .idea/gradle.xml.qodana .idea/gradle.xml
fi
