# Lot 1 — Renommage `ms-client` → `ms-webui` — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Renommer le module UI `ms-client` en `ms-webui` de façon cohérente (dossier, package, classes, flag `clientWebUI`→`webUI`, nom de service, variable d'env), sans changer le port (8090).

**Architecture:** Le générateur est un Spring Boot qui stream une plateforme ZIP. Le module UI vit comme arbre décompressé sous `src/main/resources/templates/ms-platform/ms-client/`. Trois fichiers du générateur (`FeatureOptions`, `FeatureFilterProcessor`, `CrossCuttingConfigProcessor`) le référencent, ainsi que 3 tests, le `docker-compose.yml`, les fichiers env, la page d'accueil du générateur et le README. **Le code Java du module template n'est PAS compilé par Maven** (ce sont des ressources) : la sécurité vient des tests unitaires du générateur (fixtures inline + `GeneratedOutputLayoutTest` sur les vrais templates) et d'une génération réelle vérifiée par `grep`.

**Tech Stack:** Java 17, Spring Boot, Maven, Lombok, JUnit 5 + AssertJ.

**Spec :** `docs/superpowers/specs/2026-06-01-webui-rename-and-password-management-design.md`

---

## File Structure

Fichiers modifiés / déplacés :

- **Générateur (code)** — `src/main/java/com/mr486/generator/` :
  - `dto/FeatureOptions.java` — champ `clientWebUI` → `webUI`.
  - `pipeline/processor/FeatureFilterProcessor.java` — `isClientWebUI()` + chemin `ms-client/`.
  - `pipeline/processor/CrossCuttingConfigProcessor.java` — `isClientWebUI()`, `"ms-client"` (modules/blocks/smoke), détection de chemin, catalogue `client:`→`webui:`.
- **Générateur (tests)** — `src/test/java/com/mr486/generator/` :
  - `dto/FeatureOptionsDeserializationTest.java`, `pipeline/processor/FeatureFilterProcessorTest.java`, `pipeline/processor/CrossCuttingConfigProcessorTest.java`.
- **Module template** — `src/main/resources/templates/ms-platform/ms-client/` → `ms-webui/` (27 fichiers : dossier, package `…/client/`→`…/webui/`, `ClientApplication`→`WebUiApplication`, `ClientProperties`→`WebUiProperties`, prefix `client`→`webui`, `application.yml`, `pom.xml`, `Dockerfile`).
- **Périphérie** — `src/main/resources/templates/ms-platform/docker-compose.yml`, `dot-env`, `dist.env`, `src/main/resources/static/index.html`, `README.md`.

**Non touché (pièges) :** la clé `eureka.client:` dans les YAML/fixtures, les classes HTTP `MsAuthClient`/`GatewayClient`, le `WebClient` Spring, la dépendance `…eureka-client`. Le renommage ne cible que `ms-client`, le package `…msplatform.client`, les classes `ClientApplication`/`ClientProperties`, le préfixe de config `client:`, le flag `clientWebUI` et `MS_CLIENT_PORT`. La page d'accueil `static/index.html` du générateur doit envoyer la clé JSON `webUI` (pas `clientWebUI`) au backend.

---

## Task 1 : Renommer le flag + les références module dans le générateur (code + tests)

Objectif : renommer `clientWebUI`→`webUI` (symbole) et `ms-client`→`ms-webui` (chaînes) dans tout le générateur, plus le préfixe de catalogue `client:`→`webui:`. À l'issue, `mvn test` passe : les fixtures inline et la logique sont cohérentes (le template réel est renommé en Task 2, mais aucun test unitaire ne charge son nom — `GeneratedOutputLayoutTest` ne vérifie que la mise en forme).

