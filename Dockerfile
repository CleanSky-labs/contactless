FROM eclipse-temurin:17-jdk

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=$PATH:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools

WORKDIR /app

# Install dependencies
RUN apt-get update && apt-get install -y \
    wget unzip git && \
    rm -rf /var/lib/apt/lists/*

# Download Android SDK command line tools
RUN mkdir -p $ANDROID_SDK_ROOT/cmdline-tools && \
    wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline.zip && \
    unzip cmdline.zip -d $ANDROID_SDK_ROOT/cmdline-tools && \
    mv $ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools $ANDROID_SDK_ROOT/cmdline-tools/latest && \
    rm cmdline.zip

# Accept licenses
RUN yes | sdkmanager --licenses

# Install required SDK packages
RUN sdkmanager \
    "platform-tools" \
    "platforms;android-35" \
    "build-tools;35.0.0"

# --- Layer 1: Gradle wrapper + config (changes rarely) ---
COPY gradlew gradlew
COPY gradle/ gradle/
COPY gradle.properties gradle.properties
COPY build.gradle build.gradle
COPY settings.gradle settings.gradle
COPY app/build.gradle app/build.gradle
RUN chmod +x gradlew

# --- Layer 2: Download dependencies (cached unless build.gradle changes) ---
RUN ./gradlew dependencies --no-daemon 2>/dev/null || true

# --- Layer 3: Source code (changes frequently) ---
COPY app/src/ app/src/

# Default: run tests then build APK
CMD ./gradlew testDebugUnitTest assembleDebug --no-daemon --build-cache && \
    mkdir -p /out && \
    cp app/build/outputs/apk/debug/app-debug.apk /out/ && \
    echo "Build complete: /out/app-debug.apk"
