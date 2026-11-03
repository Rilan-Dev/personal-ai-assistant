package dev.rilandev.personalaiassistant

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

/** Android's own recent process-exit evidence (API 30+), not a guessed crash cause.
 * Safe summary excludes other apps and does not expose account IDs, secrets or audio.
 * Detailed ANR trace is included only in an explicitly owner-shared diagnostics export.
 */
internal object NexaCrashDiagnostics {
    private fun entries(context:Context,limit:Int):List<ApplicationExitInfo> {
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.R)return emptyList()
        return try {
            val manager=context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            manager.getHistoricalProcessExitReasons(null,0,limit.coerceIn(1,20))
                .filter { it.processName == context.packageName ||
                    it.processName.startsWith(context.packageName+":") }
        }catch (_:Exception){ emptyList() }
    }
    private fun reason(code:Int):String=when(code) {
        ApplicationExitInfo.REASON_CRASH -> "java_crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "native_crash"
        ApplicationExitInfo.REASON_ANR -> "app_not_responding"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "resource_limit"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "user_requested"
        ApplicationExitInfo.REASON_USER_STOPPED -> "user_stopped"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission_changed"
        ApplicationExitInfo.REASON_EXIT_SELF -> "normal_exit"
        else -> "other"
    }
    private fun metadata(info:ApplicationExitInfo):JSONObject =
        JSONObject().put("process",info.processName)
            .put("timestamp",info.timestamp)
            .put("reason",reason(info.reason))
            .put("reasonCode",info.reason)
            .put("pid",info.pid)
            .put("importance",info.importance)
            .put("rssKb",info.rss)
    fun recent(context:Context,limit:Int=12):JSONArray {
        val data=JSONArray()
        for(info in entries(context,limit))data.put(metadata(info))
        return data
    }
    fun ownerExport(context:Context):String {
        val result=JSONArray()
        for(info in entries(context,12)){
            val item=metadata(info)
            // System ANR traces can contain app UI values. Only export on an
            // explicit foreground action. Cap it to 16KiB per event.
            if(info.reason in setOf(ApplicationExitInfo.REASON_ANR,
                ApplicationExitInfo.REASON_CRASH,ApplicationExitInfo.REASON_CRASH_NATIVE)){
                val trace=try { info.traceInputStream?.bufferedReader()?.use {
                    val buffer=CharArray(16_384)
                    val count=it.read(buffer,0,buffer.size)
                    if(count>0)String(buffer,0,count) else ""
                } ?: "" }catch (_:Exception){""}
                if(trace.isNotBlank())item.put("systemTrace",trace)
            }
            result.put(item)
        }
        return JSONObject().put("format","Nexa Android process exits v1")
            .put("exportedAt",System.currentTimeMillis())
            .put("processExits",result).toString(2)
    }
}
