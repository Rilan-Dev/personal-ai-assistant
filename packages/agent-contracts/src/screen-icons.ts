/** Read-only UI observations. These values never authorize an Android action. */
export type IconSemantic =
  | 'call' | 'video_call' | 'menu' | 'back' | 'search' | 'attach'
  | 'microphone' | 'camera' | 'send' | 'settings' | 'share'
  | 'delete' | 'close' | 'more_options' | 'unknown';

export type IconEvidence = 'accessibility' | 'vision' | 'package_manager';
export type IconRect = { left: number; top: number; right: number; bottom: number };
export type ScreenIcon = {
  id: string;
  label: string;
  semantic: IconSemantic;
  boundsPx: IconRect;
  clickable: boolean;
  evidence: IconEvidence;
};
export type ScreenIconSnapshot = {
  version: 1;
  snapshotId: string;
  packageName: string;
  capturedAtWallMs: number;
  expiresInMs: number;
  icons: ScreenIcon[];
  /** App-window rectangle captured alongside icons. Never a target for actions. */
  windowBoundsPx?: IconRect;
  /** There is deliberately no screenshot or arbitrary page text in this contract. */
};

const names: Readonly<Record<string, IconSemantic>> = {
  'call': 'call', 'voice call': 'call', 'phone': 'call',
  'video call': 'video_call', 'video': 'video_call',
  'menu': 'menu', 'navigation menu': 'menu',
  'back': 'back', 'navigate back': 'back',
  'search': 'search', 'search icon': 'search',
  'attach': 'attach', 'attachment': 'attach', 'attach file': 'attach',
  'microphone': 'microphone', 'mic': 'microphone', 'voice message': 'microphone',
  'camera': 'camera', 'take photo': 'camera',
  'send': 'send', 'send message': 'send',
  'settings': 'settings', 'share': 'share',
  'delete': 'delete', 'trash': 'delete', 'close': 'close',
  'more options': 'more_options', 'more': 'more_options'
};
export function iconSemanticFromLabel(label: unknown): IconSemantic {
  if (typeof label !== 'string' || label.length > 80) return 'unknown';
  const normalized = label.trim().toLowerCase().replace(/\s+/g, ' ').replace(/ button$/, '');
  return Object.prototype.hasOwnProperty.call(names, normalized) ? names[normalized] : 'unknown';
}
const semantics = new Set<IconSemantic>([...Object.values(names), 'unknown']);
const evidences = new Set<IconEvidence>(['accessibility', 'vision', 'package_manager']);
const obj = (x: unknown): x is Record<string, unknown> =>
  x !== null && typeof x === 'object' && !Array.isArray(x);
const boundedString = (x: unknown, max: number): x is string =>
  typeof x === 'string' && x.length > 0 && x.length <= max;
const coordinate = (x: unknown) => Number.isInteger(x) && (x as number) >= 0 && (x as number) <= 20000;

/** Fail closed when bridge data is malformed. Only return approved, non-text metadata. */
export function parseScreenIconSnapshot(input: unknown): ScreenIconSnapshot | null {
  if (!obj(input) || input.version !== 1 || !boundedString(input.snapshotId, 80) ||
      !boundedString(input.packageName, 150) || !/^[-a-zA-Z0-9._]+$/.test(input.packageName) ||
      !Number.isSafeInteger(input.capturedAtWallMs) ||
      !Number.isInteger(input.expiresInMs) || (input.expiresInMs as number) <= 0 ||
      (input.expiresInMs as number) > 60_000 ||
      !Array.isArray(input.icons) || input.icons.length > 64) return null;
  let windowBoundsPx: IconRect | undefined;
  if (input.windowBoundsPx !== undefined) {
    const b=input.windowBoundsPx;
    if (!obj(b) || !coordinate(b.left) || !coordinate(b.top) ||
        !coordinate(b.right) || !coordinate(b.bottom) ||
        (b.right as number) <= (b.left as number) ||
        (b.bottom as number) <= (b.top as number)) return null;
    windowBoundsPx={left:b.left as number,top:b.top as number,
      right:b.right as number,bottom:b.bottom as number};
  }
  const icons: ScreenIcon[] = [];
  const ids = new Set<string>();
  for (const item of input.icons) {
    if (!obj(item) || !boundedString(item.id, 40) || ids.has(item.id) ||
        !boundedString(item.label, 48) || !semantics.has(item.semantic as IconSemantic) ||
        !evidences.has(item.evidence as IconEvidence) || typeof item.clickable !== 'boolean' ||
        !obj(item.boundsPx)) return null;
    const b = item.boundsPx;
    if (!coordinate(b.left) || !coordinate(b.top) || !coordinate(b.right) ||
        !coordinate(b.bottom) || (b.right as number) <= (b.left as number) ||
        (b.bottom as number) <= (b.top as number)) return null;
    // Never accept a claimed semantic different from the local label classification.
    if (item.semantic !== iconSemanticFromLabel(item.label) && item.semantic !== 'unknown') return null;
    ids.add(item.id);
    icons.push({ id: item.id, label: item.label, semantic: item.semantic as IconSemantic,
      evidence: item.evidence as IconEvidence, clickable: item.clickable,
      boundsPx: {left: b.left as number, top: b.top as number,
        right: b.right as number, bottom: b.bottom as number} });
  }
  return {version: 1, snapshotId: input.snapshotId,
    packageName: input.packageName, capturedAtWallMs: input.capturedAtWallMs as number,
    expiresInMs: input.expiresInMs as number, icons,
    ...(windowBoundsPx ? {windowBoundsPx} : {})};
}
