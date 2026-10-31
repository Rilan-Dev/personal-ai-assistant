# Opt-in real agent model acceptance

A separate test path now exists to verify the real model used by LangGraph:
npm run test:provider-live

It requires AGENT_LLM_API_KEY and AGENT_LLM_MODEL environment variables,
optionally AGENT_LLM_BASE_URL (HTTPS or local loopback only). The token is
never part of Expo/React Native bundles or test output. If credentials are
missing, the test exits nonzero without making a provider request.

The GitHub workflow agent-provider-acceptance.yml is MANUAL workflow_dispatch
only, with an environment named live-agent-provider-tests. Configure the
GitHub environment's AGENT_LLM_API_KEY secret, AGENT_LLM_MODEL variable and
optional AGENT_LLM_BASE_URL variable. Enable environment approvals if desired.

The acceptance sends only five synthetic commands to the real provider, then
checks each output after the actual LangGraph StateGraph/policy nodes:
- Battery status, WhatsApp app launch and flashlight: an allowed tool.
- Attempt to send a WhatsApp message: clarify, no sending tool.
- Attempt to read contacts: clarify, no contact tool.

It measures basic per-case latency and a 5/5 pass gate. It uses NO contacts,
mail, screen capture, live Android device, webhook side effects, or persistent
user history. It cannot validate microphone, hardware, native permissions,
provider billing, user preferences, or security of an arbitrary malicious
prompt. It is not a replacement for a full agent evaluation suite.

The workflow has not been run with a real provider yet. A successful mock
OpenAI-compatible server test is not equivalent to live provider acceptance.

## Correcting two failed manual runs (2026-11-01)
Run 41246478040 had neither AGENT_LLM_API_KEY nor AGENT_LLM_MODEL.
Run 41246896110 had a masked key but still no AGENT_LLM_MODEL.
No real model request was made by either run. Do not rerun them without
correcting the model; that wastes quota. The workflow now accepts a model
Run workflow input, falling back to the GitHub environment variable
AGENT_LLM_MODEL. It checks credentials and model BEFORE npm ci; both
must be present. The model ID is not statically pinned, because model
availability is provider/account-specific and can change. A passing
Android build is independent of this opt-in paid-provider acceptance.
