package dev.rilandev.personalaiassistant

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ViewManager

/** Registered explicitly in MainApplication; no third-party native plugin. */
class DeviceControlPackage : ReactPackage {
    override fun createNativeModules(
        reactContext: ReactApplicationContext
    ): List<NativeModule> = listOf(DeviceControlModule(reactContext), AgentJournalModule(reactContext), VoiceControlModule(reactContext), FlashlightControlModule(reactContext), ScreenAssistModule(reactContext), RealtimeVoiceModule(reactContext), SarvamSpeechModule(reactContext))

    override fun createViewManagers(
        reactContext: ReactApplicationContext
    ): List<ViewManager<*, *>> = emptyList()
}
