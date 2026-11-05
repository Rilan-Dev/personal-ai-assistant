package dev.rilandev.personalaiassistant

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Phase 2: transactional persistence of reviewed, non-executable task plans.
 *
 * Only Android-minted owner review records may receive plans. Neither a model
 * nor an RN JavaScript bridge gains tool execution, mandate or approval powers.
 * Real native execution remains behind the future coordinator feature flag.
 */
internal object NexaTaskStore {
    private val lock=Any()
    private const val POLICY="owner-review-v1"
    private val id=Regex("[A-Za-z0-9][A-Za-z0-9_-]{7,99}")
    private val stepId=Regex("step-[A-Za-z0-9_-]{3,72}")
    private const val MAX_STEPS=8
    private const val MAX_PLAN_AGE_MS=300_000L

    /** The ONLY initial admitted registry is a native, no-side-effect read. */
    private val readOnlyRegistry=NexaTaskNativeRead.proposedReadTools
    private val ownerOrigins=setOf("chat","live-voice","assistant-role","overlay")
    private val rootKeys=setOf("version","taskId","ownerRequestId","origin",
        "policyVersion","expiresAtMs","steps","executable")
    private val stepKeys=setOf("stepId","toolId","input","dependsOn",
        "risk","expectedEvidence","requiresApproval")

    private data class ReadStep(val sourceId:String,val tool:String,
                                val dependencies:List<String>)
    private data class AdmittedPlan(val taskId:String,val origin:String,
                                    val policy:String,val steps:List<ReadStep>)

    private fun exactKeys(value:JSONObject,allowed:Set<String>):Boolean {
        if(value.length()!=allowed.size)return false
        val keys=value.keys()
        while(keys.hasNext())if(keys.next() !in allowed)return false
        return true
    }

    /** Independently reparse every field; JS validation is not Android authority. */
    private fun parse(raw:String):AdmittedPlan? {
        if(raw.length !in 32..12_000)return null
        val plan=try{JSONObject(raw)}catch(_:Exception){return null}
        if(!exactKeys(plan,rootKeys) || plan.optInt("version",-1)!=1 ||
            plan.opt("executable")!=false ||
            plan.optString("origin") !in ownerOrigins ||
            plan.optString("policyVersion")!=POLICY)return null
        val taskId=plan.optString("taskId")
        if(!id.matches(taskId) || plan.optString("ownerRequestId")!=taskId)
            return null
        val now=System.currentTimeMillis()
        val expires=plan.optLong("expiresAtMs",-1)
        if(expires<=now || expires>now+MAX_PLAN_AGE_MS)return null
        val arr=plan.optJSONArray("steps")?:return null
        if(arr.length() !in 1..MAX_STEPS)return null
        val used=mutableSetOf<String>()
        val steps=mutableListOf<ReadStep>()
        for(index in 0 until arr.length()){
            val item=arr.optJSONObject(index)?:return null
            if(!exactKeys(item,stepKeys))return null
            val sid=item.optString("stepId")
            val tool=item.optString("toolId")
            if(!stepId.matches(sid) || !used.add(sid) ||
                tool !in readOnlyRegistry ||
                item.optString("risk")!="low" ||
                item.optString("expectedEvidence")!="native_reading" ||
                item.opt("requiresApproval")!=false)return null
            val input=item.optJSONObject("input")?:return null
            if(input.length()!=0)return null
            val depends=item.optJSONArray("dependsOn")?:return null
            if(depends.length()>MAX_STEPS)return null
            val deps=mutableListOf<String>()
            for(j in 0 until depends.length()){
                val dependency=depends.optString(j)
                if(dependency !in used || dependency==sid ||
                    dependency in deps)return null
                deps.add(dependency)
            }
            steps.add(ReadStep(sid,tool,deps))
        }
        return AdmittedPlan(taskId,plan.optString("origin"),POLICY,steps)
    }

