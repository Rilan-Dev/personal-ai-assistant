/** Memory-only owner feedback on an explicitly reviewed icon guess.
 * It is not calibrated model confidence or permission to control Android.
 */
export type IconReviewOffer={
  snapshotId:string;cropId:string;modelLabel:string;
  evidenceStatus:'tentative'|'corroborated'|'blocked';
  score:number;expiresAtWallMs:number;
};
export type IconReviewSummary={reviewed:number;accepted:number;rejected:number};
export type IconReviewResult={status:'recorded'|'refused';actionEligible:false};
const allowedLabels=new Set(['search','menu','settings','back','close','call',
  'video call','send','delete','camera','share','attach','microphone','more options']);
const boundedId=(s:string,n:number)=>typeof s==='string'&&s.length>0&&s.length<=n&&
  /^[a-zA-Z0-9_.:-]+$/.test(s);
export function createIconReviewSession(){
  let pending:IconReviewOffer|null=null;
  const voted=new Set<string>();
  let accepted=0,rejected=0;
  const key=(a:string,b:string)=>a+'\u0000'+b;
  return {
    offer(input:IconReviewOffer,now:number):boolean{
      pending=null;
      if(!input || !boundedId(input.snapshotId,80)||!boundedId(input.cropId,40)||
        !allowedLabels.has(input.modelLabel.toLowerCase())||
        input.evidenceStatus==='blocked' ||
        !Number.isInteger(input.score)||input.score<1||input.score>85||
        !Number.isSafeInteger(input.expiresAtWallMs)||!Number.isFinite(now)||
        input.expiresAtWallMs<=now||input.expiresAtWallMs-now>60000||
        voted.has(key(input.snapshotId,input.cropId)))return false;
      pending={...input};
      return true;
    },
    vote(snapshotId:string,cropId:string,choice:'accurate'|'inaccurate',now:number):IconReviewResult{
      const offer=pending;
      if(!offer||offer.snapshotId!==snapshotId||offer.cropId!==cropId||
        (choice!=='accurate'&&choice!=='inaccurate')||
        !Number.isFinite(now)||now>=offer.expiresAtWallMs||
        voted.has(key(snapshotId,cropId)))return {status:'refused',actionEligible:false};
      pending=null;
      voted.add(key(snapshotId,cropId));
      if(choice==='accurate')accepted++;else rejected++;
      return {status:'recorded',actionEligible:false};
    },
    summary():IconReviewSummary{return {reviewed:accepted+rejected,accepted,rejected};},
    clear():void{pending=null;voted.clear();accepted=0;rejected=0;}
  };
}
