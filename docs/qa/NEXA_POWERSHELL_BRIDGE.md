# Nexa PowerShell screen and tool bridge

`nexa.ps1` sends requests to the debug Nexa app over an ADB port forward. The
script uses ADB only for `forward` and its cleanup; all screen reads, taps,
scrolls, named navigation keys, and app actions execute through Nexa's Android
tool registry and `AgentHarness`.

## Setup

1. Install the Nexa debug APK on the selected phone. All Android debug and QA
   flows use the single isolated package `dev.rilandev.personalaiassistant.debug`.
2. Open **Nexa Screen Assist (Debug)** in Android Accessibility settings and enable
   its service. Keep the phone unlocked.
3. Open the Nexa debug app once. Its local bridge starts with the app process.
4. From the repository root in PowerShell:

```powershell
.\nexa.ps1 status -Serial 099344034S003788
.\nexa.ps1 tools -Serial 099344034S003788
.\nexa.ps1 tree -Serial 099344034S003788 -Format json -Confirm
```

The bridge accepts only the `.debug` package to avoid routing a test action to
an older or parallel Nexa installation.
The script selects an available local port automatically; `-LocalPort` is an
optional override.

When building this embedded QA bundle locally, set
`EXPO_PUBLIC_ACA_API_ORIGIN` to the configured ACA HTTPS origin. For a bridge
only test with no ACA backend, use the project's non-routable sentinel:
`https://aca-unconfigured.invalid`. Embedded bundles set `__DEV__` to false, so
the origin must be supplied at bundle time even for a debug APK.

The status response must identify `executor: NexaAppTools`, `debugBuild: true`,
`bridgeAvailable: true`, `rawAdbInput: false`, and—when screen control is
needed—`accessibilityEnabled: true`. If status says the bridge is unavailable,
open the matching debug package on the phone; if it was already running, bring
it forward once and retry. The script deliberately does not launch apps with
ADB shell commands. After the bridge is ready, it can remain open while
`device.open_app` places the target app in front.

## Observe and act

Screen observations need `-Confirm`. They return a short-lived `SnapshotId`,
visible non-editable text, and dynamically observed clickable targets. Target
indices are zero-based; bounds are reported in physical screen pixels for
reference. `tap` accepts the snapshot ID and index, never hard-coded x/y.

```powershell
.\nexa.ps1 observe -Serial 099344034S003788 -Format json -Confirm
.\nexa.ps1 tap -Serial 099344034S003788 -SnapshotId <fresh-snapshot-id> -Index <observed-index> -Format json -Confirm
.\nexa.ps1 scroll -Serial 099344034S003788 -SnapshotId <fresh-snapshot-id> -Direction down -Format json -Confirm
.\nexa.ps1 key -Serial 099344034S003788 -Name back -Format json -Confirm
```

Pass `-Confirm` separately to every observation or action. It confirms that
single command only; it does not authorize a batch. Re-observe after every
action attempt, even when Nexa refuses it or its result is uncertain. Indices
belong only to the snapshot that produced them; do not retry with an old
snapshot. For taps, Nexa checks the same external app window and exact observed
target label and bounds, so unrelated dynamic content changes do not invalidate
a stable target. Scrolls still require the full observed hierarchy to match.
Snapshots expire after 30 seconds. The available named keys are
`back`, `home`, and `notifications`; raw keycodes and text-entry keys are not
exposed. The `observe` command keeps its text in process memory and returns it
to the local PowerShell caller; password and editable fields are excluded.

## Accessibility hierarchy

The debug-only `tree` command invokes Nexa's `screen.accessibility_tree` tool
through the same Android app registry used by LLM providers. It returns the
current active external app's accessibility-exposed nodes, including
non-clickable text nodes, clickable parent rows, buttons, image controls,
fields, and containers. It does not use ADB shell, UIAutomator, or raw input.

```powershell
$tree = .\nexa.ps1 tree -Serial 099344034S003788 -Format json -Confirm | ConvertFrom-Json
$tree.nodes | Format-Table id, parentId, role, text, clickable, bounds
$tree.nodes | Where-Object { $_.clickable } | Format-Table id, text, resourceId, bounds
```

Each node has a snapshot-local `id` such as `0.2.1`, its `parentId`, direct
`childIds`, `className` and `role`, `resourceId` and Android `uniqueId` when
the app exposes them, accessibility flags/actions, and screen-pixel bounds
(`left`, `top`, `right`, `bottom`, `centerX`, `centerY`). Coordinates start at
the top-left; x grows right and y grows down. Child-index paths identify this
snapshot only; capture a fresh tree after the screen changes. The JSON has
`nodeCount`, `truncated`, and `textTruncated` fields so output limits are
visible rather than silently reported as a complete tree.

One confirmed read is returned to the active LLM provider so it can reason
about the visible screen. Nexa redacts values from editable and password nodes
while retaining field hints and widget metadata. Other visible, non-editable
text is included. The tool reads one snapshot only; it does not monitor in the
background or save the tree. Android apps can omit custom-drawn or otherwise
inaccessible elements from their accessibility hierarchy, so those cannot be
enumerated by this tool.

