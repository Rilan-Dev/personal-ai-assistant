import React, {useEffect, useRef, useState} from 'react';
import {AccessibilityInfo, Animated, Easing, StyleSheet, Text, View} from 'react-native';

/** Honor Android/iOS Reduce motion; all animations are decorative only. */
export function useNexaReducedMotion() {
  const [reduced, setReduced] = useState(true);
  useEffect(() => {
    let mounted=true;
    AccessibilityInfo.isReduceMotionEnabled()
      .then(value => { if (mounted) setReduced(value); })
      .catch(() => {});
    const listener=AccessibilityInfo.addEventListener('reduceMotionChanged',
      value => { if(mounted) setReduced(value); });
    return () => {mounted=false;listener.remove();};
  },[]);
  return reduced;
}

/** Lightweight one-time entrance; no deferred tool action or overlay permission. */
export function NexaReveal({children, enabled=true}:{
  children:React.ReactNode; enabled?:boolean;
}) {
  const reduced=useNexaReducedMotion();
  const alpha=useRef(new Animated.Value(1)).current;
  const offset=useRef(new Animated.Value(0)).current;
  useEffect(() => {
    if (!enabled || reduced) {
      alpha.setValue(1);
      offset.setValue(0);
      return;
    }
    // Nearly visible: async Reduce Motion checks should never flash the card.
    alpha.setValue(0.98);
    offset.setValue(8);
    const animation=Animated.parallel([
      Animated.timing(alpha,{toValue:1,duration:230,easing:Easing.out(Easing.cubic),useNativeDriver:true}),
      Animated.timing(offset,{toValue:0,duration:230,easing:Easing.out(Easing.cubic),useNativeDriver:true})
    ]);
    animation.start();
    return () => animation.stop();
  },[alpha,offset,reduced,enabled]);
  return <Animated.View style={{opacity:alpha,transform:[{translateY:offset}]}}>{children}</Animated.View>;
}

export type VoiceVisualState='idle'|'connecting'|'listening'|'speaking'|'muted'|'error';

/**
 * Visual session state, NOT proof of microphone access. Android's mic indicator
 * and live-voice status are authoritative; this component opens no audio stream.
 */
export function NexaVoiceOrb({state}: {state:VoiceVisualState}) {
  const reduced=useNexaReducedMotion();
  const scale=useRef(new Animated.Value(1)).current;
  const alpha=useRef(new Animated.Value(0.48)).current;
  const active=['connecting','listening','speaking'].includes(state);
  useEffect(()=>{
    if(reduced || !active){
      scale.stopAnimation();alpha.stopAnimation();
      scale.setValue(1);alpha.setValue(0.48);
      return;
    }
    const loop=Animated.loop(Animated.parallel([
      Animated.sequence([
        Animated.timing(scale,{toValue:1.065,duration:850,easing:Easing.inOut(Easing.sin),useNativeDriver:true}),
        Animated.timing(scale,{toValue:1,duration:850,easing:Easing.inOut(Easing.sin),useNativeDriver:true})
      ]),
      Animated.sequence([
        Animated.timing(alpha,{toValue:.88,duration:850,useNativeDriver:true}),
        Animated.timing(alpha,{toValue:.48,duration:850,useNativeDriver:true})
      ])
    ]));
    loop.start();
    return ()=>{loop.stop();scale.setValue(1);alpha.setValue(.48);};
  },[reduced,active,scale,alpha]);
  const details:Record<VoiceVisualState,{headline:string,hint:string}> = {
    idle:{headline:'Ready when you are',hint:'Tap to start; microphone is off'},
    connecting:{headline:'Connecting',hint:'Setting up a secure voice session'},
    listening:{headline:'Listening',hint:'Microphone session active'},
    speaking:{headline:'Speaking',hint:'Nexa is replying'},
    muted:{headline:'Microphone muted',hint:'Stop live voice to release the microphone'},
    error:{headline:'Connection paused',hint:'Check the voice status and retry'}
  };
  const tone=state==='error'?'#F9A8B4':state==='muted'?'#C3CAD9':'#C5CBFF';
  return <View style={styles.voiceVisual} accessibilityRole="text"
    accessibilityLabel={details[state].headline+'. '+details[state].hint}>
    <Animated.View style={[styles.aura,{opacity:alpha,transform:[{scale}]}]}/>
    <View style={[styles.core,(state==='error'||state==='muted')&&styles.coreInactive]}>
      <Text style={[styles.glyph,{color:tone}]}>{state==='muted'?'×':state==='error'?'!':'✦'}</Text>
    </View>
    <Text style={styles.headline}>{details[state].headline}</Text>
    <Text style={styles.hint}>{details[state].hint}</Text>
  </View>;
}

const styles=StyleSheet.create({
  voiceVisual:{alignItems:'center',justifyContent:'center',paddingTop:4,paddingBottom:12},
  aura:{position:'absolute',top:20,width:152,height:152,borderRadius:76,
    backgroundColor:'#565AC6',borderWidth:1,borderColor:'#A1ACFF'},
  core:{width:120,height:120,borderRadius:60,backgroundColor:'#262B61',
    borderWidth:1,borderColor:'#666EDF',alignItems:'center',justifyContent:'center',marginBottom:25},
  coreInactive:{backgroundColor:'#303342',borderColor:'#6B7285'},
  glyph:{fontSize:55,fontWeight:'700'},
  headline:{color:'#F6F7FF',fontWeight:'700',fontSize:22},
  hint:{color:'#AAB8D3',fontSize:12,marginTop:7,textAlign:'center'}
});
