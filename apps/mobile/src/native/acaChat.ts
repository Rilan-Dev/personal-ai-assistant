import { getAcaApiOrigin } from "./acaConfig.ts";
import type { AcaSession } from "./acaEmail";

const SESSION_TOKEN = /^nexa_m1_[A-Za-z0-9_-]{43}$/;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MAX_MESSAGE_BYTES = 12_000;
const MAX_EVENT_BYTES = 32_768;

export type AcaChatEvent =
  | { type: "conversation"; conversationId: string }
  | { type: "status"; phase: string }
  | { type: "progress"; phase: string }
  | { type: "delta"; text: string }
  | {
      type: "done";
      conversationId: string;
      provider?: string;
      model?: string;
      source?: string;
      complete?: boolean;
      matchedCount?: number;
      includedCount?: number;
      hasMore?: boolean;
      lastSyncAt?: string | null;
    }
  | { type: "error"; message: string; code?: string };

export class AcaChatRequestError extends Error {
  readonly httpStatus: number;
  constructor(httpStatus: number, message: string) {
    super(message);
    this.name = "AcaChatRequestError";
    this.httpStatus = httpStatus;
  }
}

function validString(value: unknown, maxLength = 200): value is string {
  return typeof value === "string" && value.length > 0 && value.length <= maxLength;
}

function eventFromPayload(eventName: string, value: unknown): AcaChatEvent {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new Error("ACA returned a malformed chat event.");
  }
  const payload = value as Record<string, unknown>;
  switch (eventName) {
    case "conversation":
      if (typeof payload.conversationId !== "string" || !UUID.test(payload.conversationId)) {
        throw new Error("ACA returned a malformed conversation event.");
      }
      return { type: "conversation", conversationId: payload.conversationId };
    case "status":
    case "progress":
      if (!validString(payload.phase, 80)) throw new Error("ACA returned a malformed chat status event.");
      return { type: eventName, phase: payload.phase };
    case "delta":
      if (typeof payload.text !== "string" || payload.text.length > MAX_EVENT_BYTES) {
        throw new Error("ACA returned a malformed chat text event.");
      }
      return { type: "delta", text: payload.text };
    case "done": {
      if (typeof payload.conversationId !== "string" || !UUID.test(payload.conversationId)) {
        throw new Error("ACA returned a malformed chat completion event.");
      }
      for (const key of ["provider", "model", "source"] as const) {
        if (payload[key] !== undefined && !validString(payload[key], 120)) {
          throw new Error("ACA returned malformed chat completion metadata.");
        }
      }
      for (const key of ["matchedCount", "includedCount"] as const) {
        if (payload[key] !== undefined && (!Number.isSafeInteger(payload[key]) || Number(payload[key]) < 0)) {
          throw new Error("ACA returned malformed chat coverage metadata.");
        }
      }
      if (payload.complete !== undefined && typeof payload.complete !== "boolean") {
        throw new Error("ACA returned malformed chat completion metadata.");
      }
      if (payload.hasMore !== undefined && typeof payload.hasMore !== "boolean") {
        throw new Error("ACA returned malformed chat coverage metadata.");
      }
      if (payload.lastSyncAt !== undefined && payload.lastSyncAt !== null &&
          (typeof payload.lastSyncAt !== "string" || payload.lastSyncAt.length > 80)) {
        throw new Error("ACA returned malformed chat sync metadata.");
      }
      return {
        type: "done",
        conversationId: payload.conversationId,
        ...(typeof payload.provider === "string" ? { provider: payload.provider } : {}),
        ...(typeof payload.model === "string" ? { model: payload.model } : {}),
        ...(typeof payload.source === "string" ? { source: payload.source } : {}),
        ...(typeof payload.complete === "boolean" ? { complete: payload.complete } : {}),
        ...(typeof payload.matchedCount === "number" ? { matchedCount: payload.matchedCount } : {}),
        ...(typeof payload.includedCount === "number" ? { includedCount: payload.includedCount } : {}),
        ...(typeof payload.hasMore === "boolean" ? { hasMore: payload.hasMore } : {}),
        ...(payload.lastSyncAt === null || typeof payload.lastSyncAt === "string"
          ? { lastSyncAt: payload.lastSyncAt }
          : {}),
      };
    }
    case "error":
      if (!validString(payload.message, 2000)) throw new Error("ACA returned a malformed chat error event.");
      if (payload.code !== undefined && !validString(payload.code, 120)) {
        throw new Error("ACA returned malformed chat error metadata.");
      }
      return { type: "error", message: payload.message, ...(typeof payload.code === "string" ? { code: payload.code } : {}) };
    default:
      throw new Error("ACA returned an unsupported chat event.");
  }
}

function validateRequest(session: AcaSession, input: {
  organizationId: string;
  agentId: string;
  conversationId?: string;
  message: string;
}): string {
  const origin = getAcaApiOrigin();
  if (!session || session.version !== 1 || session.origin !== origin ||
      typeof session.token !== "string" || !SESSION_TOKEN.test(session.token) ||
      !Number.isFinite(Date.parse(session.expiresAt)) || Date.parse(session.expiresAt) <= Date.now()) {
    throw new Error("ACA session is invalid or expired. Reconnect your ACA account.");
  }
  if (!UUID.test(input.organizationId) || !UUID.test(input.agentId) ||
      (input.conversationId !== undefined && !UUID.test(input.conversationId))) {
    throw new Error("Select a valid ACA organization and agent before starting Chat.");
  }
  if (session.organizationId !== input.organizationId || session.agentId !== input.agentId) {
    throw new Error("The selected ACA organization or agent does not match this session. Refresh ACA settings.");
  }
  if (typeof input.message !== "string" || !input.message.trim() ||
      new TextEncoder().encode(input.message).byteLength > MAX_MESSAGE_BYTES) {
    throw new Error("ACA chat message is empty or too large.");
  }
  return origin;
}

