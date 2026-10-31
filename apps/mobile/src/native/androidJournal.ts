import { NativeModules, Platform } from "react-native";
import type { DurableActionJournal, SessionCheckpoint } from "@paa/agent-core";

interface NativeJournalBridge {
  newSessionId(): Promise<string>;
  loadSession(sessionId: string): Promise<string | null>;
  latestSession(): Promise<string | null>;
  saveSession(sessionId: string, json: string): Promise<boolean>;
  prepareApproval(sessionId: string, callId: string, fingerprint: string): Promise<boolean>;
  approveAction(sessionId: string, callId: string, fingerprint: string): Promise<boolean>;
  claimAction(sessionId: string, callId: string, fingerprint: string, requiresApproval: boolean): Promise<boolean>;
  finishAction(sessionId: string, callId: string, uncertain: boolean): Promise<boolean>;
}
function bridge(): NativeJournalBridge {
  const native = NativeModules.PAAAgentJournal;
  if (Platform.OS !== "android" || typeof native?.newSessionId !== "function" ||
      typeof native?.claimAction !== "function" || typeof native?.saveSession !== "function") {
    throw new Error("Secure Android journal unavailable; device actions disabled");
  }
  return native as NativeJournalBridge;
}

export function androidJournalAvailable(): boolean {
  return Platform.OS === "android" && typeof NativeModules.PAAAgentJournal?.newSessionId === "function" &&
    typeof NativeModules.PAAAgentJournal?.claimAction === "function" &&
    typeof NativeModules.PAAAgentJournal?.saveSession === "function";
}

export const androidActionJournal: DurableActionJournal = {
  async loadSession(id) {
    const raw = await bridge().loadSession(id);
    if (raw === null) return null;
    const value = JSON.parse(raw) as SessionCheckpoint;
    if (value.version !== 1 || value.sessionId !== id || !Array.isArray(value.observations) ||
        value.observations.length > 32 || typeof value.request !== "string") {
      throw new Error("Invalid saved session; no action permitted");
    }
    return value;
  },
  async saveSession(snapshot) {
    if (!await bridge().saveSession(snapshot.sessionId, JSON.stringify(snapshot))) {
      throw new Error("Secure session write failed");
    }
  },
  prepareApproval: (id, call, fingerprint) =>
    bridge().prepareApproval(id, call, fingerprint),
  approveAction: (id, call, fingerprint) =>
    bridge().approveAction(id, call, fingerprint),
  claimAction: (id, call, fingerprint, needsApproval) =>
    bridge().claimAction(id, call, fingerprint, needsApproval),
  async finishAction(id, call, uncertain) {
    if (!await bridge().finishAction(id, call, uncertain)) {
      throw new Error("Action reconciliation failed");
    }
  }
};

export async function newAndroidSessionId(): Promise<string> {
  return bridge().newSessionId();
}
export async function readLastAndroidSession(): Promise<SessionCheckpoint | null> {
  const id = await bridge().latestSession();
  return id ? androidActionJournal.loadSession(id) : null;
}
