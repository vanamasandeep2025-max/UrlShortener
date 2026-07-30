# Local CA certificates

Drop any extra CA certificates (`.crt`, PEM-encoded) here that the Docker build should
trust, in addition to the JDK's normal trust store.

This exists for networks where a local proxy, corporate firewall, or antivirus "web/mail
shield" (Avast, Kaspersky, etc.) transparently intercepts and re-signs outbound HTTPS
traffic with its own certificate authority. Your host OS already trusts that CA (it's
usually installed automatically), which is why `curl`/browsers work fine - but a freshly
built Docker image has its own separate Java trust store that has never heard of it,
so `mvn dependency:go-offline` fails with a `PKIX path building failed` /
`certificate_unknown` error when the build tries to reach Maven Central.

Files placed here are **not committed** (see `.gitignore`) - they're specific to whatever
network the image happens to be built on, not to the project.

## Exporting your intercepting CA (Windows + Avast example)

```powershell
$cert = Get-ChildItem Cert:\LocalMachine\Root | Where-Object { $_.Subject -like "*Avast*" }
$path = "backend\certs\local-network-ca.crt"
[System.IO.File]::WriteAllText($path, "-----BEGIN CERTIFICATE-----`n" +
  [Convert]::ToBase64String($cert[0].RawData, 'InsertLineBreaks') +
  "`n-----END CERTIFICATE-----")
```

Adjust the `Where-Object` filter for whatever is actually intercepting your traffic - check
with `openssl s_client -connect repo.maven.apache.org:443 | openssl x509 -noout -issuer`
to see who signed the certificate your machine is actually receiving.
