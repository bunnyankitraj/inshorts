# High-Volume Production Database Design

The current implementation uses an in-memory H2 database, which is perfect for an interview demonstration. However, if this system were to handle millions of articles and thousands of requests per second (high volume), the database architecture must evolve. 

This document outlines the database structure, indexing strategies, and technology choices for a production-scale environment.

---

## 1. Database Choice: PostgreSQL + PostGIS

For production, the primary relational database should be **PostgreSQL**, combined with the **PostGIS** extension for advanced geospatial querying.

### The `articles` Table

```sql
CREATE TABLE articles (
    id VARCHAR(36) PRIMARY KEY,
    title TEXT NOT NULL,
    description TEXT,
    url TEXT,
    publication_date TIMESTAMP WITH TIME ZONE,
    source_name VARCHAR(100),
    category TEXT[],                  -- Native array type instead of CSV string
    relevance_score NUMERIC(5, 4),
    location GEOGRAPHY(Point, 4326)   -- Native PostGIS spatial type
);
```

### Indexing Strategy for `articles`

To ensure sub-millisecond query performance at scale:

1. **Category Array Index (GIN)**
   ```sql
   CREATE INDEX idx_articles_category ON articles USING GIN(category);
   ```
   *Why:* Allows blazing-fast queries like `WHERE 'sports' = ANY(category)` across millions of rows.

2. **Geospatial Index (GiST)**
   ```sql
   CREATE INDEX idx_articles_location ON articles USING GIST(location);
   ```
   *Why:* Enables highly optimized bounding-box and radius queries. A query like `ST_DWithin(location, user_location, 10000)` (within 10km) uses the R-Tree index, completely eliminating the need for our manual Java-based Haversine calculations.

3. **Sorting Indexes (B-Tree)**
   ```sql
   CREATE INDEX idx_articles_pub_date ON articles(publication_date DESC);
   CREATE INDEX idx_articles_score ON articles(relevance_score DESC);
   CREATE INDEX idx_articles_source ON articles(source_name);
   ```

---

## 2. Full-Text Search: Elasticsearch

The current SQL `LIKE '%query%'` is a full table scan and will collapse under heavy load. For a production `/search` endpoint, we must offload text search to **Elasticsearch**.

- **Structure:** Articles are synced to an Elasticsearch index.
- **Benefits:** 
  - Sub-millisecond full-text search.
  - Native handling of our ranking formula (50% text match + 50% relevance score) using Elasticsearch's `function_score` queries.
  - Typo tolerance, stemming, and exact phrase matching.

---

## 3. High-Volume User Events (Trending Feed)

The `user_events` table currently lives in the primary SQL database. At high volume, write-heavy event streams (clicks, views) will lock the database and degrade read performance.

### Evolved Architecture for Events:
1. **Ingestion:** API sends events directly to a Message Broker (e.g., **Apache Kafka**).
2. **Storage:** Events are streamed into a time-series database like **TimescaleDB** or **ClickHouse**.
   
```sql
-- In TimescaleDB/ClickHouse
CREATE TABLE user_events (
    id UUID,
    article_id UUID,
    event_type VARCHAR(20),  -- view, click, share
    user_lat DOUBLE PRECISION,
    user_lon DOUBLE PRECISION,
    event_time TIMESTAMP WITH TIME ZONE
);

-- Partitioned by time
SELECT create_hypertable('user_events', 'event_time');
```

### Pre-Computing the Trending Feed
Instead of calculating the trending score "on the fly" when the user requests it:
1. A background worker (e.g., Apache Flink or Spark) consumes the Kafka stream.
2. It continuously updates the "Trending Score" for geographic zones (e.g., geohashes).
3. The final computed feeds are stored directly in **Redis**.
4. The `/api/v1/trending` endpoint simply performs a fast `O(1)` read from Redis based on the user's Geohash.

---

## Summary of High-Volume Architecture

1. **Primary DB:** PostgreSQL + PostGIS (for `/category`, `/source`, `/score`, `/nearby`).
2. **Search Engine:** Elasticsearch (for `/search` and NL queries).
3. **Event Stream:** Kafka + TimescaleDB (for high-throughput `user_events`).
4. **Caching Layer:** Redis (replaces Caffeine for distributed, multi-node caching).
