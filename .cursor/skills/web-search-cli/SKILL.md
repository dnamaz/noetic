---
name: noetic
description: Search the web, crawl pages, extract PDF content, chunk text, and query a local vector cache using Noetic. Use when the user asks to search the internet, fetch a web page, extract content from a URL, build a knowledge base, research a topic, or find cached information.
---

# Noetic

Local web search, crawling, chunking, and vector cache. All output is JSON. Each command runs as a one-shot CLI invocation and exits when done.

## Base Command

Every command follows this pattern:

```bash
noetic --websearch.adapter.default-mode=cli <command> [args]
```

No server to start. No curl. No setup. Just run and read the JSON output.

## Commands

### Search the Web

```bash
noetic --websearch.adapter.default-mode=cli search "YOUR QUERY" --max-results=5
```

Options: `--freshness=day|week|month|year`, `--language=en`, `--include-domains=example.com,other.com`

Returns: `{provider, results: [{title, url, snippet}], responseTime, fromCache}`

### Crawl a Web Page

```bash
noetic --websearch.adapter.default-mode=cli crawl "https://example.com"
```

Options: `--fetch-mode=auto|static|dynamic` (default: auto), `--include-links`, `--include-images`

Automatically detects and extracts PDF content.

Returns: `{url, title, content, rawHtml, links, images, wordCount, statusCode, fetcherUsed, fetchTime}`

### Chunk and Cache Content

```bash
noetic --websearch.adapter.default-mode=cli chunk --content "TEXT TO CHUNK" --source-url="https://source.url"
```

Options: `--strategy=sentence|token|semantic` (default: sentence), `--max-chunk-size=512`, `--overlap=50`

Content can also be piped via stdin instead of `--content`.

Returns: `[{chunkId, text, tokenCount, embeddingStored}]`

### Query the Vector Cache

```bash
noetic --websearch.adapter.default-mode=cli cache "YOUR QUERY" --top-k=5
```

Options: `--threshold=0.0` (minimum similarity score)

Returns previously cached content ranked by semantic similarity.

Returns: `[{id, score, content, metadata}]`

### Discover Sitemap

```bash
noetic --websearch.adapter.default-mode=cli sitemap "example.com" --max-urls=50
```

Options: `--path-filter=REGEX`

Parses robots.txt and sitemap XML to discover crawlable URLs.

### Batch Crawl

```bash
noetic --websearch.adapter.default-mode=cli batch-crawl --urls="https://example.com,https://example.org"
```

Or with domain sitemap discovery:

```bash
noetic --websearch.adapter.default-mode=cli batch-crawl --domain="example.com" --max-urls=10
```

Options: `--fetch-mode=auto`, `--chunk-strategy=sentence`, `--max-concurrency=3`, `--rate-limit=1000`, `--path-filter=REGEX`

## Workflows

### Deep Research

1. Search: `noetic --websearch.adapter.default-mode=cli search "topic"`
2. Crawl: `noetic --websearch.adapter.default-mode=cli crawl "URL"`
3. Chunk: `noetic --websearch.adapter.default-mode=cli chunk --content "..." --source-url="URL"`
4. Query: `noetic --websearch.adapter.default-mode=cli cache "synthesis query"`

### Build Knowledge Base

1. Discover: `noetic --websearch.adapter.default-mode=cli sitemap "example.com"`
2. Crawl all: `noetic --websearch.adapter.default-mode=cli batch-crawl --domain="example.com" --max-urls=50`
3. Query: `noetic --websearch.adapter.default-mode=cli cache "query"`

### Extract Data from a Page

1. Crawl: `noetic --websearch.adapter.default-mode=cli crawl "https://example.com"`
2. Content is returned as clean markdown -- parse/extract what you need

## Notes

- Native binary starts in ~100ms. First embedding request downloads the ONNX model (~23MB) and vocabulary (~231KB) from Hugging Face, cached afterward at `~/.websearch/models/`.
- Subsequent embedding requests are sub-second. JVM mode has ~2s startup + ~5s first embedding warmup.
- DuckDuckGo may rate-limit after many rapid searches. Space requests or use `crawl` directly if you have URLs.
- PDF files are automatically detected and text-extracted via PDFBox.
- Vector cache persists at `~/.websearch/index/`. Delete this directory to reset.
- All log output goes to stderr; only JSON goes to stdout.
