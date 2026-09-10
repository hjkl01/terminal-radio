.DEFAULT_GOAL := help

help:
	@echo "Available targets:"
	@echo "  docker    - Build Android APK via Docker"
	@echo "  android   - Build Android APK (native Gradle)"
	@echo "  build     - Build Android APK locally"

.PHONY: help docker android build

GRADLE_VERSION := 8.2
GRADLE_ZIP := gradle-$(GRADLE_VERSION)-bin.zip
GRADLE_MIRROR_URL := https://mirrors.aliyun.com/gradle/distributions/v$(GRADLE_VERSION).0/$(GRADLE_ZIP)

# Build Android APK using Docker with Gradle and Android SDK
docker:
	@downloaded=0; \
	if [ -f "$(GRADLE_ZIP)" ]; then \
		echo "Using local $(GRADLE_ZIP)"; \
	else \
		echo "$(GRADLE_ZIP) not found, downloading from Alibaba Cloud mirror..."; \
		curl -fL --retry 3 "$(GRADLE_MIRROR_URL)" -o "$(GRADLE_ZIP)" || exit 1; \
		downloaded=1; \
	fi; \
	docker build -f Dockerfile.android -t terminal-radio-android . || { \
		if [ "$$downloaded" = "1" ]; then rm -f "$(GRADLE_ZIP)"; fi; \
		exit 1; \
	}; \
	if [ "$$downloaded" = "1" ]; then rm -f "$(GRADLE_ZIP)"; fi
	docker run --rm \
		--user "$(uid):$(gid)" \
		-v $(shell pwd):/workspace \
		terminal-radio-android \
		./gradlew assembleDebug
	cp $(shell pwd)/android/app/build/outputs/apk/debug/app-debug.apk $(shell pwd)/TerminalRadio-v$(shell grep -oP 'versionName = "\K[^"]+' android/app/build.gradle.kts).apk

build:
	cd android && ./gradlew assembleDebug
	grep -qP 'versionName = "\K[^"]+' android/app/build.gradle.kts || (echo "Error: versionName not found in build.gradle.kts" && exit 1)
	cp $(shell pwd)/android/app/build/outputs/apk/debug/app-debug.apk $(shell pwd)/TerminalRadio-v$(shell grep -oP 'versionName = "\K[^"]+' android/app/build.gradle.kts).apk
