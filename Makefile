.DEFAULT_GOAL := help

help:
	@echo "Available targets:"
	@echo "  build     - Build the project (debug)"
	@echo "  run       - Build and run the project"
	@echo "  test      - Run tests"
	@echo "  clippy    - Run clippy lints"
	@echo "  fmt       - Format code"
	@echo "  check     - Run fmt, clippy, and test"
	@echo "  clean     - Clean build artifacts"
	@echo "  install   - Install locally"
	@echo "  release   - Build release binary"
	@echo "  watch     - Run with cargo-watch"

.PHONY: help build run test clippy clean install fmt check

BINARY_NAME := terminal-radio

build:
	cargo build
	cp target/debug/$(BINARY_NAME) ./$(BINARY_NAME)

run:
	cargo run

test:
	cargo test

clippy:
	cargo clippy

fmt:
	cargo fmt

check: fmt clippy test

clean:
	cargo clean
	 rm -f ./$(BINARY_NAME)

install:
	cargo install --path .

release:
	cargo build --release
	cp target/release/$(BINARY_NAME) ./$(BINARY_NAME)

watch:
	cargo watch -x run