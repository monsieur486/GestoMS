# Mise en page + javadoc des fichiers générés — Design

**Date:** 2026-06-01
**Statut:** spec validé (design approuvé), prêt pour plan d'implémentation
**Périmètre:** rendre la **sortie générée** par le générateur cohérente en mise en page (indentation, longueur de ligne) et documentée (javadoc française), sur les **deux sources** de contenu (templates statiques + chaînes embarquées dans les processors), avec un test garde-fou contre les régressions.

## Contexte

Le générateur produit une plateforme microservices en transformant une `List<GeneratedFile>` via une chaîne de `FileProcessor` beans `@Order(N)`, transformations **textuelles** :

```
RootRename(10) → FeatureFilter(20) → PackagePlaceholder(30) → BatchConfig(40)
              → ResourceExpand(50) → CrossCuttingConfig(60) → VersionInjection(70)
```

La substitution d'identité est faite par **remplacement de chaînes littérales** (`com.mr486.msplatform` → `basePackage`, `com.mr486` → `groupId`, `<java.version>17</java.version>` → version demandée) — **il n'y a pas de tokens `{{…}}`** dans les fichiers Java. Reformater le code autour de ces littéraux est donc sans risque tant que les littéraux restent intacts.

### Le contenu généré a DEUX sources

1. **Fichiers template statiques** (`src/main/resources/templates/ms-platform/**`) : copiés puis substitués. ~99 `.java` + ~70 fichiers texte (yml/yaml, html, xml/pom, sh, sql, json, css, Dockerfile, `dot-env`, `dot-gitignore`).
2. **Chaînes Java embarquées** dans les processors, émises à la génération :
   - `ResourceExpandProcessor` (`@Order(50)`) : document Mongo, repository Mongo, `application.yml` Mongo, variante UUID (constantes chaînes avec `\n`).
   - `CrossCuttingConfigProcessor` (`@Order(60)`) : régénération d'`AggregateController.java`, de `test-all.sh`, de `ms-realm-realm.json`, et de blocs `docker-compose` (`StringBuilder`).

**Reformater seulement les templates serait un no-op** pour tout ce que les processors régénèrent par-ressource (« piège dual-source »). Le design traite **les deux**.

### État actuel

Mesure des templates `.java` (max car./ligne, nb lignes) : deux groupes nets.

- **Minifiés** (4–9 lignes, lignes de 190 à **931** car.) — à reformater :
  - `service-a|b|c/**` : `entity|document/Resource*.java`, `dto/Resource*Dto.java`, `service/Resource*Service.java`, `controller/Resource*Controller.java`, `repository/Resource*Repository.java`
  - `*/…/*Application.java` (mains, imports collés)
  - `service-consumer/**/AggregateController.java` (931, **aussi régénéré par CrossCutting**)
  - `service-b/**/dbchangelogs/DataBaseChangeLog.java` (623)
  - `service-consumer/**/configuration/WebClientConfig.java` (243)
- **Déjà propres** (4 espaces, ≤ ~140 car., 1 import/ligne) : le reste (~75 fichiers, ex. `AuthController`, `KeycloakAdminClient`).

Côté **javadoc** : **aucun** fichier généré (même propre) n'a de javadoc, alors que le code source du générateur en a (français). À ajouter largement.

Aucune config de formatage (`.editorconfig`, checkstyle, spotless) n'existe.

## Objectifs (validés)

1. **Mise en page** uniforme sur la sortie générée :
   - **Java** : indentation **4 espaces**, **≤ 120 car./ligne**, **1 import par ligne**, accolades et retours à la ligne standard.
   - **Autres fichiers texte** (yml/yaml, html, xml/pom, sh, sql, Dockerfile) : indentation cohérente + longueur de ligne raisonnable, **sans casser la sémantique** (indentation YAML signifiante).
2. **Javadoc en français** sur **tous** les fichiers `.java` générés : bloc de **classe/interface** (rôle) + javadoc sur chaque **méthode publique**. **Exclus** : getters/setters Lombok et champs de DTO triviaux.
3. **Garde-fou** : test qui génère une plateforme (avec `resources[]` pour exercer les processors) et **échoue** si un `.java` de sortie contient une ligne **> 120 car.** ou des **imports collés** (`;import `).

