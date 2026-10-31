# Open-source dependencies and adoption gates

- Expo SDK and bare native template: https://github.com/expo/expo
  Template is MIT/0BSD-licensed as documented upstream; verify exact files and
  package metadata before distributions. React Native license is MIT.
- React Native Windows and macOS:
  https://github.com/microsoft/react-native-windows
  https://github.com/microsoft/react-native-macos
  These are future desktop adapters, not installed dependencies.
- Tauri: https://github.com/tauri-apps/tauri
  Candidate Linux desktop shell; not installed in current repository.
- LangGraph: https://github.com/langchain-ai/langgraph
  Candidate for durable agent orchestration; not yet integrated.
- MCP SDK: https://github.com/modelcontextprotocol/typescript-sdk
  Candidate for future plugin boundary; not yet integrated.
- Mobile-use automation projects are research references, not source vendored here.

Before including any library: verify project license, provenance, compatibility,
maintained release, transitive security findings, runtime privacy characteristics,
and actual device/API support. Never claim that Android-level control is portable
to iOS/web/desktop by changing only the UI.
