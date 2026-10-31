import type { ToolCall, ToolOutcome } from "../../agent-contracts/src/index.ts";

/**
 * Persistent device-owned state. All production implementations must guarantee:
 * - saveSession is durable before the next action,
 * - claimAction is atomic and durable BEFORE dispatch,
 * - a previously claimed action can never be claimed again,
 * - grants are explicit, single-use and bound to an exact fingerprint.
 *
 * The Android implementation is encrypted with an Android Keystore-backed key.
 */
export type ObservedAction = { call: ToolCall; outcome: ToolOutcome };
export type SessionStatus =
  | "running" | "awaiting_approval" | "completed" | "blocked" | "failed" | "cancelled";
export type SessionCheckpoint = {
  version: 1;
  sessionId: string;
  request: string;
  status: SessionStatus;
  observations: ObservedAction[];
  pendingCall?: ToolCall;
  updatedAt: number;
};

export interface DurableActionJournal {
  loadSession(sessionId: string): Promise<SessionCheckpoint | null>;
  saveSession(snapshot: SessionCheckpoint): Promise<void>;
  prepareApproval(sessionId: string, callId: string, fingerprint: string): Promise<boolean>;
  approveAction(sessionId: string, callId: string, fingerprint: string): Promise<boolean>;
  claimAction(sessionId: string, callId: string, fingerprint: string, requiresApproval: boolean): Promise<boolean>;
  finishAction(sessionId: string, callId: string, uncertain: boolean): Promise<void>;
}

/** Non-persistent test fixture ONLY. Never use for real device operations. */
export class InMemoryActionJournal implements DurableActionJournal {
  private sessions = new Map<string, SessionCheckpoint>();
  private actions = new Map<string, { state: "pending" | "approved" | "claimed" | "settled" | "uncertain"; fingerprint: string }>();
  private key(session: string, call: string) { return JSON.stringify([session, call]); }

  async loadSession(id: string): Promise<SessionCheckpoint | null> {
    const found = this.sessions.get(id);
    return found ? JSON.parse(JSON.stringify(found)) as SessionCheckpoint : null;
  }
  async saveSession(snapshot: SessionCheckpoint): Promise<void> {
    this.sessions.set(snapshot.sessionId, JSON.parse(JSON.stringify(snapshot)) as SessionCheckpoint);
  }
  async prepareApproval(session: string, call: string, fingerprint: string): Promise<boolean> {
    const k = this.key(session, call), existing = this.actions.get(k);
    if (existing) return existing.fingerprint === fingerprint && ["pending", "approved"].includes(existing.state);
    this.actions.set(k, { state: "pending", fingerprint }); return true;
  }
  async approveAction(session: string, call: string, fingerprint: string): Promise<boolean> {
    const v = this.actions.get(this.key(session,call));
    if (!v || v.state !== "pending" || v.fingerprint !== fingerprint) return false;
    v.state = "approved"; return true;
  }
  async claimAction(session: string, call: string, fingerprint: string, requiresApproval: boolean): Promise<boolean> {
    const k=this.key(session,call), old=this.actions.get(k);
    if (requiresApproval) {
      if (!old || old.state !== "approved" || old.fingerprint !== fingerprint) return false;
    } else if (old) return false;
    this.actions.set(k,{state:"claimed",fingerprint}); return true;
  }
  async finishAction(session: string, call: string, uncertain: boolean): Promise<void> {
    const v=this.actions.get(this.key(session,call));
    if (!v || v.state !== "claimed") throw Error("Claim missing or already final");
    v.state=uncertain?"uncertain":"settled";
  }
  /** Test inspection; not a production approval interface. */
  state(session: string, call: string): string | undefined { return this.actions.get(this.key(session,call))?.state; }
}
