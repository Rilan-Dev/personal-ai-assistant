import React, {useState} from 'react';
import {ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View} from 'react-native';
import type {NexaSetupStatus} from '../native/nexaSetup';
import type {WakeStatus} from '../native/androidWakeWord';

type Props = {
  status: NexaSetupStatus | null;
  providerReady: boolean;
  wakeStatus: WakeStatus | null;
  onWakeDownload: () => void;
  onWakeStart: () => void;
  onWakeStop: () => void;
  onWakeRefresh: () => void;
  onAutoVoiceToggle: () => void;
  busy: boolean;
  notice: string;
  onVoice: () => void;
  onAssistant: () => void;
  onAssistantSettings: () => void;
  onOverlaySettings: () => void;
  onOverlayShow: () => void;
  onOverlayHide: () => void;
  onAccessibility: () => void;
  onCamera: () => void;
  onLocation: () => void;
  onProvider: () => void;
  onAppSettings: () => void;
  onRefresh: () => void;
  onFinish: () => void;
};
type PermissionStep = {
  key: string; index: string; title: string; detail: string;
  granted: boolean; action: string; onPress: () => void;
  optional?: boolean;
};

export default function NexaSetupScreen(props: Props) {
  const {
    status: s, providerReady, wakeStatus, onWakeDownload, onWakeStart,
    onWakeStop, onWakeRefresh, onAutoVoiceToggle, busy, notice, onVoice, onAssistant, onAssistantSettings, onOverlaySettings, onOverlayShow, onOverlayHide,
    onAccessibility, onCamera, onLocation, onProvider, onAppSettings,
    onRefresh, onFinish
  } = props;
  const permissions: PermissionStep[] = [
    {key:'audio', index:'01', title:'Microphone & notifications',
      detail:'Ask Android for microphone and notification access. Listening starts only after you tap a voice control.',
      granted: Boolean(s?.microphone && s?.notifications),
      action:'Allow voice access', onPress:onVoice},
    {key:'assistant', index:'02', title:'Default assistant gesture',
      detail:'Select Nexa in Android. This enables the system assistant gesture, not an always-on wake word.',
      granted:Boolean(s?.assistantSelected),
      action:'Choose Nexa assistant', onPress:onAssistant},
    {key:'screen', index:'03', title:'One-time screen assistance',
      detail:'Enable Nexa Screen Assist in Android Accessibility for owner-approved screen reading and navigation. The service must be connected before actions can run.',
      granted:Boolean(s?.screenAssist && s?.screenAssistConnected),
      action:'Open Accessibility', onPress:onAccessibility, optional:true},
    {key:'launcher', index:'04', title:'Home-screen app search',
      detail:'Separately enable Nexa Launcher Navigation for a user-started GitHub icon search. No automatic app clicking.',
      granted:Boolean(s?.launcherNavigation),
      action:'Open Accessibility', onPress:onAccessibility, optional:true},
    {key:'provider', index:'05', title:'Connect your AI',
      detail:'Configure Sarvam, OpenAI, or another supported provider in Settings. A key is not required for basic local tools.',
      granted:providerReady, action:'Configure AI', onPress:onProvider, optional:true},
    {key:'camera', index:'06', title:'Flashlight control',
      detail:'Android camera permission is needed only for flashlight actions. Every on/off action still requires approval.',
      granted:Boolean(s?.camera), action:'Request permission', onPress:onCamera, optional:true},
    {key:'location', index:'07', title:'Approximate location',
      detail:'Optional permission for one location read when requested in Tools. No background tracking.',
      granted:Boolean(s?.approximateLocation), action:'Request permission', onPress:onLocation, optional:true},
    {key:'overlay', index:'08', title:'Floating quick tools',
      detail:'Optional small movable bubble over home screen. Open Chat, Voice or Tools without covering the whole display. It does not secretly listen or click other apps.',
      granted:Boolean(s?.overlayGranted), action:'Allow display over apps',
      onPress:onOverlaySettings, optional:true},
  ];
  const [showOptional,setShowOptional] = useState(false);
  const [showAdvanced,setShowAdvanced] = useState(false);
  const [expandedStep,setExpandedStep] = useState<string|null>(null);
  const essential = permissions.filter(step=>!step.optional);
  const optional = permissions.filter(step=>step.optional);
  const visibleSteps = showOptional ? permissions : essential;
  const readyCount = permissions.filter(item => item.granted).length;
  const revision = s?.sourceRevision && /^[0-9a-f]{7,40}$/i.test(s.sourceRevision)
    ? s.sourceRevision.slice(0,10) : 'local build';

  return (
    <>
    <ScrollView style={styles.scroll} contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled">
      <View style={styles.hero}>
        <View style={styles.topLine}>
          <Text style={styles.topKicker}>NEXA  /  PRIVATE ASSISTANT</Text>
          <Pressable accessibilityRole="button" accessibilityLabel="Skip setup for now"
            onPress={onFinish} disabled={busy} style={styles.skip}>
            <Text style={styles.skipText}>Skip for now</Text>
          </Pressable>
        </View>
        <Text style={styles.title}>Make your assistant yours.</Text>
        <Text style={styles.subtitle}>Choose which Android features Nexa can use. All grants remain under your control.</Text>
        <View style={styles.progressHead}>
          <Text style={styles.progressText}>{s ? `${readyCount} of 8 ready` : 'Checking permissions…'}</Text>
          <Text style={styles.progressHint}>Optional setup</Text>
        </View>
        <View style={styles.track}><View style={[styles.progress,{width:`${readyCount / 8 * 100}%`}]} /></View>
      </View>
      <View style={styles.sectionIntro}>
        <Text style={styles.sectionTitle}>Get started</Text>
        <Text style={styles.sectionDesc}>Enable only what you want. Android confirms each permission separately.</Text>
      </View>
      {visibleSteps.map(step => (
        <View key={step.key} style={styles.step}>
          <View style={styles.stepTop}>
            <View style={styles.stepContent}>
              <Text style={styles.stepTitle}>{step.title}</Text>
              <Text style={[styles.status,step.granted&&styles.statusReady]}>
                {s === null ? 'Checking…' : step.granted ? '✓ Ready' : step.optional ? 'Optional · Off' : 'Not configured'}
              </Text>
            </View>
            <Pressable accessibilityRole="button"
              accessibilityLabel={step.action + ' for ' + step.title}
              disabled={busy || s === null || step.granted}
              onPress={step.onPress}
              style={[styles.action,(busy || step.granted || !s)&&styles.actionDisabled]}>
              <Text style={styles.actionText}>{step.granted?'Enabled':step.action}</Text>
            </Pressable>
          </View>
          <Pressable accessibilityRole="button"
            accessibilityLabel={`Why ${step.title} is needed`}
            accessibilityState={{expanded:expandedStep===step.key}}
            onPress={()=>setExpandedStep(expandedStep===step.key?null:step.key)}
            style={styles.detailToggle}>
            <Text style={styles.detailToggleText}>{expandedStep===step.key?'Hide details  −':'Why is this needed?  +'}</Text>
          </Pressable>
          {(expandedStep===step.key || (step.key==='screen' && s?.screenAssist && !s?.screenAssistConnected)) &&
            <Text style={styles.stepDescription}>{step.detail}</Text>}
          {step.key==='screen' && s && (expandedStep==='screen'||(s.screenAssist&&!s.screenAssistConnected)) &&
            <Text style={styles.helper}>{s.screenAssistConnected
              ? 'Nexa Screen Assist connected.'
              : s.screenAssist
                ? 'Nexa Screen Assist disconnected. Android says it is enabled, but it is not connected. Toggle the service off and on in Android Accessibility, then return and refresh.'
                : 'Open Android Accessibility, enable Nexa Screen Assist and confirm. Nexa cannot grant this permission automatically.'}</Text>}
          {step.key==='screen' && expandedStep==='screen' && s && !s.screenAssistConnected && s.sdkLevel>=33 &&
            <>
              <Text style={styles.helper}>If Android marks a sideloaded APK as “Restricted setting”, only if you trust this APK: open Nexa App info → More options → Allow restricted settings, confirm, then enable Nexa Screen Assist in Accessibility. Nexa cannot grant this permission automatically.</Text>
              <Pressable accessibilityRole="button" style={styles.secondary} disabled={busy}
                onPress={onAppSettings}><Text style={styles.secondaryText}>Open Nexa app info</Text></Pressable>
            </>}
          {step.key==='assistant' && expandedStep==='assistant' && !step.granted &&
            <Pressable accessibilityRole="button" style={styles.secondary} disabled={busy}
              onPress={onAssistantSettings}><Text style={styles.secondaryText}>Open Android Default apps / Digital assistant settings</Text></Pressable>}
          {step.key==='overlay' && s?.overlayGranted &&
            <View style={styles.footerActions}>
              <Pressable accessibilityRole="button" style={styles.secondary} disabled={busy} onPress={onOverlayShow}>
                <Text style={styles.secondaryText}>{s.overlayVisible?'Refresh floating bubble':'Show floating bubble'}</Text>
              </Pressable>
              <Pressable accessibilityRole="button" style={styles.secondary} disabled={busy} onPress={onOverlayHide}>
                <Text style={styles.secondaryText}>Hide bubble</Text>
              </Pressable>
            </View>}
        </View>
      ))}
      <Pressable accessibilityRole="button" accessibilityLabel="Show optional permissions and features"
        accessibilityState={{expanded:showOptional}} onPress={()=>setShowOptional(v=>!v)}
        style={styles.expandCard}>
        <Text style={styles.expandTitle}>{showOptional?'Hide optional features':'Explore 6 optional features'}</Text>
        <Text style={styles.expandSubtitle}>Screen access, installed apps, AI provider, flashlight, location and floating bubble  {showOptional?'−':'+'}</Text>
      </Pressable>
      <Pressable accessibilityRole="button" accessibilityLabel="Voice and wake activation advanced settings"
        accessibilityState={{expanded:showAdvanced}} onPress={()=>setShowAdvanced(v=>!v)}
        style={styles.expandCard}>
        <Text style={styles.expandTitle}>Advanced voice & wake settings  {showAdvanced?'−':'+'}</Text>
        <Text style={styles.expandSubtitle}>Live AI on invocation, offline wake detector and microphone disclosures</Text>
      </Pressable>
      {showAdvanced && <>
      <View style={styles.footerCard}>
        <Text style={styles.footerTitle}>Assistant gesture → AI conversation</Text>
        <Text style={styles.footerCopy}>
          Optional: after you explicitly invoke Nexa using the Android assistant
          gesture or enabled wake detector, start a live conversation with your
          configured OpenAI or Sarvam provider instead of the offline multi-turn
          command conversation. Audio is sent to that provider during the active session.
          Android shows the microphone indicator and a Stop notification.
          This never enables idle microphone capture on its own.
        </Text>
        <Text style={styles.status}>{s?.autoStartVoiceOnInvocation
          ? '✓ Continuous provider conversation on invocation enabled'
          : '○ Offline multi-command conversation · live provider disabled'}</Text>
        <Pressable accessibilityRole="button" disabled={busy || !s}
          style={styles.secondary} onPress={onAutoVoiceToggle}>
          <Text style={styles.secondaryText}>{s?.autoStartVoiceOnInvocation
            ? 'Disable automatic live AI · use offline commands'
            : 'Enable live AI after assistant invocation'}</Text>
        </Pressable>
      </View>
      <View style={styles.footerCard}>
        <Text style={styles.footerTitle}>Voice shortcut · no idle Nexa microphone</Text>
        <Text style={styles.footerCopy}>
          You can open Nexa's Voice screen with the Android launcher shortcut
          "Nexa Voice". On supported devices, Google Assistant may recognize
          "Hey Google, open Nexa voice" after the App Action is made available
          through Google Play. This uses Google's own hotword detection;
          Nexa itself does not keep the microphone active while idle.
          Starting microphone capture remains a separate, visible action.
          This is NOT an always-on "Hey Nexa" replacement. Google Assistant
          normally must remain the selected voice assistant for its own
          "Hey Google" phrase. If you select Nexa as the default assistant
          instead, use Android's assistant gesture; Google hotword launch
          may no longer be available on that phone.
        </Text>
      </View>
      <View style={styles.footerCard}>
        <Text style={styles.footerTitle}>Nexa activation · {wakeStatus?.enabled ? 'Continuous microphone active' : 'Privacy-first'}</Text>
        <Text style={styles.footerCopy}>
          Recommended: choose Nexa as the default Android assistant and use your
          phone's assistant gesture or button. There is NO idle microphone capture
          in this mode. Unless live AI activation is explicitly enabled, Nexa
          listens for successive offline device commands after invocation, and
          ends the conversation when you stop or remain silent. Android still
          shows its microphone indicator during actual recording.
        </Text>
        <Text style={styles.status}>
          {wakeStatus?.protectedHotwordAvailable
            ? 'Protected system hotword: check OEM integration before using'
            : 'Protected no-dot Hey Nexa: unavailable in this APK'}
        </Text>
        <Text style={styles.footerCopy}>
          Android allows indicator-free idle hotword detection only through an
          authorized protected system/OEM mechanism. A downloadable Nexa APK
          cannot provide this mechanism just by being selected as assistant or suppress Android's privacy indicator.
          To have no persistent microphone notification or green dot, disable
          continuous Hey Nexa listening and use the assistant gesture.
          That means voice-only wake phrase activation is not available in this mode.
        </Text>
        <Text style={styles.footerCopy}>
          Optional continuous Hey Nexa mode requires an explicit microphone opt-in,
          shows the green microphone dot and Stop notification, and may use more
          battery. Existing installs must re-enable it after this upgrade.
        </Text>
        <Text style={styles.footerCopy}>
          Experimental, English-first voice trigger. Download an Apache-licensed
          40 MB model once. You must explicitly start microphone listening.
          Android shows a visible microphone indicator and Stop notification while
          audio is in use. Wake detection stays on this phone. When a command
          ends, Nexa automatically resumes listening while enabled. Android
          may require a visible app after reboot or a system interruption.
        </Text>
        <Text style={styles.status}>
          {wakeStatus ? wakeStatus.modelReady
            ? wakeStatus.listening ? '● Listening for Hey Nexa' : '● Model ready · '+wakeStatus.state
            : wakeStatus.installing ? '○ Downloading model' : '○ Offline model not installed'
            : 'Checking offline voice availability'}
        </Text>
        <Text style={styles.footerCopy}>Activation mode: {wakeStatus?.mode === 'continuous' ? 'Continuous microphone (opt-in)' : 'Assistant gesture (no idle recording)'}.
          The Android green dot indicates real microphone activity; this app cannot hide it.
        </Text>
        {wakeStatus?.message ? <Text style={styles.footerCopy}>{wakeStatus.message}</Text> : null}
        <View style={styles.footerActions}>
          {!wakeStatus?.modelReady && <Pressable accessibilityRole="button"
            style={styles.secondary} disabled={busy || wakeStatus?.installing}
            onPress={onWakeDownload}>
            <Text style={styles.secondaryText}>{wakeStatus?.installing ? 'Installing…':'Download offline model'}</Text>
          </Pressable>}
          {wakeStatus?.modelReady && !wakeStatus.enabled && <Pressable accessibilityRole="button"
            style={styles.secondary} disabled={busy}
            onPress={onWakeStart}><Text style={styles.secondaryText}>Enable Hey Nexa (saved setting) · continuous mic</Text></Pressable>}
          {wakeStatus?.modelReady && wakeStatus.enabled && wakeStatus.restartRequired && <Pressable accessibilityRole="button"
            style={styles.secondary} disabled={busy}
            onPress={onWakeStart}><Text style={styles.secondaryText}>Resume wake listening</Text></Pressable>}
          {wakeStatus?.enabled && <Pressable accessibilityRole="button"
            style={styles.secondary} onPress={onWakeStop}>
            <Text style={styles.secondaryText}>Disable Hey Nexa · switch to privacy-first mode (stop microphone)</Text>
          </Pressable>}
          <Pressable accessibilityRole="button" style={styles.secondary} onPress={onWakeRefresh}>
            <Text style={styles.secondaryText}>Refresh wake status</Text>
          </Pressable>
        </View>
        <Text style={styles.footerCopy}>
          If Nexa is the Android default assistant, invocation uses your selected
          live AI or short on-device command mode. Otherwise tap the detection notification
          to open Nexa. No message will be sent without review.
        </Text>
      </View>
      </>}
      <View style={styles.footerCard}>
        <Text style={styles.footerTitle}>Manage & verify</Text>
        <Text style={styles.footerCopy}>Android may deny a permission without showing its prompt again. Use App settings if it was previously denied, and return here to re-check.</Text>
        <View style={styles.footerActions}>
          <Pressable accessibilityRole="button" disabled={busy} style={styles.secondary} onPress={onRefresh}>
            <Text style={styles.secondaryText}>↻ Refresh status</Text>
          </Pressable>
          <Pressable accessibilityRole="button" disabled={busy} style={styles.secondary} onPress={onAppSettings}>
            <Text style={styles.secondaryText}>Android app settings</Text>
          </Pressable>
        </View>
        <Text style={styles.build}>Installed version: {s?.versionName ?? 'Unknown'} · code {s?.versionCode ?? '—'} · {revision}</Text>
      </View>
      {notice !== '' && <Text style={styles.notice}>{notice}</Text>}
    </ScrollView>
    <View style={styles.stickyFooter}>
      <Pressable accessibilityRole="button" disabled={busy} onPress={onFinish}
        style={[styles.finish,busy && styles.actionDisabled]}>
        {busy ? <ActivityIndicator color="#fff"/> : <Text style={styles.finishText}>Continue to Nexa  →</Text>}
      </Pressable>
      <Text style={styles.finePrint}>You can change these permissions anytime in Settings.</Text>
    </View>
    </>
  );
}

