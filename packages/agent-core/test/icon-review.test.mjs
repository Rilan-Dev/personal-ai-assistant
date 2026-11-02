import test from 'node:test';
import assert from 'node:assert/strict';
import {createIconReviewSession} from '../src/icon-review.ts';
const sample={snapshotId:'current-scan',cropId:'icon-3',modelLabel:'search',
 evidenceStatus:'tentative',score:53,expiresAtWallMs:61000};
test('requires explicit owner vote and only returns aggregate feedback',()=>{
 const review=createIconReviewSession();
 assert.deepEqual(review.summary(),{reviewed:0,accepted:0,rejected:0});
 assert.equal(review.offer(sample,2000),true);
 assert.equal(review.summary().reviewed,0);
 assert.equal(review.vote('current-scan','icon-3','accurate',2500).status,'recorded');
 assert.deepEqual(review.summary(),{reviewed:1,accepted:1,rejected:0});
 assert.equal(review.vote('current-scan','icon-3','accurate',2600).status,'refused');
 assert.equal(JSON.stringify(review.summary()).includes('current-scan'),false);
});
test('expires and blocks feedback from another crop or scan',()=>{
 const review=createIconReviewSession();
 review.offer(sample,2000);
 assert.equal(review.vote('wrong-scan','icon-3','inaccurate',2200).status,'refused');
 assert.equal(review.vote('current-scan','other-crop','accurate',2200).status,'refused');
 assert.equal(review.vote('current-scan','icon-3','inaccurate',62000).status,'refused');
 assert.equal(review.summary().reviewed,0);
});
test('refuses unsupported, ambiguous or no-evidence model guesses',()=>{
 const review=createIconReviewSession();
 for(const value of [
  {...sample,score:100}, {...sample,evidenceStatus:'blocked'},
  {...sample,modelLabel:'delete all user data'},
  {...sample,modelLabel:'Search',expiresAtWallMs:1000}
 ])assert.equal(review.offer(value,2000),false);
 assert.equal(review.vote('current-scan','icon-3','accurate',2100).status,'refused');
});
test('corrections are session-only and never authorize screen actions',()=>{
 const review=createIconReviewSession();
 assert.equal(review.offer({...sample,evidenceStatus:'corroborated',score:82},2000),true);
 const result=review.vote('current-scan','icon-3','inaccurate',2100);
 assert.equal(result.status,'recorded');
 assert.equal(result.actionEligible,false);
 assert.deepEqual(review.summary(),{reviewed:1,accepted:0,rejected:1});
 review.clear();
 assert.deepEqual(review.summary(),{reviewed:0,accepted:0,rejected:0});
});

test('untrusted provider and bridge data fail closed rather than throwing',()=>{
 const review=createIconReviewSession();
 assert.equal(review.offer({...sample,modelLabel:null},2000),false);
 assert.equal(review.offer({...sample,evidenceStatus:'invented'},2000),false);
 assert.equal(review.summary().reviewed,0);
});
