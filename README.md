# Personal AI Assistant

Owner-controlled, cross-platform AI assistant. Android-first, TypeScript agent harness, React Native UI, Kotlin native device adapter. Early development: do not rely on for messaging or emergencies.

## Security note
This repository is currently PUBLIC on GitHub. Do not commit personal data, signing credentials, contacts, API keys or conversations.

## Current state
- Expo SDK 58 bare-native foundation with Android and iOS directories.
- Shared typed tools and bounded agent execution with explicit capability and approval gates.
- Demo chat tools and a native Android open-app / camera module under development.
- No always-on wake word, LLM provider, WhatsApp sender, accessibility automation, or background microphone yet.

## Commands
Requires Node.js 22.13+; Android Studio and Android SDK for APK builds; macOS + Xcode for iOS.

    npm ci
    npm test
    npm run typecheck
    npm run start:mobile
    npm run android

Web is experimental. Desktop apps are not present in this phase.
See docs/project-control/NEXT_ACTION.md for acceptance gates.
