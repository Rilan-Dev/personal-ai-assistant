/** Pure policy for the one-shot action review shown inside floating Chat. */
export function isSafeSharedOverlayActionSummary(summary:string):boolean {
  if(typeof summary!=="string"||summary.length>1800)return false;
  if(/^screen\.scroll direction=(up|down|left|right)$/.test(summary))return true;
  const tap=/^screen\.tap_label label=([0-9]|[A-Za-z][A-Za-z0-9 _.-]{1,44})$/.exec(summary);
  const blocked=/\b(send|submit|pay|purchase|buy|delete|remove|call|dial|transfer|confirm|allow|grant|install|uninstall|post|share|password|passcode|pin|otp|logout|sign out|payment|bank|security|verify)\b/i;
  if(tap)return !blocked.test(tap[1]);
  let value:unknown;
  try{value=JSON.parse(summary);}catch{return false;}
  if(!value||typeof value!=="object"||Array.isArray(value))return false;
  const action=value as Record<string,unknown>;
  if(Object.keys(action).length!==2||
      typeof action.toolId!=="string"||
      !action.input||typeof action.input!=="object"||Array.isArray(action.input))return false;
  const input=action.input as Record<string,unknown>;
  const hasKeys=(...keys:string[])=>(
    Object.keys(input).length===keys.length&&keys.every(key=>Object.hasOwn(input,key))
  );
  const app=(value:unknown):value is string=>typeof value==="string"&&
    value.length>=2&&value.length<=56&&value.trim()===value&&
    /^[a-z0-9][a-z0-9 ._+-]*$/i.test(value)&&
    !/\s{2}|\b(?:and|then|send|text|message|call|dial|delete|install|uninstall)\b/i.test(value)&&
    !new Set(["app","an app","the app"]).has(value.toLowerCase());
  const recipient=(value:unknown):value is string=>typeof value==="string"&&
    /^[\p{L}][\p{L}\p{N} '-]{1,49}$/u.test(value)&&
    !/\b(?:and|then|send|submit|pay|buy|call|delete|grant|allow|confirm|password|otp)\b/i.test(value);
  const exactText=(value:unknown):value is string=>typeof value==="string"&&
    value.length>=1&&value.length<=500&&value.trim().length>0&&
    !/[\x00-\x08\x0b-\x1f]/.test(value);
  const safeLabel=(value:unknown):value is string=>typeof value==="string"&&
    value.length>=1&&value.length<=45&&value.trim()===value&&
    ((value.length===1&&/^[0-9]$/.test(value))||
      (value.length>=2&&/^[A-Za-z][A-Za-z0-9 _.-]{1,44}$/.test(value)))&&
    !blocked.test(value);
  const safeField=(value:unknown):value is string=>safeLabel(value)&&
    typeof value==="string"&&value.length>=2&&
    !/\b(recipient|account|email address|phone number|amount|address|checkout|password|security|payment)\b/i.test(value);
  const appMessage=(appId:unknown)=>
    appId==="whatsapp"||appId==="imo"||appId==="botim";
  switch(action.toolId){
    case "device.open_app":
      return hasKeys("app")&&app(input.app);
    case "device.visible_controls":
    case "device.open_notification_shade":
      return hasKeys();
    case "screen.scroll":
      return hasKeys("direction")&&
        (input.direction==="up"||input.direction==="down"||
         input.direction==="left"||input.direction==="right");
    case "screen.tap_label":
      return hasKeys("label")&&safeLabel(input.label);
    case "screen.tap_observed":
      return hasKeys("snapshotId","index","label")&&
        typeof input.snapshotId==="string"&&
        /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(input.snapshotId)&&
        typeof input.index==="number"&&Number.isInteger(input.index)&&
        input.index>=0&&input.index<40&&safeLabel(input.label);
    case "screen.open_whatsapp_chat":
      return hasKeys("recipientHint")&&recipient(input.recipientHint);
    case "screen.fill_text":
      return hasKeys("targetLabel","exactText")&&
        safeField(input.targetLabel)&&exactText(input.exactText);
    case "screen.compose_whatsapp":
    case "screen.send_whatsapp":
      return hasKeys("recipientHint","exactText")&&
        recipient(input.recipientHint)&&exactText(input.exactText);
    case "screen.compose_message":
    case "screen.send_message":
      return hasKeys("appId","recipientHint","exactText")&&
        appMessage(input.appId)&&recipient(input.recipientHint)&&
        exactText(input.exactText);
    default:
      return false;
  }
}
