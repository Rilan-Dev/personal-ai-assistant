import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

const base = 'apps/mobile/android/app/src/main/java/dev/rilandev/personalaiassistant/';
const theme = readFileSync(base + 'NexaOverlayTheme.kt', 'utf8');
const systemAssistant = readFileSync(base + 'NexaAssistantService.kt', 'utf8');
const fallback = readFileSync(base + 'NexaWakeFallbackOverlay.kt', 'utf8');
const bubble = readFileSync(base + 'NexaToolOverlayService.kt', 'utf8');

test('adaptive palette follows system night mode and Android wallpaper-derived accent', () => {
  assert.match(theme, /Configuration\.UI_MODE_NIGHT_MASK/);
  assert.match(theme, /system_accent1_300/);
  assert.match(theme, /system_accent1_600/);
  assert.match(theme, /VERSION_CODES\.S/);
  assert.match(theme, /AUTO -> DARK; DARK -> LIGHT; else -> AUTO/);
  assert.match(theme, /nexa_overlay_ui/);
  assert.doesNotMatch(theme, /WallpaperManager|READ_EXTERNAL_STORAGE|screenshot|MediaProjection/);
});

test('system assistant, wake fallback and tool bubble share the native glass theme', () => {
  for (const screen of [systemAssistant, fallback, bubble]) {
    assert.match(screen, /NexaOverlayTheme\.palette\(/);
    assert.match(screen, /NexaOverlayTheme\.card\(/);
    assert.match(screen, /How can I help\?/);
    assert.match(screen, /NexaOverlayTheme\.waveform\(/);
  }
  assert.match(fallback, /showDetail\(!expanded\)/);
  assert.match(fallback, /FLAG_NOT_TOUCH_MODAL/);
  assert.match(systemAssistant, /window\.clearFlags\(WindowManager\.LayoutParams\.FLAG_DIM_BEHIND\)/);
  assert.match(bubble, /Theme · /);
  assert.match(bubble, /onConfigurationChanged/);
});

test('overlay actions retain explicit consent and no fraudulent screen awareness', () => {
  assert.match(fallback, /Approve app/);
  assert.match(fallback, /ACTION_DENY_TOOL/);
  assert.match(fallback, /Screen access requires consent/);
  assert.match(fallback, /ACTION_STOP/);
  assert.match(bubble, /Screen access requires permission/);
  assert.match(bubble, /Actions need approval/);
  assert.match(systemAssistant, /Stop \/ Dismiss/);
  assert.match(systemAssistant, /ACTION_APPROVE_TOOL/);
  assert.match(systemAssistant, /ACTION_DENY_TOOL/);
});

test('configured voice can show the overlay when Android offline recognition is absent', () => {
  assert.doesNotMatch(fallback, /val offlineReady =/);
  assert.match(fallback, /val providerReady =/);
  assert.match(fallback, /if \(providerReady && Build.VERSION.SDK_INT >= 26\)/);
  assert.match(fallback, /auto_voice_invocation_v1/);
  assert.match(fallback, /openVoiceCredentials\(service\)/);
});
