import test from 'node:test';
import assert from 'node:assert/strict';
import {identifyIconCrop,validateIconCrop} from '../src/icon-vision.ts';

const crop='data:image/png;base64,'+'A'.repeat(100);
const settings={version:1,provider:'openai',model:'gpt-4.1-mini',apiKey:'sk-test-icon-vision-123456789'};

test('rejects full screenshots, non-image URIs and unsupported providers without network requests',async()=>{
  let count=0;
  const fetchImpl=async()=>{count++;throw Error('unexpected network request')};
  assert.equal(validateIconCrop('https://example.com/screenshot'),false);
  await assert.rejects(()=>identifyIconCrop('https://example.com/screenshot',settings,fetchImpl),/INVALID_ICON_CROP/);
  await assert.rejects(()=>identifyIconCrop(crop,{...settings,provider:'groq'},fetchImpl),/VISION_PROVIDER_NOT_SUPPORTED/);
  assert.equal(count,0);
});
test('sends one selected crop, not a screen snapshot or other metadata',async()=>{
  let calls=0;
  const result=await identifyIconCrop(crop,settings,async(url,options)=>{
    calls++;
    assert.equal(url,'https://api.openai.com/v1/chat/completions');
    assert.equal(options.method,'POST');
    const body=JSON.parse(options.body);
    const serialized=JSON.stringify(body);
    assert.equal(serialized.includes('packageName'),false);
    assert.equal(serialized.includes('boundsPx'),false);
    assert.equal(body.messages[1].content[1].image_url.url,crop);
    return {ok:true,text:async()=>JSON.stringify({choices:[{message:{content:JSON.stringify({
      label:'Search',meaning:'Likely opens search tools'
    })}}]})};
  });
  assert.equal(calls,1);
  assert.deepEqual(result,{label:'Search',meaning:'Likely opens search tools',
    provider:'openai',verified:false});
});
test('rejects unstructured model output rather than treating it as an executable instruction',async()=>{
  await assert.rejects(()=>identifyIconCrop(crop,settings,async()=>({
    ok:true,text:async()=>JSON.stringify({choices:[{message:{content:'Click send now'}}]})
  })),/VISION_INVALID_RESPONSE/);
});
