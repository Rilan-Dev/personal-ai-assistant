import React, { useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator, KeyboardAvoidingView, Platform, Pressable, SafeAreaView, ScrollView,
  StatusBar, StyleSheet, Text, TextInput, View
} from 'react-native';
import { AgentHarness, ToolRegistry, createDemoTools, demoPlanner, androidIntentPlanner, createRemotePlanner, validateAgentEndpoint } from '@paa/agent-core';
import { createAndroidTools, nativeDeviceAvailable } from './src/native/androidTools';
import type { AgentTraceEvent, Capability } from '@paa/agent-contracts';

type ChatMessage = { id: number; speaker: 'user' | 'assistant'; text: string };
const initialMessages: ChatMessage[] = [
  { id: 1, speaker: 'assistant', text: 'Welcome! This assistant is under active development. Native Android builds can launch the camera or allowlisted apps after you enable app launches; other platforms use clearly marked demo tools.' }
];
const quickPrompts = ['Open camera', 'Open WhatsApp', 'Open WhatsApp Business', 'Check device status'];
const makeEngine = () => {
  const registry = new ToolRegistry();
  const live = nativeDeviceAvailable();
  (live ? createAndroidTools() : createDemoTools()).forEach(tool => registry.register(tool));
  return {
    registry,
    harness: new AgentHarness(registry, live ? androidIntentPlanner : demoPlanner),
    toolCount: registry.describe().length,
    live
  };
};

