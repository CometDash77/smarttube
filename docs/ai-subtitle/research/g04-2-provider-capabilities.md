# G04-2 — Provider Capability Failure and Model-Discovery UX

Status: **Settled (Worker, M04-C0)**. Date: 2026-09-12.

Evidence scope: Phase 0 official-documentation research recorded in `architecture.md` §6 and §12, plus repository facts. Live re-verification was unavailable from this workstation (see Limitations).

## Question

Per gate G04-2: define how provider capability failure and model discovery behave at runtime and in the UI, before M04 protocol adapters exist.

## Protocol baseline (Phase 0; official documentation cited in `architecture.md` §12)

| Provider Type | Protocol | Chat/completion path | Model discovery | Authentication |
|---|---|---|---|---|
| OpenAI-Compatible | OpenAI | `POST {base}/v1/chat/completions` | `GET {base}/v1/models` | Bearer |
| Anthropic-Compatible | Anthropic | `POST {base}/v1/messages` (top-level `system`, explicit `max_tokens`, `anthropic-version`) | `GET {base}/v1/models` | Bearer or `x-api-key` |
| OpenRouter | OpenAI | `POST https://openrouter.ai/api/v1/chat/completions` | `GET /api/v1/models` | Bearer (optional attribution headers) |
| DeepSeek | OpenAI | base `https://api.deepseek.com`, `/chat/completions` | `GET /models` | Bearer |
| MiMo | OpenAI or Anthropic | bases `https://api.xiaomimimo.com/v1` and `/anthropic` | `GET /v1/models` | `api-key` or Bearer |

The full Phase 0 findings and source list live in `architecture.md` §6 and §12; they were verified against official OpenAI, Anthropic, OpenRouter, DeepSeek, and MiMo documentation during Phase 0.

## Policy (accepted)

1. **Runtime capability checks only.** A Provider Type selects a preset, not a capability claim. Adapters verify actual response shapes at runtime; provider-specific optional fields are capability-gated per profile, never inferred from a display name.
2. **Manual Model ID is first-class.** A user-entered model id can always be saved, selected, and used, whether or not discovery works for the endpoint.
3. **Discovery failure never invalidates a saved profile.** A failed or unsupported discovery keeps the profile and its currently selected model; the UI reports the failure and offers retry or manual entry.
4. **Normalized outcomes.** Discovery normalizes to success (list), unsupported (endpoint/feature absent), or failure (auth/network/timeout/protocol). Only success replaces a cached model list; every other outcome preserves saved state.
5. **Connection test is normalized.** The connectivity test distinguishes auth, rate limit, timeout, network, server, protocol, and invalid response, and never exposes secrets — reusing the `TranslationFailureCategory` vocabulary already established in M03.
6. **Presets are defaults, not facts.** Base URL, optional headers, and parameter policies are editable; deployments with different base URLs (for example token-plan endpoints) stay usable.

## Limitations

Provider documentation could not be re-fetched from this workstation during M04-C0. The table is the Phase 0-verified baseline. The adapter milestones (M04-C3..C5) must re-confirm temporally unstable details (endpoint paths, required headers) when network access is available, and every adapter test runs against a fake HTTP executor so no test depends on live endpoints.
