import type {ToolCall,ToolMetadata} from "../../agent-contracts/src/index.ts";
import type {DurableActionJournal} from "./journal.ts";

const outboundActionWords=new Set([
  "send","call","pay","payment","transfer","purchase","buy"
]);

/** Messages, calls and payments always get a separate, exact owner review. */
export function requiresExplicitOutboundReview(
  tool:Pick<ToolMetadata,"id"|"capabilities">
):boolean {
  if(tool.capabilities.includes("messaging.send") ||
      tool.capabilities.includes("calls.place"))return true;
  return tool.id.toLowerCase().split(/[._-]+/).some(word=>outboundActionWords.has(word));
}

function extractCall(sessionId:string,callId:string,fingerprint:string):ToolCall|null {
  try{
    const decoded:unknown=JSON.parse(fingerprint);
    if(!Array.isArray(decoded)||decoded.length!==4||
        decoded[0]!==sessionId||decoded[1]!==callId||
        typeof decoded[2]!=="string"||!decoded[3]||
        typeof decoded[3]!=="object"||Array.isArray(decoded[3]))return null;
    return {callId,toolId:decoded[2],input:decoded[3] as Record<string,unknown>};
  }catch{return null;}
}

/**
 * Apply the owner's explicit Full Access choice to registered agent tools.
 * The encrypted one-use journal still prepares and claims every action before
 * dispatch. Android's native pause, unlock, OS-grant and target checks remain
 * authoritative. Outbound messages, calls and payments stay owner-reviewed.
 */
export function withFullAccessApprovals(
  journal:DurableActionJournal,
  tools:readonly ToolMetadata[],
  canAutoApprove:(toolId:string)=>Promise<boolean>,
  active:boolean
):DurableActionJournal {
  const knownTools=new Map(tools.map(tool=>[tool.id,tool]));
  return {
    loadSession:id=>journal.loadSession(id),
    saveSession:snapshot=>journal.saveSession(snapshot),
    approveAction:(id,call,fingerprint)=>journal.approveAction(id,call,fingerprint),
    claimAction:(id,call,fingerprint,requiresApproval)=>
      journal.claimAction(id,call,fingerprint,requiresApproval),
    finishAction:(id,call,uncertain)=>journal.finishAction(id,call,uncertain),
    async prepareApproval(sessionId,callId,fingerprint){
      const prepared=await journal.prepareApproval(sessionId,callId,fingerprint);
      if(!prepared||!active)return prepared;
      const call=extractCall(sessionId,callId,fingerprint);
      const tool=call&&knownTools.get(call.toolId);
      // Unknown tools, malformed fingerprints and irreversible outbound
      // actions retain the ordinary exact-action owner review.
      if(!tool||requiresExplicitOutboundReview(tool))return true;
      const allowed=await canAutoApprove(tool.id).catch(()=>false);
      if(!allowed)return false;
      try{
        // A resumed task may already carry the same exact one-time approval.
        // Let claimAction decide: it succeeds only for that fingerprint and
        // still rejects pending, claimed, uncertain or mismatched records.
        await journal.approveAction(sessionId,callId,fingerprint);
      }catch{return false;}
      return true;
    }
  };
}
