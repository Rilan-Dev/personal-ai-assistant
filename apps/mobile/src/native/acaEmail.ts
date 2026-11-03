import { NativeModules, Platform } from "react-native";

/** First-party, feature-gated ACA read-only email client. Device credentials
 * are separate from BYOK AI keys and never enter the Android tool planner.
 */
export const ACA_ORIGIN = "https://aca.starcitytechnical.com";
const PREFIX = "nexa_m1_";
export type AcaSession = {
  version: 1; origin: typeof ACA_ORIGIN; token: string; expiresAt: string;
  organizationId: string; agentId: string;
};
export type AcaOrganization = {
  id: string; name: string; role: string;
  agents: Array<{id:string;name:string}>;
};
export type AcaSummary = {
  report: string; source: "local_mail_database";
  matchedCount: number; includedCount: number; hasMore: boolean;
  lastSyncAt: string | null; fromUtc: string; toUtc: string;
};
type NativeAcaVault = {
  saveAcaSession(payload:string):Promise<boolean>;
  loadAcaSession():Promise<string|null>;
  clearAcaSession():Promise<boolean>;
};
const sessionPattern = /^nexa_m1_[A-Za-z0-9_-]{43}$/;
function nativeVault():NativeAcaVault {
  const module=NativeModules.PAAAgentJournal;
  if (Platform.OS !== "android" || !module ||
      typeof module.saveAcaSession !== "function" ||
      typeof module.loadAcaSession !== "function" ||
      typeof module.clearAcaSession !== "function")
    throw new Error("Secure ACA account storage is unavailable.");
  return module as NativeAcaVault;
}
export function acaVaultAvailable():boolean {
  const module=NativeModules.PAAAgentJournal;
  return Platform.OS === "android" && Boolean(module &&
    typeof module.saveAcaSession === "function" &&
    typeof module.loadAcaSession === "function" &&
    typeof module.clearAcaSession === "function");
}
export function validAcaSession(input:unknown):input is AcaSession {
  if (!input || typeof input !== "object" || Array.isArray(input))return false;
  const x=input as Record<string,unknown>;
  return x.version === 1 && x.origin === ACA_ORIGIN &&
    typeof x.token === "string" && sessionPattern.test(x.token) &&
    typeof x.expiresAt === "string" && Number.isFinite(Date.parse(x.expiresAt)) &&
    typeof x.organizationId === "string" && /^[0-9a-f-]{0,36}$/i.test(x.organizationId) &&
    typeof x.agentId === "string" && /^[0-9a-f-]{0,36}$/i.test(x.agentId);
}
export async function loadAcaSession():Promise<AcaSession|null> {
  const saved=await nativeVault().loadAcaSession();
  if (saved === null) return null;
  const parsed:unknown=JSON.parse(saved);
  return validAcaSession(parsed) && Date.parse(parsed.expiresAt)>Date.now()?parsed:null;
}
export async function storeAcaSession(session:AcaSession):Promise<void> {
  if (!validAcaSession(session)) throw new Error("Invalid ACA session");
  if (!await nativeVault().saveAcaSession(JSON.stringify(session)))throw new Error("Secure session storage failed");
}
export async function clearAcaSession():Promise<void> {
  if (!await nativeVault().clearAcaSession())throw new Error("Secure session removal failed");
}
async function requestJson(path:string,options:RequestInit = {}):Promise<unknown> {
  const response=await fetch(ACA_ORIGIN+path,{...options,headers:{
    "Accept":"application/json",
    ...(options.body ? {"Content-Type":"application/json"} : {}),
    ...options.headers
  }});
  if (!response.ok) {
    if (response.status===401)throw new Error("Session expired or login rejected. Reconnect your ACA account.");
    if (response.status===403)throw new Error("ACA access denied. Check your organization and email permissions.");
    if (response.status===404)throw new Error("Nexa mobile email access is not enabled on the ACA server.");
    throw new Error("ACA request unavailable (HTTP "+response.status+").");
  }
  const body=await response.text();
  if (body.length>280000)throw new Error("ACA response too large.");
  return JSON.parse(body) as unknown;
}
function bearer(session:AcaSession) {
  if (!validAcaSession(session) || Date.parse(session.expiresAt)<=Date.now())
    throw new Error("ACA session expired. Reconnect in Settings.");
  return {Authorization:"Bearer "+session.token};
}
export async function connectAca(email:string,password:string):Promise<AcaSession> {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || !password)
    throw new Error("Enter your ACA account email and password.");
  const data=await requestJson("/api/mobile/v1/session",{
    method:"POST",body:JSON.stringify({email:email.trim(),password})
  }) as {accessToken?:unknown;expiresAt?:unknown};
  const session:AcaSession={
    version:1,origin:ACA_ORIGIN,token:String(data.accessToken ?? ""),
    expiresAt:String(data.expiresAt ?? ""),organizationId:"",agentId:""
  };
  if (!validAcaSession(session))throw new Error("ACA returned an invalid device session.");
  await storeAcaSession(session);
  return session;
}
export async function acaTargets(session:AcaSession):Promise<AcaOrganization[]> {
  const response=await requestJson("/api/mobile/v1/targets",{
    method:"GET",headers:bearer(session)
  }) as {organizations?:AcaOrganization[]};
  if (!Array.isArray(response.organizations))throw new Error("Invalid ACA organization response.");
  return response.organizations.filter(o=>typeof o.id==="string"&&typeof o.name==="string"&&Array.isArray(o.agents));
}
export async function acaMailSummary(session:AcaSession,options:{unreadOnly:boolean;inboxOnly:boolean;window:"all"|"24h"},signal?:AbortSignal):Promise<AcaSummary> {
  if (!session.organizationId || !session.agentId)throw new Error("Select an organization and agent in ACA settings.");
  const params=new URLSearchParams({
    organizationId:session.organizationId,agentId:session.agentId,
    unreadOnly:String(options.unreadOnly),inboxOnly:String(options.inboxOnly),window:options.window
  });
  const response=await requestJson("/api/mobile/v1/mail/summary?"+params.toString(),{
    method:"GET",headers:bearer(session),signal
  }) as AcaSummary;
  if (!response || response.source!=="local_mail_database" ||
    typeof response.report!=="string" || !Number.isFinite(response.matchedCount))
    throw new Error("Invalid ACA mail summary response.");
  return response;
}
export async function disconnectAca(session:AcaSession|null):Promise<void> {
  if (session && validAcaSession(session)) {
    try {await requestJson("/api/mobile/v1/session",{method:"DELETE",headers:bearer(session)});}
    catch { /* Local Keystore token is removed even if the server is unavailable. */ }
  }
  await clearAcaSession();
}

export { parseAcaMailSummaryIntent } from "./acaMailIntent";