**Files:**
- Modify: `src/main/java/com/mr486/generator/dto/FeatureOptions.java`
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessor.java`
- Modify: `src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java`
- Modify: `src/test/java/com/mr486/generator/dto/FeatureOptionsDeserializationTest.java`
- Modify: `src/test/java/com/mr486/generator/pipeline/processor/FeatureFilterProcessorTest.java`
- Modify: `src/test/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessorTest.java`

- [ ] **Step 1 : Renommer le symbole du flag (Lombok génère `isWebUI()`/`setWebUI()`)**

Ordre important : `ClientWebUI` (méthodes) avant `clientWebUI` (champ + chaîne JSON).

```bash
cd /home/mr486/Developpement/Projets/GestoMS
find src/main/java/com/mr486/generator src/test/java/com/mr486/generator -name '*.java' \
  -exec sed -i 's/ClientWebUI/WebUI/g; s/clientWebUI/webUI/g' {} +
```

- [ ] **Step 2 : Renommer les références chaîne `ms-client` → `ms-webui` dans le générateur**

Couvre `desiredModules` (`"ms-client"`), `blocksToRemove`, la détection de chemin `endsWith("/ms-client/src/main/resources/application.yml")`, les smoke `wait_for 'ms-client'`, le javadoc, et toutes les fixtures de test (`ms-platform/ms-client/...`, `<module>ms-client</module>`, `"  ms-client:"`, `build: ./ms-client`).

```bash
cd /home/mr486/Developpement/Projets/GestoMS
find src/main/java/com/mr486/generator src/test/java/com/mr486/generator -name '*.java' \
  -exec sed -i 's/ms-client/ms-webui/g' {} +
```

- [ ] **Step 3 : Renommer le préfixe de catalogue `client:` → `webui:` dans le processor**

Le bloc catalogue de l'`application.yml` du module passe de `client:` à `webui:`. Édite `CrossCuttingConfigProcessor.java` :

Remplacer le constructeur du bloc :
```java
        StringBuilder block = new StringBuilder("client:\n  resources:\n");
```
par :
```java
        StringBuilder block = new StringBuilder("webui:\n  resources:\n");
