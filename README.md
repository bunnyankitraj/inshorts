# 📰 Contextual News Data Retrieval System

A Spring Boot backend that ingests news articles, uses **OpenAI** to understand
natural-language queries, routes them to the right retrieval strategy, enriches
results with AI-generated summaries, and returns structured JSON. It also ships a
location-aware **trending feed** driven by simulated user engagement.

---

## 🏗️ Tech Stack

| Layer        | Technology                          |
|--------------|-------------------------------------|
| Language     | Java 21                             |
| Framework    | Spring Boot 3.2.5                    |
| Database     | H2 (in-memory, `create-drop`)       |
| ORM          | Spring Data JPA + Hibernate         |
| LLM          | OpenAI (`gpt-4o-mini`)              |
| Caching      | Caffeine (trending feed, 5-min TTL) |
| HTTP Client  | Spring WebFlux `WebClient`          |
| JSON         | Jackson (+ JSR-310)                 |
| Boilerplate  | Lombok                              |
| Build        | Maven                               |

---

## 🚀 Getting Started

### Prerequisites
- Java 21+
- Maven 3.9+ (or use the bundled wrapper if present)

If multiple JDKs are installed, make sure Maven is building with Java 21.

### 1. Navigate to the project
```bash
cd /path/to/Inshorts
```

### 2. Add your OpenAI API key
Create a `.env` file in the project root (it is git-ignored):

```properties
OPENAI_API_KEY=sk-your-openai-api-key-here
```

`application.properties` imports `.env` automatically via
`spring.config.import=optional:file:.env[.properties]`.

> Without a key, the LLM-dependent paths degrade gracefully:
> - `POST /api/v1/query` falls back to a plain text **search** intent.
> - `llm_summary` falls back to a truncated description.

### 3. Run the application
```bash
mvn spring-boot:run
```

The app starts on **`http://localhost:8080`** and on startup automatically:
- Ingests all articles from `src/main/resources/data/news_data.json` (~2000 articles) into H2.
- Seeds **300** simulated user events (deterministic, `Random(42L)`) for the trending feed.

H2 console is available at `http://localhost:8080/h2-console`
(JDBC URL `jdbc:h2:mem:newsdb`, user `sa`, no password).

---

## 📂 Project Structure

```
src/main/java/com/inshorts/news/
├── NewsApplication.java             # Spring Boot entry point
├── config/
│   ├── AppConfig.java               # WebClient.Builder + ObjectMapper beans
│   └── CacheConfig.java             # Caffeine cache manager ("trendingFeed", 5-min TTL)
├── controller/
│   ├── QueryController.java         # POST /api/v1/query   (LLM entry point)
│   ├── NewsController.java          # GET  /api/v1/news/*   (5 direct endpoints)
│   └── TrendingController.java      # GET  /api/v1/trending
├── service/
│   ├── DataIngestionService.java    # @PostConstruct: loads JSON + seeds user events
│   ├── QueryService.java            # Orchestrates analyze → route → enrich → respond
│   ├── LLMService.java              # OpenAI calls: query analysis + summaries
│   ├── NewsService.java             # 5 retrieval strategies + Haversine distance
│   ├── EnrichmentService.java       # Parallel LLM summary generation
│   └── TrendingService.java         # Trending score + geo-cluster caching
├── repository/
│   ├── ArticleRepository.java       # JPQL queries for all 5 strategies
│   └── UserEventRepository.java     # Recent user events
├── model/
│   ├── Article.java                 # Article entity (category stored CSV, exposed as List)
│   └── UserEvent.java               # Simulated engagement event
├── dto/
│   ├── QueryRequest.java            # Body for POST /query
│   ├── ArticleResponse.java         # Output article shape
│   ├── NewsApiResponse.java         # Wrapper: metadata + articles
│   ├── LLMAnalysis.java             # Parsed LLM output (entities, concepts, intent, query)
│   └── ErrorResponse.java           # Consistent error body
├── util/
│   └── NewsRequestValidator.java    # Shared validation + response building
└── exception/
    ├── GlobalExceptionHandler.java  # @RestControllerAdvice — centralized errors
    └── NewsNotFoundException.java    # 404 — no articles found
```

---

## 🔁 Request Pipeline (`POST /api/v1/query`)

```
QueryController
   └─> QueryService.processQuery()
         1. LLMService.analyzeQuery()      → entities, concepts, intent, searchQuery
         2. routeToEndpoint()              → picks one NewsService strategy
         3. EnrichmentService              → parallel LLM summaries
         4. buildResponse()                → { metadata, articles }
```

**Routing priority** (first match wins):
`nearby` → `source` → `category` → `score` → `search` (default).

