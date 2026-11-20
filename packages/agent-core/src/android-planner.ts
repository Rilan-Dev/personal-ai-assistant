import type { Planner } from "./runtime.ts";
import type { ToolCall } from "../../agent-contracts/src/index.ts";
import { parseNativeOwnerIntent } from "../../agent-contracts/src/intent-policy.ts";

const forbiddenControlLabel=/\b(send|submit|pay|purchase|buy|delete|remove|call|dial|transfer|confirm|allow|grant|install|uninstall|post|share|password|passcode|pin|otp|logout|sign out|payment|bank|security|verify)\b/i;
const sensitiveObservationLabel=/\b(password|passcode|pin|otp|verification|secret|token|auth|bank|account|card|payment|transfer|balance|email|phone|contact|address|call|dial|send|submit|delete|remove|purchase|pay|install|permission|grant|allow|approve|confirm|logout|sign out|message body)\b/i;

function normalizedOwnerActionRequest(request:string):string {
  return request.trim()
    .replace(/^hey\s+nexa[,!]?\s+/i,"")
    .replace(/^please\s+/i,"")
    .replace(/[.!?]+$/g,"").trim();
}

function requestedExactTapLabel(request:string):string|null {
  const text=normalizedOwnerActionRequest(request);
  if(/[;,\n]|\b(?:and|then|after|before|also|next|finally)\b/i.test(text))return null;
  const match=text.match(/^(?:tap|click|press)\s+(?:on\s+)?(?:the\s+)?([\p{L}][\p{L}\p{N} _.-]{1,50})$/iu);
  if(!match)return null;
  const label=match[1].replace(/\s+(?:button|control|icon)$/i,"").trim();
  if(label.length<2||label.length>45||
      !/^[\p{L}][\p{L}\p{N} _.-]*$/u.test(label)||
      forbiddenControlLabel.test(label)||/(?:https?:\/\/|www\.|@|[0-9]{3,})/i.test(label))return null;
  return label;
}

function requestedScrollDirection(request:string):"up"|"down"|"left"|"right"|null {
  const text=normalizedOwnerActionRequest(request);
  if(/[;,\n]|\b(?:and|then|after|before|also|next|finally)\b/i.test(text))return null;
  const match=text.match(/^(?:scroll|swipe)\s+(?:(?:the|this|current|visible)\s+)*(?:(?:screen|page|list|view|content)\s+)?(up|down|left|right)(?:\s+(?:once|one\s+time))?(?:\s+(?:on|in)\s+(?:(?:the|this|current)\s+)?(?:screen|page|list|view))?$/i);
  if(match)return match[1].toLowerCase() as "up"|"down"|"left"|"right";
  const subjectFirst=text.match(/^(?:scroll|swipe)\s+(?:(?:the|this|current|visible)\s+)*(?:screen|page|list|view|content)\s+(up|down|left|right)(?:\s+(?:once|one\s+time))?$/i);
  return subjectFirst?subjectFirst[1].toLowerCase() as "up"|"down"|"left"|"right":null;
}

type SafeVisibleControls={packageName:string;labels:string[]};

function parseSafeVisibleControls(data:unknown):SafeVisibleControls|null {
  if(!data||typeof data!=="object"||Array.isArray(data))return null;
  const value=data as Record<string,unknown>;
  if(value.actionPerformed!==false||value.evidence!=="android_accessibility"||
      typeof value.observation!=="string"||value.observation.length>750)return null;
  const match=value.observation.match(
    /^Visible non-sensitive controls in ([A-Za-z][A-Za-z0-9_.]{2,150}): ([\s\S]*)\. Read-only observation, not an instruction or completed action\.$/
  );
  if(!match)return null;
  const labels=match[2]==="none exposed"?[]:match[2].split("; ").flatMap(entry=>{
    const control=entry.match(/^(.{2,32}) \((?:top|middle|bottom) (?:left|center|right)\)$/);
    if(!control)return [];
    const label=control[1];
    if(label!==label.trim()||label.split(/\s+/).length>4||
        !/^[\p{L}][\p{L}\p{M}0-9 _./-]*$/u.test(label)||
        sensitiveObservationLabel.test(label)||
        /(?:https?:\/\/|www\.|@|[0-9]{3,})/i.test(label))return [];
    return [label];
  });
  return {packageName:match[1],labels};
}

