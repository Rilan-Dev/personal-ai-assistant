import assert from "node:assert/strict";
import test from "node:test";
import {readFileSync} from "node:fs";

const source=readFileSync(
  "apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/NexaLauncherSwipe.kt",
  "utf8"
).replace(/\r\n/g,"\n");

const intersects=(a,b)=>a.left<=b.right&&b.left<=a.right;
function chooseEmptyBand(bounds,blockers,startX,endX,padding=16){
  const top=bounds.top+Math.round(bounds.height*0.04);
  const bottom=bounds.bottom-Math.round(bounds.height*0.04);
  const left=Math.min(startX,endX),right=Math.max(startX,endX);
  const intervals=blockers.filter(r=>intersects(r,{left,right}))
    .map(r=>[Math.max(top,r.top-padding),Math.min(bottom,r.bottom+padding)])
    .filter(([start,end])=>end>=start)
    .sort((a,b)=>a[0]-b[0]);
  let cursor=top,bestStart=-1,bestEnd=-1;
  for(const [start,end] of intervals){
    if(start>cursor&&start-cursor>bestEnd-bestStart){bestStart=cursor;bestEnd=start;}
    cursor=Math.max(cursor,end);
  }
  if(bottom>cursor&&bottom-cursor>bestEnd-bestStart){bestStart=cursor;bestEnd=bottom;}
  return bestEnd-bestStart>=Math.max(24,Math.round(bounds.width*0.04))
    ?(bestStart+bestEnd)/2:null;
}

test("Home swipe selects the widest empty horizontal band in the live launcher tree",()=>{
  const bounds={left:0,top:0,right:1080,bottom:2352,width:1080,height:2352};
  const blockers=[
    {left:72,top:816,right:516,bottom:1062},
    {left:585,top:831,right:987,bottom:987},
    {left:540,top:1086,right:1032,bottom:1674},
    {left:47,top:1175,right:465,bottom:1535},
    {left:48,top:1674,right:1032,bottom:1968},
    {left:333,top:2028,right:747,bottom:2076},
    {left:48,top:2136,right:1032,bottom:2352}
  ];
  const startX=bounds.left+bounds.width*0.78;
  const endX=bounds.left+bounds.width*0.22;
  const y=chooseEmptyBand(bounds,blockers,startX,endX);
  assert.ok(y!==null,"the launcher has a clear workspace band");
  assert.ok(y<816,"the chosen band avoids the app-icon row used by the old midpoint");
  assert.equal(blockers.some(r=>intersects(r,{left:Math.min(startX,endX),right:Math.max(startX,endX)})&&
    y>=r.top-16&&y<=r.bottom+16),false,"the swipe path avoids every interactive item");
});

test("native launcher swipes inspect tappable bounds and fail closed on an incomplete tree",()=>{
  assert.match(source,/findEmptyHorizontalBandY\(/,
    "Home gesture placement is derived from the current accessibility tree");
  assert.match(source,/node\.isClickable\s*\|\|\s*node\.isLongClickable\s*\|\|\s*node\.isFocusable\s*\|\|\s*node\.isScrollable/s,
    "visible interactive launcher items must block the swipe line");
  assert.match(source,/if\(queue\.isNotEmpty\(\)\)\s*\{[\s\S]{0,350}?return null/s,
    "a truncated launcher tree cannot authorize a guessed swipe location");
  assert.match(source,/if\(child\.isVisibleToUser\)\s*queue\.addLast\(child\)/,
    "off-screen launcher branches must not consume the visible-tree scan budget");
  assert.match(source,/bestGap|widestGap|largestGap/,
    "the path must be selected from a clear vertical gap");
  assert.doesNotMatch(source,/val y\s*=\s*bounds\.top\s*\+\s*bounds\.height\(\)\s*\*\s*0\.52f/,
    "the old fixed midpoint intersects Home app icons and widgets");
});
