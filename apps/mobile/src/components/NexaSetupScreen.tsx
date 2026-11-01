import React from 'react';
import {ActivityIndicator, Pressable, ScrollView, StyleSheet, Text, View} from 'react-native';
import type {NexaSetupStatus} from '../native/nexaSetup';

type Props = {
  status: NexaSetupStatus | null;
  providerReady: boolean;
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
    status: s, providerReady, busy, notice, onVoice, onAssistant, onAssistantSettings, onOverlaySettings, onOverlayShow, onOverlayHide,
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
      detail:'Enable Nexa Screen Assist in Android Accessibility. Only an explicitly armed single screen read is allowed.',
      granted:Boolean(s?.screenAssist),
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
  const readyCount = permissions.filter(item => item.granted).length;
  const revision = s?.sourceRevision && /^[0-9a-f]{7,40}$/i.test(s.sourceRevision)
    ? s.sourceRevision.slice(0,10) : 'local build';

  return (
    <ScrollView style={styles.scroll} contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled">
      <View style={styles.hero}>
        <View style={styles.topLine}>
          <View style={styles.brandMark}><Text style={styles.brandGlyph}>✦</Text></View>
          <Text style={styles.topKicker}>NEXA AI  /  DEVICE SETUP</Text>
        </View>
        <Text style={styles.title}>Make your assistant yours.</Text>
        <Text style={styles.subtitle}>
          Set up the features you want, directly through Android. No hidden permissions,
          background listening or automatic messages.
        </Text>
        <View style={styles.progressPanel}>
          <View style={styles.progressHead}>
            <Text style={styles.progressText}>{s ? `${readyCount} of 8 features available` : 'Checking device permissions…'}</Text>
            <Text style={styles.progressHint}>Optional setup</Text>
          </View>
          <View style={styles.track}><View style={[styles.progress,{width:`${readyCount / 8 * 100}%`}]} /></View>
        </View>
      </View>
      <View style={styles.sectionIntro}>
        <Text style={styles.sectionTitle}>Permissions & features</Text>
        <Text style={styles.sectionDesc}>Each step shows the current Android state. Choose only what you need.</Text>
      </View>
      {permissions.map(step => (
        <View key={step.key} style={styles.step}>
          <View style={styles.stepTop}>
            <Text style={styles.number}>{step.index}</Text>
            <View style={styles.stepContent}>
              <Text style={styles.stepTitle}>{step.title}</Text>
              <Text style={styles.stepDescription}>{step.detail}</Text>
            </View>
          </View>
          <View style={styles.stepBottom}>
            <Text style={[styles.status,step.granted&&styles.statusReady]}>
              {s === null ? 'Checking…' : step.granted ? '✓ Ready' : step.optional ? '○ Optional · off' : '○ Not configured'}
            </Text>
            <Pressable accessibilityRole="button"
              accessibilityLabel={step.action + ' for ' + step.title}
              disabled={busy || s === null || step.granted}
              onPress={step.onPress}
              style={[styles.action, (busy || step.granted || !s)&&styles.actionDisabled]}>
              <Text style={styles.actionText}>{step.granted?'Enabled':step.action}</Text>
            </Pressable>
          </View>
          {step.key==='assistant' && !step.granted && <>
            <Pressable accessibilityRole="button" style={styles.secondary}
              disabled={busy} onPress={onAssistantSettings}>
              <Text style={styles.secondaryText}>Open Android Default apps / Digital assistant settings</Text>
            </Pressable>
            {s && !s.assistantRoleAvailable && <Text style={styles.helper}>
              This phone did not offer the assistant-role dialog. Select Nexa in Android Settings if listed, then return and refresh.
            </Text>}
          </>}
          {step.key==='overlay' && s?.overlayGranted && <View style={styles.footerActions}>
            <Pressable accessibilityRole="button" style={styles.secondary}
              disabled={busy} onPress={onOverlayShow}>
              <Text style={styles.secondaryText}>{s.overlayVisible?'Refresh floating bubble':'Show floating bubble'}</Text>
            </Pressable>
            <Pressable accessibilityRole="button" style={styles.secondary}
              disabled={busy} onPress={onOverlayHide}>
              <Text style={styles.secondaryText}>Hide bubble</Text>
            </Pressable>
          </View>}
        </View>
      ))}
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
      <Pressable accessibilityRole="button" disabled={busy} onPress={onFinish}
        style={[styles.finish,busy && styles.actionDisabled]}>
        {busy ? <ActivityIndicator color="#fff"/> : <Text style={styles.finishText}>Continue to Nexa  →</Text>}
      </Pressable>
      <Text style={styles.finePrint}>You can skip every optional permission. Reopen Setup anytime from Settings. Android permission controls remain yours.</Text>
    </ScrollView>
  );
}

