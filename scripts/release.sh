#!/usr/bin/env bash
# release.sh - Build and package Noetic for a GitHub Release.
#
# Produces a release tarball containing the native binary and helper scripts,
# computes SHA256 for the Homebrew formula, and optionally signs the binary.
#
# Usage:
#   ./scripts/release.sh                        # build + package
#   ./scripts/release.sh --sign                  # build + sign + package
#   ./scripts/release.sh --sign --notarize       # build + sign + notarize + package
#   ./scripts/release.sh --skip-build            # package existing binary (no rebuild)
#
# Environment variables:
#   APPLE_IDENTITY    - Code signing identity (default: "Developer ID Application")
#   APPLE_ID          - Apple ID email for notarization
#   APPLE_TEAM_ID     - Apple Developer Team ID
#   APPLE_APP_PWD     - App-specific password for notarization
#
# Output:
#   dist/noetic-<version>-<os>-<arch>.tar.gz
#   dist/checksums.txt
#   Formula/noetic.rb (SHA256 updated for this platform)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
VERSION="0.1.0"
DIST_DIR="$PROJECT_DIR/dist"

SIGN=false
NOTARIZE=false
SKIP_BUILD=false

for arg in "$@"; do
    case "$arg" in
        --sign) SIGN=true ;;
        --notarize) NOTARIZE=true; SIGN=true ;;
        --skip-build) SKIP_BUILD=true ;;
        --help|-h)
            echo "Usage: $0 [--sign] [--notarize] [--skip-build]"
            echo ""
            echo "  --sign        Sign the binary with Apple Developer ID (macOS only)"
            echo "  --notarize    Sign and notarize for full Gatekeeper trust (macOS only)"
            echo "  --skip-build  Package existing binary without rebuilding"
            echo ""
            echo "Set APPLE_IDENTITY, APPLE_ID, APPLE_TEAM_ID, APPLE_APP_PWD for signing/notarization."
            exit 0
            ;;
        *) echo "Unknown argument: $arg"; exit 1 ;;
    esac
done

# --- Detect platform ---
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"
case "$OS" in
    darwin) OS_LABEL="macos" ;;
    linux)  OS_LABEL="linux" ;;
    *)      echo "Unsupported OS: $OS"; exit 1 ;;
esac
case "$ARCH" in
    arm64|aarch64) ARCH_LABEL="arm64" ;;
    x86_64|amd64)  ARCH_LABEL="x86_64" ;;
    *)             echo "Unsupported architecture: $ARCH"; exit 1 ;;
esac

TARBALL_NAME="noetic-${VERSION}-${OS_LABEL}-${ARCH_LABEL}.tar.gz"
BINARY="$PROJECT_DIR/build/native/nativeCompile/noetic"

echo "==> Building Noetic $VERSION for $OS_LABEL-$ARCH_LABEL"
echo ""

# --- Build native image ---
if [[ "$SKIP_BUILD" == false ]]; then
    echo "--- Step 1: Native compile ---"
    cd "$PROJECT_DIR"
    ./gradlew nativeCompile
    echo ""
fi

if [[ ! -x "$BINARY" ]]; then
    echo "Error: native binary not found at $BINARY"
    echo "Run without --skip-build or build first with: ./gradlew nativeCompile"
    exit 1
fi

BINARY_SIZE=$(du -h "$BINARY" | cut -f1)
echo "Native binary: $BINARY ($BINARY_SIZE)"
echo ""

