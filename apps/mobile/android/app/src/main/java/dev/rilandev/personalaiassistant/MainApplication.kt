package dev.rilandev.personalaiassistant

import android.app.Application
import android.os.Build
import android.content.res.Configuration
import java.io.File

import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.ReactPackage
import com.facebook.react.ReactHost
import com.facebook.react.common.ReleaseLevel
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint

import expo.modules.ApplicationLifecycleDispatcher
import expo.modules.ExpoReactHostFactory

class MainApplication : Application(), ReactApplication {

  private val isQaFixtureProcess: Boolean by lazy(LazyThreadSafetyMode.NONE) {
    val processName=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P) {
      Application.getProcessName()
    } else {
      File("/proc/self/cmdline").readText().substringBefore('\u0000')
    }
    processName=="$packageName:nexa_qa"
  }

  override val reactHost: ReactHost by lazy {
    ExpoReactHostFactory.getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          // Packages that cannot be autolinked yet can be added manually here, for example:
          add(DeviceControlPackage())
        }
    )
  }

  override fun onCreate() {
    super.onCreate()
    // Debug-only synthetic Accessibility fixtures run in a private process.
    // They must not initialize React Native, recover user voice state, or
    // perform production application startup work.
    if(isQaFixtureProcess)return
    // Android may have killed a previous Voice process between owner-approved
    // steps. Fence old cursor checkpoints before starting any new agent UI.
    // This never resumes a claimed third-party UI action.
    NexaLegacyVoiceTaskMirror.recoverAbandonedAfterProcessRestart(this)
    DefaultNewArchitectureEntryPoint.releaseLevel = try {
      ReleaseLevel.valueOf(BuildConfig.REACT_NATIVE_RELEASE_LEVEL.uppercase())
    } catch (e: IllegalArgumentException) {
      ReleaseLevel.STABLE
    }
    loadReactNative(this)
    ApplicationLifecycleDispatcher.onApplicationCreate(this)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    if(!isQaFixtureProcess)
      ApplicationLifecycleDispatcher.onConfigurationChanged(this, newConfig)
  }
}
