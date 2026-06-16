.DEFAULT_GOAL := help

help:
	@echo "Available targets:"
	@echo "  docker    - Build Android APK via Docker"
	@echo "  android   - Build Android APK (native Gradle)"

.PHONY: help docker android

# Build Android APK using Docker with Gradle and Android SDK
docker:
	docker build -f Dockerfile.android -t terminal-radio-android .
	docker run --rm \
		--user "$(uid):$(gid)" \
		-v $(shell pwd):/workspace \
		terminal-radio-android \
		./gradlew assembleDebug
	cp $(shell pwd)/android/app/build/outputs/apk/debug/app-debug.apk ./

android:
	cd android && ./gradlew assembleDebug
