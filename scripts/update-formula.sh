#!/usr/bin/env bash
# update-formula.sh - Update the Homebrew Formula in dnamaz/homebrew-tap
# with SHA256 checksums from a GitHub Release.
#
# Usage:
#   ./scripts/update-formula.sh              # uses latest release
#   ./scripts/update-formula.sh 0.2.0        # specific version
#
# Requires: gh (GitHub CLI) authenticated with repo access to dnamaz/homebrew-tap

set -euo pipefail

REPO="dnamaz/noetic"
TAP_REPO="dnamaz/homebrew-tap"

# Resolve version
if [[ -n "${1:-}" ]]; then
    VERSION="$1"
else
    VERSION=$(gh release view --repo "$REPO" --json tagName --jq '.tagName' | sed 's/^v//')
    echo "Using latest release: v${VERSION}"
fi

TAG="v${VERSION}"

echo "==> Updating Homebrew Formula for ${TAG}"

# Download checksums from release
CHECKSUMS=$(gh release download "$TAG" --repo "$REPO" --pattern "checksums.txt" --output -)
echo "$CHECKSUMS"
echo ""

# Extract SHA256 for each platform
SHA_MACOS_ARM64=$(echo "$CHECKSUMS" | grep "macos-arm64" | awk '{print $1}')
SHA_LINUX_X86_64=$(echo "$CHECKSUMS" | grep "linux-x86_64" | awk '{print $1}')
SHA_LINUX_ARM64=$(echo "$CHECKSUMS" | grep "linux-arm64" | awk '{print $1}')

if [[ -z "$SHA_MACOS_ARM64" || -z "$SHA_LINUX_X86_64" || -z "$SHA_LINUX_ARM64" ]]; then
    echo "Error: Could not extract all SHA256 values from checksums.txt"
    echo "  macos-arm64:  ${SHA_MACOS_ARM64:-MISSING}"
    echo "  linux-x86_64: ${SHA_LINUX_X86_64:-MISSING}"
    echo "  linux-arm64:  ${SHA_LINUX_ARM64:-MISSING}"
    exit 1
fi

echo "SHA256 values:"
echo "  macos-arm64:  $SHA_MACOS_ARM64"
echo "  linux-x86_64: $SHA_LINUX_X86_64"
echo "  linux-arm64:  $SHA_LINUX_ARM64"
echo ""

# Generate Formula
cat > /tmp/noetic.rb << FORMULA
class Noetic < Formula
  desc "Web search, crawl, and knowledge cache for AI coding assistants"
  homepage "https://github.com/dnamaz/noetic"
  version "${VERSION}"
  license "MIT"

  on_macos do
    url "https://github.com/dnamaz/noetic/releases/download/v#{version}/noetic-#{version}-macos-arm64.tar.gz"
    sha256 "${SHA_MACOS_ARM64}"
  end

  on_linux do
    if Hardware::CPU.arm?
      url "https://github.com/dnamaz/noetic/releases/download/v#{version}/noetic-#{version}-linux-arm64.tar.gz"
      sha256 "${SHA_LINUX_ARM64}"
    else
      url "https://github.com/dnamaz/noetic/releases/download/v#{version}/noetic-#{version}-linux-x86_64.tar.gz"
      sha256 "${SHA_LINUX_X86_64}"
    end
  end

  def install
    bin.install "noetic"
    bin.install "noetic-start" if File.exist?("noetic-start")
    bin.install "noetic-stop"  if File.exist?("noetic-stop")
    bin.install "mcp-server.sh" if File.exist?("mcp-server.sh")
  end

  service do
    run [opt_bin/"noetic",
         "--server.port=8090",
         "--websearch.adapter.default-mode=rest"]
    keep_alive true
    log_path var/"log/noetic.log"
    error_log_path var/"log/noetic.log"
    working_dir var/"noetic"
  end

  def post_install
    (var/"noetic").mkpath
    (var/"log").mkpath
  end

  def caveats
    <<~EOS
      CLI (works immediately, no server needed):
        noetic --websearch.adapter.default-mode=cli search "your query"
        noetic --websearch.adapter.default-mode=cli crawl "https://example.com"

      Start the REST API server as a background service:
        brew services start noetic

      Or run the MCP server (foreground, for IDE integration):
        noetic

      Install AI assistant instructions:
        noetic install-skill --target=cursor
        noetic install-skill --list

      Server logs:  #{var}/log/noetic.log
      Model cache:  ~/.websearch/models/
      Vector cache: ~/.websearch/index/
    EOS
  end

  test do
    assert_match "noetic", shell_output("#{bin}/noetic --version")
    assert_match version.to_s, shell_output("#{bin}/noetic --version")
  end
end
FORMULA

# Push to tap repo
TAP_SHA=$(gh api "repos/${TAP_REPO}/contents/Formula/noetic.rb" --jq '.sha')

gh api "repos/${TAP_REPO}/contents/Formula/noetic.rb" \
    -X PUT \
    -f message="Update Formula for v${VERSION}" \
    -f content="$(base64 < /tmp/noetic.rb)" \
    -f sha="$TAP_SHA" \
    --jq '.commit.html_url'

echo ""
echo "==> Homebrew Formula updated for v${VERSION}"
echo "    brew update && brew upgrade noetic"
