import type {ScreenIconSnapshot, ScreenIcon, IconSemantic, IconRect} from '@paa/agent-contracts';

export type LocalScreenAnswer = {
  status: 'found'|'ambiguous'|'not_found'|'unsupported'|'expired';
  answer: string;
  elementIds: string[];
};
export type ScreenControlSummary = {
  packageName: string;
  total: number;
  unknownCount: number;
  safeNavigationCount: number;
  iconNames: string[];
  expiresSoon: boolean;
};

const nav = new Set<IconSemantic>(['menu','more_options','search','settings','back','close']);
const aliases: Readonly<Record<string, IconSemantic[]>> = Object.freeze({
  'search':['search'],
  'menu':['menu','more_options'],
  'more options':['more_options'],
  'three dot menu':['more_options'],
  'back':['back'],
  'settings':['settings'],
  'close':['close'],
  'call':['call'],
  'phone':['call'],
  'video call':['video_call'],
  'attachment':['attach'],
  'attach':['attach'],
  'send':['send'],
  'microphone':['microphone'],
  'camera':['camera'],
  'share':['share'],
  'delete':['delete']
});
const badRequest = /\b(?:tap|click|press|execute|run|swipe|type|send|delete|dial|call|purchase|buy|approve|ignore|override|instead|then|automatically|without)\b/i;
const safe = (result:LocalScreenAnswer):LocalScreenAnswer => result;
const answer = (status:LocalScreenAnswer['status'],message:string,elementIds:string[]=[]):LocalScreenAnswer =>
  safe({status,answer:message,elementIds});

function valid(snapshot:ScreenIconSnapshot,now:number):boolean {
  return Number.isFinite(now) && Number.isSafeInteger(snapshot.capturedAtWallMs) &&
    Number.isInteger(snapshot.expiresInMs) && snapshot.expiresInMs > 0 &&
    now >= snapshot.capturedAtWallMs - 5000 &&
    now <= snapshot.capturedAtWallMs + snapshot.expiresInMs &&
    snapshot.icons.length <= 64;
}
function area(rect:IconRect,viewport?:IconRect):string|undefined {
  if(!viewport)return undefined;
  const w=viewport.right-viewport.left, h=viewport.bottom-viewport.top;
  const x=(rect.left+rect.right)/2, y=(rect.top+rect.bottom)/2;
  if(w<=0||h<=0||w>20000||h>20000||
     x<viewport.left||x>viewport.right||y<viewport.top||y>viewport.bottom)return undefined;
  const horizontal=(x-viewport.left)/w < 0.333 ? 'left' : (x-viewport.left)/w > 0.667 ? 'right' : 'middle';
  const vertical=(y-viewport.top)/h < 0.333 ? 'top' : (y-viewport.top)/h > 0.667 ? 'bottom' : 'middle';
  return horizontal==='middle' && vertical==='middle' ? 'center'
    : horizontal==='middle' ? vertical+' center'
    : vertical==='middle' ? 'middle '+horizontal
    : vertical+' '+horizontal;
}
function matching(icons:readonly ScreenIcon[],semantics:readonly IconSemantic[]):ScreenIcon[]{
  return icons.filter(i=>semantics.includes(i.semantic));
}
function queryKind(raw:string):{kind:'summary'|'search'|'unsupported';key?:string}{
  const q=raw.toLowerCase().trim().replace(/[?!.]+$/,'').replace(/\s+/g,' ');
  if(q.length < 5 || q.length > 100 || badRequest.test(q) || /[^a-z0-9 '\-]/.test(q))
    return {kind:'unsupported'};
  if(['what icons are on my screen','what icons are on screen','list visible icons',
      'list icons','which icons are visible'].includes(q))return {kind:'summary'};
  const m=/^(?:where is|where's|find|locate|show me|identify)\s+(.+)$/.exec(q);
  if(!m)return {kind:'unsupported'};
  const key=m[1].replace(/^(?:the|my|a)\s+/,'')
    .replace(/\s+(?:icon|button|control)$/,'').trim();
  if(!Object.prototype.hasOwnProperty.call(aliases,key))return {kind:'unsupported'};
  return {kind:'search',key};
}

/**
 * Read-only, deterministic localization. Never produces a click tool,
 * target token, mutable capability, coordinate, or model-executable plan.
 * Fresh snapshots must be explicitly selected by the user.
 */
export function resolveScreenQuestion(
  snapshot:ScreenIconSnapshot,question:string,now:number
):LocalScreenAnswer {
  if(!valid(snapshot,now))return answer('expired','This screen observation expired. Scan again.');
  const query=queryKind(question);
  if(query.kind==='unsupported')return answer('unsupported',
    'I can only locate a supported icon or list visible icons. No action was taken.');
  if(query.kind==='summary'){
    const details=summarizeScreenControls(snapshot,now);
    return answer('found',
      details.total === 0 ? 'No labeled icons were available in this scan.'
      : details.iconNames.slice(0,12).join(', ') +
        (details.total>12?' and other visible icons':'') +
        '. This is read-only information; no action was taken.');
  }
  const semanticNames=aliases[query.key || ''] || [];
  const found=matching(snapshot.icons,semanticNames);
  if(found.length === 0)return answer('not_found',
    'That icon was not recognized in the current accessibility scan. Try a visual icon scan; no action was taken.');
  if(found.length>1)return answer('ambiguous',
    'Multiple matching controls are visible. Inspect them individually rather than guessing. No action was taken.',
    found.slice(0,8).map(x=>x.id));
  const icon=found[0];
  const where=area(icon.boundsPx,snapshot.windowBoundsPx);
  const position=where ? ' near the '+where+' of the app window' : ' in the visible app';
  const warning=nav.has(icon.semantic)?'Use the floating Nexa Tools panel to review and separately approve navigation.'
    :'This control may have sensitive effects and is not eligible for general screen automation.';
  return answer('found',icon.label+' was identified'+position+'. '+warning+
    ' This is read-only information; no action was taken.',[icon.id]);
}

export function summarizeScreenControls(snapshot:ScreenIconSnapshot,now:number):ScreenControlSummary {
  if(!valid(snapshot,now))return {
    packageName:snapshot.packageName,total:0,unknownCount:0,safeNavigationCount:0,
    iconNames:[],expiresSoon:true
  };
  const icons=snapshot.icons;
  return {packageName:snapshot.packageName,total:icons.length,
    unknownCount:icons.filter(x=>x.semantic==='unknown').length,
    safeNavigationCount:icons.filter(x=>x.clickable && nav.has(x.semantic)).length,
    iconNames:icons.filter(x=>x.semantic!=='unknown').slice(0,32).map(x=>x.label),
    expiresSoon:snapshot.capturedAtWallMs+snapshot.expiresInMs-now<10000};
}
