/**
 * Bounded, model-assisted launcher reconnaissance. The model sees only
 * aggregate five-app observations, never raw Accessibility nodes/text, app
 * contents, screenshots, contacts or coordinates. It may SUGGEST one
 * horizontal HOME swipe; Android remains the only gesture authority.
 *
 * The first pilot deliberately DOES NOT tap/open WhatsApp, IMO, Contacts,
 * Phone or Clock. This prevents a fake LLM success claim for a sensitive
 * third-party task until an independently verified real-device acceptance.
 */
export const HOME_PILOT_TARGETS = [
  "whatsapp", "imo", "contacts", "calls", "clock"
] as const;
export type HomePilotTarget = typeof HOME_PILOT_TARGETS[number];
export type HomePilotView = {
  kind:"home"|"other"|"locked"|"unavailable";
  revision:string;
  /** Current visible launcher page only; no screen text or coordinates. */
  unique:readonly HomePilotTarget[];
  ambiguous:readonly HomePilotTarget[];
};
export type HomePilotProposal =
  | {kind:"swipe";direction:"left"|"right"}
  | {kind:"stop"};
export type HomePilotReceipt =
  | {status:"observed_change";fromRevision:string;toRevision:string}
  | {status:"unknown"|"blocked"|"unverified"};
export type HomePilotPort = {
  observe:()=>Promise<HomePilotView>;
  /** Model input is strictly the five allowed keys and a revision. */
  propose:(current:HomePilotView,remaining:readonly HomePilotTarget[],
           attempts:number)=>Promise<unknown>;
  /** Native one-shot approval+journal+fresh target check. Never coordinates. */
  dispatchReviewed:(proposal:HomePilotProposal,revision:string)=>Promise<HomePilotReceipt>;
  ownerAllowsNavigation:()=>Promise<boolean>;
  cancelled?:()=>boolean;
};
export type HomePilotResult={
  status:"passed"|"incomplete"|"blocked"|"uncertain"|"cancelled";
  observed:readonly HomePilotTarget[];
  missing:readonly HomePilotTarget[];
  swipes:number;
  reason:string;
};

const allowed=new Set<string>(HOME_PILOT_TARGETS);
const maxSwipes=8;
function safeView(raw:HomePilotView):HomePilotView|null {
  if(!raw || raw.kind!=="home" || typeof raw.revision!=="string" ||
      raw.revision.length<8 || raw.revision.length>120 ||
      !Array.isArray(raw.unique) || !Array.isArray(raw.ambiguous) ||
      raw.unique.some(v=>!allowed.has(v)) ||
      raw.ambiguous.some(v=>!allowed.has(v)) ||
      new Set(raw.unique).size!==raw.unique.length ||
      new Set(raw.ambiguous).size!==raw.ambiguous.length ||
      raw.unique.some(v=>raw.ambiguous.includes(v)))
      return null;
  return raw;
}
function validateProposal(raw:unknown):HomePilotProposal|null{
  if(!raw || typeof raw!=="object" || Array.isArray(raw))return null;
  const p=raw as Record<string,unknown>;
  if(Object.keys(p).some(k=>!["kind","direction"].includes(k)))return null;
  if(p.kind==="stop" && p.direction===undefined && Object.keys(p).length===1)
    return {kind:"stop"};
  if(p.kind==="swipe" && (p.direction==="left"||p.direction==="right") &&
     Object.keys(p).length===2)
    return {kind:"swipe",direction:p.direction};
  return null;
}
export async function runFiveAppHomePilot(port:HomePilotPort):Promise<HomePilotResult>{
  const seen=new Set<HomePilotTarget>();
  let swipes=0;
  const result=(status:HomePilotResult["status"],reason:string):HomePilotResult=>({
    status,reason,swipes,observed:HOME_PILOT_TARGETS.filter(v=>seen.has(v)),
    missing:HOME_PILOT_TARGETS.filter(v=>!seen.has(v))
  });
  const seenRevisions=new Set<string>();
  let expectedNextRevision:string|null=null;
  if(!await port.ownerAllowsNavigation())return result("blocked","Owner's safe-navigation permission is not active.");
  for(let step=0;step<=maxSwipes;step++){
    if(port.cancelled?.())return result("cancelled","Owner stopped the pilot.");
    const observation=await port.observe().catch(()=>null);
    if(!observation||observation.kind!=="home")
      return result("blocked","The device is not on its unlocked home launcher.");
    const view=safeView(observation);
    if(!view)return result("blocked","The home observation is incomplete or malformed.");
    // A dispatch receipt alone cannot move the pilot. Reacquire EXACTLY
    // the home revision that Android independently recorded after the swipe.
    if(expectedNextRevision!==null && view.revision!==expectedNextRevision)
      return result("uncertain","Observed home page does not match the verified Android swipe receipt.");
    expectedNextRevision=null;
    for(const v of view.unique)seen.add(v);
    if(seen.size===HOME_PILOT_TARGETS.length)
      return result("passed","All five app icons uniquely observed on verified home pages; no app was opened.");
    if(step===maxSwipes)return result("incomplete","Maximum eight reviewed home swipes reached.");
    if(seenRevisions.has(view.revision))
      return result("uncertain","The launcher page did not advance to a unique new revision.");
    seenRevisions.add(view.revision);
    const remaining=HOME_PILOT_TARGETS.filter(v=>!seen.has(v));
    const raw=await port.propose(
      {kind:"home",revision:view.revision,unique:[...view.unique],
       ambiguous:[...view.ambiguous]},remaining,swipes).catch(()=>null);
    const proposal=validateProposal(raw);
    if(!proposal)return result("blocked","Model proposed an unsupported or sensitive action.");
    if(proposal.kind==="stop")return result("incomplete","Model stopped before all five app icons were uniquely observed.");
    if(!await port.ownerAllowsNavigation())
      return result("blocked","Owner revoked navigation trust before dispatch.");
    // A receipt must include two distinct, explicit page revisions. Mere
    // Android gesture ACCEPTED is not proof of a completed page turn.
    const receipt=await port.dispatchReviewed(proposal,view.revision)
      .catch(()=>({status:"unknown" as const}));
    swipes++;
    if(receipt.status!=="observed_change")
      return result("uncertain","Gesture outcome was not independently verified; no retry.");
    if(receipt.fromRevision!==view.revision ||
       receipt.toRevision===receipt.fromRevision ||
       receipt.toRevision.length<8 ||
       seenRevisions.has(receipt.toRevision))
      return result("uncertain","Launcher revision evidence changed unexpectedly.");
    expectedNextRevision=receipt.toRevision;
    // The NEXT observe must reacquire the live launcher AND match this
    // observed revision. No receipt itself can complete a target or task.
  }
  return result("incomplete","Pilot stopped at its configured bound.");
}
