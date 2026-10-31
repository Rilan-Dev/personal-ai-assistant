import { createPlanningServer } from "./server.ts";
import { LangGraphStepPlanner } from "./graph.ts";
import { createConfiguredModel } from "./provider.ts";

const token = process.env.AGENT_API_TOKEN;
if (!token) throw new Error("AGENT_API_TOKEN missing");
const planner = new LangGraphStepPlanner(createConfiguredModel());
const server = createPlanningServer({ token, planner });
const host = process.env.AGENT_BIND_HOST ?? "127.0.0.1";
const port = Number(process.env.AGENT_PORT ?? "8741");
if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error("Invalid AGENT_PORT");
// Loopback by default. Remote production access requires HTTPS gateway and
// per-device credentials; never expose an HTTP port directly to the Internet.
if (host !== "127.0.0.1" && host !== "::1" && host !== "localhost" &&
    process.env.AGENT_ALLOW_EXTERNAL_BIND !== "yes") {
  throw new Error("Non-loopback binding requires explicit AGENT_ALLOW_EXTERNAL_BIND=yes");
}
server.listen(port, host, () => {
  console.log("Agent planning API listening on " + host + ":" + port);
});
for (const sig of ["SIGINT", "SIGTERM"] as const) {
  process.on(sig, () => server.close(() => process.exit(0)));
}
