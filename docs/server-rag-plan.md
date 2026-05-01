# Server-Side RAG / Knowledge Plan

Current state:

```text
android/app/src/main/assets/knowledge/sansevieria_knowledge.txt
android/app/src/main/java/com/example/smartphonapptest001/data/knowledge/PlantKnowledgeRepository.kt
```

Knowledge is bundled into the APK. Updating it requires rebuilding and
installing a new APK.

## Target Direction

Move knowledge management to the server:

```text
Android
  -> POST /api/chat
    -> Spring Boot
      -> plant knowledge search
      -> AI provider
```

Initial server-side storage can be PostgreSQL text rows:

```text
plant_knowledge
  id
  plant_species
  title
  content
  source
  updated_at
```

Later, add embeddings/vector search only after plain text retrieval becomes
insufficient.

## Migration Strategy

1. Keep Android bundled knowledge as fallback.
2. Add server table and seed Sansevieria knowledge.
3. Include relevant snippets in server-side chat prompt.
4. Remove Android RAG prompt injection after server behavior is verified.
