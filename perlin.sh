#!/bin/bash

# Perlin Noise Landscape Generator - Managing Script

set -e  # Exit on error

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔═══════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  Perlin Noise Procedural Landscape Generator  ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════╝${NC}"
echo ""

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to find kotlinc
find_kotlinc() {
    # Try system kotlinc first
    if command_exists kotlinc; then
        echo "kotlinc"
        return 0
    fi
    
    # Try IntelliJ IDEA's kotlinc
    local IDEA_KOTLINC="$HOME/.local/share/JetBrains/Toolbox/apps/intellij-idea-ultimate/plugins/Kotlin/kotlinc/bin/kotlinc"
    if [ -f "$IDEA_KOTLINC" ]; then
        echo "$IDEA_KOTLINC"
        return 0
    fi
    
    # Try other common locations
    local COMMON_PATHS=(
        "$HOME/.local/share/JetBrains/apps/IDEA-U/plugins/Kotlin/kotlinc/bin/kotlinc"
        "/usr/local/bin/kotlinc"
        "/opt/kotlinc/bin/kotlinc"
    )
    
    for path in "${COMMON_PATHS[@]}"; do
        if [ -f "$path" ]; then
            echo "$path"
            return 0
        fi
    done
    
    return 1
}

find_java() {
    if command_exists java; then
        echo "java"
        return 0
    fi
    
    echo -e "${RED}✗ Java not found!${NC}"
    echo ""
    echo "Java is required to run this application."
    echo "Please install Java 8 or higher:"
    echo ""
    echo "  • Ubuntu/Debian:  sudo apt install openjdk-11-jdk"
    echo "  • Fedora/RHEL:    sudo dnf install java-11-openjdk"
    echo "  • macOS:          brew install openjdk@11"
    echo "  • Or download from: https://jdk.java.net/"
    echo ""
    return 1
}

# Check flags
BUILD_ONLY=false
RUN_ONLY=false
FORCE_BUILD=false

for arg in "$@"; do
    case $arg in
        --build-only|-b)
            BUILD_ONLY=true
            ;;
        --run-only|-r)
            RUN_ONLY=true
            ;;
        --force-build|-f)
            FORCE_BUILD=true
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --build-only, -b     Only build, don't run"
            echo "  --run-only, -r       Only run (skip build if jar exists)"
            echo "  --force-build, -f    Force rebuild even if jar exists"
            echo "  --help, -h           Show this help message"
            echo ""
            echo "With no options: Build (if needed) and run"
            exit 0
            ;;
    esac
done

# Find Java
JAVA=$(find_java) || exit 1

NEED_BUILD=false

# Check if we need to build
if [ "$RUN_ONLY" = false ]; then
    if [ "$FORCE_BUILD" = true ] || [ ! -f "perlin.jar" ]; then
        NEED_BUILD=true
    else
        # Check if source files are newer than jar
        if [ -d "src" ]; then
            for src_file in src/*.kt; do
                if [ "$src_file" -nt "perlin.jar" ]; then
                    NEED_BUILD=true
                    echo -e "${YELLOW}⚠ Source files modified, rebuilding...${NC}"
                    break
                fi
            done
        fi
    fi
fi

# Build if needed
if [ "$NEED_BUILD" = true ]; then
    echo -e "${BLUE}Building application...${NC}"
    echo ""
    
    # Find kotlinc
    KOTLINC=$(find_kotlinc)
    if [ ! "$KOTLINC" ]; then
        echo -e "${RED}✗ Kotlin compiler (kotlinc) not found!${NC}"
        echo ""
        echo "The Kotlin compiler is required to build this application."
        echo ""
        echo "Installation options:"
        echo ""
        echo "1. Install IntelliJ IDEA (includes Kotlin):"
        echo "   https://www.jetbrains.com/idea/download/"
        echo ""
        echo "2. Install Kotlin compiler manually:"
        echo "   • Using SDKMAN:   sdk install kotlin"
        echo "   • Using Homebrew: brew install kotlin"
        echo "   • Manual install: https://kotlinlang.org/docs/command-line.html"
        echo ""
        echo "3. Or open this project in IntelliJ IDEA and build from there."
        echo ""
        exit 1
    fi
    
    echo -e "Using Kotlin compiler: ${GREEN}$KOTLINC${NC}"
    echo ""
    
    # Compile
    echo "Compiling source files..."
    if "$KOTLINC" src/*.kt -include-runtime -d perlin.jar 2>&1; then
        echo ""
        echo -e "${GREEN}✓ Build successful!${NC}"
        echo -e "JAR file created: ${GREEN}perlin.jar${NC}"
    else
        echo ""
        echo -e "${RED}✗ Build failed!${NC}"
        echo ""
        echo "Please check the error messages above for details."
        exit 1
    fi
    echo ""
elif [ -f "perlin.jar" ]; then
    echo -e "${GREEN}✓ Using existing jar file: perlin.jar${NC}"
    echo ""
fi

# Exit if build-only mode
if [ "$BUILD_ONLY" = true ]; then
    echo "Build complete. Use --run-only to run the application."
    exit 0
fi

# Run the application
if [ -f "perlin.jar" ]; then
    echo -e "${BLUE}Starting Perlin Noise Landscape Generator...${NC}"
    echo ""
    echo -e "${YELLOW}Controls:${NC}"
    echo "  • Drag mouse to rotate and tilt camera"
    echo "  • Scroll wheel to zoom in/out"
    echo "  • Click 'Regenerate' button for new terrain"
    echo ""
    echo -e "${GREEN}Launching...${NC}"
    echo ""
    
    "$JAVA" -jar perlin.jar
    
    EXIT_CODE=$?
    echo ""
    if [ $EXIT_CODE -eq 0 ]; then
        echo -e "${GREEN}Application closed normally.${NC}"
    else
        echo -e "${YELLOW}Application exited with code: $EXIT_CODE${NC}"
    fi
else
    echo -e "${RED}✗ perlin.jar not found!${NC}"
    echo "Please run with --force-build to build the application first."
    exit 1
fi
