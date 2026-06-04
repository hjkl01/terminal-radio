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

build:
	cargo build

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

install:
	cargo install --path .

release:
	cargo build --release

watch:
	cargo watch -x run