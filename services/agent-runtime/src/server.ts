import { createServer, type IncomingMessage, type Server, type ServerResponse } from "node:http";
import { timingSafeEqual } from "node:crypto";
import type { LangGraphStepPlanner } from "./graph.ts";

type Options = {
  token: string;
  planner: LangGraphStepPlanner;
};
function sendJson(response: ServerResponse, code: number, body: unknown): void {
  response.writeHead(code, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff"
  });
  response.end(JSON.stringify(body));
}
function authorized(req: IncomingMessage, token: string): boolean {
  const value = req.headers.authorization;
  if (typeof value !== "string" || !value.startsWith("Bearer ")) return false;
  const left = Buffer.from(value.slice(7), "utf8");
  const right = Buffer.from(token, "utf8");
  return left.length === right.length && timingSafeEqual(left, right);
}
async function readBody(req: IncomingMessage): Promise<unknown> {
  let length = 0;
  let oversized = false;
  const chunks: Buffer[] = [];
  for await (const part of req) {
    const buf = Buffer.isBuffer(part) ? part : Buffer.from(part);
    length += buf.length;
    if (length > 32768) { oversized = true; break; }
    chunks.push(buf);
  }
  if (oversized) throw new Error("BODY_TOO_LARGE");
  try { return JSON.parse(Buffer.concat(chunks).toString("utf8")); }
  catch { throw new Error("INVALID_JSON"); }
}

/** Planning-only API. The HTTP server has no executor, device adapter, or UI access. */
export function createPlanningServer(options: Options): Server {
  if (options.token.length < 32) throw new Error("AGENT_API_TOKEN must contain at least 32 characters");

  return createServer(async (req, res) => {
    if (req.url === "/healthz" && req.method === "GET") {
      return sendJson(res, 200, { status: "ok", capability: "planning-only" });
    }
    if (req.url !== "/v1/plan" || req.method !== "POST") {
      return sendJson(res, 404, { error: "not_found" });
    }
    if (!authorized(req, options.token)) {
      return sendJson(res, 401, { error: "unauthorized" });
    }
    try {
      const input = await readBody(req);
      const decision = await options.planner.next(input);
      return sendJson(res, 200, { decision });
    } catch (error) {
      if (error instanceof Error && error.message === "BODY_TOO_LARGE")
        return sendJson(res, 413, { error: "request_too_large" });
      if (error instanceof Error && error.message === "INVALID_JSON")
        return sendJson(res, 400, { error: "invalid_json" });
      if (error instanceof Error && error.name === "ZodError")
        return sendJson(res, 400, { error: "invalid_planning_request" });
      // Do not leak backend keys, private prompts, model output or stack traces.
      return sendJson(res, 502, { error: "planning_unavailable" });
    }
  });
}
