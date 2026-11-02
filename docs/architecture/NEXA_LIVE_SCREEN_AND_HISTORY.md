# Nexa live screen controls and local conversation history

## Confirmed root cause (November 3, 2026)

The published Android APK contained Accessibility screen actions, but the Sarvam API live transport advertised only battery, storage and device facts to its LLM. The local speech matcher accepted a tiny exact-phrase set, while OpenAI's live instructions expressly stated it could not control screens. OpenAI's published tool definitions omitted scrolling and taps. The voice UI also described screen actions as unavailable. This kept the voice agent from reaching native Accessibility actions.

## New behavior

When the owner has enabled Nexa Screen Assist under Android Accessibility Settings, the **foreground app behind the Nexa overlay** can be inspected upon a single explicit approval. Voice requests for screen labels, icons, four-direction scroll, safe semantic icon tap, or a uniquely matched visible text-label tap use the native service's fresh window/root validation and one-time action journal. App launcher discovery uses Android launcher-visible apps only. Access is unavailable on the lock screen, system UI, passwords or editable fields. Unknown or risky actions, ungrounded X/Y clicks, phone calls, purchases, messaging and automatic permissions remain disallowed. A model can propose these actions; only a visible owner approval authorizes each request. Android accepting a click is NOT proof the requested task completed.

The Saaras transcript route locally recognizes spoken device actions before the Sarvam chat model is queried. Sarvam Chat still exposes its limited **read-only** model function set. The OpenAI Realtime model receives new reviewed request tools, not unrestricted Android capabilities. Provider-side screen access requires approval to share only the bounded requested observation.

## Conversation history

Android-private SQLite `custom.db` uses `conversation_entries` with auto-increment IDs, session ID, channel, provider, role, timestamp and Android Keystore AES-256-GCM encrypted text. No microphone audio is stored. New RN Chat turns and supported provider transcriptions/replies are stored on device. The Voice tab can show recent entries, copy JSON or open the Android JSON share chooser. Exports include the most recent 10,000 records, with their plaintext content at the owner's explicit request. Sharing/copying puts private data outside the encrypted database; do not share tokens, passwords or other secrets. Existing legacy ephemeral conversations cannot be reconstructed. The app keeps no external synchronization and Android backups remain disabled; uninstalling may delete the history.

**Future**: if the owner provides a live cloud PostgreSQL server, build a separately consented secure sync backend (e.g. Prisma schema and migrations) using these fields, authenticated identities, encryption at rest, and explicit retention/access policies. No Postgres credentials or remote data storage are configured in this change.

## Device acceptance

1. Install the signed APK; grant microphone, overlay and Screen Assist Android Accessibility permissions manually in Nexa Setup.
2. Start from the Voice tab or configured offline **Hey Nexa**; verify the selected Sarvam/OpenAI provider connection.
3. Open an external app and say “read my screen”, approve once and verify a bounded label summary.
4. Say “scroll left”, “scroll right”, “scroll down”, “scroll up”, approve each. Test only on scrollable views and verify Android actually moves the content.
5. Say “tap menu” or “tap the profile button”, approve once, and inspect resulting screen. Try ambiguous/risky controls to verify denial.
6. Say “list installed apps”, approve once. Try “open GitHub”, approve and verify the actual launch.
7. Open Voice → View recent history → Copy JSON or Export conversations as JSON and verify that new Chat, spoken turns and answers appear. Do not expect earlier APK sessions to be present.

Native instrumentation and microphone/device acceptance must be checked on a real Android device and the GitHub Actions runner; source tests alone are insufficient.
