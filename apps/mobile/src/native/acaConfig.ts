const UNCONFIGURED_ACA_API_ORIGIN = "https://aca-unconfigured.invalid";

/** Validate and normalize the one ACA origin shared by the JS client and the
 * native Keystore session validator. Missing Development configuration is a
 * non-routable sentinel; release callers must provide an explicit origin.
 */
export function resolveAcaApiOrigin(
  value: string | undefined,
  development: boolean
): string {
  if (value === undefined) {
    if (development) return UNCONFIGURED_ACA_API_ORIGIN;
    throw new Error("An explicit ACA API origin is required for release builds.");
  }
  if (value.length === 0 || value.trim() !== value || /[?#]/.test(value)) {
    throw new Error("ACA API origin must be a bare origin without whitespace, query, or fragment.");
  }

  let parsed: URL;
  try {
    parsed = new URL(value);
  } catch {
    throw new Error("ACA API origin is malformed.");
  }

  if (parsed.username || parsed.password) {
    throw new Error("ACA API origin cannot contain credentials.");
  }
  if (parsed.pathname !== "/" || parsed.search || parsed.hash || parsed.origin === "null") {
    throw new Error("ACA API origin cannot contain a path, query, or fragment.");
  }

  const host = parsed.hostname.toLowerCase().replace(/^\[|\]$/g, "");
  const loopback = host === "localhost" || host === "::1" ||
    /^127(?:\.\d{1,3}){3}$/.test(host);
  if (parsed.protocol === "https:") return parsed.origin;
  if (parsed.protocol === "http:" && development && loopback) return parsed.origin;
  throw new Error("ACA API origin must use HTTPS; HTTP is allowed only for Development loopback.");
}

export function getAcaApiOrigin(): string {
  const development = typeof __DEV__ !== "undefined"
    ? __DEV__
    : process.env.NODE_ENV !== "production";
  return resolveAcaApiOrigin(process.env.EXPO_PUBLIC_ACA_API_ORIGIN, development);
}
