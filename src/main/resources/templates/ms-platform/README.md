# ms-platform

## Lancement
```bash
./prod-start.sh
```

## Utilisateurs Keycloak
| Utilisateur | Mot de passe | Rôles |
|---|---|---|
| `test-admin` | `admin123` | ADMIN, USER_BATCH, toutes les ressources |
| `test-batch` | `user123` | USER_BATCH |
| `test-service-a` | `user123` | USER_SERVICE_A |
| `test-service-b` | `user123` | USER_SERVICE_B |
| `test-service-c` | `user123` | USER_SERVICE_C |

## Tests
```bash
./test-all.sh
source tokens.env
./benchmark-async-batch.sh 100 40
```

URLs: Gateway http://localhost:9000, Eureka http://localhost:8761, Admin http://localhost:9100, Keycloak http://localhost:8089, RabbitMQ http://localhost:15672.


## Réglages batch

Variables dans `.env` :

```env
BATCH_REPLICAS=4
BATCH_FILE_CONCURRENCY=5
BATCH_MEMORY_LIMIT=768m
```

Commandes :

```bash
./scale-batch.sh 4
./benchmark-async-batch.sh 10 5
```

`BATCH_FILE_CONCURRENCY` règle le parallélisme intra-job dans `service-batch`. Une valeur de `5` est un bon équilibre pour une machine type i5 / 32 Go RAM.


## Batch tuning

```env
BATCH_REPLICAS=4
BATCH_FILE_CONCURRENCY=5
BATCH_MIN_DELAY_MS=500
BATCH_MAX_DELAY_MS=1500
BATCH_MEMORY_LIMIT=768m
```


## Observability Loki / Grafana

La stack légère de logs est incluse :

- Loki : http://localhost:3100
- Grafana : http://localhost:3000
- Login Grafana : `admin / admin` par défaut
- Timezone Grafana : `Browser Time` / `Europe/Paris` via `TZ=Europe/Paris`

Dashboard : `Batch / Batch Dashboard`

Démarrage observability seule :

```bash
docker compose up -d loki promtail grafana
```

Test Loki :

```bash
curl -G http://localhost:3100/loki/api/v1/query_range \
  --data-urlencode 'query={service="service-batch"} |= "BATCH_COMPLETED"'
```

Le dashboard affiche :

- `BATCH_COMPLETED`
- `BATCH_FAILED`
- recherche par `jobId`
- filtre par instance/conteneur
