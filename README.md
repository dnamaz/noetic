# Noetic

Web search, crawl, and knowledge cache for AI coding assistants.

Noetic gives your AI agent the ability to search the web, crawl pages, extract content, and build a local semantic knowledge base -- all without API keys. It runs as an MCP server, REST API, or CLI tool.

## Features

- **Web search** via DuckDuckGo (no API key required), Brave, SerpAPI, or Tavily
- **Page crawling** with static (Jsoup) and dynamic (headless Chromium) fetchers
- **PDF extraction** via Apache PDFBox -- automatically detected
- **Semantic caching** with local ONNX embeddings and vector search
- **Per-project namespace isolation** -- one instance serves multiple projects without cross-contamination
- **MCP server** for direct integration with AI coding assistants (Cursor, Claude Code, Windsurf, etc.)
- **REST API** for language-agnostic HTTP access
- **CLI** for scripting and one-shot commands
- **Install skill** command for 11 AI coding environments (Cursor, Antigravity, Droid, Claude Code, OpenHands, Copilot, Windsurf, Cline, Roo, Kilo, Mistral Vibe)
- **Pluggable providers** for search, embeddings, and vector storage
- **SOCKS5/HTTP proxy** support with stream isolation for privacy
- **GraalVM native image** -- compiles to a single native binary with ~100ms startup

## Install

### Homebrew (macOS)

```bash
brew tap dnamaz/tap
brew install noetic
```

### Manual Install

