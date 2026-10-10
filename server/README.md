This is a small approvals test server used by the sample app.

Run locally (Node.js required):

1) Install dependencies (only uses builtin modules - no install needed). If you add packages later, run:

   npm install

2) Start the server with an API key (recommended):

   # Windows PowerShell example - set API key and start
   $env:APP_SERVER_KEY = "my-secret-key"; node index.js

   # Or on Linux/macOS
   APP_SERVER_KEY=my-secret-key node index.js

   By default the server listens on port 3000. You can change port with the PORT environment variable.

3) Example requests (use the x-api-key header):

   # Create approval
   curl -X POST "http://localhost:3000/approvals" -H "Content-Type: application/json" -H "x-api-key: my-secret-key" -d '{"email":"alice@example.com","deviceId":"dev123","deviceName":"Alice's phone"}'

   # List pending approvals for an email
   curl "http://localhost:3000/approvals?email=alice@example.com" -H "x-api-key: my-secret-key"

   # Approve / deny
   curl -X POST "http://localhost:3000/approvals/<requestId>/approve" -H "x-api-key: my-secret-key"

Notes:
- The server uses APP_SERVER_KEY or API_KEY environment variables. If neither is set it falls back to the default key `dev_key` (useful for quick local testing).
- The Android app will automatically send the value from BuildConfig.APP_SERVER_KEY as the `x-api-key` header when configured in `app/build.gradle.kts` (set APP_SERVER_KEY in your environment, gradle.properties or local.properties).

# Production recommendations

For production use please follow these steps:

- Set `APP_SERVER_KEY` to a strong random value and never commit it to source control.
- Run the service behind a TLS-terminating reverse proxy (nginx) and do not expose the Node process directly.
- Use a persistent database (Postgres, Redis) instead of `approvals.json` when you need durability or multiple instances.
- Configure `APP_ALLOWED_ORIGINS` (comma-separated) to restrict browser CORS. Mobile apps typically do not send an Origin header.
- Set `NODE_ENV=production` and ensure `APP_SERVER_KEY` is present — the server will refuse to start without it.
- Consider replacing the simple API key with short-lived JWTs or use your auth issuer to sign tokens for stronger guarantees.
- Integrate FCM (Firebase Cloud Messaging) on the server to notify the primary device instead of relying on polling.

Example nginx proxy snippet:

```
server {
  listen 443 ssl;
  server_name approvals.example.com;

  ssl_certificate /etc/letsencrypt/live/approvals.example.com/fullchain.pem;
  ssl_certificate_key /etc/letsencrypt/live/approvals.example.com/privkey.pem;

  location / {
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_pass http://127.0.0.1:3000;
  }
}
```

Systemd unit example:

```
[Unit]
Description=Approval Server
After=network.target

[Service]
Type=simple
User=appuser
WorkingDirectory=/srv/fastpay/approval-server
Environment=NODE_ENV=production
Environment=APP_SERVER_KEY=your_production_key_here
ExecStart=/usr/bin/node index.js
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

## Using Postgres for persistence

If you want durable, production-grade persistence use a managed Postgres instance and set `DATABASE_URL` in the environment. Example `DATABASE_URL`:

```
postgres://username:password@hostname:5432/approvals_db
```

Start the server with:

```powershell
$env:DATABASE_URL = 'postgres://username:password@host:5432/approvals_db'
$env:APP_SERVER_KEY = 'your_production_key'
$env:NODE_ENV = 'production'
node index.js
```

The server will apply a minimal set of migrations to create `approvals` and `sessions` tables if they do not exist.

The server still supports the lightweight file-backed `approvals.json` when `DATABASE_URL` is not set (useful for quick local testing).
