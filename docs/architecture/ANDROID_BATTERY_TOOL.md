# Android battery status — read-only tool milestone

## Platform/API
The native Android module uses android.os.BatteryManager:
https://developer.android.com/reference/android/os/BatteryManager

It queries BATTERY_PROPERTY_CAPACITY and isCharging. It accepts only a
percentage in the inclusive integer range 0 to 100; unsupported/broken
readings return a controlled error rather than invented battery levels.
No phone identifiers, application lists, battery history, serial numbers,
location, privileged battery statistics, or contacts are requested.

## Agent architecture and upstream reuse
Reuses the already installed LangGraph.js planning graph with exact typed
schemas, Android ToolRegistry, durable task journal and model-result policy.
Reviewed upstream source snapshots:
- LangGraph.js 959f041dfb8f6bbafa6af3ebb4bc20484a9290da:
  https://github.com/langchain-ai/langgraphjs/tree/959f041dfb8f6bbafa6af3ebb4bc20484a9290da
- Deep Agents.js 26f190d40f44a2bb498db73e9e98b002bb8554fe:
  https://github.com/langchain-ai/deepagentsjs/tree/26f190d40f44a2bb498db73e9e98b002bb8554fe

New tool ID: device.battery_status, empty input {}, no special Android
permissions, no side effects or explicit grant. A constrained owner command
like "Check battery level" is required by BOTH server-side and client-side
intent policies; unsupported multi-step commands fail closed.

Minimal observations forwarded to an opted-in model server include only
levelPercent and charging. Never forward unexpected Android response keys.
The LangGraph done-step policy produces the final percentage from validated
tool data rather than copying an ungrounded model answer.

## Acceptance
- Local automated policy, mobile -> HTTP -> LangGraph -> mock native tool
  tests pass without provider credentials.
- Emulator/native APK compile still required for the updated Kotlin method.
- Real Android model/hardware UAT remains separate and must not be claimed
  passed from a mock. Device might report unsupported capacity.