- `nearby` requires `userLat` + `userLon` in the request body.
- `source` / `category` use the first extracted **entity** (category falls back to the
  first **concept**, then `"General"`).
- `score` uses a fixed threshold of `0.7`.
- `search` uses the LLM-cleaned `searchQuery`.

---

## 🌐 API Endpoints

Base URL: `http://localhost:8080/api/v1`

### 1. `POST /api/v1/query` — Natural-language query ⭐

**Request body:**
```json
{
  "query": "Latest developments near Mumbai",
  "userLat": 19.0760,
  "userLon": 72.8777,
  "radiusKm": 50,
  "limit": 5
}
```
| Field      | Required | Default | Notes                                  |
|------------|----------|---------|----------------------------------------|
| `query`    | ✅       | —       | Must be non-empty                      |
| `userLat`  | optional | —       | Needed for `nearby` routing            |
| `userLon`  | optional | —       | Needed for `nearby` routing            |
| `radiusKm` | optional | `10`    | Used only by `nearby`                  |
| `limit`    | optional | `5`     | Number of results                      |

**Example intents the LLM detects:**
| Query                              | Intent     | Action                              |
|------------------------------------|------------|-------------------------------------|
| "Tech news from BBC"               | `source`   | Fetch from source=BBC               |
| "Sports articles"                  | `category` | Fetch category=sports               |
| "Top relevant articles"            | `score`    | relevance_score ≥ 0.7               |
| "Latest developments near Mumbai"  | `nearby`   | Nearby using user coordinates       |
| "IPL 2025 updates"                 | `search`   | Full-text search                    |

---

### 2. `GET /api/v1/news/category`
Articles whose category contains the term (case-insensitive substring), newest first.
```
GET /api/v1/news/category?category=sports&limit=5
GET /api/v1/news/category?category=technology&limit=5
```
Categories present in the data include: `national`, `world`, `sports`, `politics`,
`business`, `entertainment`, `technology`, `science`, `automobile`, plus topic tags.

### 3. `GET /api/v1/news/score`
Articles with `relevance_score >= threshold`, ranked by score (highest first).
```
GET /api/v1/news/score?threshold=0.7&limit=5
```
| Param       | Default | Range     |
|-------------|---------|-----------|
| `threshold` | `0.7`   | 0.0–1.0   |
| `limit`     | `5`     | 1–50      |

### 4. `GET /api/v1/news/search`
Full-text search over title + description. Results are re-ranked by a combined score:

```
combined_score = 0.5 * relevance_score + 0.5 * text_match_score
text_match_score = 1.0 if title matches, else 0.5 if description matches, else 0.0
```
```
GET /api/v1/news/search?query=Elon+Musk&limit=5
GET /api/v1/news/search?query=IPL+2025&limit=5
```
> The returned `relevance_score` is the **combined** score (rounded to 2 dp), not the raw one.

### 5. `GET /api/v1/news/source`
Articles from an exact source name (case-insensitive), newest first.
```
GET /api/v1/news/source?source=Reuters&limit=5
GET /api/v1/news/source?source=News18&limit=5
```

### 6. `GET /api/v1/news/nearby`
Articles near a location. A SQL **bounding-box pre-filter** narrows candidates, then the
exact **Haversine** distance is computed in Java and results are sorted closest-first.
```
GET /api/v1/news/nearby?lat=19.076&lon=72.877&radius=50&limit=5
```
| Param    | Default  | Range          |
|----------|----------|----------------|
| `lat`    | required | -90 to 90      |
| `lon`    | required | -180 to 180    |
| `radius` | `10`     | km             |
| `limit`  | `5`      | 1–50           |

Each result includes `distance_km`.

### 7. `GET /api/v1/trending` ⭐ Bonus
Trending articles near the user, based on the last **48 hours** of user engagement.
```
GET /api/v1/trending?lat=19.076&lon=72.877&limit=10
```

**Trending score** (per article):
```
score = Volume Weight (summed over events) + Recency Bonus + Proximity Bonus

Volume Weight:    view = 1, click = 3, share = 5
Recency Bonus:    +2 if event ≤ 24h ago, otherwise +1   (added per event)
Proximity Bonus:  +5 if ≤ 10km, +2 if ≤ 30km            (added once per article)
```
Only articles within `trending.radius.km` (default 50km) of the user are returned,
ranked by score (highest first), capped at `trending.max-results` (default 10).

> **Fallback:** if there are no recent user events, the feed returns top-scored
> (`relevance_score ≥ 0.7`) articles within the radius.

**Caching:** results are cached per geo-cluster — the cache key rounds lat/lon to 2 decimal
places (~1.1km grid) — with a **5-minute TTL** via Caffeine.