    /**
     * Insert the entire plan and its review event atomically. A plan never
     * changes the task status from 'proposed' and creates no mandate, approval
     * or action claim. Consequently nothing can execute from this API.
     */
    fun admitOwnerReadOnlyPlan(context:Context,raw:String):Boolean {
        val plan=parse(raw)?:return false
        return try { synchronized(lock){
            val helper=NexaConversationDb(context)
            try{
                val db=helper.writableDatabase
                // Prepare authenticated ciphertext before beginning SQLite work.
                val nativeIds=plan.steps.map{"step-"+UUID.randomUUID()}
                val sourceToNative=plan.steps.mapIndexed{index,item ->
                    item.sourceId to nativeIds[index]}.toMap()
                val cipher=plan.steps.map{item->
                    NexaConversationDb.sealNativeTaskArguments(
                        JSONObject().put("sourceStepId",item.sourceId)
                            .put("input",JSONObject())
                            .put("dependsOn",JSONArray(item.dependencies.map {
                                sourceToNative[it]
                            })).toString())
                }
                db.beginTransaction()
                try {
                    val correctOwner=db.rawQuery(
                        "SELECT origin,status,policy_version,session_id FROM agent_tasks WHERE task_id=?",
                        arrayOf(plan.taskId)
                    ).use{row->
                        row.moveToFirst() && row.getString(0)==plan.origin &&
                            row.getString(1)=="proposed" &&
                            row.getString(2)==plan.policy &&
                            row.getString(3).startsWith("owner-")
                    }
                    if(!correctOwner)return@synchronized false
                    val hasSteps=db.rawQuery(
                        "SELECT 1 FROM agent_task_steps WHERE task_id=? LIMIT 1",
                        arrayOf(plan.taskId)).use{it.moveToFirst()}
                    if(hasSteps)return@synchronized false
                    val now=System.currentTimeMillis()
                    for((ordinal,item) in plan.steps.withIndex()){
                        db.compileStatement(
                            "INSERT INTO agent_task_steps(step_id,task_id,ordinal,tool_id,status,risk,expected_evidence,arguments_cipher) VALUES(?,?,?,?,'planned','low','native_reading',?)"
                        ).use{stmt->
                            stmt.bindString(1,nativeIds[ordinal])
                            stmt.bindString(2,plan.taskId)
                            stmt.bindLong(3,ordinal.toLong())
                            stmt.bindString(4,item.tool)
                            stmt.bindBlob(5,cipher[ordinal])
                            if(stmt.executeInsert()==-1L)return@synchronized false
                        }
                    }
                    db.compileStatement(
                        "INSERT INTO agent_task_events(task_id,step_id,event_kind,detail_cipher,created_at) VALUES(?,NULL,'read_plan_admitted',?,?)"
                    ).use{stmt->
                        stmt.bindString(1,plan.taskId)
                        stmt.bindBlob(2,NexaConversationDb.sealNativeTaskArguments(
                            JSONObject().put("stepCount",plan.steps.size)
                                .put("executionEnabled",false).toString()))
                        stmt.bindLong(3,now)
                        if(stmt.executeInsert()==-1L)return@synchronized false
                    }
                    db.setTransactionSuccessful()
                    true
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }}catch(_:Exception){false}
    }

    /**
     * Build a review-only native plan from the owner's SAVED, encrypted goal,
     * not from model-provided parameters. Revision, origin, and the immutable
     * plan are bound in one transaction. This grants no mandate or approval.
     */
    fun prepareOwnerGoalReadPlan(context:Context,taskId:String,
                                 expectedRevision:Int):Boolean {
        if(!id.matches(taskId)||expectedRevision !in 1..1_000_000)return false
        return try{synchronized(lock){
            val helper=NexaConversationDb(context)
            try{
                val db=helper.writableDatabase
                db.beginTransaction()
                try{
                    val goal=db.rawQuery(
                        "SELECT origin,status,policy_version,session_id,plan_revision,goal_cipher "+
                            "FROM agent_tasks WHERE task_id=?",arrayOf(taskId)
                    ).use{row->
                        if(!row.moveToFirst() ||
                            row.getString(0) !in ownerOrigins ||
                            row.getString(1)!="proposed" ||
                            row.getString(2)!=POLICY ||
                            !row.getString(3).startsWith("owner-") ||
                            row.getInt(4)!=expectedRevision)
                            return@synchronized false
                        NexaConversationDb.openNativeTaskArguments(row.getBlob(5))
                    }
                    if(db.rawQuery(
                        "SELECT 1 FROM agent_task_steps WHERE task_id=? LIMIT 1",
                        arrayOf(taskId)
                    ).use{it.moveToFirst()})return@synchronized false

                    val operations=NexaTaskCoordinator.classifyOwnerReadOnlyPlan(goal)
                        ?:listOfNotNull(NexaTaskCoordinator.classifyOwnerFact(goal))
                    val tools=operations.map{
                        when(it){
                            "get_battery_level"->"device.battery_status"
                            "get_storage_usage"->"device.storage_status"
                            else->return@synchronized false
                        }
                    }
                    if(tools.isEmpty()||tools.size>MAX_STEPS ||
                        tools.any{it !in readOnlyRegistry})
                        return@synchronized false
                    val steps=tools.map{"step-"+UUID.randomUUID().toString()}
                    val now=System.currentTimeMillis()
                    for((ordinal,tool) in tools.withIndex()){
                        val deps=if(ordinal==0)JSONArray() else
                            JSONArray().put(steps[ordinal-1])
                        val arguments=JSONObject()
                            .put("sourceStepId","step-"+(ordinal+1).toString().padStart(4,'0'))
                            .put("input",JSONObject())
                            .put("dependsOn",deps)
                        db.compileStatement(
                            "INSERT INTO agent_task_steps(step_id,task_id,ordinal,tool_id,status,"+
                                "risk,expected_evidence,arguments_cipher) "+
                                "VALUES(?,?,?,?,'planned','low','native_reading',?)"
                        ).use{statement->
                            statement.bindString(1,steps[ordinal])
                            statement.bindString(2,taskId)
                            statement.bindLong(3,ordinal.toLong())
                            statement.bindString(4,tool)
                            statement.bindBlob(5,NexaConversationDb
                                .sealNativeTaskArguments(arguments.toString()))
                            check(statement.executeInsert()!=-1L)
                        }
                    }
                    val detail=NexaConversationDb.sealNativeTaskArguments(
                        JSONObject().put("stepCount",steps.size)
                            .put("executionEnabled",false)
                            .put("source","saved_owner_request").toString()
                    )
                    db.compileStatement(
                        "INSERT INTO agent_task_events(task_id,step_id,event_kind,"+
                            "detail_cipher,created_at) VALUES(?,NULL,?,?,?)"
                    ).use{statement->
                        statement.bindString(1,taskId)
                        statement.bindString(2,"owner_goal_read_plan_prepared")
                        statement.bindBlob(3,detail)
                        statement.bindLong(4,now)
                        check(statement.executeInsert()!=-1L)
                    }
                    db.setTransactionSuccessful()
                    true
                }finally{db.endTransaction()}
            }finally{helper.close()}
        }}catch(_:Exception){false}
    }

    /**
     * Interpret encrypted dependency edges for an owner-visible review only.
     * Satisfied dependencies NEVER imply permission to execute a step.
     *
     * All typed arguments stay within Kotlin. Corrupt/tampered ciphertext
     * fails closed, returning no candidate rather than guessing a dependency.
     */
    fun reviewDependencies(context:Context,taskId:String):JSONArray {
        if(!id.matches(taskId))return JSONArray()
        return try{synchronized(lock){
            val helper=NexaConversationDb(context)
            try{
                val db=helper.readableDatabase
                val status=db.rawQuery(
                    "SELECT status FROM agent_tasks WHERE task_id=? AND origin='chat' " +
                        "AND policy_version=? AND session_id LIKE 'owner-%'",
                    arrayOf(taskId,POLICY)
                ).use{c->
                    if(c.moveToFirst())c.getString(0) else ""
                }
                // Keep verified step metadata visible after completion, cancellation or
                // recovery. Only metadata is decrypted; input bodies are never returned.
                if(status !in setOf("proposed","cancelled","ready","running",
                        "completed","uncertain"))return@synchronized JSONArray()
                data class Step(val id:String,val ordinal:Int,val tool:String,
                                val state:String,val dependencies:List<String>)
                val rows=mutableListOf<Step>()
                db.rawQuery(
                    "SELECT step_id,ordinal,tool_id,status,arguments_cipher " +
                        "FROM agent_task_steps WHERE task_id=? ORDER BY ordinal LIMIT 9",
                    arrayOf(taskId)
                ).use{c->
                    while(c.moveToNext()){
                        if(rows.size>=MAX_STEPS)return@synchronized JSONArray()
                        val tool=c.getString(2)
                        if(tool !in readOnlyRegistry)return@synchronized JSONArray()
                        val content=JSONObject(NexaConversationDb.unsealNativeTaskArguments(c.getBlob(4)))
                        if(!exactKeys(content,setOf("sourceStepId","input","dependsOn")) ||
                            content.optJSONObject("input")?.length()!=0)
                            return@synchronized JSONArray()
                        val dependencies=content.optJSONArray("dependsOn")
                            ?:return@synchronized JSONArray()
                        if(dependencies.length()>MAX_STEPS)return@synchronized JSONArray()
                        val ids=(0 until dependencies.length()).map{dependencies.optString(it)}
                        if(ids.distinct().size!=ids.size)return@synchronized JSONArray()
                        rows.add(Step(c.getString(0),c.getInt(1),tool,c.getString(3),ids))
                    }
                }
                val allIds=rows.map{it.id}.toSet()
                val verified=rows.filter{it.state=="verified"}.map{it.id}.toSet()
                val result=JSONArray()
                for((index,row) in rows.withIndex()){
                    if(row.ordinal!=index || row.dependencies.any{it !in allIds || it==row.id})
                        return@synchronized JSONArray()
                    val missing=row.dependencies.count{it !in verified}
                    result.put(JSONObject()
                        .put("ordinal",row.ordinal)
                        .put("toolId",row.tool)
                        .put("status",row.state)
                        .put("evidence","native_reading")
                        .put("dependencies",row.dependencies.size)
                        .put("missingDependencies",missing)
                        .put("dependenciesSatisfied",missing==0&&status=="proposed"&&row.state=="planned")
                        .put("executable",false))
                }
                result
            }finally{helper.close()}
        }}catch(_:Exception){JSONArray()}
    }

    /** Owner review data only; plaintext tool inputs never enter logs. */
    fun readSafeStepSummary(context:Context,taskId:String):JSONArray {
        val result=JSONArray()
        if(!id.matches(taskId))return result
        val helper=NexaConversationDb(context)
        try{
            helper.readableDatabase.rawQuery(
                "SELECT s.ordinal,s.tool_id,s.status,s.expected_evidence FROM agent_task_steps s JOIN agent_tasks t ON s.task_id=t.task_id WHERE s.task_id=? AND t.origin IN (\'chat\',\'live-voice\',\'assistant-role\',\'overlay\') AND t.session_id LIKE \'owner-%\' AND t.policy_version=\'owner-review-v1\' ORDER BY s.ordinal",
                arrayOf(taskId)
            ).use{cursor->
                while(cursor.moveToNext()){
                    result.put(JSONObject()
                        .put("ordinal",cursor.getInt(0))
                        .put("toolId",cursor.getString(1))
                        .put("status",cursor.getString(2))
                        .put("evidence",cursor.getString(3)))
                }
            }
        }finally{helper.close()}
        return result
    }
}