# --- Sign (macOS only) ---
if [[ "$SIGN" == true && "$OS_LABEL" == "macos" ]]; then
    IDENTITY="${APPLE_IDENTITY:-Developer ID Application}"

    echo "--- Code signing ---"
    codesign --force --options runtime \
        --sign "$IDENTITY" \
        "$BINARY"
    echo "Signed with: $IDENTITY"

    # Verify
    codesign --verify --verbose "$BINARY"
    echo ""

    # --- Notarize (macOS only) ---
    if [[ "$NOTARIZE" == true ]]; then
        echo "--- Notarization ---"
        APPLE_ID="${APPLE_ID:?Set APPLE_ID for notarization}"
        APPLE_TEAM_ID="${APPLE_TEAM_ID:?Set APPLE_TEAM_ID for notarization}"
        APPLE_APP_PWD="${APPLE_APP_PWD:?Set APPLE_APP_PWD for notarization}"

        NOTARIZE_ZIP="$DIST_DIR/noetic-notarize.zip"
        mkdir -p "$DIST_DIR"
        ditto -c -k --keepParent "$BINARY" "$NOTARIZE_ZIP"

        echo "Submitting for notarization (this may take a few minutes)..."
        xcrun notarytool submit "$NOTARIZE_ZIP" \
            --apple-id "$APPLE_ID" \
            --team-id "$APPLE_TEAM_ID" \
            --password "$APPLE_APP_PWD" \
            --wait

        rm -f "$NOTARIZE_ZIP"
        echo "Notarization complete."
        echo ""
    fi
elif [[ "$SIGN" == true ]]; then
    echo "--- Skipping code signing (not macOS) ---"
    echo ""
fi

# --- Package ---
echo "--- Packaging ---"
mkdir -p "$DIST_DIR"

STAGING="$DIST_DIR/staging"
rm -rf "$STAGING"
mkdir -p "$STAGING"

# Single native binary
cp "$BINARY" "$STAGING/noetic"
chmod +x "$STAGING/noetic"

# Include helper scripts if they exist
for script in noetic-start noetic-stop; do
    if [[ -f "$PROJECT_DIR/bin/$script" ]]; then
        cp "$PROJECT_DIR/bin/$script" "$STAGING/$script"
        chmod +x "$STAGING/$script"
    fi
done

# Create tarball
tar -czf "$DIST_DIR/$TARBALL_NAME" -C "$STAGING" .
rm -rf "$STAGING"

# --- Checksums ---
CHECKSUM=$(shasum -a 256 "$DIST_DIR/$TARBALL_NAME" | awk '{print $1}')
echo "$CHECKSUM  $TARBALL_NAME" >> "$DIST_DIR/checksums.txt"

# --- Update Formula SHA256 ---
FORMULA="$PROJECT_DIR/Formula/noetic.rb"
if [[ -f "$FORMULA" ]]; then
    echo "--- Updating Formula ---"
    case "${OS_LABEL}-${ARCH_LABEL}" in
        macos-arm64)   PLACEHOLDER="PLACEHOLDER_ARM64_SHA256" ;;
        macos-x86_64)  PLACEHOLDER="PLACEHOLDER_X86_64_SHA256" ;;
        linux-arm64)   PLACEHOLDER="PLACEHOLDER_LINUX_ARM64_SHA256" ;;
        linux-x86_64)  PLACEHOLDER="PLACEHOLDER_LINUX_X86_64_SHA256" ;;
    esac

    if grep -q "$PLACEHOLDER" "$FORMULA"; then
        sed -i '' "s/$PLACEHOLDER/$CHECKSUM/" "$FORMULA" 2>/dev/null || \
        sed -i "s/$PLACEHOLDER/$CHECKSUM/" "$FORMULA"
        echo "Updated Formula: $PLACEHOLDER -> $CHECKSUM"
    else
        echo "Formula placeholder '$PLACEHOLDER' not found (may already be set)"
    fi
    echo ""
fi

echo ""
echo "=========================================="
echo "  Release artifact ready!"
echo "=========================================="
echo ""
echo "  File:     $DIST_DIR/$TARBALL_NAME"
echo "  Size:     $(du -h "$DIST_DIR/$TARBALL_NAME" | cut -f1)"
echo "  SHA256:   $CHECKSUM"
echo ""
echo "Next steps:"
echo "  1. Create GitHub Release:"
echo "     gh release create v$VERSION $DIST_DIR/$TARBALL_NAME --title 'Noetic v$VERSION'"
echo ""
echo "  2. Or push a tag to trigger CI release:"
echo "     git tag v$VERSION && git push --tags"
echo ""
echo "  3. After all platform builds, update Formula with SHA256s and push to tap:"
echo "     brew tap dnamaz/tap"
echo "     brew install noetic"
echo ""
