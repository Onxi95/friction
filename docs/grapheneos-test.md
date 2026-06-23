# GrapheneOS Test Checklist

1. Install signed release APK.
2. Confirm VPN permission prompt appears.
3. Confirm foreground notification appears after VPN start.
4. Confirm Brave package `com.brave.browser` is available.
5. Disable Brave Secure DNS.
6. Add one blocked domain and lock editing.
7. Verify blocked domain returns `NXDOMAIN` in Brave.
8. Verify allowed domain loads in Brave.
9. Verify non-Brave apps keep network access.
10. Enable Always-on VPN without lockdown mode and repeat blocked/allowed checks.