function httpError(status: number): AcaChatRequestError {
  if (status === 401) return new AcaChatRequestError(401, "ACA session expired or is invalid. Reconnect your ACA account.");
  if (status === 403) return new AcaChatRequestError(403, "ACA denied access to this organization, agent, or operation.");
  if (status === 404) return new AcaChatRequestError(404, "ACA mobile Chat is not enabled on this server.");
  return new AcaChatRequestError(status, `ACA chat request unavailable (HTTP ${status}).`);
}

/** Stream one authorized ACA Chat turn. Provider selection and all business
 * services stay server-side; this client sends only scope selectors and text.
 */
export async function streamAcaChatTurn(
  session: AcaSession,
  input: {
    organizationId: string;
    agentId: string;
    conversationId?: string;
    message: string;
    signal: AbortSignal;
  },
  onEvent: (event: AcaChatEvent) => void,
): Promise<void> {
  if (input.signal.aborted) return;
  const origin = validateRequest(session, input);
  const body = JSON.stringify({
    organizationId: input.organizationId,
    agentId: input.agentId,
    ...(input.conversationId ? { conversationId: input.conversationId } : {}),
    message: input.message.trim(),
  });

  let response: Response;
  try {
    response = await fetch(`${origin}/api/mobile/v1/chat/stream`, {
      method: "POST",
      headers: {
        Accept: "text/event-stream",
        "Content-Type": "application/json",
        Authorization: `Bearer ${session.token}`,
      },
      credentials: "omit",
      redirect: "error",
      body,
      signal: input.signal,
    });
  } catch {
    if (input.signal.aborted) return;
    throw new Error("ACA chat connection is unavailable.");
  }
  if (!response.ok) throw httpError(response.status);
  if (!/^text\/event-stream(?:\s*;|$)/i.test(response.headers.get("content-type") ?? "")) {
    throw new Error("ACA returned an unsupported chat response.");
  }
  if (!response.body) throw new Error("ACA returned an empty chat stream.");

  const reader = response.body.getReader();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  const encoder = new TextEncoder();
  let pending = "";
  let eventName = "";
  let dataLines: string[] = [];
  let frameBytes = 0;
  let terminal = false;

  const resetFrame = () => {
    eventName = "";
    dataLines = [];
    frameBytes = 0;
  };

  const dispatchFrame = (): boolean => {
    if (!eventName && dataLines.length === 0) {
      resetFrame();
      return false;
    }
    if (!eventName || dataLines.length === 0) throw new Error("ACA returned an incomplete chat event.");
    let payload: unknown;
    try {
      payload = JSON.parse(dataLines.join("\n")) as unknown;
    } catch {
      throw new Error("ACA returned malformed chat event data.");
    }
    const event = eventFromPayload(eventName, payload);
    onEvent(event);
    terminal = event.type === "done" || event.type === "error";
    resetFrame();
    return terminal || input.signal.aborted;
  };

  const processLine = (line: string): boolean => {
    frameBytes += encoder.encode(line).byteLength + 1;
    if (frameBytes > MAX_EVENT_BYTES) throw new Error("ACA chat event is too large.");
    if (line === "") return dispatchFrame();
    if (line.startsWith(":")) return false;
    const separator = line.indexOf(":");
    const field = separator < 0 ? line : line.slice(0, separator);
    let value = separator < 0 ? "" : line.slice(separator + 1);
    if (value.startsWith(" ")) value = value.slice(1);
    if (field === "event") eventName = value;
    else if (field === "data") dataLines.push(value);
    return false;
  };

  try {
    while (!terminal && !input.signal.aborted) {
      const { done, value } = await reader.read();
      if (done) {
        pending += decoder.decode();
        break;
      }
      pending += decoder.decode(value, { stream: true });
      let newline = pending.indexOf("\n");
      let stop = false;
      while (newline >= 0) {
        const line = pending.slice(0, newline).replace(/\r$/, "");
        pending = pending.slice(newline + 1);
        if (processLine(line)) {
          stop = true;
          break;
        }
        newline = pending.indexOf("\n");
      }
      if (stop) break;
      if (encoder.encode(pending).byteLength + frameBytes > MAX_EVENT_BYTES) {
        throw new Error("ACA chat event is too large.");
      }
    }

    if (input.signal.aborted) return;
    if (!terminal) {
      if (pending) processLine(pending.replace(/\r$/, ""));
      if (!terminal && (eventName || dataLines.length)) dispatchFrame();
    }
    if (!terminal) throw new Error("ACA chat stream ended before completion.");
  } catch (error) {
    if (input.signal.aborted) return;
    throw error;
  } finally {
    try {
      if (!terminal || input.signal.aborted) await reader.cancel();
    } catch { /* A closed/aborted stream has no remaining reader to cancel. */ }
    try { reader.releaseLock(); } catch { /* The stream already released its lock. */ }
  }
}
