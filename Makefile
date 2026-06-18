.DEFAULT_GOAL := help

help:
	@echo "Available targets:"
	@echo "  docker    - Build Android APK via Docker"
	@echo "  android   - Build Android APK (native Gradle)"
	@echo "  build     - Build Android APK locally"

.PHONY: help docker android build

# Build Android APK using Docker with Gradle and Android SDK
docker:
	docker build -f Dockerfile.android -t terminal-radio-android .
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