---

## 📤 Response Format

All endpoints return the same envelope:

```json
{
  "metadata": {
    "totalResults": 5,
    "page": 1,
    "queryUsed": "sports",
    "intent": ["category"],
    "entities": ["sports"],
    "endpoint": "category"
  },
  "articles": [
    {
      "title": "Article Title",
      "description": "Full article description...",
      "url": "https://source.com/article",
      "publication_date": "2025-03-26T04:46:55",
      "source_name": "Reuters",
      "category": ["sports", "cricket"],
      "relevance_score": 0.89,
      "llm_summary": "AI-generated 2-sentence summary of the article.",
      "latitude": 19.076,
      "longitude": 72.877,
      "distance_km": 7.66
    }
  ]
}
```

> `distance_km` is only present in `/nearby` and `/trending` responses.
> Null fields are omitted (`@JsonInclude(NON_NULL)`).
> `llm_summary` falls back to a truncated description when no OpenAI key is configured.

---

## ❌ Error Handling

Errors are handled centrally by `GlobalExceptionHandler` and returned as:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "limit must be between 1 and 50",
  "timestamp": "2025-03-26T10:30:00",
  "path": "/api/v1/news/nearby"
}
```

| Scenario                              | HTTP Status               | Trigger                         |
|---------------------------------------|---------------------------|---------------------------------|
| Invalid param (limit/threshold/coords)| `400 Bad Request`         | `IllegalArgumentException`      |
| Empty query                           | `400 Bad Request`         | `IllegalArgumentException`      |
| No articles found                     | `404 Not Found`           | `NewsNotFoundException`         |
| Unexpected server error               | `500 Internal Server Error`| any other `Exception`          |

> Note: a missing required request param currently surfaces through the generic handler
> as `500` rather than `400`.

**Validation rules** (`NewsRequestValidator`):
- `limit` ∈ [1, 50]
- `threshold` ∈ [0.0, 1.0]
- `lat` ∈ [-90, 90], `lon` ∈ [-180, 180]

---

## ⚙️ Configuration

Key properties in `src/main/resources/application.properties`:

| Property                        | Default                  | Description                       |
|---------------------------------|--------------------------|-----------------------------------|
| `server.port`                   | `8080`                   | HTTP port                         |
| `llm.openai.api-key`            | `${OPENAI_API_KEY:...}`  | OpenAI key (from `.env`)          |
| `llm.openai.model`              | `gpt-4o-mini`            | OpenAI chat model                 |
| `llm.openai.base-url`           | `https://api.openai.com/v1` | OpenAI base URL                |
| `news.data.file-path`           | `classpath:data/news_data.json` | Source data file           |
| `trending.radius.km`            | `50`                     | Trending search radius            |
| `trending.cache.ttl-minutes`    | `5`                      | Trending cache TTL                |
| `trending.max-results`          | `10`                     | Max trending results              |
| `nearby.default-radius.km`      | `10`                     | Default nearby radius             |

---

## 📋 Data Format

Each article in `news_data.json`:

```json
{
  "id": "19aaddc0-7508-4659-9c32-2216107f8604",
  "title": "Article Title",
  "description": "Article description...",
  "url": "https://source.com/article",
  "publication_date": "2025-03-26T04:46:55",
  "source_name": "News18",
  "category": ["world"],
  "relevance_score": 0.4,
  "latitude": 17.900636,
  "longitude": 77.465262
}
```

> Internally, `category` is persisted as a comma-separated string in H2 and re-exposed
> as a `List<String>` on load (`Article.syncCategoryFromRaw()`).

To use a different data file, update `news.data.file-path`.

---

## 🔍 Quick Test with curl

```bash
# 1. Category
curl "http://localhost:8080/api/v1/news/category?category=sports&limit=3"

# 2. High relevance score
curl "http://localhost:8080/api/v1/news/score?threshold=0.9&limit=3"

# 3. Text search
curl "http://localhost:8080/api/v1/news/search?query=IPL+2025&limit=3"

# 4. By source
curl "http://localhost:8080/api/v1/news/source?source=News18&limit=3"

# 5. Nearby (Mumbai)
curl "http://localhost:8080/api/v1/news/nearby?lat=19.076&lon=72.877&radius=50&limit=3"

# 6. Trending (Mumbai)
curl "http://localhost:8080/api/v1/trending?lat=19.076&lon=72.877&limit=10"

# 7. LLM natural-language query (requires OPENAI_API_KEY)
curl -X POST http://localhost:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{"query": "Latest IPL 2025 cricket news", "limit": 5}'
```

A Postman collection is also available at
`src/main/resources/postman/NewsRetrieval.postman_collection.json`.
