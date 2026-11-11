GRADLE   := ./gradlew
VERSIONS := 1.21.4 1.21.11 26.2

# Optional: set PLUGINS_DIR to a rusherhack/plugins folder to use `make install`
PLUGINS_DIR ?=

.DEFAULT_GOAL := build
.PHONY: build test clean install help $(addprefix build-,$(VERSIONS))

build: ## Build jars for every Minecraft version
	$(GRADLE) build

$(addprefix build-,$(VERSIONS)): build-%: ## Build a single version, e.g. make build-26.2
	$(GRADLE) :$*:build

test: ## Run tests for every Minecraft version
	$(GRADLE) test

clean: ## Remove build outputs
	$(GRADLE) clean

install: build ## Copy built jars into PLUGINS_DIR (make install PLUGINS_DIR=...)
	@test -n "$(PLUGINS_DIR)" || { echo "Set PLUGINS_DIR=/path/to/rusherhack/plugins"; exit 1; }
	@for v in $(VERSIONS); do \
		for jar in $$v/build/libs/*.jar; do \
			case $$jar in *-sources.jar|*-javadoc.jar|*-dev.jar) continue;; esac; \
			echo "install $$jar"; cp "$$jar" "$(PLUGINS_DIR)/"; \
		done; \
	done

help: ## Show this help
	@grep -hE '^[a-zA-Z0-9_%-]+:.*## ' $(MAKEFILE_LIST) | \
		awk -F':.*## ' '{printf "  %-14s %s\n", $$1, $$2}'
