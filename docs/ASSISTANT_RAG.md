# AI Assistant (Spring AI + RAG)

An in-app chat assistant for every role. It answers questions about policy and about the user's own live data, and cites its sources.

## Pipeline
1. **Live facts (by role).** `DataContextService` gathers only what the caller may see: a beneficiary gets their own entitlement; a dealer or
   warehouse manager gets their own stock; officers and admins get system figures and open alerts; auditors get alerts only.
2. **Retrieve.** The question is matched against the policy notes in `backend/src/main/resources/kb/*.md` (split at each `##` heading).
   - llm mode: Spring AI `EmbeddingModel` + `SimpleVectorStore` similarity search (embeddings are saved to `data/vectorstore.json`).
   - local mode, or if the vector store is unavailable: built-in TF-IDF keyword search.
3. **Generate.** 
   - llm mode: Spring AI `ChatClient` writes a short answer from the live facts and numbered passages only, citing `[1]`, `[2]`.
   - local mode: the answer is assembled from the same facts and the best passages. No model, no key, no network.
4. The response shows the answer, the mode used, the live data that was used, and the source passages.

## Modes
| | local (default) | llm |
|---|---|---|
| Needs | nothing | an OpenAI-compatible model (OpenAI, Ollama, Groq, Together, ...) |
| Answers | extracted from notes + data | written in natural language, in the user's language |
| Retrieval | keyword | vector similarity (semantic) |
| If the model is down | n/a | falls back to local and tells the user |

## Turn on llm mode
OpenAI: `ASSISTANT_MODE=llm`, `OPENAI_API_KEY=sk-...`.
Free local model (Ollama): install Ollama, run `ollama pull llama3.1` and `ollama pull nomic-embed-text`, then set
`ASSISTANT_MODE=llm`, `OPENAI_API_KEY=ollama`, `LLM_BASE_URL=http://localhost:11434` (`http://host.docker.internal:11434` inside Docker),
`LLM_CHAT_MODEL=llama3.1`, `LLM_EMBEDDING_MODEL=nomic-embed-text`.
Changing the embedding model needs a re-index (admin: `POST /api/admin/assistant/reindex`, and delete `data/vectorstore.json`).

## Improve the knowledge base
Add or edit Markdown files in `backend/src/main/resources/kb/` (one `# Title`, then `## Section` headings). Replace the project summaries with
the official NFSA text and your state's orders, rebuild, and re-index. The bundled notes are simplified summaries, not legal advice.

## Safety design
- Role-scoped context: the model never receives data the user could not see in the normal screens.
- The prompt tells the model to answer only from the context, say "I do not know" otherwise, and treat context as data, not instructions.
- 20 questions per user per minute (`pgds.assistant.requests-per-minute`); questions are limited to 500 characters.
- The assistant is read-only: it cannot issue grain, change stock or edit records.
- Questions are sent to the configured model provider. With a hosted provider, tell users that their questions leave your server; with Ollama they stay local.
- Weak spots to mention honestly: small local models can still hallucinate; the notes are only as good as what you put in them.
