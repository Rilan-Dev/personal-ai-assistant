package dev.rilandev.personalaiassistant

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import java.util.UUID

/**
 * One previously owner-approved external Intent, never model authorization.
 *
 * The target is pinned to one exact exported, uniquely resolved installed
 * Android component. An irreversible native journal claim is committed before
 * dispatch, and the final OS Intent is serialized against owner Pause, Voice
 * Stop and pause-generation invalidation. Rechecking the component inside
 * the dispatch fence prevents silent handler substitution. Neither accepted
 * dispatch nor optional package observation proves the user's task completed.
 */
internal object NexaApprovedExternalAction {
    enum class Result { ACCEPTED, DENIED, UNCERTAIN }

    fun launch(context:Context,source:Intent,targetPackage:String,
               origin:String):Result {
        if(origin !in setOf("chat","live-voice","assistant-role","overlay") ||
            !targetPackage.matches(Regex("[A-Za-z][A-Za-z0-9_.]{2,150}")))
            return Result.DENIED
        val keyguard=context.getSystemService(Context.KEYGUARD_SERVICE)
            as? KeyguardManager
        if(keyguard?.isKeyguardLocked==true)return Result.DENIED
        val fence=NexaNativeActionSafety.approvalFence(context)
            ?:return Result.DENIED
        val resolved=try{source.resolveActivity(context.packageManager)}
            catch(_:Exception){null} ?:return Result.DENIED
        if(resolved.packageName!=targetPackage ||
            resolved.className.contains("ResolverActivity",ignoreCase=true))
            return Result.DENIED
        // A package-only Intent can later resolve to a different activity;
        // pin the exact already-observed explicit component.
        val intent=Intent(source).apply{
            component=resolved
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val token=UUID.randomUUID().toString()
        if(!NexaUiActionJournal.claim(context,token,"app_launch",targetPackage,
                origin=origin))return Result.DENIED
        return try{
            val accepted=NexaNativeActionSafety.dispatchWithFence(context,fence){
                val current=source.resolveActivity(context.packageManager)
                if(current!=resolved)return@dispatchWithFence false
                context.startActivity(intent)
                true
            }
            when(accepted){
                true->{
                    if(!NexaUiActionJournal.finish(context,token,"android_accepted"))
                        Result.UNCERTAIN
                    else{
                        NexaAppLaunchObservation.observeAfter(context,token,targetPackage)
                        Result.ACCEPTED
                    }
                }
                false,null->{
                    NexaUiActionJournal.finish(context,token,"android_rejected")
                    Result.DENIED
                }
            }
        }catch(_:Exception){
            // Android dispatch may have reached the external app before an
            // exception: never automatically retry this consumed claim.
            NexaUiActionJournal.finish(context,token,"unknown")
            Result.UNCERTAIN
        }
    }
}
