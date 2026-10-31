# Android flashlight: one-shot authorized Camera2 control

## Platform source and maintainable reuse
- Android CameraManager.setTorchMode:
  https://developer.android.com/reference/android/hardware/camera2/CameraManager
- Android CameraManager.TorchCallback:
  https://developer.android.com/reference/android/hardware/camera2/CameraManager.TorchCallback
- LangGraph.js planning backend, source pinned:
  https://github.com/langchain-ai/langgraphjs/tree/959f041dfb8f6bbafa6af3ebb4bc20484a9290da
- Deep Agents.js permission design comparison:
  https://github.com/langchain-ai/deepagentsjs/tree/26f190d40f44a2bb498db73e9e98b002bb8554fe

Reuses the installed LangGraph planning API, mobile AgentHarness and
Keystore-backed action journal. Does not introduce another autonomous loop,
generic camera SDK, arbitrary device commands or filesystem tool.

## Owner-controlled tool contract
Tool ID: device.set_flashlight.
Tool input: exactly one boolean parameter named enabled.
Allowed phrases: "Turn flashlight on/off", "Turn on/off flashlight", and
narrow equivalent forms with torch/phone; no multi-step request or implicit
hardware action is supported.

To execute, ALL of the following are checked independently:
1. Android native bridge is available and hardware reports flash capability.
2. The owner explicitly presses "Grant Android flashlight permission".
   Android shows its CAMERA runtime permission request. No video/image capture
   is used by this tool.
3. The owner separately enables "Allow flashlight changes" in-app.
4. The model proposes an exact tool ID/input matching the owner's utterance.
5. The Android durable journal stores an exact one-time approval fingerprint.
6. The owner presses "Approve once", then "Resume" to consume that fingerprint.
7. At execution, Kotlin checks the app is foreground, CAMERA remains granted,
   hardware is available and no operation is in progress.

The Android Camera2 service uses getCameraIdList and
CameraCharacteristics.FLASH_INFO_AVAILABLE to select a flash device, then
setTorchMode. A short-lived CameraManager.TorchCallback confirms enabled
or disabled state before the native Promise resolves success. It always
unregisters the callback and times out after 3 seconds. Cancellation or
hardware errors report an uncertain/failed action; the journal will not
silently resend the same claimed action.

The cloud model sees only a redacted "enabled" boolean + "observed" status
when state was verified. It cannot claim success purely from its own
reasoning. Even after confirmation, other applications or the OS can change
the flashlight state; it is not an exclusive hardware lock.

## Device and test limitations
- Some Android devices do not have a flash unit and the tool reports
  unsupported hardware. Camera2 may reject torch changes while another
  app is using the camera.
- Manifest declares CAMERA and optional camera/flash hardware features:
  feature flags do not exclude devices without flash from installation.
- The Kotlin bridge does not read camera frames, photos, contacts or location.
- Explicit CAMERA permission is broad at the OS level; Android Settings can
  revoke it. Installing this app does not grant it automatically.
- No torch action is permitted merely because the Quick Settings launcher
  was added or the microphone is active.
- Unit tests use a simulated tool and a simulated journal. Native Android
  build verifies compilation but not hardware. Physical phone testing is
  required for permission denials, callback correctness, system preemption,
  process death and actual LED behavior.

## UAT
1. Without OS CAMERA permission, flashlight request cannot execute.
2. With OS permission but no in-app capability, request is blocked.
3. With capability but no one-time approval, no hardware change.
4. Grant exact approval and resume: LED state matches requested result;
   callback is observed; no duplicate action on resume.
5. Try opposite action after approval: requires an entirely new task grant.
6. Deny/cancel, background while pending, and revoke permission: controlled
   no-retry failure. Inspect torch in Android's system Quick Settings.
7. Use a no-flash emulator/phone: return unsupported without fabricated
   success. Also verify camera Intent launching still works as before.
