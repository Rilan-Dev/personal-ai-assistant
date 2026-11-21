export type PowerShellToolMetadata = {
  id: string;
  risk: "low" | "medium" | "high";
  hasExternalSideEffect: boolean;
  capabilities?: readonly string[];
};

export type PowerShellRequest =
  | { kind: "status" | "tools" }
  | { kind: "observe"; format: "text" | "json" }
  | { kind: "tap"; snapshotId: string; index: number }
  | { kind: "scroll"; snapshotId: string; direction: "up" | "down" | "left" | "right" }
  | { kind: "key"; name: "back" | "home" | "notifications" }
  | { kind: "readArm" | "readReview" }
  | { kind: "invoke"; toolId: string; input: Record<string, unknown>; confirmed: boolean }
  | { kind: "error"; error: string };

const requestIdPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const snapshotIdPattern = requestIdPattern;
const safeTools = new Set([
  "device.status",
  "device.battery_status",
  "device.storage_status",
  "device.set_flashlight",
  "device.browser_search",
  "device.open_notification_shade",
  "device.open_camera",
  "device.open_app",
  "screen.tap_index",
  "screen.scroll",
  "screen.open_whatsapp_chat",
  "screen.compose_whatsapp",
  "screen.fill_text",
  "screen.inspect",
  "screen.accessibility_tree",
  "screen.key_action"
]);

const bad = (error: string): PowerShellRequest => ({ kind: "error", error });
const plainObject = (value: unknown): value is Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value);

function parseBody(value: unknown): Record<string, unknown> | null {
  if (typeof value === "string") {
    if (value.length > 16_384) return null;
    if (!value.trim()) return {};
    try {
      const parsed: unknown = JSON.parse(value);
      return plainObject(parsed) ? parsed : null;
    } catch {
      return null;
    }
  }
  return plainObject(value) ? value : null;
}

/** Validate the bounded host-to-app request before it reaches a tool. */
export function parsePowerShellRequest(value: unknown): PowerShellRequest {
  if (!plainObject(value) ||
      typeof value.requestId !== "string" ||
      !requestIdPattern.test(value.requestId) ||
      typeof value.method !== "string" ||
      typeof value.path !== "string") return bad("INVALID_REQUEST");

  const method = value.method.toUpperCase();
  const body = parseBody(value.body);
  if (body === null) return bad("INVALID_BODY");

  if (method === "GET" && value.path === "/v1/status") return { kind: "status" };
  if (method === "GET" && value.path === "/v1/tools") return { kind: "tools" };
  if (method !== "POST") return bad("METHOD_NOT_ALLOWED");

  if (value.path === "/v1/observe") {
    if (body.confirmed !== true) return bad("CONFIRM_REQUIRED");
    if (body.format !== "text" && body.format !== "json") return bad("INVALID_FORMAT");
    return { kind: "observe", format: body.format };
  }
  if (value.path === "/v1/tap") {
    if (body.confirmed !== true) return bad("CONFIRM_REQUIRED");
    if (typeof body.snapshotId !== "string" || !snapshotIdPattern.test(body.snapshotId) ||
        !Number.isSafeInteger(body.index) || Number(body.index) < 0 || Number(body.index) > 39)
      return bad("INVALID_TAP_TARGET");
    return { kind: "tap", snapshotId: body.snapshotId, index: Number(body.index) };
  }
  if (value.path === "/v1/scroll") {
    if (body.confirmed !== true) return bad("CONFIRM_REQUIRED");
    if (typeof body.snapshotId !== "string" || !snapshotIdPattern.test(body.snapshotId) ||
        !["up", "down", "left", "right"].includes(String(body.direction)))
      return bad("INVALID_SCROLL");
    return { kind: "scroll", snapshotId: body.snapshotId,
      direction: body.direction as "up" | "down" | "left" | "right" };
  }
  if (value.path === "/v1/key") {
    if (body.confirmed !== true) return bad("CONFIRM_REQUIRED");
    if (!["back", "home", "notifications"].includes(String(body.name)))
      return bad("INVALID_KEY");
    return { kind: "key", name: body.name as "back" | "home" | "notifications" };
  }
  if (value.path === "/v1/readarm" || value.path === "/v1/readreview") {
    if (body.confirmed !== true) return bad("CONFIRM_REQUIRED");
    if (Object.keys(body).length !== 1) return bad("INVALID_BODY");
    return { kind: value.path === "/v1/readarm" ? "readArm" : "readReview" };
  }
  if (value.path === "/v1/invoke") {
    if (typeof body.toolId !== "string" || !safeTools.has(body.toolId) ||
        !plainObject(body.input) ||
        JSON.stringify(body.input).length > 8192)
      return bad("TOOL_NOT_ALLOWED");
    return { kind: "invoke", toolId: body.toolId, input: body.input,
      confirmed: body.confirmed === true };
  }
  return bad("NOT_FOUND");
}

export function isPowerShellToolAllowed(toolId: string): boolean {
  return safeTools.has(toolId);
}

export function getPowerShellToolMetadata<T extends PowerShellToolMetadata>(
  tools: readonly T[]
): T[] {
  return tools.filter(tool => safeTools.has(tool.id));
}

export function requiresPowerShellConfirmation(tool: PowerShellToolMetadata): boolean {
  return tool.hasExternalSideEffect || tool.risk !== "low" ||
    (tool.capabilities ?? []).includes("device.observe");
}

