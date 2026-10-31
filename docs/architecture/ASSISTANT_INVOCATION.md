# Android assistant invocation via Quick Settings

## Upstream sources
- https://developer.android.com/reference/android/service/quicksettings/TileService
- https://developer.android.com/reference/android/app/StatusBarManager
- https://developer.android.com/reference/android/app/PendingIntent
- https://developer.android.com/reference/android/service/voice/VoiceInteractionService
- Existing LangGraph/Deep Agents review: HARNESS_UPSTREAM_RESEARCH.md

## Delivered and security boundaries
- AssistantTileService is registered under system-owned
  android.permission.BIND_QUICK_SETTINGS_TILE.
- Android 13+: UI requests a system consent prompt through
  StatusBarManager.requestAddTileService. Older: manual Edit tiles directions.
- Tapping the installed tile opens MainActivity, nothing else.
  Android 14+ uses PendingIntent.FLAG_IMMUTABLE; older supports Intent.
- A locked device invokes unlockAndRun before launching chat.
- No background microphone, no wake word, no capture or agent execution.
  Voice needs separate Tap to talk, Android RECORD_AUDIO grant, user Send.
- This is not a privileged or selected default Android assistant service.
  The hardware long-press assistant role is a separate future evaluation.

## UAT (not yet verified)
- Android 13+ accept/decline/already added outcomes; Android 12 manual add.
- Tapping tile unlocked opens chat but not audio or native tool actions.
- Tapping tile while locked prompts unlock.
- Android 14+ PendingIntent and Android 13 Intent launch path.
- Remove tile; app's permissions and journal remain unchanged.
- Emulator checks service permission metadata; real UI acceptance still required.