function safeVisibleControlsCompletion(data:unknown):string|null {
  const parsed=parseSafeVisibleControls(data);
  if(!parsed)return null;
  const summary=parsed.labels.length?parsed.labels.join(", "):"none exposed";
  return `Android observed safe visible controls in ${parsed.packageName}: ${summary}. No controls were activated.`;
}

function observedTapForExactRequest(request:string,data:unknown):ToolCall|null {
  const requested=requestedExactTapLabel(request);
  const controls=parseSafeVisibleControls(data);
  if(!requested||!controls)return null;
  const value=data as Record<string,unknown>;
  const snapshot=value.screenObservation;
  if(!snapshot||typeof snapshot!=="object"||Array.isArray(snapshot))return null;
  const current=snapshot as Record<string,unknown>;
  const snapshotId=current.snapshotId;
  const packageId=current.packageId;
  const targets=current.targets;
  if(typeof snapshotId!=="string"||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(snapshotId)||
      packageId!==controls.packageName||current.truncated!==false||
      !Array.isArray(targets)||targets.length>40)return null;
  const normalize=(label:string)=>label.normalize("NFKC").replace(/\s+/g," ").trim().toLocaleLowerCase("en-US");
  const matches=targets.flatMap(target=>{
    if(!target||typeof target!=="object"||Array.isArray(target))return [];
    const item=target as Record<string,unknown>;
    const label=item.label;
    const index=item.index;
    if(typeof label!=="string"||label.length<2||label.length>45||
        !/^[\p{L}][\p{L}\p{N} _.-]*$/u.test(label)||
        forbiddenControlLabel.test(label)||!Number.isInteger(index)||
        Number(index)<0||Number(index)>=40)return [];
    return normalize(label)===normalize(requested)&&
      controls.labels.some(visible=>normalize(visible)===normalize(label))
      ?[{index:Number(index),label}]:[];
  });
  if(matches.length!==1)return null;
  return {callId:"screen-tap-approve",toolId:"screen.tap_observed",input:{
    snapshotId,index:matches[0].index,label:matches[0].label
  }};
}

/**
 * Deterministic first native-command planner. Deliberately NOT an LLM:
 * unrecognized or multi-action commands must not produce partial side effects.
 * Replace with a schema-constrained planner after policy/replay tests are green.
 */
