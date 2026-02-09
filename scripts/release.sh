#!/usr/bin/env bash
# release.sh - Build and package Noetic for a GitHub Release.
#
# Produces a release tarball containing the native binary and wrapper script,
# prints the SHA256 hash for the Homebrew formula, and optionally signs the
# binary for macOS distribution.
#
# Usage:
#   ./scripts/release.sh                        # build + package
#   ./scripts/release.sh --sign                  # build + sign + package
#   ./scripts/release.sh --sign --notarize       # build + sign + notarize + package
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

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
VERSION="0.1.0"
DIST_DIR="$PROJECT_DIR/dist"

SIGN=false
NOTARIZE=false

for arg in "$@"; do
    case "$arg" in
        --sign) SIGN=true ;;
        --notarize) NOTARIZE=true; SIGN=true ;;
        --help|-h)
            echo "Usage: $0 [--sign] [--sign --notarize]"
            echo ""
            echo "  --sign        Sign the binary with Apple Developer ID (macOS only)"
            echo "  --notarize    Sign and notarize for full Gatekeeper trust (macOS only)"
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
WRAPPER="$PROJECT_DIR/bin/noetic"

echo "==> Building Noetic $VERSION for $OS_LABEL-$ARCH_LABEL"
echo ""

# --- Build native image ---
echo "--- Step 1: Native compile ---"
cd "$PROJECT_DIR"
./gradlew nativeCompile
echo ""

if [[ ! -x "$BINARY" ]]; then
    echo "Error: native binary not found at $BINARY"
    exit 1
fi

BINARY_SIZE=$(du -h "$BINARY" | cut -f1)
echo "Native binary: $BINARY ($BINARY_SIZE)"
echo ""

# --- Sign (macOS only) ---
if [[ "$SIGN" == true && "$OS_LABEL" == "macos" ]]; then
    IDENTITY="${APPLE_IDENTITY:-Developer ID Application}"

    echo "--- Step 2: Code signing ---"
    codesign --force --options runtime \
        --sign "$IDENTITY" \
        "$BINARY"
    echo "Signed with: $IDENTITY"

    # Verify
    codesign --verify --verbose "$BINARY"
    echo ""

    # --- Notarize (macOS only) ---
    if [[ "$NOTARIZE" == true ]]; then
        echo "--- Step 3: Notarization ---"
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
echo "--- Step $([ "$SIGN" == true ] && echo "4" || echo "2"): Packaging ---"
mkdir -p "$DIST_DIR"

# Create a staging directory with both binary and wrapper
STAGING="$DIST_DIR/staging"
rm -rf "$STAGING"
mkdir -p "$STAGING"

# Install native binary as noetic-bin, wrapper as noetic
cp "$BINARY" "$STAGING/noetic-bin"
cp "$WRAPPER" "$STAGING/noetic"
chmod +x "$STAGING/noetic" "$STAGING/noetic-bin"

# Create tarball
tar -czf "$DIST_DIR/$TARBALL_NAME" -C "$STAGING" noetic noetic-bin
rm -rf "$STAGING"

# --- Checksums ---
CHECKSUM=$(shasum -a 256 "$DIST_DIR/$TARBALL_NAME" | awk '{print $1}')
echo "$CHECKSUM  $TARBALL_NAME" >> "$DIST_DIR/checksums.txt"

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
echo "  1. Upload $TARBALL_NAME to GitHub Release v$VERSION"
echo "  2. Update homebrew-tap/Formula/noetic.rb:"
echo "     - Set version to \"$VERSION\""
echo "     - Set sha256 for $OS_LABEL/$ARCH_LABEL to \"$CHECKSUM\""
echo "  3. Push the homebrew-tap repo"
echo ""
echo "Users can then install with:"
echo "  brew tap dnamaz/tap"
echo "  brew install noetic"
