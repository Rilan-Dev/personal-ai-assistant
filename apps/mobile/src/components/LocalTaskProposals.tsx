import React,{useEffect,useRef,useState} from 'react';
import {ActivityIndicator,Pressable,Text,TextInput,View,StyleSheet} from 'react-native';
import {localTaskProposalsAvailable,saveLocalTaskProposal,listLocalTaskProposals,cancelLocalTaskProposal,reviseLocalTaskProposal,readLocalTaskDetail,inspectOwnerReadOnlyPlan,runOwnerReviewedReadOnlyPlan,clearCancelledOwnerTaskDetails} from '../native/localTaskProposals';
import {NEXA_TASK_V2_ENABLED} from '@paa/agent-contracts';
import type {LocalTaskProposal,LocalTaskReviewDetail} from '../native/localTaskProposals';
import {listNativeTaskTimeline,revokeNativeTask,listUnifiedTaskTimeline} from '../native/nativeTaskCoordinator';
import type {NativeTaskSummary,UnifiedTaskSummary} from '../native/nativeTaskCoordinator';

/** Owner-driven review-only storage. It never uses the agent executor. */
export function LocalTaskProposals({prefill}:{prefill?:{
  nonce:number;goal:string;origin?:'chat'|'live-voice'|'assistant-role'|'overlay';handoffToken?:string;
}|null}){
  const [goal,setGoal]=useState('');
  const [handoff,setHandoff]=useState<{goal:string;token:string;origin:string}|null>(null);
  const [items,setItems]=useState<LocalTaskProposal[]>([]);
  const [nativeTasks,setNativeTasks]=useState<NativeTaskSummary[]>([]);
  const [allTasks,setAllTasks]=useState<UnifiedTaskSummary[]>([]);
  const [busy,setBusy]=useState(false);
  // A rapid double-tap must not persist two different task IDs before React rerenders.
  const busyRef=useRef(false);
  const [loading,setLoading]=useState(false);
  const [notice,setNotice]=useState('');
  const [detail,setDetail]=useState<LocalTaskReviewDetail|null>(null);
  const [editingId,setEditingId]=useState<string|null>(null);
  const [correction,setCorrection]=useState('');
  const available=localTaskProposalsAvailable();
  const lastReviewNonce=useRef<number|null>(null);
  useEffect(()=>{
    if(!prefill||prefill.nonce===lastReviewNonce.current)return;
    lastReviewNonce.current=prefill.nonce;
    setGoal(prefill.goal);
    setHandoff(prefill.handoffToken&&prefill.origin?
      {goal:prefill.goal,token:prefill.handoffToken,origin:prefill.origin}:null);
    setNotice('Review the task and tap Save for review. Nothing has started.');
  },[prefill]);
  async function refresh(){
    if(!available||busyRef.current||loading)return;
    setLoading(true);
    try{
      setItems(await listLocalTaskProposals());
      setDetail(null);
      setNotice('');
    }catch{setNotice('Could not securely read your local task proposals.');}
    try{setNativeTasks(await listNativeTaskTimeline());}
    catch{setNotice('Native task history could not be refreshed. Existing records remain.');}
    try{setAllTasks(await listUnifiedTaskTimeline());}
    catch{setNotice('Unified task index unavailable. Other local history remains accessible.');}
    finally{setLoading(false);}
  }
  useEffect(()=>{if(available)void refresh();},[available]);
  async function save(){
    if(busyRef.current||!available||loading)return;
    busyRef.current=true;setBusy(true);setNotice('');
    let savedId:string|null=null;
    try{
      savedId=await saveLocalTaskProposal(goal,
        handoff&&goal===handoff.goal?handoff.token:undefined);
      setGoal('');setHandoff(null);
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
  async function inspect(item:LocalTaskProposal){
    if(busyRef.current||loading)return;
    setLoading(true);
    try {
      const next=await readLocalTaskDetail(item.taskId);
      setDetail(next);
      setNotice('');
    }catch {
      setNotice('Task history could not be read. Check the current device and refresh.');
    }finally {setLoading(false);}
  }
  async function revise(item:LocalTaskProposal){
    if(busyRef.current||loading||item.status!=='proposed'||
       !Number.isSafeInteger(item.revision))return;
    busyRef.current=true;setBusy(true);setNotice('');
    try{
      const saved=await reviseLocalTaskProposal(item.taskId,item.revision!,correction);
      if(!saved)throw Error('Conflicting task revision');
      setEditingId(null);setCorrection('');setDetail(null);
      setNotice('Correction saved. Any previous unclaimed plan steps were invalidated; nothing executed.');
      try {setItems(await listLocalTaskProposals());}
      catch {setNotice('Correction saved. History refresh unavailable; tap Refresh before further edits.');}
    }catch{
      setNotice('Correction was not confirmed. Refresh before editing again; do not overwrite another revision.');
    }finally{busyRef.current=false;setBusy(false);}
  }
  /** This button cannot execute without an explicit review and matching revision. */
  async function runReviewed(item:LocalTaskProposal){
    if(!NEXA_TASK_V2_ENABLED||busyRef.current||loading||
      item.status!=='proposed'||detail?.taskId!==item.taskId||
      !Number.isSafeInteger(item.revision)||item.revision!==detail.revision)return;
    busyRef.current=true;setBusy(true);setNotice('');
    try{
      const fresh=await inspectOwnerReadOnlyPlan(item.taskId);
      if(fresh.length===0||fresh.some(step=>step.status!=='planned'))
        throw Error('No eligible native read-only plan');
      const result=await runOwnerReviewedReadOnlyPlan(item.taskId,item.revision!);
      setDetail(null);
      setNotice(result);
      try{setItems(await listLocalTaskProposals());}
      catch{setNotice(result+' Refresh to inspect the durable task state.');}
    }catch{
      setDetail(null);
      setNotice('Task activation was not confirmed. Refresh before another action.');
    }finally{busyRef.current=false;setBusy(false);}
  }
  /** Revocation is an atomic native task/mandate fence, never a UI-only flag. */
  /** Erase the encrypted description only; retain the event/replay history. */
  async function clearCancelled(item:LocalTaskProposal){
    if(busyRef.current||loading||item.status!=='cancelled'||
       item.goal==='[details cleared]')return;
    busyRef.current=true;setBusy(true);setNotice('');
    try{
      if(!await clearCancelledOwnerTaskDetails(item.taskId))
        throw Error('Redaction was already applied or is unavailable');
      setItems(previous=>previous.map(x=>x.taskId===item.taskId
        ? {...x,goal:'[details cleared]'}:x));
      setDetail(null);
      setNotice('Encrypted task details cleared. Permanent no-replay history is retained.');
      try{setItems(await listLocalTaskProposals());}
      catch{setNotice('Details cleared securely. Refresh history when available.');}
    }catch{
      setNotice('Task privacy change could not be confirmed. Refresh before retrying.');
    }finally{busyRef.current=false;setBusy(false);}
  }
  async function revokeReviewed(item:LocalTaskProposal){
    if(busyRef.current||loading||!['ready','running'].includes(item.status)||
       item.policyVersion!=='owner-review-v1')return;
    busyRef.current=true;setBusy(true);setNotice('');
    try{
      if(!await revokeNativeTask(item.taskId))
        throw Error('Task status changed before revocation');
      setItems(previous=>previous.map(task=>task.taskId===item.taskId
        ? {...task,status:'cancelled'}:task));
      setDetail(null);setNotice('The task mandate was revoked; no further steps may run.');
      try{setItems(await listLocalTaskProposals());}
      catch{setNotice('Revocation was saved. Refresh the task list when available.');}
    }catch{
      setNotice('Could not confirm task revocation. Refresh before acting again.');
    }finally{busyRef.current=false;setBusy(false);}
  }
  async function cancelNative(item:NativeTaskSummary){
    if(busyRef.current||loading||!['ready','running'].includes(item.status))return;
    busyRef.current=true;setBusy(true);setNotice('');
    try{
      if(!await revokeNativeTask(item.taskId))
        throw Error('Native task already finished or changed');
      setNativeTasks(previous=>previous.map(row=>row.taskId===item.taskId?
        {...row,status:'cancelled',stepStatus:'cancelled'}:row));
      setNotice('Native task mandate revoked. It cannot continue.');
      try{setNativeTasks(await listNativeTaskTimeline());}
      catch{setNotice('Revoked. Task history refresh unavailable; tap Refresh.');}
    }catch{
      setNotice('Revocation could not be confirmed. Refresh before retrying.');
    }finally{busyRef.current=false;setBusy(false);}
  }
  return <View style={styles.container} accessibilityLabel="Private task proposals">
    <Text style={styles.heading}>Private task proposals</Text>
    <Text style={styles.help}>Explicitly save a future request on this phone.</Text>
    <Text accessibilityLabel="Proposal safety: no AI agent, message or phone action runs from a proposal."
      style={styles.help}>Encrypted at rest. Saving never executes a task. Running a reviewed device read requires a separate owner action and an enabled native safety gate.</Text>
    {!available?<Text style={styles.help}>Update the Android APK to use on-device task proposals.</Text>:<>
      {handoff&&<Text style={styles.help}>Source: {handoff.origin}. Editing this request creates a new manually written Chat task instead; it does not authorize any action.</Text>}
      <TextInput style={styles.input} placeholder="E.g. Follow up on the ELV quotation"
        placeholderTextColor="#9AA9C4" value={goal} onChangeText={value=>{
          setGoal(value);
          if(handoff&&value!==handoff.goal)setHandoff(null);
        }}
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
        <Text style={styles.help}>Source: {item.origin==='live-voice'?'Voice':item.origin==='assistant-role'?'Android assistant':item.origin==='overlay'?'Overlay':'Chat'} · private owner review</Text>
        <Text style={styles.help}>{item.status==='proposed'?'Awaiting owner review · no action started':
          item.status==='cancelled'?'Canceled permanently':
          item.status==='uncertain'?'Outcome uncertain · never automatically retried':
          'Status: '+item.status}</Text>
        {item.status==='cancelled'&&item.goal!=='[details cleared]'&&
          <Pressable accessibilityRole="button"
            accessibilityLabel={'Clear encrypted details for cancelled task'}
            disabled={busy||loading} onPress={()=>void clearCancelled(item)}
            style={styles.action}>
            <Text style={styles.buttonText}>Clear saved details</Text>
          </Pressable>}
        {['ready','running'].includes(item.status)&&item.policyVersion==='owner-review-v1'&&
          <Pressable accessibilityRole="button"
            accessibilityLabel={'Revoke active task: '+item.goal.slice(0,50)}
            disabled={busy||loading} onPress={()=>void revokeReviewed(item)}
            style={styles.action}>
            <Text style={styles.buttonText}>Revoke task</Text>
          </Pressable>}
        {item.status==='proposed'&&<Pressable accessibilityRole="button"
          accessibilityLabel={'Cancel task proposal: '+item.goal.slice(0,50)}
          disabled={busy} onPress={()=>void cancel(item)} style={styles.action}>
          <Text style={styles.buttonText}>Cancel proposal</Text>
        </Pressable>}
        {item.status==='proposed'&&Number.isSafeInteger(item.revision)&&<Pressable
          accessibilityRole="button" accessibilityLabel="Correct saved task request"
          disabled={busy||loading} onPress={()=>{
            setEditingId(item.taskId);setCorrection(item.goal);setDetail(null);
          }} style={styles.action}>
          <Text style={styles.buttonText}>Correct request</Text>
        </Pressable>}
        {editingId===item.taskId&&item.status==='proposed'&&<View style={styles.item}>
          <Text style={styles.help}>This replaces the unexecuted request and invalidates draft plan steps.</Text>
          <TextInput style={styles.input} accessibilityLabel="Correct task request"
            value={correction} onChangeText={setCorrection} multiline maxLength={500}
            editable={!busy} placeholderTextColor="#9AA9C4"/>
          <View style={styles.actions}>
            <Pressable accessibilityRole="button" disabled={busy||!correction.trim()}
              onPress={()=>void revise(item)} style={styles.action}>
              <Text style={styles.buttonText}>Save correction</Text>
            </Pressable>
            <Pressable accessibilityRole="button" disabled={busy}
              onPress={()=>{setEditingId(null);setCorrection('');}} style={styles.action}>
              <Text style={styles.buttonText}>Discard correction</Text>
            </Pressable>
          </View>
        </View>}
        <Pressable accessibilityRole="button" accessibilityLabel="View task step and event history"
          disabled={busy||loading} onPress={()=>void inspect(item)} style={styles.action}>
          <Text style={styles.buttonText}>View steps and history</Text>
        </Pressable>
        {detail?.taskId===item.taskId&&<View style={styles.item}>
          <Text style={styles.help}>Revision {detail.revision} · {detail.status} · {detail.status==='proposed'?'awaiting owner decision':detail.status==='completed'?'local native evidence recorded':'no automatic external action'}</Text>
          {detail.steps.length===0&&<Text style={styles.help}>No plan steps saved.</Text>}
          {NEXA_TASK_V2_ENABLED&&item.status==='proposed'&&
             detail.steps.length>0&&detail.steps.every(step=>
               ['device.status','device.battery_status','device.storage_status'].includes(step.toolId)&&
               step.status==='planned'&&step.evidence==='native_reading')&&
             detail.revision===item.revision&&<Pressable
               accessibilityRole="button"
               accessibilityLabel="Run the owner-reviewed native read-only plan"
               disabled={busy||loading} onPress={()=>void runReviewed(item)}
               style={styles.action}>
               <Text style={styles.buttonText}>Run reviewed read-only plan</Text>
             </Pressable>}
          {detail.steps.map((step,index)=><Text key={step.ordinal+':'+index}
            style={styles.help}>Step {step.ordinal+1}: {step.toolId} · {step.status}{typeof step.missingDependencies==='number'&&step.missingDependencies>0
              ? ` · waiting on ${step.missingDependencies} dependenc${step.missingDependencies===1?'y':'ies'}`
              : step.dependenciesSatisfied ? ' · dependencies satisfied (execution disabled)' : ''}</Text>)}
          {detail.events.map((event,index)=><Text key={event.kind+':'+index}
            style={styles.help}>{new Date(event.createdAt).toLocaleString()} · {event.kind}</Text>)}
        </View>}
      </View>)}
      <Text style={styles.heading}>All task progress</Text>
      <Text style={styles.help}>Read-only index across device tasks and older voice checkpoints. A voice checkpoint is not verified external-app delivery or permission to resume.</Text>
      {allTasks.length===0&&<Text style={styles.help}>No task records found.</Text>}
      {allTasks.map(task=><View key={task.source+':'+task.taskId} style={styles.item}>
        <Text style={styles.goal}>{task.origin==='live-voice'?'Voice':task.origin==='chat'?'Chat':task.origin} · {task.status}</Text>
        <Text style={styles.help}>Source: {task.source==='native'?'Native task store':'Legacy voice checkpoint'} · {new Date(task.updatedAt).toLocaleString()}</Text>
        {task.source==='native'&&<Text style={styles.help}>Verified local steps: {task.verifiedSteps}/{task.stepCount}</Text>}
        {task.source==='legacy_voice_checkpoint'&&<Text style={styles.help}>Older voice task · completion not independently verified here</Text>}
      </View>)}
      <Text style={styles.heading}>Native task activity</Text>
      <Text style={styles.help}>Locally verified battery/storage tasks. An unknown outcome is never automatically replayed.</Text>
      {nativeTasks.length===0&&<Text style={styles.help}>No native task activity on this device.</Text>}
      {nativeTasks.map(task=><View key={task.taskId+':'+task.stepId} style={styles.item}>
        <Text style={styles.goal}>{task.operation==='get_battery_level'?'Battery reading':'Storage reading'} · Step {task.ordinal+1} · {task.origin==='chat'?'Chat':'Voice'}</Text>
        <Text style={styles.help}>{task.status==='running'&&task.stepStatus==='outcome_unknown'?
          'Outcome unresolved · no automatic retry':task.status} · evidence: {task.observation}</Text>
        {(task.status==='ready'||task.status==='running')&&<Pressable accessibilityRole="button"
          accessibilityLabel={'Cancel native task '+task.taskId.slice(-8)}
          disabled={busy||loading} onPress={()=>void cancelNative(task)} style={styles.action}>
          <Text style={styles.buttonText}>Revoke task</Text>
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
