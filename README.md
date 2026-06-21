# 📰 Contextual News Data Retrieval System

A Spring Boot backend that fetches and organizes news articles from a data source, uses an **LLM (Google Gemini / OpenAI)** to understand natural language queries, routes them to the correct retrieval strategy, enriches results with AI-generated summaries, and returns structured JSON responses.

---

## 🏗️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2 |
| Database | H2 (in-memory, default) / PostgreSQL |
| ORM | Spring Data JPA + Hibernate |
| LLM | Google Gemini (`gemini-1.5-flash`) or OpenAI (`gpt-4o-mini`) |
| Caching | Caffeine (for trending feed) |
| HTTP Client | Spring WebFlux WebClient |
| Build | Maven |

---

## 🚀 Getting Started

### Prerequisites
- Java 21+
- Maven 3.9+
- (Optional) PostgreSQL — only if switching from H2

### 1. Clone & navigate
```bash
cd /path/to/Inshorts
```

### 2. Add your LLM API key
```bash
# For Google Gemini (default):
export GEMINI_API_KEY=your_gemini_api_key_here

# OR for OpenAI — also change llm.provider=openai in application.properties:
export OPENAI_API_KEY=your_openai_api_key_here
```

### 3. Run the application
```bash
./mvnw spring-boot:run
```

The app starts on **`http://localhost:8080`** and automatically:
- Ingests all articles from `news_data.json` into the database
- Seeds 300 simulated user events for the trending feed

---

## 📂 Project Structure

```
src/main/java/com/inshorts/news/
├── NewsApplication.java            # Entry point
├── config/
│   ├── AppConfig.java              # WebClient, ObjectMapper beans
│   └── CacheConfig.java            # Caffeine cache (5-min TTL for trending)
├── controller/
│   ├── QueryController.java        # POST /api/v1/query  (LLM entry point)
│   ├── NewsController.java         # GET /api/v1/news/*  (direct endpoints)
│   └── TrendingController.java     # GET /api/v1/trending
├── service/
│   ├── DataIngestionService.java   # Loads news_data.json on startup
│   ├── LLMService.java             # Gemini/OpenAI calls, query analysis & summaries
│   ├── NewsService.java            # 5 retrieval strategies + Haversine formula
│   ├── EnrichmentService.java      # Parallel LLM summary generation
│   └── TrendingService.java        # Trending score computation + geo caching
├── repository/
│   ├── ArticleRepository.java      # JPA queries for all 5 strategies
│   └── UserEventRepository.java    # User interaction events
├── model/
│   ├── Article.java                # DB entity
│   └── UserEvent.java              # Simulated user engagement events
├── dto/
│   ├── QueryRequest.java           # Request body for /query
│   ├── ArticleResponse.java        # Output article shape
│   ├── NewsApiResponse.java        # Wrapper: articles + metadata
│   ├── LLMAnalysis.java            # LLM parsed output (entities + intent)
│   └── ErrorResponse.java          # Consistent error format
└── exception/
    ├── GlobalExceptionHandler.java  # Centralized error handling
    ├── NewsNotFoundException.java   # 404 — no articles found
    └── LLMServiceException.java     # 502 — LLM unavailable
```

---

## 🌐 API Endpoints

### Base URL: `http://localhost:8080/api/v1`

---

### 1. `POST /api/v1/query` — LLM-Driven Natural Language Query ⭐

The main entry point. Accepts plain English, uses the LLM to extract intent and entities, routes to the right strategy, and enriches results with AI summaries.

**Request:**
```json
{
  "query": "Latest developments in the Elon Musk Twitter acquisition near Palo Alto",
  "userLat": 37.4220,
  "userLon": -122.0840,
  "radiusKm": 10,
  "limit": 5
}
```

**Example intents the LLM detects:**
| Query | Detected Intent | Action |
|---|---|---|
| "Tech news from BBC" | `source` | Fetch from source=BBC |
| "Sports articles" | `category` | Fetch category=sports |
| "Top relevant articles" | `score` | Fetch relevance_score ≥ 0.7 |
| "Elon Musk Twitter news near Palo Alto" | `nearby` + `search` | Nearby articles, text search |
| "IPL 2025 updates" | `search` | Text search query |

---

### 2. `GET /api/v1/news/category`

Retrieve articles from a specific category, ranked by publication date (newest first).

```
GET /api/v1/news/category?category=sports&limit=5
GET /api/v1/news/category?category=technology&limit=5
GET /api/v1/news/category?category=national&limit=5
```

**Available categories from data:** `national`, `world`, `sports`, `politics`, `business`, `entertainment`, `technology`, `health`, `General`, `miscellaneous`, `IPL_2025`, `Israel-Hamas_War`, `Russia-Ukraine_Conflict`

---

### 3. `GET /api/v1/news/score`

Retrieve articles with high relevance scores, ranked by score (highest first).

```
GET /api/v1/news/score?threshold=0.7&limit=5
GET /api/v1/news/score?threshold=0.9&limit=5
```

| Param | Default | Description |
|---|---|---|
| `threshold` | `0.7` | Minimum relevance score (0.0–1.0) |
| `limit` | `5` | Number of results (max 50) |

