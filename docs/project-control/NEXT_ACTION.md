# Next actions

1. Verify GitHub main contains Expo Android/iOS source, Kotlin bridge and shared agent packages.
2. Reproduce npm ci, npm test, npm run typecheck and web export; record actual results.
3. Android build requires JDK + Android SDK and a real device/emulator.
4. On Android, test that default capability-off blocks launch, enable in-app grant,
   open camera and WhatsApp, test absent WhatsApp, and verify no messages sent.
5. iOS native compile requires macOS/Xcode. Neither iOS nor Android end-to-end passed yet.
6. Next sprint: schema-constrained model planner; add push-to-talk after authorization tests.
7. Defer wake word, accessibility, messaging, contacts, notification access and MCP
   until permission threat models and device acceptance tests are ready.