export default function App() {
  const engine = useMemo(makeEngine, []);
  const [messages, setMessages] = useState(initialMessages);
  const [input, setInput] = useState('');
  const [running, setRunning] = useState(false);
  const [events, setEvents] = useState<AgentTraceEvent[]>([]);
  const [capabilities, setCapabilities] = useState<Capability[]>([]);
  const [showBackend, setShowBackend] = useState(false);
  const [backendUrl, setBackendUrl] = useState('');
  const [backendToken, setBackendToken] = useState('');
  const [remoteActive, setRemoteActive] = useState(false);
  const [backendError, setBackendError] = useState('');
  const counter = useRef(2);
  const activeAbort = useRef<AbortController | null>(null);

  const append = (speaker: ChatMessage['speaker'], text: string) => {
    setMessages(prev => [...prev, { id: counter.current++, speaker, text }]);
  };
  const toggle = (cap: Capability) => {
    setCapabilities(prev => prev.includes(cap) ? prev.filter(c => c !== cap) : [...prev, cap]);
  };
  const enableBackend = () => {
    try {
      validateAgentEndpoint(backendUrl);
      createRemotePlanner({
        baseUrl: backendUrl,
        token: backendToken,
        availableToolIds: engine.registry.describe().map(tool => tool.id)
      });
      setBackendError('');
      setRemoteActive(true);
    } catch {
      setBackendError('Enter a valid HTTPS agent URL and a token containing at least 32 characters.');
      setRemoteActive(false);
    }
  };
  const disableBackend = () => {
    setRemoteActive(false);
    setBackendToken('');
    setBackendError('');
  };
  const send = async (value: string = input) => {
    const request = value.trim();
    if (!request || running) return;
    setInput(''); setEvents([]); append('user', request);
    const controller = new AbortController(); activeAbort.current = controller;
    setRunning(true);
    try {
      const harness = remoteActive && engine.live
        ? new AgentHarness(engine.registry, createRemotePlanner({
            baseUrl: backendUrl,
            token: backendToken,
            availableToolIds: engine.registry.describe().map(tool => tool.id)
          }))
        : engine.harness;
      const result = await harness.run({
        request,
        sessionId: engine.live ? 'local-android' : 'local-demo',
        grantedCapabilities: new Set(capabilities),
        signal: controller.signal,
        maxSteps: 6,
        onEvent: event => setEvents(prev => [...prev, event])
      });
      if (result.status === 'completed') append('assistant', result.response);
      else if (result.status === 'awaiting_approval') append('assistant', 'Approval required. No action was executed. The approval UI will be implemented in a later phase.');
      else append('assistant', `Task ${result.status}: ${result.reason}`);
    } finally { setRunning(false); activeAbort.current = null; }
  };
  const stop = () => activeAbort.current?.abort();
  return (
    <SafeAreaView style={styles.root}>
      <StatusBar barStyle="light-content" />
      <View style={styles.header}>
        <View style={styles.logo}><Text style={styles.logoText}>AI</Text></View>
        <View style={{flex: 1}}><Text style={styles.title}>Personal AI Assistant</Text><Text style={styles.subtitle}>Agentic tool harness • {remoteActive ? 'LangGraph planning' : 'Local planning'}</Text></View>
        <View style={styles.demoLabel}><Text style={styles.demoLabelText}>{engine.live ? 'ANDROID' : 'DEMO'}</Text></View>
      </View>
      <View style={styles.infoBanner}>
        <Text style={styles.bannerTitle}>{engine.live ? 'Native Android intents available' : 'Simulation mode enabled'}</Text>
        <Text style={styles.bannerText}>{engine.live ? 'This build may launch the camera and WhatsApp using Android intents. No microphone, accessibility, message sending, location or screen verification is connected yet.' : 'This platform has no Android native bridge. Device actions are simulated and nothing is sent.'}</Text>
      </View>
      {engine.live && <View style={styles.backendBox}>
        <Pressable accessibilityRole="button" disabled={running} onPress={() => setShowBackend(previous => !previous)}>
          <Text style={styles.bannerTitle}>Agent backend: {remoteActive ? 'LangGraph (remote)' : 'Local deterministic planner'}  {showBackend ? '▲' : '▼'}</Text>
        </Pressable>
        {showBackend && <View style={{gap: 8, paddingTop: 10}}>
          <Text style={styles.bannerText}>Optional: sends your command and minimal action outcomes to YOUR selected backend. No contacts, screen captures, or private tool results are forwarded. Uses a per-session token kept in memory only.</Text>
          <TextInput
            accessibilityLabel="Agent server HTTPS URL"
            placeholder="https://your-agent-server.example"
            placeholderTextColor="#9ca3af"
            autoCapitalize="none"
            autoCorrect={false}
            value={backendUrl}
            onChangeText={setBackendUrl}
            editable={!remoteActive && !running}
            style={styles.input}
          />
          <TextInput
            accessibilityLabel="Agent server access token"
            placeholder="Agent access token"
            placeholderTextColor="#9ca3af"
            autoCapitalize="none"
            autoCorrect={false}
            secureTextEntry
            value={backendToken}
            onChangeText={setBackendToken}
            editable={!remoteActive && !running}
            style={styles.input}
          />
          {backendError !== '' && <Text style={styles.warning}>{backendError}</Text>}
          <Pressable accessibilityRole="button" disabled={running} onPress={remoteActive ? disableBackend : enableBackend} style={[styles.capabilityChip, remoteActive && styles.chipActive]}>
            <Text style={styles.chipText}>{remoteActive ? 'Disconnect backend and clear token' : 'Enable remote LangGraph planning'}</Text>
          </Pressable>
        </View>}
      </View>}
      <View style={styles.capabilityBar}>
        <Text style={styles.sectionLabel}>{engine.live ? 'Owner-controlled tool access (in-app)' : 'Simulated capability grants'}</Text>
        <View style={styles.controlsRow}>
          <Pressable accessibilityRole="button" onPress={() => toggle('apps.launch')} style={[styles.capabilityChip, capabilities.includes('apps.launch') && styles.chipActive]}>
            <Text style={styles.chipText}>{capabilities.includes('apps.launch') ? '✓ ' : '+ '}{engine.live ? 'Allow app launches' : 'App launch demo'}</Text>
          </Pressable>
          {!engine.live && <Pressable accessibilityRole="button" onPress={() => toggle('messaging.compose')} style={[styles.capabilityChip, capabilities.includes('messaging.compose') && styles.chipActive]}>
            <Text style={styles.chipText}>{capabilities.includes('messaging.compose') ? '✓ ' : '+ '}Message draft demo</Text>
          </Pressable>}
        </View>
      </View>
      <ScrollView style={styles.chat} contentContainerStyle={{ padding: 18, gap: 14 }}>
        {messages.map(message => <View key={message.id} style={[styles.bubble, message.speaker === 'user' ? styles.userBubble : styles.assistantBubble]}>
          <Text style={styles.speaker}>{message.speaker === 'user' ? 'YOU' : 'ASSISTANT'}</Text>
          <Text style={styles.messageText}>{message.text}</Text>
        </View>)}
        {running && <View style={styles.busy}><ActivityIndicator color="#a5b4fc" /><Text style={styles.muted}>Running bounded agent loop…</Text></View>}
        {events.length > 0 && <View style={styles.traceCard}>
          <Text style={styles.sectionLabel}>Last task trace</Text>
          {events.slice(-6).map((e, i) => <Text key={i} style={styles.traceLine}>• {e.type}{e.type === 'executing' ? `: ${e.call.toolId}` : ''}</Text>)}
        </View>}
      </ScrollView>
      <View style={styles.footer}>
        <ScrollView horizontal keyboardShouldPersistTaps="always" showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8, paddingBottom: 10 }}>
          {quickPrompts.map(prompt => <Pressable key={prompt} onPress={() => send(prompt)} disabled={running} style={styles.prompt}><Text style={styles.promptText}>{prompt}</Text></Pressable>)}
        </ScrollView>
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <View style={styles.inputRow}>
            <TextInput style={styles.input} value={input} onChangeText={setInput} placeholder="Ask your assistant…" placeholderTextColor="#9ca3af" onSubmitEditing={() => send()} returnKeyType="send" accessibilityLabel="Assistant command" />
            {running ? <Pressable accessibilityRole="button" style={styles.stopButton} onPress={stop}><Text style={styles.buttonText}>Stop</Text></Pressable> : <Pressable accessibilityRole="button" style={styles.sendButton} onPress={() => send()}><Text style={styles.buttonText}>Send</Text></Pressable>}
          </View>
          <Text style={styles.note}>{engine.live ? 'Android Intent tools active. Voice, accessibility and messaging are not implemented.' : 'Android native module not available; tools are demo-only.'} {engine.toolCount} tools registered.</Text>
        </KeyboardAvoidingView>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#0d1220' },
  header: { paddingHorizontal: 18, paddingVertical: 17, backgroundColor: '#111827', flexDirection: 'row', alignItems: 'center', gap: 12 },
  logo: { width: 40, height: 40, backgroundColor: '#4f46e5', borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  logoText: { color: '#fff', fontWeight: '900', fontSize: 16 },
  title: { fontSize: 17, fontWeight: '700', color: '#f9fafb' },
  subtitle: { fontSize: 11, marginTop: 3, color: '#9ca3af' },
  demoLabel: { backgroundColor: '#374151', padding: 7, borderRadius: 9 },
  demoLabelText: { fontSize: 10, fontWeight: '800', color: '#e5e7eb' },
  infoBanner: { margin: 14, padding: 13, backgroundColor: '#292047', borderColor: '#59418a', borderWidth: 1, borderRadius: 12 },
  bannerTitle: { fontSize: 13, fontWeight: '700', color: '#e9d5ff' },
  bannerText: { color: '#c4b5fd', marginTop: 5, fontSize: 12, lineHeight: 17 },
  capabilityBar: { paddingHorizontal: 16, paddingBottom: 12 },
  backendBox: { marginHorizontal: 14, marginBottom: 14, padding: 12, backgroundColor: '#111827', borderColor: '#374151', borderWidth: 1, borderRadius: 12 },
  warning: { color: '#fecaca', fontSize: 12 },
  sectionLabel: { color: '#9ca3af', fontSize: 11, fontWeight: '700', letterSpacing: 0.6, marginBottom: 7 },
  controlsRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  capabilityChip: { borderWidth: 1, borderColor: '#475569', backgroundColor: '#1f2937', borderRadius: 25, paddingVertical: 8, paddingHorizontal: 11 },
  chipActive: { borderColor: '#818cf8', backgroundColor: '#312e81' },
  chipText: { color: '#e5e7eb', fontSize: 12 },
  chat: { flex: 1 },
  bubble: { padding: 14, borderRadius: 16, maxWidth: '94%' },
  userBubble: { alignSelf: 'flex-end', backgroundColor: '#312e81' },
  assistantBubble: { alignSelf: 'flex-start', backgroundColor: '#1f2937', borderColor: '#374151', borderWidth: 1 },
  speaker: { color: '#a5b4fc', fontSize: 10, fontWeight: '800', marginBottom: 8 },
  messageText: { color: '#f3f4f6', fontSize: 14, lineHeight: 21 },
  busy: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  muted: { color: '#a5b4fc', fontSize: 12 },
  traceCard: { borderRadius: 12, padding: 12, borderWidth: 1, borderColor: '#374151' },
  traceLine: { fontSize: 12, color: '#9ca3af', paddingVertical: 2 },
  footer: { borderTopWidth: 1, borderTopColor: '#1f2937', paddingHorizontal: 14, paddingVertical: 10 },
  prompt: { borderRadius: 9, borderColor: '#374151', backgroundColor: '#1f2937', paddingHorizontal: 12, paddingVertical: 8, borderWidth: 1 },
  promptText: { fontSize: 12, color: '#c7d2fe' },
  inputRow: { flexDirection: 'row', gap: 8, alignItems: 'center' },
  input: { flex: 1, backgroundColor: '#1f2937', borderColor: '#475569', borderWidth: 1, color: '#fff', borderRadius: 12, paddingHorizontal: 14, paddingVertical: 12, fontSize: 14 },
  sendButton: { backgroundColor: '#4f46e5', borderRadius: 12, padding: 14 },
  stopButton: { backgroundColor: '#991b1b', borderRadius: 12, padding: 14 },
  buttonText: { color: '#fff', fontWeight: '700', fontSize: 13 },
  note: { textAlign: 'center', color: '#94a3b8', fontSize: 10, paddingTop: 10 },
});