## Reliable testing loop

Keep the phone unlocked with Screen Assist enabled. First check the bridge and
tool list, then open the intended external app through Nexa before observing.
The foreground app can be anything; never assume a label from an earlier
screen is still present:

    $serial = '099344034S003788'
    .\nexa.ps1 status -Serial $serial
    .\nexa.ps1 tools -Serial $serial
    .\nexa.ps1 invoke -Serial $serial -ToolId device.open_app `
      -ArgsJson '{"app":"Settings"}' -Confirm
    $screen = .\nexa.ps1 observe -Serial $serial -Format json -Confirm | ConvertFrom-Json
    $screen.packageName
    $screen.clickables | Format-Table index, label, bounds

Select an exact label from that observation. Run the selection guard and tap in
one pasted block so a missing or ambiguous target cannot fall through to a
separate tap command:

    & {
      $label = Read-Host 'Exact label shown in the current clickables list'
      $targets = @($screen.clickables | Where-Object { $_.label -eq $label })
      if ($targets.Count -ne 1) {
        throw "Expected one visible '$label' control; found $($targets.Count). Reopen the intended app and observe again."
      }
      $target = $targets[0]
      .\nexa.ps1 tap -Serial $serial -SnapshotId $screen.snapshotId `
        -Index $target.index -Format json -Confirm
    }

    # Always observe again before another action; never reuse $screen.
    $screen = .\nexa.ps1 observe -Serial $serial -Format json -Confirm | ConvertFrom-Json

For a scroll, use that new observation's snapshot ID and an explicit direction:
`up`, `down`, `left`, or `right`. Observe again before tapping or scrolling
again. Start acceptance with harmless
navigation controls (for example, tabs or a settings page); do not exercise
message sending, payment, deletion, or other external side effects as a screen
control smoke test.

The Nexa app itself is not an external screen target. If `packageName` is not
the app you meant to control, stop and use `device.open_app` before observing.
If a label is absent, do not use `$target[0]`, `$target[39]`, or another guessed
index; a null PowerShell index is rejected by the script. After `key back`, the
foreground screen changes, so observe again before selecting another target.

For scrolling, observe the current page and use its fresh snapshot ID:

    $screen = .\nexa.ps1 observe -Serial $serial -Format json -Confirm | ConvertFrom-Json
    .\nexa.ps1 scroll -Serial $serial -SnapshotId $screen.snapshotId `
      -Direction down -Format json -Confirm

`-Format json` is important when piping action output into `ConvertFrom-Json`.
Check `verification` and then observe again; `actionAccepted` alone means only
Android accepted the gesture. A cross-app window transition is reported as a
UI change only when a matching Accessibility window event and current external
root agree. Neither result proves navigation intent or task completion.

An observation made while Nexa itself is foreground can return
`SCREEN_OBSERVATION_UNAVAILABLE`, because the external-window reader excludes
Nexa's own window. Bring up the target app with `device.open_app`, then observe.

### Troubleshooting

- STALE_SCREEN_SNAPSHOT: the snapshot expired, the foreground app/window
  changed, the target moved, or its label is no longer unique. Observe again,
  select the target from the new list, then confirm a new action.
- TOOL_EXECUTION_FAILED: check that the installed app is the current debug
  package, open it once, ensure the local bridge responds to status, and
  verify the Screen Assist service is bound. Do not substitute adb shell,
  raw input, or screen coordinates.
- ResponseEnded / empty reply: the phone closed the bridge request before
  returning a Nexa response. Open the selected QA app, or bring it forward once
  if it was already open, then retry `status`; the detailed inner exception is included
  so transport failures are distinguishable from Nexa tool refusals.
- ACCESSIBILITY_DISABLED: enable the **Nexa Screen Assist (Debug)** entry,
  reopen the `.debug` app, and check `status` again. Android may show another
  app's service as enabled; that does not make Nexa's service ready.
- SCROLL_CONTAINER_UNAVAILABLE: the current app hierarchy exposes no unique,
  visible scroll container. Do not repeat the action against the same
  snapshot or substitute a guessed swipe. Navigate to a screen with a visible
  scrollable list, observe again, then retry with that fresh snapshot.
- UI_TAP_TARGET_UNAVAILABLE / UI_TAP_TARGET_AMBIGUOUS: use the current
  clickable list; a target must be enabled and uniquely identifiable. No tap
  is automatically replayed after an uncertain result.

The script invokes the phone-side Nexa tools over the debug bridge; ADB is used
only to create and remove the port forward. adb shell input is not part of this
workflow.

## Invoke a Nexa tool

`tools` lists the read/control tools available in that debug app. The generic
`invoke` command uses the same validators and confirmation metadata as Nexa.
Outbound message-send and payment tools are not exposed.

```powershell
.\nexa.ps1 invoke -Serial 099344034S003788 -ToolId device.battery_status
.\nexa.ps1 invoke -Serial 099344034S003788 -ToolId device.open_app `
  -ArgsJson '{"app":"Chrome"}' -Confirm
```

`Get-Help .\nexa.ps1 -Full` shows the command help. The bridge exists only in a
debug build; production builds do not start its local socket.