Download the binary for your platform from [GitHub Releases](https://github.com/dnamaz/noetic/releases), extract, and add to your PATH:

```bash
tar -xzf noetic-0.1.0-macos-arm64.tar.gz -C ~/.local/bin/
```

### Build from Source

```bash
# Prerequisites: Java 25+ (GraalVM recommended), Gradle 9+ (wrapper included)

# Fat JAR
./gradlew bootJar

# Native binary (requires GraalVM)
./gradlew nativeCompile
```

## Quick Start

### MCP Server (default)

```bash
noetic
```

The MCP server uses STDIO transport -- point your AI assistant's MCP config at the binary.

### REST API Server

```bash
noetic --websearch.adapter.default-mode=rest --server.port=8090
```

### CLI (one-shot commands)

```bash
noetic --websearch.adapter.default-mode=cli search "your query"
noetic --websearch.adapter.default-mode=cli crawl "https://example.com"
noetic --websearch.adapter.default-mode=cli cache "your query" --top-k=5
```

### Docker

```bash
docker run -p 8080:8080 ghcr.io/dnamaz/noetic:latest
```

## Install AI Assistant Instructions

Generate instruction files for your AI coding assistant with a single command:

```bash
# Install for Cursor (default)
noetic install-skill

# Install for a specific target
noetic install-skill --target=claude-code

# Install into a specific project directory
noetic install-skill --target=cursor --project-dir=/path/to/my-project

# Custom port
noetic install-skill --target=cursor --port=9090

# List all supported targets
noetic install-skill --list
```

Supported targets:

| Target | Output Path |
|---|---|
| `cursor` | `.cursor/skills/noetic/SKILL.md` |
| `antigravity` | `.agent/skills/noetic/SKILL.md` |
| `droid` | `.factory/skills/noetic/SKILL.md` |
| `claude-code` | `CLAUDE.md` |
| `openhands` | `AGENTS.md` |
| `copilot` | `.github/copilot-instructions.md` |
| `windsurf` | `.windsurfrules` |
| `cline` | `.clinerules/noetic.md` |
| `roo` | `.roo/rules/noetic.md` |
| `kilo` | `.kilocode/skills/noetic/SKILL.md` |
| `vibe` | `.vibe/prompts/noetic.md` |

For targets that support project-level MCP configuration (Cursor, Kilo), the command also generates the MCP server config file (e.g. `.cursor/mcp.json`, `.kilocode/mcp.json`).

For Mistral Vibe, add the MCP server to your `.vibe/config.toml`:

```toml
[[mcp_servers]]
name = "noetic"
transport = "stdio"
command = "noetic"
args = ["--spring.profiles.active=stdio", "--spring.main.banner-mode=off"]
```

The generated instructions include setup commands, all API endpoints, and workflow examples -- all pre-configured with resolved paths and port for the target project.

## Prioritizing Noetic Over Built-in Search

Most AI coding assistants ship with built-in web search and fetch tools. When Noetic is installed as an MCP server, the assistant has access to **both** sets of tools and may still default to its built-in ones. To ensure the assistant uses Noetic's MCP tools (`web_search`, `crawl_page`, `cache_query`, etc.), you need to create an explicit instruction or rule for your environment.

### Why This Matters

Built-in search tools are convenient but limited -- they don't cache results, can't build a knowledge base, and often return AI-summarized content that loses detail or accuracy. Noetic's MCP tools give the assistant semantic caching, page crawling, PDF extraction, and a persistent vector store that improves with use.

### How to Configure by Environment

Each AI assistant has its own mechanism for persistent instructions. Create the appropriate file with a directive to prefer Noetic:

#### Cursor

Create a rule file at `.cursor/rules/prefer-noetic.mdc`:

```markdown
---
description: Prefer Noetic MCP tools over built-in search for web lookups
alwaysApply: true
---

# Prefer Noetic MCP Tools for Web Search

When the user asks to search the web, look something up online, or fetch web content:

- Always prefer the Noetic MCP tools (web_search, crawl_page, cache_query, etc.) over built-in WebSearch and WebFetch tools.
- Only fall back to built-in search tools if the Noetic MCP tools are unavailable or return errors.
```

#### Claude Code

Append to `CLAUDE.md`:

```markdown
## Web Search

Always use the Noetic MCP tools (web_search, crawl_page, cache_query) for web searches and page fetching instead of any built-in search tools. Fall back to built-in tools only if Noetic is unavailable.
```

#### Windsurf

Append to `.windsurfrules`:

```markdown
## Web Search

Always use the Noetic MCP tools (web_search, crawl_page, cache_query) for web searches and page fetching instead of any built-in search tools. Fall back to built-in tools only if Noetic is unavailable.
```

#### Copilot

Append to `.github/copilot-instructions.md`:

```markdown
## Web Search

Always use the Noetic MCP tools (web_search, crawl_page, cache_query) for web searches and page fetching instead of any built-in search tools. Fall back to built-in tools only if Noetic is unavailable.
```

#### Cline / Roo Code / Kilo Code

Add the same directive to the target's instruction file (`.clinerules/noetic.md`, `.roo/rules/noetic.md`, or `.kilocode/skills/noetic/SKILL.md`). These environments route all tool calls through MCP, so the directive is usually only needed if the environment also exposes its own web tools.

### General Pattern

The key principle is the same across all environments: **tell the assistant which tools to prefer**. The instruction should:

1. Name the Noetic MCP tools explicitly (`web_search`, `crawl_page`, `cache_query`, etc.)
2. State they should be preferred over any built-in alternatives
3. Specify fallback behavior (use built-in only if Noetic is unavailable)

If your environment supports an "always apply" flag (like Cursor rules), use it so the directive applies to every conversation.

## REST API

All endpoints are under `/api/v1`. Start the server, then:

### Search

```bash
curl -s -X POST http://localhost:8090/api/v1/search \
  -H "Content-Type: application/json" \
  -d '{"query":"your query","maxResults":5,"skipCache":false}'
```

### Crawl

```bash
curl -s -X POST http://localhost:8090/api/v1/crawl \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com","fetchMode":"static"}'
```

Fetch modes: `auto`, `static`, `dynamic`. Add `"includeLinks": true` or `"includeImages": true` for extras.

### Chunk and Cache

```bash
curl -s -X POST http://localhost:8090/api/v1/chunk \
  -H "Content-Type: application/json" \
  -d '{"content":"text to chunk","strategy":"sentence","maxChunkSize":512,"sourceUrl":"https://source.url"}'
```

Strategies: `sentence`, `token`, `semantic`.

### Query Cache

```bash
curl -s -X POST http://localhost:8090/api/v1/cache \
  -H "Content-Type: application/json" \
  -d '{"query":"your query","topK":5}'
```

### Cache Management

```bash
# Evict expired entries (TTL-based)
curl -s -X POST http://localhost:8090/api/v1/cache/evict

# Flush entire cache
curl -s -X DELETE http://localhost:8090/api/v1/cache
```

### Sitemap Discovery

```bash
curl -s -X POST http://localhost:8090/api/v1/sitemap \
  -H "Content-Type: application/json" \
  -d '{"domain":"example.com","maxUrls":50}'
```

### Site Map (BFS)

```bash
curl -s -X POST http://localhost:8090/api/v1/map \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com","maxDepth":2,"maxUrls":20}'
```

### Batch Crawl

```bash
curl -s -X POST http://localhost:8090/api/v1/batch-crawl \
  -H "Content-Type: application/json" \
  -d '{"urls":["https://example.com"],"fetchMode":"static","chunkStrategy":"sentence","maxConcurrency":3}'
```

### Async Jobs

```bash
# Submit
curl -s -X POST http://localhost:8090/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{"urls":["https://example.com"],"fetchMode":"static"}'

# Status
curl -s http://localhost:8090/api/v1/jobs/{jobId}

# Cancel
curl -s -X DELETE http://localhost:8090/api/v1/jobs/{jobId}

# List all
curl -s http://localhost:8090/api/v1/jobs
```

## Per-Project Namespace Isolation

A single Noetic instance can serve multiple projects. Each project's cached data is isolated via namespaces so searches and chunks from one project don't leak into another.

### How It Works

Namespace is resolved via a priority chain:

1. **Explicit parameter** -- `namespace` field in request body or `?namespace=` query param
2. **HTTP header** -- `X-Noetic-Project` header (project path or custom name)
3. **MCP context** -- workspace root from MCP `roots` capability (auto-detected)
4. **Config default** -- `websearch.store.namespace` (default: `"default"`)

Long project paths are hashed to short deterministic IDs (e.g. `proj-a1b2c3d4`).

### Examples

```bash
# Explicit namespace in body
curl -X POST http://localhost:8090/api/v1/search \
  -H "Content-Type: application/json" \
  -d '{"query":"spring boot","namespace":"my-project"}'

# Namespace via header
curl -X POST http://localhost:8090/api/v1/search \
  -H "Content-Type: application/json" \
  -H "X-Noetic-Project: my-project" \
  -d '{"query":"spring boot"}'

# No namespace -- uses config default ("default")
curl -X POST http://localhost:8090/api/v1/search \
  -H "Content-Type: application/json" \
  -d '{"query":"spring boot"}'
```

MCP tools accept an optional `namespace` parameter. When omitted, the namespace is auto-resolved from the MCP client's workspace root.

## MCP Tools

When running as an MCP server, Noetic exposes these tools:

| Tool | Description |
|---|---|
| `web_search` | Search the internet |
| `crawl_page` | Fetch and extract web page content |
| `chunk_content` | Split content into chunks and cache |
| `cache_query` | Search the local vector cache |
| `cache_evict` | Remove expired cache entries |
| `cache_flush` | Delete all cache entries |
| `batch_crawl` | Crawl multiple URLs or a domain |
| `discover_sitemap` | Find crawlable URLs from a domain |
| `map_site` | Discover URLs via BFS link crawling |
| `job_status` | Check async job status |
| `job_cancel` | Cancel an async job |

Plus workflow prompts: `deep_research`, `build_knowledge_base`, `extract_structured_data`, `compare_sources`, `ingest_website`, `monitor_page`.

## Configuration

Configuration is via `application.yml` or environment variables. All `websearch.*` properties map to `WEBSEARCH_*` env vars via Spring Boot's relaxed binding.

### Operational Mode

```yaml
websearch:
  adapter:
    default-mode: mcp          # mcp | rest | cli
```

### Search Provider

```yaml
websearch:
  search:
    active: scraping           # scraping | brave | serp | tavily
    scraping:
      rate-limit-ms: 1000
    brave:
      api-key: ${BRAVE_API_KEY:}
    serp:
      api-key: ${SERP_API_KEY:}
    tavily:
      api-key: ${TAVILY_API_KEY:}
```

### Embedding Provider

```yaml
websearch:
  embedding:
    active: onnx               # onnx | openai | cohere | voyage | bedrock | azure-openai | vertex
```

The default `onnx` provider uses a local all-MiniLM-L6-v2 model (384 dimensions) -- no API key needed. The model (~23MB) and vocabulary (~231KB) download from Hugging Face on first use and are cached at `~/.websearch/models/`.

### Vector Store

```yaml
websearch:
  store:
    active: lucene             # lucene | pinecone | qdrant | weaviate | milvus
    namespace: default         # default namespace for cache isolation
    lucene:
      index-path: ${user.home}/.websearch/index
```

### Proxy

```yaml
websearch:
  proxy:
    enabled: false
    type: SOCKS5               # NONE | HTTP | SOCKS4 | SOCKS5
    host: 127.0.0.1
    port: 9050
    rotation:
      enabled: true            # SOCKS5 stream isolation for privacy
      every-n-requests: 20
      on-empty-results: true
```

Or via environment variables:

```bash
WEBSEARCH_PROXY_ENABLED=true \
WEBSEARCH_PROXY_TYPE=SOCKS5 \
WEBSEARCH_PROXY_HOST=127.0.0.1 \
WEBSEARCH_PROXY_PORT=9050 \
./noetic --server.port=8090
```

### Cache Eviction

```yaml
websearch:
  eviction:
    enabled: true
    schedule: "0 0 * * * *"    # every hour
    max-entries: 100000
    policies:
      search_result:
        ttl: 24h
      query_cache:
        ttl: 6h
      crawl_chunk:
        ttl: 7d
```

Eviction also available on-demand via `POST /api/v1/cache/evict` or `DELETE /api/v1/cache` for a full flush.

## Workflows

### Deep Research

1. Search for the topic
2. Crawl the top result URLs
3. Chunk each page's content with `sourceUrl`
4. Query the cache for a synthesis

### Build Knowledge Base

1. Discover sitemap or map the site
2. Batch crawl all discovered URLs
3. Query the cache to retrieve stored knowledge

### Extract Data from a Page

1. Crawl the page (content returned as clean markdown)
2. Parse/extract what you need from the content

## Architecture

```
noetic
├── adapter/
│   ├── cli/          # Picocli commands (search, crawl, install-skill, ...)
│   ├── mcp/          # MCP tool + prompt definitions
│   └── rest/         # Spring MVC controllers
├── config/           # Native image hints, Spring config
├── model/            # Domain records (SearchRequest, VectorEntry, ProxyConfig, ...)
├── provider/
│   ├── search/       # Search providers (DuckDuckGo, Brave, SerpAPI, Tavily)
│   ├── fetcher/      # Content fetchers (static/Jsoup, dynamic/Chromium, API)
│   ├── embedding/    # Embedding providers (ONNX, OpenAI, Cohere, ...)
│   ├── store/        # Vector stores (Lucene, Pinecone, Qdrant, ...)
│   └── chunking/     # Chunking strategies (sentence, token, semantic)
└── service/          # Business logic (search, crawl, chunk, cache, eviction, namespace)
```

## Native Image

Build a single standalone binary with GraalVM (no JVM required at runtime):

```bash
./gradlew nativeCompile
```

Output: `build/native/nativeCompile/noetic`

```bash
# Run the native binary
./build/native/nativeCompile/noetic --server.port=8090

# With Tor proxy
WEBSEARCH_PROXY_ENABLED=true WEBSEARCH_PROXY_TYPE=SOCKS5 \
./build/native/nativeCompile/noetic --server.port=8090

# With Brave Search
BRAVE_API_KEY=BSA-xxxxxxxx \
./build/native/nativeCompile/noetic --websearch.search.active=brave --server.port=8090
```

Startup time is ~100ms. First embedding request downloads the ONNX model (~23MB) and vocabulary (~231KB) from Hugging Face, cached at `~/.websearch/models/`.

## Notes

- Native binary starts in ~100ms. First embedding request downloads model files, subsequent requests are sub-second.
- JVM mode has ~2s startup + ~5s first embedding warmup.
- The ONNX embedding model (all-MiniLM-L6-v2, 384 dimensions) and vocabulary are cached at `~/.websearch/models/`.
- DuckDuckGo may rate-limit after many rapid searches. Use `skipCache: true` to force live searches, or switch to Brave Search API for higher volume.
- Vector cache persists at `~/.websearch/index/`. Use `DELETE /api/v1/cache` or `POST /api/v1/cache/evict` to manage.
- PDF files are automatically detected and text-extracted when crawled.
- All configuration supports environment variables via Spring Boot's relaxed binding (`websearch.proxy.enabled` -> `WEBSEARCH_PROXY_ENABLED`).

## Tech Stack

- **Java 25** with preview features
- **Spring Boot 4.0**
- **MCP Java SDK 0.17** (official Model Context Protocol)
- **DJL 0.36** + **ONNX Runtime 1.23** for local embeddings (all-MiniLM-L6-v2)
- **Apache Lucene 10** for local vector search (HNSW)
- **Jsoup** for static HTML fetching
- **Jvppeteer** for headless Chromium (dynamic pages)
- **Apache PDFBox** for PDF text extraction
- **Picocli** for CLI
- **GraalVM 25** native image support
