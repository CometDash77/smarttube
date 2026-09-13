# G04-2 — Provider Protocol Facts, Capability Failure, and Model-Discovery UX

Status: **VERIFIED (2026-09-13)**. Current official documentation was retrieved for all five Provider Types. No provider source is BLOCKED.

## Question

Per gate G04-2: verify the current official endpoint paths and authentication/version headers, then keep provider capability failure and model discovery behavior evidence-bounded.

## Verified current protocol facts

All links below are first-party provider documentation retrieved on 2026-09-13. Paths are shown as full default-service URLs so that base/path composition is unambiguous.

| Provider Type | Normal-response endpoint | Model discovery endpoint | Authentication and request-version headers | Official evidence |
|---|---|---|---|---|
| OpenAI-Compatible default (OpenAI) | `POST https://api.openai.com/v1/chat/completions` | `GET https://api.openai.com/v1/models` | `Authorization: Bearer <credential>`. The current reference uses the `v1` URL and does not document a caller-supplied API-version request header for these endpoints. | [Create chat completion](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create), [List models](https://developers.openai.com/api/reference/resources/models/methods/list), [API authentication](https://developers.openai.com/api/reference/overview#authentication) |
| Anthropic-Compatible default (Anthropic) | `POST https://api.anthropic.com/v1/messages` | `GET https://api.anthropic.com/v1/models` | `Authorization: Bearer <credential>` is current; legacy `x-api-key: <credential>` remains supported. `anthropic-version` is required (official examples use `2023-06-01`); JSON POSTs also require `content-type: application/json`. | [API overview and required headers](https://platform.claude.com/docs/en/api/overview#authentication), [Create a Message](https://platform.claude.com/docs/en/api/http/messages/create), [List Models](https://platform.claude.com/docs/en/api/models/list) |
| OpenRouter | `POST https://openrouter.ai/api/v1/chat/completions` | `GET https://openrouter.ai/api/v1/models` | `Authorization: Bearer <credential>`. No provider-version request header is documented for these endpoints. `HTTP-Referer` and `X-OpenRouter-Title` are attribution headers, not authentication/version requirements. | [Create a chat completion](https://openrouter.ai/docs/api/api-reference/chat/send-chat-completion-request), [List all models](https://openrouter.ai/docs/api/api-reference/models/get-models), [App attribution](https://openrouter.ai/docs/app-attribution) |
| DeepSeek | `POST https://api.deepseek.com/chat/completions` | `GET https://api.deepseek.com/models` | HTTP Bearer authentication (`Authorization: Bearer <credential>`). No provider-version request header is documented for these endpoints. | [First API call](https://api-docs.deepseek.com/guides/function_calling), [Chat Completions API](https://api-docs.deepseek.com/api/create-chat-completion/), [Lists Models](https://api-docs.deepseek.com/api/list-models), [API authentication scheme](https://api-docs.deepseek.com/api/deepseek-api/) |
| MiMo (OpenAI format) | `POST https://api.xiaomimimo.com/v1/chat/completions` | `GET https://api.xiaomimimo.com/v1/models` | Either `api-key: <credential>` or `Authorization: Bearer <credential>` is documented. The direct HTTP pages do not list a provider-version request header. | [First API Call](https://mimo.mi.com/docs/en-US/quick-start/summary/first-api-call), [OpenAI Chat Completions compatibility](https://mimo.mi.com/docs/en-US/api/chat/openai-api), [List Models](https://mimo.mi.com/docs/en-US/api/model/list-models), [API integration FAQ](https://mimo.mi.com/docs/en-US/quick-start/faq/api-integration) |
| MiMo (Anthropic format, optional profile protocol) | `POST https://api.xiaomimimo.com/anthropic/v1/messages` | MiMo's documented discovery endpoint remains `GET https://api.xiaomimimo.com/v1/models` | Either `api-key: <credential>` or `Authorization: Bearer <credential>` is documented. MiMo's direct Anthropic-format curl example lists `api-key` and `Content-Type` but does not list `anthropic-version`; this note therefore does not claim that header is required or forbidden by MiMo. | [First API Call](https://mimo.mi.com/docs/en-US/quick-start/summary/first-api-call), [Anthropic Messages compatibility](https://mimo.mi.com/docs/en-US/api/chat/anthropic-api), [List Models](https://mimo.mi.com/docs/en-US/api/model/list-models), [API integration FAQ](https://mimo.mi.com/docs/en-US/quick-start/faq/api-integration) |

## Evidence-bounded observations

- OpenAI, OpenRouter, DeepSeek, and MiMo's OpenAI-format service all expose the normal Chat Completions response shape through their cited endpoints; this supports one OpenAI-format normal-response adapter, but does not imply identical optional parameters.
- Anthropic's current documentation makes Bearer the primary authentication form while retaining `x-api-key` as a legacy fallback. The existing Anthropic-compatible default remains valid, but documentation should not describe `x-api-key` as the sole current method.
- MiMo officially documents both OpenAI and Anthropic compatibility. The current MiMo preset's OpenAI-format Bearer request is one documented combination. The Anthropic path is an editable optional profile choice, not a claim that MiMo requires an `anthropic-version` header.
- DeepSeek's current official default base remains `https://api.deepseek.com`; its documented Chat Completions and model-list paths are `/chat/completions` and `/models` relative to that base.

## Project recommendations and accepted behavior

These are project choices, separated from the provider facts above:

1. Continue treating a Provider Type as an editable preset, not a capability guarantee.
2. Continue using exactly two normal-response adapters (`OpenAiChatCompletionsAdapter` and `AnthropicMessagesAdapter`) for the five user-facing types.
3. Keep manual Model ID first-class. Discovery failure or an unsupported discovery endpoint must preserve the saved profile and selected model.
4. Normalize discovery to success, unsupported, or failure; only success may replace the displayed model list.
5. Keep authentication scheme, base URL, paths, and optional headers profile-editable. Runtime response validation remains authoritative.
6. Do not infer streaming, retries, or optional parameter support from compatibility branding; those remain outside M04.

No new provider design is introduced, and no production code is changed by this research correction.

## Source access record

| Provider | URL(s) | Access result on 2026-09-13 |
|---|---|---|
| OpenAI | <https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create>, <https://developers.openai.com/api/reference/resources/models/methods/list>, <https://developers.openai.com/api/reference/overview#authentication> | PASS — official endpoint examples and Bearer authentication retrieved. |
| Anthropic | <https://platform.claude.com/docs/en/api/overview>, <https://platform.claude.com/docs/en/api/http/messages/create>, <https://platform.claude.com/docs/en/api/models/list> | PASS — official endpoint list and required authentication/version headers retrieved. |
| OpenRouter | <https://openrouter.ai/docs/api/api-reference/chat/send-chat-completion-request>, <https://openrouter.ai/docs/api/api-reference/models/get-models>, <https://openrouter.ai/docs/app-attribution> | PASS — official endpoint, Bearer authentication, and attribution-header scope retrieved. |
| DeepSeek | <https://api-docs.deepseek.com/guides/function_calling>, <https://api-docs.deepseek.com/api/create-chat-completion/>, <https://api-docs.deepseek.com/api/list-models>, <https://api-docs.deepseek.com/api/deepseek-api/> | PASS — official base URL, endpoint paths, and Bearer scheme retrieved. |
| MiMo | <https://mimo.mi.com/docs/en-US/quick-start/summary/first-api-call>, <https://mimo.mi.com/docs/en-US/api/chat/openai-api>, <https://mimo.mi.com/docs/en-US/api/chat/anthropic-api>, <https://mimo.mi.com/docs/en-US/api/model/list-models>, <https://mimo.mi.com/docs/en-US/quick-start/faq/api-integration> | PASS — official OpenAI/Anthropic paths, model-list path, and both authentication forms retrieved. |

## Blocked sources

None.
