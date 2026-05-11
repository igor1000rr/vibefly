# Makefile для VibeFly agent.
# Использование:
#   make build    — собрать бинарник в bin/vibefly-agent
#   make run      — запустить локально на 127.0.0.1:3001
#   make test     — go test ./...
#   make tidy     — go mod tidy
#   make arm64    — кросс-сборка под ARM64 (для rootfs телефона)

VERSION ?= 0.0.1-dev
LDFLAGS := -s -w -X main.Version=$(VERSION)
BIN := bin/vibefly-agent

.PHONY: build run test tidy arm64 clean

build:
	go build -ldflags "$(LDFLAGS)" -o $(BIN) ./cmd/agent

run: build
	./$(BIN) --config config/agent.example.toml

test:
	go test -race -count=1 ./...

tidy:
	go mod tidy

arm64:
	GOOS=linux GOARCH=arm64 CGO_ENABLED=0 \
		go build -ldflags "$(LDFLAGS)" -o bin/vibefly-agent-arm64 ./cmd/agent

clean:
	rm -rf bin/