const styles=StyleSheet.create({
  scroll:{flex:1,backgroundColor:'#080E1E'},
  content:{padding:18,paddingTop:16,paddingBottom:38,gap:13},
  hero:{backgroundColor:'#151A36',borderWidth:1,borderColor:'#353D76',borderRadius:26,padding:22,overflow:'hidden',gap:12},
  topLine:{flexDirection:'row',gap:10,alignItems:'center',marginBottom:2},
  brandMark:{width:34,height:34,borderRadius:11,backgroundColor:'#6366F1',alignItems:'center',justifyContent:'center'},
  brandGlyph:{fontSize:20,color:'#FFFFFF',fontWeight:'700'},
  topKicker:{color:'#BFC8FF',fontSize:11,fontWeight:'700',letterSpacing:1.6},
  title:{color:'#F7F9FF',fontSize:27,lineHeight:33,fontWeight:'800',letterSpacing:-.6},
  subtitle:{color:'#B2BDD5',fontSize:14,lineHeight:22},
  progressPanel:{backgroundColor:'#202647',padding:14,borderRadius:16,marginTop:4,gap:12},
  progressHead:{flexDirection:'row',justifyContent:'space-between',gap:8},
  progressText:{fontSize:12,fontWeight:'700',color:'#E6E9FF'},
  progressHint:{fontSize:11,color:'#A9B6D1'},
  track:{height:6,backgroundColor:'#3D486A',borderRadius:5,overflow:'hidden'},
  progress:{height:6,borderRadius:5,backgroundColor:'#8F8AFF'},
  sectionIntro:{paddingTop:9,paddingBottom:3,gap:4},
  sectionTitle:{fontSize:19,fontWeight:'700',color:'#EDF2FF'},
  sectionDesc:{fontSize:12,color:'#A4B1C9',lineHeight:18},
  step:{backgroundColor:'#111B30',borderWidth:1,borderColor:'#2A3852',borderRadius:18,padding:15,gap:14},
  stepTop:{flexDirection:'row',gap:12},
  number:{color:'#8D9EF0',fontSize:14,fontWeight:'800',width:25},
  stepContent:{flex:1,gap:5},
  stepTitle:{fontSize:15,fontWeight:'700',color:'#F1F5FF'},
  stepDescription:{fontSize:12,lineHeight:19,color:'#AAB7CE'},
  stepBottom:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:10,paddingLeft:37},
  status:{fontSize:12,color:'#98A9C4',fontWeight:'600',flexShrink:1},
  statusReady:{color:'#99D9B4'},
  action:{paddingVertical:11,paddingHorizontal:13,minHeight:44,backgroundColor:'#5658C9',borderRadius:12,alignItems:'center',justifyContent:'center',maxWidth:'65%'},
  actionDisabled:{opacity:.5},
  actionText:{color:'#FFFFFF',fontWeight:'700',fontSize:12,textAlign:'center'},
  helper:{fontSize:11,color:'#EABBC0',paddingLeft:37},
  footerCard:{backgroundColor:'#121B2F',padding:17,borderRadius:18,borderWidth:1,borderColor:'#283955',gap:10},
  footerTitle:{fontWeight:'700',color:'#EFF3FF',fontSize:15},
  footerCopy:{color:'#A9B7CE',fontSize:12,lineHeight:19},
  footerActions:{flexDirection:'row',flexWrap:'wrap',gap:8},
  secondary:{borderWidth:1,borderColor:'#4D5D83',borderRadius:12,minHeight:44,paddingHorizontal:12,paddingVertical:10,justifyContent:'center'},
  secondaryText:{fontWeight:'700',fontSize:12,color:'#CDD8F2'},
  build:{fontSize:11,color:'#8C9CB9',lineHeight:17},
  notice:{fontSize:12,lineHeight:19,color:'#D8E0FF',backgroundColor:'#262E52',borderRadius:12,padding:12},
  finish:{minHeight:53,borderRadius:16,backgroundColor:'#6366F1',justifyContent:'center',alignItems:'center'},
  finishText:{color:'#fff',fontSize:15,fontWeight:'800'},
  finePrint:{fontSize:11,color:'#96A7C1',lineHeight:18,textAlign:'center'}
});