## Approche retenue : Option 1

Reformatage et javadoc **rédigés à la main** sur les deux sources, **sans dépendance runtime ajoutée** (Option 3 « processor de formatage runtime » écartée ; Option 2 « plugin formateur Maven » écartée car elle ne couvre pas les chaînes des processors et la javadoc reste manuelle).

- Le gros reflow des `.java` **templates** peut s'appuyer sur `google-java-format` comme **outil ponctuel** de développement (jamais ajouté au build), puis javadoc ajoutée à la main.
- Les **chaînes embarquées** des processors sont réécrites à la main : passage des littéraux `\n`-joints en blocs multi-lignes lisibles (text blocks `"""…"""` Java 17 quand c'est plus clair), en **préservant** les tokens de substitution.
- Charge répartie par **module** via subagents parallèles (un module = un lot de fichiers cohérent).

## Périmètre détaillé par source

### Source A — templates statiques

| Catégorie | Action |
|---|---|
| `.java` minifiés (~20-25) | reformater (4 esp./120) **+** javadoc classe + méthodes publiques |
| `.java` déjà propres (~75) | vérifier 120/imports **+** ajouter javadoc classe + méthodes publiques |
| `.yml` / `.yaml` (17) | normaliser indentation/longueur, **ne pas casser** la sémantique ni les `${…}` / simple-accolade |
| `.html` (15, Thymeleaf) | indentation cohérente, longueur raisonnable |
| `.xml` / pom (13) | indentation 2 ou 4 esp. cohérente |
| `.sh` (6), `.sql` (4), `Dockerfile` (11) | indentation/longueur cohérentes |
| `dot-env`, `dot-gitignore`, `.css`, `.json`, `.md` | mise en page légère si pertinent |

### Source B — chaînes embarquées dans les processors

| Processor | Contenu émis | Action |
|---|---|---|
| `ResourceExpandProcessor` | document Mongo, repository Mongo | multi-lignes + **javadoc** sur la classe/interface émise |
| `ResourceExpandProcessor` | `application.yml` Mongo, variante UUID | multi-lignes lisibles, sémantique YAML préservée |
| `CrossCuttingConfigProcessor` | `AggregateController.java` (931 car.) | multi-lignes + **javadoc** |
| `CrossCuttingConfigProcessor` | `test-all.sh`, blocs `docker-compose` | indentation/longueur cohérentes |
| `CrossCuttingConfigProcessor` | `ms-realm-realm.json` | JSON indenté lisible |

## Contraintes à respecter (pièges connus)

- **Tokens littéraux intacts** : `com.mr486.msplatform`, `com.mr486`, `<java.version>17</java.version>`, et les identifiants clonés/remplacés par ResourceExpand (`ResourceA`→className, `servicea`→serviceName, `resources_a`, `{CLASS}`, `{PKG}`, `{COLLECTION}`, `{SERVICE_NAME}`, `{SERVICE_UPPER}`, `{SERVICE_SNAKE}`) doivent rester **mot pour mot**. Reformater **autour** d'eux.
- **YAML** : indentation signifiante ; ne pas toucher aux placeholders simple-accolade Mongo ni aux `${…}` d'environnement.
- **dotfiles** : `dot-env` / `dot-gitignore` sont décodés au chargement — garder le préfixe `dot-`.
- Le garde-fou tourne sur la sortie **post-tous-processors** (resources[] activé), donc il couvre A **et** B.

## Vérification

1. `mvn -q test` : suite existante verte (les processors transforment toujours correctement).
2. **Nouveau test garde-fou** : génère une plateforme avec `resources[]` (au moins un service JPA + un Mongo pour exercer les variantes), assert qu'aucun `.java` de sortie ne dépasse 120 car. et ne contient `;import `.
3. **Compilation d'un module généré** : vérifier qu'un service généré compile encore (tokens substitués restent du Java valide) — via le test d'intégration existant ou un contrôle manuel ponctuel.

## Hors périmètre (YAGNI)

- Pas de plugin formateur ni de processor de formatage runtime dans le produit.
- Pas de checkstyle/spotless ajouté aux poms **générés** (sauf décision ultérieure).
- Pas de réécriture fonctionnelle : mise en page + javadoc uniquement, comportement inchangé.
