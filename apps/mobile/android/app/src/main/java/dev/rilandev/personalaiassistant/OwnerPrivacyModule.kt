package dev.rilandev.personalaiassistant

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.ContactsContract
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.BaseActivityEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer
import kotlin.math.round

/** Explicit owner-controlled observations; neither model nor background
 * services may call this bridge. No cached sensitive results or telemetry.
 * Location is rounded to approximately 1.1 km decimal-degree precision.
 * Contacts are accessed only through Android's temporary picker URI grant.
 */
class OwnerPrivacyModule(private val context:ReactApplicationContext):
    ReactContextBaseJavaModule(context) {
    override fun getName()="PAAOwnerPrivacy"
    private val pendingContact=AtomicReference<Promise?>(null)
    private val locationBusy=AtomicBoolean(false)
    private val main=Handler(Looper.getMainLooper())

    private val contactListener=object:BaseActivityEventListener(){
        override fun onActivityResult(activity:Activity,requestCode:Int,
                                      resultCode:Int,data:Intent?){
            if(requestCode!=SELECT_PHONE)return
            val callback=pendingContact.getAndSet(null)?:return
            if(resultCode!=Activity.RESULT_OK || data?.data==null){
                callback.resolve(null)
                return
            }
            val uri=data.data!!
            if(uri.scheme!="content" ||
                uri.authority!=ContactsContract.AUTHORITY){
                callback.reject("INVALID_CONTACT_URI","Contact picker returned an untrusted URI")
                return
            }
            try {
                val fields=arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER)
                context.contentResolver.query(uri,fields,null,null,null).use{ rows->
                    if(rows==null || !rows.moveToFirst()) {
                        callback.reject("CONTACT_UNAVAILABLE","Could not read selected number")
                        return
                    }
                    val name=rows.getString(rows.getColumnIndexOrThrow(fields[0])).orEmpty()
                    val phone=rows.getString(rows.getColumnIndexOrThrow(fields[1])).orEmpty()
                    if(name.length>160 || phone.length>80 || phone.isBlank()){
                        callback.reject("CONTACT_INVALID","Selected contact data invalid")
                        return
                    }
                    val result=Arguments.createMap()
                    result.putString("name",name)
                    result.putString("phone",phone)
                    result.putString("source","android-contact-picker")
                    callback.resolve(result)
                }
            }catch(_:Exception){
                callback.reject("CONTACT_UNAVAILABLE","Cannot read the selected contact")
            }
        }
    }

    init { context.addActivityEventListener(contactListener) }

    private fun visible():Boolean=context.currentActivity?.hasWindowFocus()==true

    @ReactMethod fun pickPhoneContact(p:Promise){
        if(!visible()){
            p.reject("FOREGROUND_REQUIRED","Open Tools to select a contact")
            return
        }
        if(!pendingContact.compareAndSet(null,p)){
            p.reject("PICKER_BUSY","Another contact selection is running")
            return
        }
        val activity=context.currentActivity
        if(activity==null){
            pendingContact.set(null)
            p.reject("NO_ACTIVITY","Contact picker requires a visible screen")
            return
        }
        try {
            val intent=Intent(Intent.ACTION_PICK).apply {
                type=ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent,SELECT_PHONE)
        }catch(_:Exception){
            pendingContact.getAndSet(null)?.reject("PICKER_UNAVAILABLE",
                "No Android contact picker available")
        }
    }

    @ReactMethod fun readApproximateLocation(p:Promise){
        if(!visible()){
            p.reject("FOREGROUND_REQUIRED","Open Tools to read location")
            return
        }
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.R){
            p.reject("UNSUPPORTED_ANDROID","Approximate location requires Android 11 or newer")
            return
        }
        if(context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=
            PackageManager.PERMISSION_GRANTED &&
           context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=
            PackageManager.PERMISSION_GRANTED){
            p.reject("LOCATION_PERMISSION_REQUIRED","Grant approximate location first")
            return
        }
        if(!locationBusy.compareAndSet(false,true)){
            p.reject("LOCATION_BUSY","A location read is already running")
            return
        }
        try {
            val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers=manager.getProviders(true)
            val choice=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.S &&
                providers.contains(LocationManager.FUSED_PROVIDER))
                LocationManager.FUSED_PROVIDER
                else LocationManager.NETWORK_PROVIDER
            if(!providers.contains(choice)){
                locationBusy.set(false)
                p.reject("LOCATION_DISABLED","Turn on Android location services")
                return
            }
            val signal=CancellationSignal()
            val resolved=AtomicBoolean(false)
            val timeout=Runnable{
                if(resolved.compareAndSet(false,true)){
                    signal.cancel()
                    locationBusy.set(false)
                    p.reject("LOCATION_TIMEOUT","Could not obtain a current location")
                }
            }
            main.postDelayed(timeout,12000L)
            try {
                manager.getCurrentLocation(choice,signal,context.mainExecutor,
                    Consumer<Location?>{ fix->
                    if(!resolved.compareAndSet(false,true))return@Consumer
                    main.removeCallbacks(timeout)
                    locationBusy.set(false)
                    if(fix==null || !fix.latitude.isFinite() || !fix.longitude.isFinite() ||
                        fix.latitude !in -90.0..90.0 ||
                        fix.longitude !in -180.0..180.0 ||
                        (SystemClock.elapsedRealtimeNanos()-fix.elapsedRealtimeNanos)
                            !in 0..60_000_000_000L){
                        p.reject("LOCATION_UNAVAILABLE","No fresh approximate location")
                    }else{
                        val data=Arguments.createMap()
                        data.putDouble("latitude",quantizeCoordinate(fix.latitude))
                        data.putDouble("longitude",quantizeCoordinate(fix.longitude))
                        data.putString("precision","two-decimal-degrees")
                        data.putString("source","android-location-manager")
                        p.resolve(data)
                    }
                    })
            }catch(_:Exception) {
                main.removeCallbacks(timeout)
                resolved.set(true)
                locationBusy.set(false)
                p.reject("LOCATION_UNAVAILABLE","Location request rejected by Android")
            }
        }catch(_:Exception){
            locationBusy.set(false)
            p.reject("LOCATION_UNAVAILABLE","Android could not read location")
        }
    }
    companion object{
        private const val SELECT_PHONE=61823
        internal fun quantizeCoordinate(value:Double):Double=round(value*100.0)/100.0
    }
}
