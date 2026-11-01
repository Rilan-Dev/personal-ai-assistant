import test from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
const read=(p)=>readFileSync(p,"utf8");
const native="apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/";
const source=read(native+"OwnerPrivacyModule.kt");
const wrapper=read("apps/mobile/src/native/ownerPrivacy.ts");
const app=read("apps/mobile/App.tsx");
const manifest=read("apps/mobile/android/app/src/main/AndroidManifest.xml");

test("Android location is user-initiated, approximate, fresh and bounded",()=>{
  assert.match(manifest,/ACCESS_COARSE_LOCATION/);
  assert.doesNotMatch(manifest,/ACCESS_FINE_LOCATION|ACCESS_BACKGROUND_LOCATION/);
  assert.match(source,/fun readApproximateLocation\(p:Promise\)/);
  assert.match(source,/if\(!visible\(\)\)/);
  assert.match(source,/ACCESS_COARSE_LOCATION/);
  assert.match(source,/getCurrentLocation\(choice,signal,context\.mainExecutor/);
  assert.match(source,/main\.postDelayed\(timeout,12000L\)/);
  assert.match(source,/60_000_000_000L/);
  assert.match(source,/fun quantizeCoordinate\(value:Double\)/);
  assert.match(source,/data\.putDouble\("latitude",quantizeCoordinate\(fix\.latitude\)\)/);
  assert.match(source,/data\.putDouble\("longitude",quantizeCoordinate\(fix\.longitude\)\)/);
  assert.doesNotMatch(source,/requestLocationUpdates|startForeground|ACCESS_BACKGROUND_LOCATION/);
});

test("Android picker accesses only selected phone URI, not entire address book",()=>{
  assert.doesNotMatch(manifest,/READ_CONTACTS|WRITE_CONTACTS/);
  assert.match(source,/Intent\(Intent\.ACTION_PICK\)/);
  assert.match(source,/ContactsContract\.CommonDataKinds\.Phone\.CONTENT_TYPE/);
  assert.match(source,/context\.contentResolver\.query\(uri,fields,null,null,null\)/);
  assert.match(source,/pendingContact\.compareAndSet\(null,p\)/);
  assert.match(source,/uri\.authority!=ContactsContract\.AUTHORITY/);
  assert.match(source,/fun pickPhoneContact\(p:Promise\)/);
  assert.doesNotMatch(source,/ContactsContract\.Contacts\.CONTENT_URI|READ_CONTACTS/);
});

test("No model-driven location/contact action or automatic prompt transmission",()=>{
  assert.match(wrapper,/request\(permission/);
  assert.match(wrapper,/pickOwnerContact/);
  assert.match(wrapper,/getOwnerApproximateLocation/);
  assert.match(app,/Private, one-time observations/);
  assert.match(app,/Read approximate location/);
  assert.match(app,/Select one phone contact/);
  assert.match(app,/for up to 60 seconds/);
  assert.match(app,/setSelectedContact\(null\)/);
  assert.match(app,/setApproximateLocation\(null\)/);
  assert.match(app,/Review contact question in Chat/);
  assert.match(app,/Review location question in Chat/);
  assert.doesNotMatch(app,/send\(selectedContact|send\(approximateLocation/);
  assert.match(read(native+"DeviceControlPackage.kt"),/OwnerPrivacyModule\(reactContext\)/);
  assert.doesNotMatch(read("packages/agent-core/src/direct-provider.ts"),/device\.read_contact|device\.get_location/);
});


test("Private observations expire even after app background/resume",()=>{
  assert.match(app,/privateObservationDeadline=useRef\(0\)/);
  assert.match(app,/privateObservationDeadline\.current=Date\.now\(\)\+60_000/);
  assert.match(app,/if\(Date\.now\(\)>privateObservationDeadline\.current\)/);
  assert.match(app,/AppState\.addEventListener\('change',status=>/);
  assert.match(app,/setSelectedContact\(null\)/);
  assert.match(app,/setApproximateLocation\(null\)/);
  assert.match(app,/if\(tab!=='tools'\)/);
});
