# Correctif Loki BatchWorker

Corrige le log structuré `BATCH_COMPLETED` dans `service-batch` pour utiliser l'objet `job` au lieu d'une variable inexistante `response`.

Test recommandé :

```bash
mvn clean package -DskipTests
docker compose up -d --build --force-recreate service-batch loki promtail
./test-all.sh
source tokens.env
./benchmark-async-batch.sh 10 5
```
