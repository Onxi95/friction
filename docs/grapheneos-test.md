# GrapheneOS Test Checklist

1. Install signed release APK.
2. Confirm VPN permission prompt appears.
3. Confirm foreground notification appears after VPN start.
4. Confirm at least one supported browser is available: Brave `com.brave.browser`, Chrome `com.android.chrome`, or Vanadium `app.vanadium.browser`.
5. Disable Secure DNS in the tested browser.
6. Add one blocked domain and lock editing.
7. Verify blocked domain returns `NXDOMAIN` in the tested browser.
8. Verify allowed domain loads in the tested browser.
9. Verify apps outside the supported-browser allowlist keep network access.
10. Enable Always-on VPN without lockdown mode and repeat blocked/allowed checks.
11. Run `android/scripts/device-evidence.sh` and attach the generated evidence directory to the release notes.
