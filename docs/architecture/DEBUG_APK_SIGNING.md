# Reproducible private DEBUG APK updates without wiping task journals

Default GitHub Actions builds use a disposable Android DEBUG signing key.
Android treats each generated key as a different app identity; installing
a new APK over a previous one generally fails with INSTALL_FAILED_UPDATE_INCOMPATIBLE.
Uninstalling removes the app's Android Keystore key and encrypted journal,
so do not mistake uninstall/reinstall for a successful process-recovery test.

To reuse one owner-controlled DEBUG key across CI builds:
1. Generate a dedicated DEBUG keystore locally, never in the repository:
   keytool -genkeypair -keystore paa-debug.keystore -alias androiddebugkey
   -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 3650
   -dname "CN=PAA Device Testing,O=Private Debug,C=US" -noprompt
2. Convert its raw bytes to Base64 locally and save as the GitHub Actions
   repository secret PAA_DEBUG_KEYSTORE_B64.
   Windows PowerShell example:
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("paa-debug.keystore")) | Set-Clipboard
3. The Android debug compile workflow automatically uses this secret only
   when it exists, verifies alias androiddebugkey and the standard debug
   password, and never prints, logs, commits or uploads the key.
4. Save the keystore securely outside GitHub, as losing it breaks in-place
   upgrades. Treat it as a device-testing credential, not a production key.
5. Keep using the same signer to upgrade an installed DEBUG APK and run
   force-stop/restart/replay tests without wiping journal data.

If the secret is absent, the existing ephemeral debug-key path remains
available. If it is set but invalid, CI fails instead of silently signing
with a different key that would break data continuity.

This is NOT a release signing workflow and is not proof of production
security. Production release must use a distinct appropriately protected
release key and explicitly audited Gradle configuration.
