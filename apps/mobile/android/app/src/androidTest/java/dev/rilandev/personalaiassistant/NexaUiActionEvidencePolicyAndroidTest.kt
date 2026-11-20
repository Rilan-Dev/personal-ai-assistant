package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaUiActionEvidencePolicyAndroidTest {
 @Test fun clickEventMustMatchTheExactTransientTargetIdentity() {
  assertTrue(NexaUiActionEvidencePolicy.isMatchingTapEvent("target-a","target-a"))
  assertFalse(NexaUiActionEvidencePolicy.isMatchingTapEvent("target-a","target-b"))
  assertFalse(NexaUiActionEvidencePolicy.isMatchingTapEvent(null,"target-a"))
  assertFalse(NexaUiActionEvidencePolicy.isMatchingTapEvent("target-a",null))
  assertFalse(NexaUiActionEvidencePolicy.isMatchingTapEvent(" "," "))
 }

 @Test fun onlyVerifiedLauncherSwipeCanRetainEvidenceAcrossWindowIds() {
  assertTrue(NexaUiActionEvidencePolicy.sameEvidenceContext(
   "scroll","launcher",41,"launcher",42,true))
  assertFalse(NexaUiActionEvidencePolicy.sameEvidenceContext(
   "scroll","com.android.settings",41,"com.android.settings",42,false))
  assertFalse(NexaUiActionEvidencePolicy.sameEvidenceContext(
   "scroll","launcher",41,"launcher",42,false))
  assertFalse(NexaUiActionEvidencePolicy.sameEvidenceContext(
   "tap","launcher",41,"launcher",42,true))
  assertFalse(NexaUiActionEvidencePolicy.sameEvidenceContext(
   "scroll","launcher",41,"com.whatsapp",42,true))
 }
 @Test fun scrollEventsAreMoreSpecificThanUnchangedGeometry() {
  assertEquals("scroll_event_observed",NexaUiActionEvidencePolicy.classify("scroll","a","a",1,0))
  assertEquals("structural_change_observed",NexaUiActionEvidencePolicy.classify("scroll","a","b",1,0))
  assertEquals("no_observable_change",NexaUiActionEvidencePolicy.classify("scroll","a","a",0,0))
 }
 @Test fun tapsRequireWindowsOrStructureInsteadOfUnrelatedScrollEvents() {
  assertEquals("window_change_observed",NexaUiActionEvidencePolicy.classify("tap","a","a",0,1))
  assertEquals("no_observable_change",NexaUiActionEvidencePolicy.classify("tap","a","a",9,0))
  assertEquals("structural_change_observed",NexaUiActionEvidencePolicy.classify("tap","a","b",0,0))
 }
 @Test fun aMatchedClickEventConfirmsOnlyTheTapWhenEditableContentIsExcluded() {
  assertEquals("tap_event_observed",
   NexaUiActionEvidencePolicy.classify("tap","a","a",0,0,tapEvents=1))
  assertEquals("no_observable_change",
   NexaUiActionEvidencePolicy.classify("tap","a","a",0,0,tapEvents=0))
  assertEquals("context_changed_unverified",
   NexaUiActionEvidencePolicy.classify("tap","a","b",0,0,
    sameContext=false,tapEvents=1))
 }
 @Test fun sameWindowContentChangeEventConfirmsOnlyThatTheTapChangedTheScreen() {
  assertTrue(NexaUiActionEvidencePolicy.isMatchingContentChangeEvent(
   "tap","com.android.chrome",17,"com.android.chrome",17))
  assertFalse(NexaUiActionEvidencePolicy.isMatchingContentChangeEvent(
   "tap","com.android.chrome",17,"com.android.settings",17))
  assertFalse(NexaUiActionEvidencePolicy.isMatchingContentChangeEvent(
   "tap","com.android.chrome",17,"com.android.chrome",18))
  assertFalse(NexaUiActionEvidencePolicy.isMatchingContentChangeEvent(
   "scroll","com.android.chrome",17,"com.android.chrome",17))
  assertEquals("content_change_observed",
   NexaUiActionEvidencePolicy.classify("tap","before","before",0,0,
    contentEvents=1))
  assertEquals("context_changed_unverified",
   NexaUiActionEvidencePolicy.classify("tap","before","before",0,0,
    sameContext=false,contentEvents=1))
 }
 @Test fun unavailableWindowCannotBeCalledSuccessful() {
  assertEquals("unverified",NexaUiActionEvidencePolicy.classify("tap","a",null,0,0))
 }
 @Test fun evidenceFromDifferentForegroundAppIsNotAttributedToApprovedAction() {
  assertEquals("context_changed_unverified",
   NexaUiActionEvidencePolicy.classify("tap","old","new",0,1,false))
  assertEquals("context_changed_unverified",
   NexaUiActionEvidencePolicy.classify("scroll","old","new",1,0,false))
 }
 @Test fun aTapCanReportOnlyAWindowChangeWhenTheCurrentExternalRootMatchesItsEvent() {
  assertTrue(NexaUiActionEvidencePolicy.isCorroboratedExternalWindowTransition(
   "tap","com.android.settings",41,
   "com.android.settings.intelligence",52,
   "com.android.settings.intelligence",52,true))
  assertTrue(NexaUiActionEvidencePolicy.isCorroboratedExternalWindowTransition(
   "tap","com.android.settings",41,
   "com.android.settings",52,
   "com.android.settings",52,true))
  assertFalse(NexaUiActionEvidencePolicy.isCorroboratedExternalWindowTransition(
   "scroll","com.android.settings",41,
   "com.android.settings.intelligence",52,
   "com.android.settings.intelligence",52,true))
  assertFalse(NexaUiActionEvidencePolicy.isCorroboratedExternalWindowTransition(
   "tap","com.android.settings",41,
   "com.android.settings.intelligence",52,
   "com.android.settings",41,true))
  assertFalse(NexaUiActionEvidencePolicy.isCorroboratedExternalWindowTransition(
   "tap","com.android.settings",41,
   "com.android.settings.intelligence",51,
   "com.android.settings.intelligence",52,true))
  assertFalse(NexaUiActionEvidencePolicy.isCorroboratedExternalWindowTransition(
   "tap","com.android.settings",41,
   "com.android.settings",41,
   "com.android.settings",41,true))
  assertFalse(NexaUiActionEvidencePolicy.isCorroboratedExternalWindowTransition(
   "tap","com.android.settings",41,
   "com.android.settings.intelligence",52,
   "com.android.settings.intelligence",52,false))
  assertEquals("window_change_observed",
   NexaUiActionEvidencePolicy.classify(
    "tap","before","after",0,0,false,externalWindowChangeObserved=true))
  assertEquals("context_changed_unverified",
   NexaUiActionEvidencePolicy.classify(
    "tap","before","after",0,0,false,externalWindowChangeObserved=false))
  assertEquals("context_changed_unverified",
   NexaUiActionEvidencePolicy.classify(
    "tap","before",null,0,0,false,externalWindowChangeObserved=true))
 }
 @Test fun missingPostActionWindowCannotBeCalledConfirmed() {
  assertEquals("unverified",
   NexaUiActionEvidencePolicy.classify("scroll","old",null,1,0,true))
 }

 @Test fun ownerWindowTransitionRequiresAnExactDebugOwnerEventAndCurrentRoot() {
  assertTrue(NexaUiActionEvidencePolicy.isCorroboratedOwnerWindowTransition(
   "tap","com.android.launcher",11,
   "dev.rilandev.personalaiassistant.debug",22,
   "dev.rilandev.personalaiassistant.debug",22,
   "dev.rilandev.personalaiassistant.debug",true))
  assertFalse(NexaUiActionEvidencePolicy.isCorroboratedOwnerWindowTransition(
   "scroll","com.android.launcher",11,
   "dev.rilandev.personalaiassistant.debug",22,
   "dev.rilandev.personalaiassistant.debug",22,
   "dev.rilandev.personalaiassistant.debug",true))
  assertFalse(NexaUiActionEvidencePolicy.isCorroboratedOwnerWindowTransition(
   "tap","com.android.launcher",11,
   "dev.rilandev.personalaiassistant.debug",21,
   "dev.rilandev.personalaiassistant.debug",22,
   "dev.rilandev.personalaiassistant.debug",true))
  assertFalse(NexaUiActionEvidencePolicy.isCorroboratedOwnerWindowTransition(
   "tap","com.android.launcher",11,
   "dev.rilandev.personalaiassistant.debug",22,
   "com.android.launcher",22,
   "dev.rilandev.personalaiassistant.debug",true))
  assertFalse(NexaUiActionEvidencePolicy.isCorroboratedOwnerWindowTransition(
   "tap","com.android.launcher",11,
   "dev.rilandev.personalaiassistant.debug",22,
   "dev.rilandev.personalaiassistant.debug",22,
   "dev.rilandev.personalaiassistant.debug",false))
 }

 @Test fun ownerWindowTransitionIsReportedOnlyWhenTheExactTransitionWasObserved() {
  assertEquals("window_change_observed",
   NexaUiActionEvidencePolicy.classify(
    "tap","before","owner-window-present",0,0,false,
    ownerWindowChangeObserved=true))
  assertEquals("context_changed_unverified",
   NexaUiActionEvidencePolicy.classify(
    "tap","before","owner-window-present",0,0,false,
    ownerWindowChangeObserved=false))
 }

}
