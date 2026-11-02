package dev.rilandev.personalaiassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NexaUiActionEvidencePolicyAndroidTest {
 @Test fun scrollEventsAreMoreSpecificThanUnchangedGeometry() {
  assertEquals("scroll_event_observed",NexaUiActionEvidencePolicy.classify("scroll","a","a",1,0))
  assertEquals("no_observable_change",NexaUiActionEvidencePolicy.classify("scroll","a","a",0,0))
 }
 @Test fun tapsRequireWindowsOrStructureInsteadOfUnrelatedScrollEvents() {
  assertEquals("window_change_observed",NexaUiActionEvidencePolicy.classify("tap","a","a",0,1))
  assertEquals("no_observable_change",NexaUiActionEvidencePolicy.classify("tap","a","a",9,0))
  assertEquals("structural_change_observed",NexaUiActionEvidencePolicy.classify("tap","a","b",0,0))
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
 @Test fun missingPostActionWindowCannotBeCalledConfirmed() {
  assertEquals("unverified",
   NexaUiActionEvidencePolicy.classify("scroll","old",null,1,0,true))
 }

}