---

### 4. `GET /api/v1/news/search`

Full-text search across article titles and descriptions. Ranked by a combined score of `relevance_score` (50%) + text match (50%).

```
GET /api/v1/news/search?query=Elon+Musk&limit=5
GET /api/v1/news/search?query=IPL+2025&limit=5
GET /api/v1/news/search?query=Bangladesh+Yunus&limit=5
```

---

### 5. `GET /api/v1/news/source`

Retrieve articles from a specific news source, ranked by publication date.

```
GET /api/v1/news/source?source=Reuters&limit=5
GET /api/v1/news/source?source=NDTV&limit=5
GET /api/v1/news/source?source=Hindustan+Times&limit=5
```

---

### 6. `GET /api/v1/news/nearby`

Retrieve articles published near a geographic location. Uses the **Haversine formula** to compute exact distances and ranks results closest-first.

```
GET /api/v1/news/nearby?lat=19.076&lon=72.877&radius=50&limit=5
GET /api/v1/news/nearby?lat=28.613&lon=77.209&radius=25&limit=5
```

| Param | Default | Description |
|---|---|---|
| `lat` | required | User latitude (-90 to 90) |
| `lon` | required | User longitude (-180 to 180) |
| `radius` | `10` | Search radius in km |
| `limit` | `5` | Number of results |

Response includes `distance_km` for each article.

---

### 7. `GET /api/v1/trending` ⭐ Bonus

Returns trending articles near the user's location based on recent user engagement.

```
GET /api/v1/trending?lat=19.076&lon=72.877&limit=10
```

**Trending Score Formula:**
```
trending_score = SUM[ event_weight × recency_decay(event_time) ] × geo_proximity_boost

event_weight:       view=1, click=3, share=5
recency_decay:      1 / (1 + hours_since_event)
geo_proximity_boost: 1 + (1 / (1 + distance_km))
```

**Caching:** Results are cached per geospatial cluster (lat/lon rounded to 2 decimal places = ~1.1km grid) with a **5-minute TTL** using Caffeine.

---

## 📤 Response Format

All endpoints return a consistent structure:

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

> `distance_km` is only present in `/nearby` responses.
> `llm_summary` falls back to a truncated description if LLM API key is not set.

---

## ❌ Error Handling

All errors return consistent JSON:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Missing required parameter: lat",
  "timestamp": "2025-03-26T10:30:00",
  "path": "/api/v1/news/nearby"
}
```

| Scenario | HTTP Status |
|---|---|
| Missing required param | `400 Bad Request` |
| Invalid coordinates / values | `400 Bad Request` |
| No articles found | `404 Not Found` |
| LLM API unavailable | `502 Bad Gateway` |
| Unexpected server error | `500 Internal Server Error` |

---

## ⚙️ Configuration

### Switch LLM provider (`application.properties`)
```properties
# Use Gemini (default):
llm.provider=gemini
GEMINI_API_KEY=your_key

# Or switch to OpenAI:
llm.provider=openai
OPENAI_API_KEY=your_key
```

### Switch to PostgreSQL
```properties
# In application.properties:
spring.profiles.active=postgres

# Set env vars:
export DB_USERNAME=postgres
export DB_PASSWORD=your_password
# Ensure a database named 'newsdb' exists on localhost:5432
```

### Key configuration properties
| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | Application port |
| `llm.provider` | `gemini` | LLM to use: `gemini` or `openai` |
| `llm.gemini.model` | `gemini-1.5-flash` | Gemini model |
| `trending.cache.ttl-minutes` | `5` | Trending feed cache TTL |
| `trending.radius.km` | `50` | Default trending search radius |
| `nearby.default-radius.km` | `10` | Default nearby radius |

---

## 📋 Data Format

The system ingests `src/main/resources/news_data.json`. Each article:

```json
{
  "id": "uuid",
  "title": "Article Title",
  "description": "Article description...",
  "url": "https://source.com/article",
  "publication_date": "2025-03-26T04:46:55",
  "source_name": "Reuters",
  "category": ["world", "national"],
  "relevance_score": 0.85,
  "latitude": 19.076,
  "longitude": 72.877
}
```

To use a different data file, update `news.data.file-path` in `application.properties`.

---

## 🔍 Quick Test with curl

```bash
# 1. Category endpoint
curl "http://localhost:8080/api/v1/news/category?category=sports&limit=3"

# 2. High relevance score
curl "http://localhost:8080/api/v1/news/score?threshold=0.9&limit=3"

# 3. Text search
curl "http://localhost:8080/api/v1/news/search?query=IPL+2025&limit=3"

# 4. By source
curl "http://localhost:8080/api/v1/news/source?source=Reuters&limit=3"

# 5. Nearby (Mumbai)
curl "http://localhost:8080/api/v1/news/nearby?lat=19.076&lon=72.877&radius=50&limit=3"

# 6. Trending (Mumbai)
curl "http://localhost:8080/api/v1/trending?lat=19.076&lon=72.877&limit=10"

# 7. LLM natural language query (requires GEMINI_API_KEY)
curl -X POST http://localhost:8080/api/v1/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Latest IPL 2025 cricket news",
    "limit": 5
  }'
```
