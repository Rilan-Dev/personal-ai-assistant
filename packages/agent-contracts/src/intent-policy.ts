/**
 * Initial product boundary: the only native commands supported end-to-end
 * are single-step, owner-requested status and allowlisted app/camera launches.
 * A model suggesting a syntactically valid but unrelated tool is NOT enough
 * to prove user intent. Expand via dedicated, evaluated multi-step policies.
 */
export function requestAllowsNativeTool(
  request: string,
  toolId: string,
  input: Record<string, unknown>
): boolean {
  const normalized = request.toLowerCase().trim()
    .replace(/^hey\s+(nexa|assistant)[,!]?\s+/, "")
    .replace(/[.!?]+$/, "").trim();
  if (toolId === "device.status") {
    return /^(check|show|what is|what's) (my |the )?(agent |device )?status$/.test(normalized);
  }
  if (toolId === "device.battery_status") {
    return /^(?:check|show|what is|what's) (?:my |the )?(?:device |phone )?battery(?: level| status)?$/.test(normalized);
  }
  if (toolId === "device.open_camera") {
    return /^(?:please )?(?:open|launch|start|turn on) (?:my |the )?camera(?: please)?$/.test(normalized);
  }
  if (toolId === "device.open_app") {
    const match = normalized.match(/^(?:please )?(?:open|launch|start) (?:my |the )?(whatsapp business|whatsapp)(?: please)?$/);
    return !!match && input.app === (match[1] === "whatsapp business" ? "whatsapp-business" : "whatsapp");
  }
  return false;
}