const styles=StyleSheet.create({
  scroll:{flex:1,backgroundColor:'#0D1017'},
  content:{padding:16,paddingTop:14,paddingBottom:30,gap:12},
  hero:{backgroundColor:'#171D2B',borderWidth:1,borderColor:'#303A50',borderRadius:22,padding:17,gap:9},
  topLine:{flexDirection:'row',justifyContent:'space-between',alignItems:'center',gap:8},
  topKicker:{color:'#B0BCEC',fontSize:10,fontWeight:'700',letterSpacing:1.2},
  skip:{minHeight:44,minWidth:94,justifyContent:'center',alignItems:'flex-end'},
  skipText:{fontSize:12,color:'#D0DBFF',fontWeight:'600'},
  title:{color:'#F8FAFF',fontSize:22,lineHeight:28,fontWeight:'800',letterSpacing:-.4},
  subtitle:{color:'#AEBBD1',fontSize:12,lineHeight:18},
  progressHead:{flexDirection:'row',justifyContent:'space-between',gap:8,marginTop:5},
  progressText:{fontSize:12,fontWeight:'700',color:'#E6E9FF'},
  progressHint:{fontSize:11,color:'#A9B6D1'},
  track:{height:5,backgroundColor:'#384154',borderRadius:5,overflow:'hidden'},
  progress:{height:5,borderRadius:5,backgroundColor:'#9C9BFF'},
  sectionIntro:{paddingVertical:5,gap:3},
  sectionTitle:{fontSize:18,fontWeight:'700',color:'#EDF2FF'},
  sectionDesc:{fontSize:12,color:'#A4B1C9',lineHeight:18},
  step:{backgroundColor:'#151C2A',borderWidth:1,borderColor:'#2C364B',borderRadius:17,padding:13,gap:8},
  stepTop:{flexDirection:'row',alignItems:'center',gap:12,justifyContent:'space-between'},
  stepContent:{flex:1,gap:6},
  stepTitle:{fontSize:14,fontWeight:'700',color:'#F1F5FF'},
  stepDescription:{fontSize:12,lineHeight:19,color:'#B2BED4'},
  status:{fontSize:11,color:'#9CAAC2',fontWeight:'600',flexShrink:1},
  statusReady:{color:'#9ADCB8'},
  action:{paddingVertical:9,paddingHorizontal:11,minHeight:44,backgroundColor:'#4C56B8',borderRadius:12,alignItems:'center',justifyContent:'center',maxWidth:'52%'},
  actionDisabled:{opacity:.57},
  actionText:{color:'#FFFFFF',fontWeight:'700',fontSize:11,textAlign:'center'},
  detailToggle:{minHeight:36,justifyContent:'center'},
  detailToggleText:{color:'#B7C4E5',fontWeight:'600',fontSize:11},
  helper:{fontSize:12,lineHeight:19,color:'#EABBC0'},
  expandCard:{backgroundColor:'#1C2536',borderColor:'#33415A',borderWidth:1,borderRadius:16,padding:14,gap:6,minHeight:69},
  expandTitle:{color:'#E8ECFA',fontSize:14,fontWeight:'700'},
  expandSubtitle:{color:'#A5B4CD',fontSize:12,lineHeight:18},
  footerCard:{backgroundColor:'#121B2F',padding:16,borderRadius:18,borderWidth:1,borderColor:'#283955',gap:10},
  footerTitle:{fontWeight:'700',color:'#EFF3FF',fontSize:15},
  footerCopy:{color:'#A9B7CE',fontSize:12,lineHeight:19},
  footerActions:{flexDirection:'row',flexWrap:'wrap',gap:8},
  secondary:{borderWidth:1,borderColor:'#4D5D83',borderRadius:12,minHeight:44,paddingHorizontal:12,paddingVertical:10,justifyContent:'center'},
  secondaryText:{fontWeight:'700',fontSize:12,color:'#CDD8F2'},
  build:{fontSize:11,color:'#8C9CB9',lineHeight:17},
  notice:{fontSize:12,lineHeight:19,color:'#D8E0FF',backgroundColor:'#262E52',borderRadius:12,padding:12},
  stickyFooter:{backgroundColor:'#141A26',borderTopWidth:1,borderColor:'#30394B',paddingHorizontal:16,paddingVertical:10,gap:5},
  finish:{minHeight:49,borderRadius:15,backgroundColor:'#6065D8',justifyContent:'center',alignItems:'center'},
  finishText:{color:'#fff',fontSize:15,fontWeight:'800'},
  finePrint:{fontSize:10,color:'#A8B4CE',textAlign:'center'}
});
