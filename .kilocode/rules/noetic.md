# Noetic

Local web search, crawling, chunking, and vector cache via CLI commands. All output is JSON.

## Setup

```bash
# Build (only needed once, or after code changes)
cd /Users/alek.mitrevski/Development/dnamaz/web-search-api
./gradlew bootJar
```

**JAR path:** `/Users/alek.mitrevski/Development/dnamaz/web-search-api/build/libs/noetic-0.1.0-SNAPSHOT.jar`

**Java:** `/Users/alek.mitrevski/.local/bin/noetic`

## Base Command

All commands use this prefix (assign to a variable for convenience):

```bash
NOETIC="/Users/alek.mitrevski/.local/bin/noetic --enable-preview -jar /Users/alek.mitrevski/Development/dnamaz/web-search-api/build/libs/noetic-0.1.0-SNAPSHOT.jar --server.port=8090"
```

Then start the server and use curl:

```bash
# Start the REST server (background)
$NOETIC &
sleep 10  # wait for startup

# All commands below use curl against localhost:8090
```

## Commands

### Search the Web

```bash
curl -s -X POST http://localhost:8090/api/v1/search \
  -H "Content-Type: application/json" \
  -d '{"query":"YOUR QUERY","maxResults":5,"freshness":"NONE","safeSearch":false,"searchDepth":"BASIC","skipCache":false,"includeDomains":[],"excludeDomains":[],"extra":{}}'
```

Set `"skipCache":true` to bypass the semantic cache and force a live search.

Returns: `{provider, results: [{title, url, snippet}], responseTime, fromCache}`

### Crawl a Web Page

```bash
curl -s -X POST http://localhost:8090/api/v1/crawl \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com","fetchMode":"static"}'
```

Options: `fetchMode` = auto | static | dynamic. Add `"includeLinks": true` or `"includeImages": true` for extras. Automatically detects and extracts PDF content.

Returns: `{url, title, content, rawHtml, links, images, wordCount, statusCode, fetcherUsed, fetchTime}`

### Chunk and Cache Content

```bash
curl -s -X POST http://localhost:8090/api/v1/chunk \
  -H "Content-Type: application/json" \
  -d '{"content":"TEXT TO CHUNK","strategy":"sentence","maxChunkSize":512,"sourceUrl":"https://source.url"}'
```

Strategies: sentence | token | semantic. Embeds and stores each chunk in the local Lucene vector cache.

Returns: `[{chunkId, text, tokenCount, embeddingStored}]`

### Query the Vector Cache

```bash
curl -s -X POST http://localhost:8090/api/v1/cache \
  -H "Content-Type: application/json" \
  -d '{"query":"YOUR QUERY","topK":5}'
```

Returns previously cached content ranked by semantic similarity.

Returns: `[{id, score, content, metadata}]`

### Evict Expired Cache Entries

```bash
curl -s -X POST http://localhost:8090/api/v1/cache/evict
```

Triggers TTL-based eviction (search results >24h, query cache >6h, crawl chunks >7d). Same as the scheduled hourly job.

Returns: `{entriesBefore, entriesAfter, removed, typesEvicted}`

### Flush Entire Cache

```bash
curl -s -X DELETE http://localhost:8090/api/v1/cache
```

Deletes ALL entries from the vector cache. Destructive -- use for a clean slate.

Returns: `{entriesBefore, entriesAfter, removed, typesEvicted}`

### Discover Sitemap

```bash
curl -s -X POST http://localhost:8090/api/v1/sitemap \
  -H "Content-Type: application/json" \
  -d '{"domain":"example.com","maxUrls":50}'
```

Parses robots.txt and sitemap XML. Optional `pathFilter` regex.

### Map Site (BFS Link Discovery)

```bash
curl -s -X POST http://localhost:8090/api/v1/map \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com","maxDepth":2,"maxUrls":20}'
```

Follows links on the page to discover all reachable same-domain URLs.

### Batch Crawl

```bash
curl -s -X POST http://localhost:8090/api/v1/batch-crawl \
  -H "Content-Type: application/json" \
  -d '{"urls":["https://example.com","https://example.org"],"fetchMode":"static","chunkStrategy":"sentence","maxConcurrency":3,"rateLimitMs":1000}'
```

Or with domain sitemap discovery: `{"domain":"example.com","maxUrls":10}`

### Async Jobs

```bash
# Submit
curl -s -X POST http://localhost:8090/api/v1/jobs \
  -H "Content-Type: application/json" \
  -d '{"urls":["https://example.com"],"fetchMode":"static"}'
# Returns: {jobId: "..."}

# Check status
curl -s http://localhost:8090/api/v1/jobs/JOB_ID

# Cancel
curl -s -X DELETE http://localhost:8090/api/v1/jobs/JOB_ID

# List all
curl -s http://localhost:8090/api/v1/jobs
```

## Workflows

### Deep Research

1. Search for the topic
2. Crawl the top result URLs
3. Chunk each page's content with `sourceUrl`
4. Query the cache for a synthesis

### Build Knowledge Base

1. Discover sitemap or map the site
2. Batch crawl all discovered URLs
3. Cache is now populated -- use cache query to retrieve

### Extract Data from a Page

1. Crawl the page
2. The content is returned as clean markdown
3. Parse/extract what you need from the content directly

## Notes

- First request after startup is slow (~10s for JVM + model warmup). Subsequent requests are fast.
- The ONNX embedding model (all-MiniLM-L6-v2) downloads from Hugging Face on first use (~23MB, cached afterward).
- DuckDuckGo may rate-limit after many rapid searches. Space requests or use `crawl` directly if you have URLs.
- PDF files are automatically detected and text-extracted via PDFBox.
- Vector cache persists at `~/.websearch/index/`. Delete this directory to reset.
