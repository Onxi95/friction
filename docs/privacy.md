# Privacy

FocusGate runs locally on the Android device.

- No remote server is used.
- No analytics are collected.
- Rules and lock state are stored locally in Proto DataStore.
- DNS requests from supported browsers pass through the local VPN service.
- Allowed DNS requests are forwarded to the configured upstream resolver.
- Blocked DNS requests receive local `NXDOMAIN` responses.

The MVP upstream resolver is Cloudflare DNS at `1.1.1.1`.
