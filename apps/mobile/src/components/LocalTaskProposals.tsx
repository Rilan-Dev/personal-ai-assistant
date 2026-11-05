import React,{useEffect,useRef,useState} from 'react';
import {ActivityIndicator,Pressable,Text,TextInput,View,StyleSheet} from 'react-native';
import {localTaskProposalsAvailable,saveLocalTaskProposal,listLocalTaskProposals,cancelLocalTaskProposal} from '../native/localTaskProposals';
import type {LocalTaskProposal} from '../native/localTaskProposals';

/** Owner-driven review-only storage. It never uses the agent executor. */
export function LocalTaskProposals(){
  const [goal,setGoal]=useState('');
  const [items,setItems]=useState<LocalTaskProposal[]>([]);
  const [busy,setBusy]=useState(false);
  // A rapid double-tap must not persist two different task IDs before React rerenders.
  const busyRef=useRef(false);
  const [loading,setLoading]=useState(false);
  const [notice,setNotice]=useState('');
  const available=localTaskProposalsAvailable();
  async function refresh(){
    if(!available||busyRef.current||loading)return;
    setLoading(true);
    try{setItems(await listLocalTaskProposals());setNotice('');}
    catch{setNotice('Could not securely read your local task proposals.');}
    finally{setLoading(false);}
  }
  useEffect(()=>{if(available)void refresh();},[available]);
  async function save(){
    if(busyRef.current||!available||loading)return;
    busyRef.current=true;setBusy(true);setNotice('');
    let savedId:string|null=null;
    try{
      savedId=await saveLocalTaskProposal(goal);
      setGoal('');
      // Keep the confirmed receipt even if a subsequent list read fails.
      setNotice('Saved privately for review. No assistant action or message was sent.');
      try{setItems(await listLocalTaskProposals());}
      catch{setNotice('Task saved privately. List refresh failed; do not save it again. Tap Refresh.');}
    }catch{
      // A rejected native write has uncertain persistence, so do not claim failure
      // or encourage duplicate retries until the owner refreshes the local store.
      setNotice(savedId
        ? 'Task saved. List refresh unavailable; tap Refresh.'
        : 'The task save could not be verified. Refresh before retrying.');
    }finally{busyRef.current=false;setBusy(false);}
  }
  async function cancel(item:LocalTaskProposal){
    if(busyRef.current||item.status!=='proposed'||loading)return;
    busyRef.current=true;setBusy(true);setNotice('');
    try{
      const canceled=await cancelLocalTaskProposal(item.taskId);
      if(!canceled)throw Error('not canceled');
      // A successful native cancellation is final, even if refreshing fails.
      setItems(previous=>previous.map(row=>row.taskId===item.taskId
        ? {...row,status:'cancelled'}:row));
      setNotice('Proposal canceled permanently; no action was executed.');
      try{setItems(await listLocalTaskProposals());}
      catch{setNotice('Proposal canceled. History refresh unavailable; tap Refresh.');}
    }catch{
      setNotice('Cancellation could not be confirmed. Refresh before retrying.');
    }finally{busyRef.current=false;setBusy(false);}
  }
  return <View style={styles.container} accessibilityLabel="Private task proposals">
    <Text style={styles.heading}>Private task proposals</Text>
    <Text style={styles.help}>Explicitly save a future request on this phone.</Text>
    <Text accessibilityLabel="Proposal safety: no AI agent, message or phone action runs from a proposal."
      style={styles.help}>Encrypted at rest. No AI agent, message or phone action runs from a proposal.</Text>
    {!available?<Text style={styles.help}>Update the Android APK to use on-device task proposals.</Text>:<>
      <TextInput style={styles.input} placeholder="E.g. Follow up on the ELV quotation"
        placeholderTextColor="#9AA9C4" value={goal} onChangeText={setGoal}
        maxLength={500} editable={!busy} multiline
        accessibilityLabel="Task to save for review"/>
      <View style={styles.actions}>
        <Pressable accessibilityRole="button" accessibilityLabel="Save task proposal"
          disabled={busy||!goal.trim()} onPress={()=>void save()} style={styles.action}>
          <Text style={styles.buttonText}>Save for review</Text>
        </Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Refresh local task proposals"
          disabled={busy||loading} onPress={()=>void refresh()} style={styles.action}>
          <Text style={styles.buttonText}>Refresh</Text>
        </Pressable>
      </View>
      {loading&&<ActivityIndicator accessibilityLabel="Loading local tasks" color="#C4B5FD"/>}
      {notice!==''&&<Text accessibilityRole="alert" style={styles.help}>{notice}</Text>}
      {items.length===0&&!loading&&<Text style={styles.help}>No saved proposals.</Text>}
      {items.map(item=><View key={item.taskId} style={styles.item}>
        <Text style={styles.goal}>{item.goal}</Text>
        <Text style={styles.help}>{item.status==='proposed'?'Awaiting your review · execution disabled':'Canceled permanently'}</Text>
        {item.status==='proposed'&&<Pressable accessibilityRole="button"
          accessibilityLabel={'Cancel task proposal: '+item.goal.slice(0,50)}
          disabled={busy} onPress={()=>void cancel(item)} style={styles.action}>
          <Text style={styles.buttonText}>Cancel proposal</Text>
        </Pressable>}
      </View>)}
    </>}
  </View>;
}
const styles=StyleSheet.create({
  container:{borderWidth:1,borderColor:'#303A59',backgroundColor:'#161F32',borderRadius:16,padding:15,gap:12},
  heading:{color:'#EAEFFF',fontSize:16,fontWeight:'700'},
  help:{color:'#AEBBD5',fontSize:12,lineHeight:19},
  goal:{color:'#F0F3FA',fontSize:14,lineHeight:21},
  input:{borderWidth:1,borderColor:'#55617D',borderRadius:10,color:'#F0F3FA',padding:12,minHeight:64,textAlignVertical:'top'},
  actions:{flexDirection:'row',flexWrap:'wrap',gap:10},
  action:{borderRadius:10,borderWidth:1,borderColor:'#5D68A4',backgroundColor:'#273451',paddingHorizontal:13,paddingVertical:10,alignSelf:'flex-start'},
  buttonText:{color:'#E5EDFF',fontSize:12,fontWeight:'600'},
  item:{borderColor:'#35415C',borderWidth:1,borderRadius:12,padding:12,gap:9}
});