```

Remplacer la regex de remplacement :
```java
        String newText = text.replaceAll("(?ms)^client:.*\\z",
```
par :
```java
        String newText = text.replaceAll("(?ms)^webui:.*\\z",
```

Mettre à jour le javadoc de la méthode (lignes ~841-845) en remplaçant `client:` par `webui:` et `ms-client` (déjà fait en Step 2). Renommer la méthode pour la cohérence :
```bash
cd /home/mr486/Developpement/Projets/GestoMS
sed -i 's/rewriteClientCatalog/rewriteWebUiCatalog/g' \
  src/main/java/com/mr486/generator/pipeline/processor/CrossCuttingConfigProcessor.java
```

- [ ] **Step 4 : Renommer le préfixe de catalogue `client:` → `webui:` dans le test**

Édite `CrossCuttingConfigProcessorTest.java`. Dans la constante `SAMPLE_CLIENT_YML` UNIQUEMENT, remplacer la ligne du bloc catalogue :
```java
        "client:\n" +
```
par :
```java
        "webui:\n" +
```

⚠️ **NE PAS** toucher la ligne `"  client:\n"` (indentée) du bloc `eureka:` (≈ ligne 140) ni aucune autre `eureka.client`.

- [ ] **Step 5 : Vérifier la non-régression — aucun symbole/chaîne périmé dans le générateur**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
grep -rn "clientWebUI\|isClientWebUI\|ClientWebUI\|ms-client\|rewriteClientCatalog" \
  src/main/java/com/mr486/generator src/test/java/com/mr486/generator
```
Expected : **aucune sortie** (exit code 1).

- [ ] **Step 6 : Lancer les tests du générateur**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn -q test -Dtest=FeatureOptionsDeserializationTest,FeatureFilterProcessorTest,CrossCuttingConfigProcessorTest
```
Expected : **BUILD SUCCESS**, 0 échec. (`isWebUI()`/`setWebUI()` générés par Lombok ; fixtures `ms-webui` + bloc `webui:` cohérents.)

- [ ] **Step 7 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/java/com/mr486/generator src/test/java/com/mr486/generator
git commit -m "$(cat <<'EOF'
refactor(generator): rename clientWebUI flag + ms-client refs to webUI/ms-webui

FeatureOptions flag clientWebUI→webUI, processors + tests updated,
catalog block prefix client:→webui:. Template module renamed next.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2 : Renommer le module template `ms-client/` → `ms-webui/`

Objectif : déplacer le dossier, renommer le package Java, les classes `ClientApplication`/`ClientProperties`, le préfixe de config et la variable `MS_CLIENT_PORT`. Le port reste 8090.

**Files:**
- Rename dir: `src/main/resources/templates/ms-platform/ms-client/` → `ms-webui/`
- Rename pkg dir: `…/ms-webui/src/main/java/com/mr486/msplatform/client/` → `…/webui/` (+ arbre test)
- Rename: `ClientApplication.java`→`WebUiApplication.java`, `ClientProperties.java`→`WebUiProperties.java`

- [ ] **Step 1 : Déplacer le dossier du module (git mv)**

```bash
cd /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform
git mv ms-client ms-webui
```

- [ ] **Step 2 : Déplacer l'arbre de package Java `client` → `webui` (main + test)**

```bash
cd /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform/ms-webui
git mv src/main/java/com/mr486/msplatform/client src/main/java/com/mr486/msplatform/webui
git mv src/test/java/com/mr486/msplatform/client src/test/java/com/mr486/msplatform/webui
```

- [ ] **Step 3 : Renommer les fichiers des deux classes d'identité**

```bash
cd /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform/ms-webui/src/main/java/com/mr486/msplatform/webui
git mv ClientApplication.java WebUiApplication.java
git mv config/ClientProperties.java config/WebUiProperties.java
```

- [ ] **Step 4 : Réécrire le contenu du module (package, classes, service, env)**

Renommages ciblés sur tout le sous-arbre `ms-webui/` (sûrs, sans toucher `MsAuthClient`/`GatewayClient`/`WebClient`/`eureka-client`) :

```bash
cd /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform/ms-webui
find . -type f -exec sed -i \
  -e 's/com\.mr486\.msplatform\.client/com.mr486.msplatform.webui/g' \
  -e 's/\bClientApplication\b/WebUiApplication/g' \
  -e 's/\bClientProperties\b/WebUiProperties/g' \
  -e 's/\bms-client\b/ms-webui/g' \
  -e 's/MS_CLIENT_PORT/MS_WEBUI_PORT/g' \
  {} +
```

- [ ] **Step 5 : Renommer le préfixe de config `client:` → `webui:`**

Dans `src/main/resources/application.yml`, le bloc catalogue de niveau racine `client:` (≠ `eureka.client`) :

```bash
cd /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform/ms-webui
sed -i 's/^client:/webui:/' src/main/resources/application.yml
```

Dans `config/WebUiProperties.java`, le préfixe de l'annotation :
```java
@ConfigurationProperties(prefix = "client")
```
devient :
```java
@ConfigurationProperties(prefix = "webui")
```

- [ ] **Step 6 : Vérifier l'absence de référence périmée dans le module**

```bash
cd /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform/ms-webui
grep -rn "msplatform\.client\|ClientApplication\|ClientProperties\|ms-client\|MS_CLIENT_PORT\|^client:\|prefix = \"client\"" .
```
Expected : **aucune sortie** (exit code 1). (Les occurrences `eureka:\n  client:` et les classes `MsAuthClient`/`GatewayClient` restent — elles ne matchent pas ces motifs.)

- [ ] **Step 7 : Lancer toute la suite (inclut `GeneratedOutputLayoutTest` sur les vrais templates)**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
mvn -q test
```
Expected : **BUILD SUCCESS**. `GeneratedOutputLayoutTest` charge le template `ms-webui` renommé et vérifie la mise en forme (≤120 colonnes / 4 espaces / un import par ligne).

- [ ] **Step 8 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add -A src/main/resources/templates/ms-platform
git commit -m "$(cat <<'EOF'
refactor(template): rename ms-client module to ms-webui

Dir + Java package (…msplatform.client→…webui), ClientApplication→
WebUiApplication, ClientProperties→WebUiProperties, config prefix
client→webui, MS_CLIENT_PORT→MS_WEBUI_PORT. Port 8090 unchanged.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3 : Mettre à jour les fichiers périphériques

Objectif : aligner `docker-compose.yml`, les fichiers env, la page d'accueil du générateur et le README.

**Files:**
- Modify: `src/main/resources/templates/ms-platform/docker-compose.yml`
- Modify: `src/main/resources/templates/ms-platform/dot-env`
- Modify: `src/main/resources/templates/ms-platform/dist.env`
- Modify: `src/main/resources/static/index.html`
- Modify: `README.md`

- [ ] **Step 1 : docker-compose + fichiers env**

```bash
cd /home/mr486/Developpement/Projets/GestoMS/src/main/resources/templates/ms-platform
sed -i -e 's/\bms-client\b/ms-webui/g' -e 's/MS_CLIENT_PORT/MS_WEBUI_PORT/g' \
  docker-compose.yml dot-env dist.env
```

Vérifier le bloc service :
```bash
grep -n "ms-webui\|MS_WEBUI_PORT\|8090" docker-compose.yml
```
Expected : `ms-webui:`, `build: ./ms-webui`, `MS_WEBUI_PORT: 8090`, `ports: ["8090:8090"]`.

- [ ] **Step 2 : Page d'accueil du générateur (`static/index.html`)**

La clé JSON envoyée à l'API doit devenir `webUI` (sinon le flag est ignoré). Édits :

`id`/`for` de la case à cocher :
```html
              <input class="form-check-input" type="checkbox" id="clientWebUI"/>
              <label class="form-check-label" for="clientWebUI">
                <strong>Interface client (ms-client)</strong>
```
devient :
```html
              <input class="form-check-input" type="checkbox" id="webUI"/>
              <label class="form-check-label" for="webUI">
                <strong>Interface web (ms-webui)</strong>
```

La construction du payload :
```javascript
        clientWebUI:     document.getElementById('clientWebUI').checked,
```
devient :
```javascript
        webUI:           document.getElementById('webUI').checked,
```

- [ ] **Step 3 : README**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
sed -i -e 's/ms-client/ms-webui/g' -e 's/clientWebUI/webUI/g' README.md
```

- [ ] **Step 4 : Vérifier l'absence de référence périmée hors `eureka.client`**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
grep -rn "ms-client\|clientWebUI\|MS_CLIENT_PORT\|msplatform\.client" \
  src/main/resources/templates/ms-platform/docker-compose.yml \
  src/main/resources/templates/ms-platform/dot-env \
  src/main/resources/templates/ms-platform/dist.env \
  src/main/resources/static/index.html README.md
```
Expected : **aucune sortie** (exit code 1).

- [ ] **Step 5 : Commit**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
git add src/main/resources/templates/ms-platform/docker-compose.yml \
        src/main/resources/templates/ms-platform/dot-env \
        src/main/resources/templates/ms-platform/dist.env \
        src/main/resources/static/index.html README.md
git commit -m "$(cat <<'EOF'
refactor(platform): rename ms-client to ms-webui in compose/env/docs/UI

docker-compose service + MS_WEBUI_PORT, env files, generator landing
page (JSON key webUI), README. Port 8090 unchanged.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4 : Vérification de bout en bout (génération réelle)

Objectif : prouver qu'une génération avec `webUI: true` + `resources[]` non vide produit `ms-webui/` (et plus aucun `ms-client`).

- [ ] **Step 1 : Build + démarrer le serveur sur un port non conflictuel**

⚠️ Tuer d'abord les serveurs zombies sur :8080 (ils servent l'ancien build).
```bash
cd /home/mr486/Developpement/Projets/GestoMS
pkill -f springboot-platform-generator 2>/dev/null; true
mvn -q clean package -DskipTests
java -jar target/*.jar --server.port=8077 &
sleep 12
```

- [ ] **Step 2 : Générer une plateforme avec webUI activé et un resource**

```bash
cd /home/mr486/Developpement/Projets/GestoMS
curl -s -X POST http://localhost:8077/api/generate/platform \
  -H "Content-Type: application/json" \
  -d '{"features":{"webUI":true},"resources":[{"serviceName":"order-service","className":"Order","databaseType":"POSTGRES"}]}' \
  --output /tmp/ms-webui-check.zip
unzip -l /tmp/ms-webui-check.zip | grep -i "client\|webui" | head -30
```
Expected : des entrées `ms-platform/ms-webui/…` (dont `…/msplatform/webui/WebUiApplication.java`, `config/WebUiProperties.java`), **aucune** `ms-client`.

- [ ] **Step 3 : Vérifier l'absence totale de référence périmée dans le ZIP généré**

```bash
cd /tmp && rm -rf ms-webui-extract && unzip -q /tmp/ms-webui-check.zip -d ms-webui-extract
grep -rn "ms-client\|msplatform\.client\|ClientApplication\|ClientProperties\|MS_CLIENT_PORT\|clientWebUI" ms-webui-extract \
  | grep -v "eureka" || echo "AUCUNE REFERENCE PERIMEE — OK"
```
Expected : `AUCUNE REFERENCE PERIMEE — OK`.

- [ ] **Step 4 : Vérifier le catalogue `webui:` + le bloc compose + le smoke test-all.sh**

```bash
cd /tmp/ms-webui-extract
echo "--- application.yml ---"; grep -n "^webui:\|port: 8090\|order-service" ms-platform/ms-webui/src/main/resources/application.yml
echo "--- docker-compose ---"; grep -n "ms-webui:\|MS_WEBUI_PORT" ms-platform/docker-compose.yml
echo "--- test-all.sh ---"; grep -n "ms-webui\|8090" ms-platform/test-all.sh
```
Expected : `webui:` présent (pas `client:` racine), `order-service` dans le catalogue, bloc compose `ms-webui:`, smoke `wait_for 'ms-webui'` sur `:8090`.

- [ ] **Step 5 : Arrêter le serveur**

```bash
pkill -f "server.port=8077" 2>/dev/null; pkill -f "target/.*\.jar" 2>/dev/null; true
```

- [ ] **Step 6 : (Optionnel mais recommandé) compiler le module généré**

```bash
cd /tmp/ms-webui-extract/ms-platform/ms-webui && mvn -q -o compile 2>&1 | tail -5 || \
  echo "compile hors-ligne indisponible — vérification grep déjà concluante"
```
Expected : compilation OK (le package `com.mr486.msplatform.webui` est cohérent), ou message de repli si dépendances absentes en mode hors-ligne.

---

## Notes pour l'exécutant

- **Pièges à NE PAS renommer :** `eureka.client` (YAML), `MsAuthClient`, `GatewayClient`, `WebClient`, dépendance Maven `…eureka-client`. Les seds utilisent `\bms-client\b`, le package complet `com.mr486.msplatform.client`, et les classes `\bClientApplication\b`/`\bClientProperties\b` précisément pour cette raison.
- **Le port reste 8090** dans tous les fichiers ; seul le nom de la variable d'env passe à `MS_WEBUI_PORT`.
- **Lot 2** (changement de mot de passe self + admin) fera l'objet de son propre plan, écrit après que ce Lot 1 soit livré et vérifié — ses chemins et tests s'appuient sur la structure `ms-webui` mise en place ici.
