# Nexa × DSH — private owner task review acceptance (2026-11-05)

## Scope and release boundary
This work establishes a **non-executing**, device-local owner task queue. It does
not activate DSH workflows, bypass existing Android permissions, or merge the
TypeScript Chat and Kotlin voice execution paths. The shared DSH-inspired task
plan schema remains gated by NEXA_TASK_V2_ENABLED=false.

Implementation is on Rilan-Dev/personal-ai-assistant, branch Development.
GitHub is the authoritative source. A BridgeHead checkout was only used for
testing. The Nexa Android APK is not a Docker Compose application; unrelated
VPS containers were not restarted.

## Source components

- packages/agent-contracts/src/task-plan.ts — bounded immutable plan admission;
  model-provided identity, risk, evidence and approval are rejected.
- NexaConversationDb.kt — additive v3 schema, encrypted task proposal, compare-
  and-swap cancellation, append-only proposal/cancellation events, permanent
  UI-action claim tombstones, owner-only history and explicit JSON export.
- NexaConversationDbAndroidTest.kt — v1/v2 migration preservation, FK orphan
  refusal, Keystore-encrypted proposals, duplicate task IDs, irreversible cancel,
  exactly one event per transition, no native step creation, and owner export.
- apps/mobile/src/components/LocalTaskProposals.tsx and
  apps/mobile/src/native/localTaskProposals.ts — single foreground owner-review
  screen/bridge; the previous duplicate Settings UI and redundant native bridge
  were removed. A manual task is only saved, displayed or cancelled.
- apps/mobile/App.tsx — Tools places an **active message or text-entry approval
  card before** the manual task panel, preserving previously fixed mobile UAT.
- .maestro/nexa/14-local-task-proposals.yaml — intended owner flow: save,
  inspect, cancel, relaunch and confirm cancellation remains terminal.

There is no new automatic external Send, SMS, phone call, payment, arbitrary
Accessibility click, service grant or background recording.

## Evidence

| Check | Last verified result | Evidence boundary |
|---|---|---|
| Previous exact-source Android acceptance | Successful | GitHub run 41926075781 on 0724292f1380636cc8282bccc63d0ea94bf9d5da |
| Prior QA Android + Maestro | 130/130 native, 2/2 review flows on earlier QA SHA | GitHub run 41922174301; not an external-app send test |
| Current queue after consolidating duplicate UI | 380/380 source Node tests, TypeScript and Expo web export passed | BridgeHead checkout on a68dd26b5da5950ba118371e3ef46d1f4bec25d4 |
| Initial native owner-task queue run | Dispatched; outcome must be read | GitHub run 41930148723 on a4823eb06da5728ee7da2ae432a4020b0c296ad8; predates UI consolidation |
| Last current development changes | Native verification not yet claimed | Docs/QA/maestro and Tools ordering changed after a68dd26 |

The current source still needs an **exact-commit** signed Android build, emulator
Keystore/SQLite and UI results. The old native success is not proof that the
new v3 transition code compiles or works on a physical device.

## Mandatory acceptance before enabling any task execution

1. Confirm signed build identity and exact SHA; pass current native Keystore,
   database upgrade, replay, and process-restart instrumentation.
2. Run the new Maestro proposal lifecycle on the signed build; capture proof
   that no message is sent or external screen action performed. Run the existing
   owner messaging/text review flows after the new Tools panel is added.
3. Implement one trusted Kotlin-native execution coordinator, real mandate and
   step-approval enforcement, read-only observations, owner-facing task timeline,
   and crash-after-claim reconciliation. The current saved proposals do not
   create executable steps.
4. Connect Chat, Sarvam/OpenAI live voice, system assistant and overlay to the
   same task identity behind an opt-in feature gate; keep all provider output
   untrusted and never silently grant Android Accessibility.
5. Verify real authorized provider sessions, foreground/locked screen,
   navigation, microphone interruptions and in-place upgrades on a physical
   Android phone (especially OEM-specific behavior). No physical device was
   connected during this work.
6. Review the documented Expo dependency advisories and Google Play
   Accessibility-distribution constraints before promoting main.
7. Retain the existing permanent signing certificate and full rollback path.

**Release decision:** Development source milestone, not production certified.
Do not label native or physical phone acceptance complete without run/device
evidence. No unsafe migration or unapproved send is authorized by this document.
