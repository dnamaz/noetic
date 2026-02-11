class Noetic < Formula
  desc "Web search, crawl, and knowledge cache for AI coding assistants"
  homepage "https://github.com/dnamaz/noetic"
  version "0.2.0"
  license "MIT"

  on_macos do
    url "https://github.com/dnamaz/noetic/releases/download/v#{version}/noetic-#{version}-macos-arm64.tar.gz"
    sha256 "e9ba114191f477152391ab8ac8c3f9d23634432701bbd880a16e49977b87229a"
  end

  on_linux do
    if Hardware::CPU.arm?
      url "https://github.com/dnamaz/noetic/releases/download/v#{version}/noetic-#{version}-linux-arm64.tar.gz"
      sha256 "d4f160b44a83db5baebed5b8968019f342ababcf3ad6444ff329ca7d13d52cf5"
    else
      url "https://github.com/dnamaz/noetic/releases/download/v#{version}/noetic-#{version}-linux-x86_64.tar.gz"
      sha256 "e5977b5065747c40e2c64f9000b67a515db079395ed7d7876ff63d917c455613"
    end
  end

  def install
    # Single native binary (GraalVM native image)
    bin.install "noetic"

    # Shell wrapper scripts for service management
    bin.install "noetic-start" if File.exist?("noetic-start")
    bin.install "noetic-stop"  if File.exist?("noetic-stop")

    # MCP server launcher for IDE integration
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