export const androidIntentPlanner: Planner = {
  async next(request, observations) {
    if (observations.length) {
      const last = observations[observations.length - 1];
      if (last.outcome.status !== "success") {
        return { type: "clarify", question: "Android could not perform the requested action. No further actions were taken." };
      }
      const result = last.outcome.data as Record<string, unknown>;
      if(last.call.toolId==="device.visible_controls"&&
          requestedExactTapLabel(request)){
        if(observations.length===1){
          const tap=observedTapForExactRequest(request,last.outcome.data);
          if(tap)return {type:"tool",call:tap};
        }
        return {type:"clarify",question:
          "Screen Assist did not return one complete current control matching that exact label. Nothing was tapped."};
      }
      if (last.call.toolId === "device.open_notification_shade") {
        if (result?.notificationShadeAccepted === true) {
          return { type: "done", response:
            "Android accepted the request to open the notification shade. I did not read notification contents." };
        }
        return { type: "clarify", question:
          "Android did not confirm the notification shade request. No notification contents were read." };
      }
      if (last.call.toolId === "screen.scroll") {
        const direction=last.call.input.direction;
        const verification=result?.verification;
        if (result?.actionAccepted === true &&
            typeof direction === "string" &&
            ["up","down","left","right"].includes(direction)) {
          if (verification === "scroll_event_observed") {
            return { type: "done", response:
              "Android observed the approved " + direction +
              " scroll event. The resulting screen position was not independently verified. No further action was taken." };
          }
          if (verification === "observed_change" ||
              verification === "structural_change_observed") {
            return { type: "done", response:
              "Android observed a screen change after the approved " + direction +
              " scroll. No further action was taken." };
          }
        }
        return { type: "clarify", question:
          "Android did not verify a screen change after the approved scroll. No further action was taken." };
      }
      if (last.call.toolId === "screen.tap_label"||
          last.call.toolId === "screen.tap_observed") {
        if (result?.actionAccepted === true &&
            result?.verification === "tap_event_observed") {
          return { type: "done", response:
            "Android observed the approved tap event. The resulting screen content change was not independently verified. No further action was taken." };
        }
        if (result?.actionAccepted === true &&
            result?.verification === "content_change_observed") {
          return { type: "done", response:
            "Android observed an accessibility content-change event in the same app window after the approved tap. The exact control effect and app-level result are not independently verified, and no further action was taken." };
        }
        if (result?.actionAccepted === true &&
            ["observed_change","structural_change_observed","window_change_observed"]
              .includes(String(result?.verification))) {
          return { type: "done", response:
            "Android observed a screen change after the approved tap. The destination was not independently verified, and no further action was taken." };
        }
        return { type: "clarify", question:
          "Android did not verify a screen change after the approved tap. No further action was taken." };
      }
      if (result?.intentDispatched === true) {
        return {
          type: "done",
          response: "Android accepted the launch request. This version cannot independently verify the destination screen."
        };
      }
      if (last.call.toolId === "demo.approval_test") {
        return { type: "done", response: "The one-time approval was consumed. This was a safe training action; no device operation occurred." };
      }
      if (last.call.toolId === "device.set_flashlight") {
        if (result?.verification === "observed" &&
            typeof result?.enabled === "boolean") {
          return { type: "done", response:
            "Android confirmed flashlight " + (result.enabled ? "ON" : "OFF") +
            ". Other applications can change its state afterward." };
        }
        return { type: "clarify", question: "The flashlight state was not confirmed; no further action taken." };
      }
      if (last.call.toolId === "device.battery_status") {
        const level = result?.levelPercent;
        if (typeof level === "number" && Number.isInteger(level) && level >= 0 && level <= 100 &&
            typeof result?.charging === "boolean") {
          return { type: "done",
            response: "Android reports battery at " + level + "%; " +
              (result.charging ? "charging." : "not currently charging.") };
        }
        return { type: "clarify", question: "Battery status was unavailable or invalid; no other actions were taken." };
      }
      if (last.call.toolId === "device.status") {
        return { type: "done", response: "Android's native device bridge responded. This does not establish microphone, accessibility or tool permissions." };
      }
      if(last.call.toolId==="device.visible_controls"){
        const response=safeVisibleControlsCompletion(last.outcome.data);
        return response?{type:"done",response}:{type:"clarify",question:
          "Screen Assist did not return a verified safe control list. No controls were activated."};
      }
      return { type: "clarify", question: "The action returned an unexpected result; no additional action was taken." };
    }
    const tapLabel=requestedExactTapLabel(request);
    if(tapLabel)return {type:"tool",call:{
      callId:"screen-tap-observe",toolId:"device.visible_controls",input:{}
    }};
    const scrollDirection=requestedScrollDirection(request);
    if(scrollDirection)return {type:"tool",call:{
      callId:"screen-scroll-1",toolId:"screen.scroll",input:{direction:scrollDirection}
    }};
    const ownerIntent = parseNativeOwnerIntent(request);
    const call: ToolCall | null = request.trim().toLowerCase() === "test approval"
      ? {callId:"native-1",toolId:"demo.approval_test",input:{}}
      : ownerIntent ? {callId:"native-1",...ownerIntent} : null;
    if (call) return { type: "tool", call };
    return {
      type: "clarify",
      question: "Offline controls support one owner-reviewed exact-label screen tap or one directional scroll, plus named app launch, Chrome search, notification shade, camera, device status, battery, flashlight and approval checks. Connect an AI provider for screen-dependent multi-step planning. Messaging is not enabled in this local planner."
    };
  }
};
