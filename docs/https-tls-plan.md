# HTTPS/TLS Plan

The VPS currently serves HTTP through nginx. Before real production use,
nginx should terminate HTTPS.

## Recommended Target

```text
Android / browser
  -> https://<domain>
    -> nginx container
      -> server container
```

Use a real domain name rather than a raw IP address for Let's Encrypt.

## Suggested Approach

1. Point a DNS A record at the VPS IP.
2. Open ports 80 and 443 on the VPS firewall.
3. Add a certificate workflow:
   - Option A: host-level Certbot writes cert files mounted into nginx.
   - Option B: Compose-managed ACME companion container.
4. Update `nginx/default.conf` to listen on 443 with TLS.
5. Redirect HTTP to HTTPS after certificates are issued.
6. Change Android `guardian.api.baseUrl` to:

```properties
guardian.api.baseUrl=https://<domain>/api
```

The Android app validates this value at startup. If it is blank, malformed, or
does not end with `/api`, startup reporting is skipped and a local app log is
written.

## Notes

- Do not send AI API keys from Android.
- Keep production secrets in the VPS `.env`.
- Add admin viewer authentication before relying on `/admin/logs.html` over the
  public internet.
