.DEFAULT_GOAL := help

GRADLE_VERSION := 8.2
GRADLE_ZIP := gradle-$(GRADLE_VERSION)-bin.zip
GRADLE_MIRROR_URL := https://mirrors.aliyun.com/gradle/distributions/v$(GRADLE_VERSION).0/$(GRADLE_ZIP)

VERSION := $(shell sed -n 's/.*versionName = "\(.*\)"/\1/p' android/app/build.gradle.kts)
APK_SRC := $(CURDIR)/android/app/build/outputs/apk/debug/app-debug.apk
APK_DST := $(CURDIR)/TerminalRadio-v$(VERSION).apk

help:
	@echo "Available targets:"
	@echo "  docker - Build Android APK via Docker"
	@echo "  build  - Build Android APK locally with Gradle Wrapper"

.PHONY: help docker build

# Build Android APK using Docker with Gradle and Android SDK
docker:
	@test -n "$(VERSION)" || (echo "Error: versionName not found in build.gradle.kts" && exit 1)
	@set -e; \
	mem=$$(docker info --format '{{.MemTotal}}' 2>/dev/null || echo 0); \
	if [ "$$mem" -gt 0 ] && [ "$$mem" -lt 4294967296 ]; then \
		echo "Error: Docker runtime has only $$((mem / 1073741824))GiB of memory."; \
		echo "Android Gradle builds need at least 4GiB, otherwise the Gradle daemon"; \
		echo "is killed and the build fails with 'Gradle build daemon disappeared unexpectedly'."; \
		echo "For Colima: colima stop && colima start --memory 6 --cpu 4"; \
		exit 1; \
	fi; \
	downloaded=0; \
	cleanup() { \
		if [ "$$downloaded" = "1" ]; then rm -f "$(GRADLE_ZIP)"; fi; \
	}; \
	trap cleanup EXIT INT TERM; \
	if [ -f "$(GRADLE_ZIP)" ]; then \
		echo "Using local $(GRADLE_ZIP)"; \
	else \
		echo "$(GRADLE_ZIP) not found, downloading from Alibaba Cloud mirror..."; \
		curl -fL --retry 3 "$(GRADLE_MIRROR_URL)" -o "$(GRADLE_ZIP)"; \
		downloaded=1; \
	fi; \
	docker build -f Dockerfile.android -t terminal-radio-android .; \
	docker run --rm \
		-v "$(CURDIR)":/workspace \
		terminal-radio-android \
		gradle assembleDebug
	cp "$(APK_SRC)" "$(APK_DST)"

build:
	@test -n "$(VERSION)" || (echo "Error: versionName not found in build.gradle.kts" && exit 1)
	cd android && ./gradlew assembleDebug
	cp "$(APK_SRC)" "$(APK_DST)"