# Patch V5.5 step1 — Loki + Promtail

Apply from the root of the generated `ms-platform` project:

```bash
unzip -o v5.5-step1-loki-promtail-patch.zip
./apply-v5.5-step1-loki-promtail.sh
docker compose up -d --build --force-recreate service-batch loki promtail
./test-all.sh
source tokens.env
./benchmark-async-batch.sh 10 5
```

Test Loki:

```bash
curl -G http://localhost:3100/loki/api/v1/query_range \
  --data-urlencode 'query={service="service-batch"} |= "BATCH_COMPLETED"'
```
