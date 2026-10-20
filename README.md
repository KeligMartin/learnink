# Learnink

Plateforme d'e-learning pour apprendre à écrire des requêtes de base de données (SQL, puis Firestore).
Les étudiants écrivent leurs requêtes dans un IDE web et reçoivent **en direct une note sur 10**.

## Démarrage rapide (développement)

Prérequis : **JDK 25**, Maven, Node 20+.

Le build Maven refuse toute autre version du JDK. Si le JDK par défaut n'est pas le 25 :

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
```

```bash
cd backend && mvn spring-boot:run
```

```bash
cd frontend && npm install && npm run dev
```

Ouvrir http://localhost:5173 (le serveur Vite redirige `/api` vers le backend sur le port 8080 ; autre backend : `LEARNINK_API=http://localhost:8081 npm run dev`).

En développement, tout tourne sur **H2 en mode compatibilité MySQL** : aucune base à installer.

| Compte de démonstration | Mot de passe | Remarque |
|---|---|---|
| `prof@learnink.dev` | `Prof1234` | Enseignant : accès à tous les niveaux et à la gestion des étudiants |
| `etudiant@learnink.dev` | `Etudiant1` | Mot de passe temporaire, à changer à la première connexion |

Pour repartir de zéro : arrêter le backend et supprimer `backend/data/`.

## Fonctionnalités

- Authentification email / mot de passe (JWT), **changement obligatoire du mot de passe à la première connexion**.
- 3 niveaux, débloqués quand **tous** les exercices du niveau précédent sont réussis. La notion la plus avancée est le regroupement (`GROUP BY` / `HAVING`) :
  - **Débutant** : une table, `WHERE`, `ORDER BY`, `LIKE`, `IS NULL`, puis `COUNT` / `AVG` ;
  - **Intermédiaire** : jointures (`JOIN`, `LEFT JOIN`, auto-jointure) ;
  - **Avancé — statistiques** : `GROUP BY`, `HAVING`, avec **graphique Chart.js** généré à partir du résultat de la requête (barres, courbe, anneau), et vue tableau.
- Conseils ciblés sur les erreurs de regroupement : colonne absente du `GROUP BY`, agrégat dans le `WHERE`, condition sans agrégat dans le `HAVING`.
- IDE web (Monaco) avec coloration, autocomplétion des tables / colonnes et exécution par `Ctrl + Entrée`.
- Explorateur du schéma (clés primaires et étrangères), historique des tentatives, affichage du résultat de la requête.
- Notation sur 10 :
  - **Résultat (6 pts)** : comparaison avec la solution de référence (noms de colonnes ignorés, ordre pris en compte seulement si l'énoncé l'exige, points partiels si le nombre de colonnes / lignes est bon) ;
  - **Performance (2 pts)** : temps d'exécution comparé à celui de la solution de référence ;
  - **Qualité (2 pts)** : `SELECT *`, jointures implicites, `DISTINCT` inutile, fonction sur une colonne dans le `WHERE`, condition sans agrégat dans le `HAVING`, sous-requêtes superflues.
- Espace enseignant : création des comptes étudiants (mot de passe temporaire généré), réinitialisation, suivi de la progression.
- **Lecture seule garantie** à trois niveaux : analyse syntaxique (une seule instruction `SELECT`, fonctions dangereuses interdites), connexion JDBC en lecture seule, utilisateur de base ne disposant que du droit `SELECT`. Délai maximal de 5 s et nombre de lignes lues limité.

## Architecture

Monorepo :

```
backend/    Spring Boot 3.5 (Java 25)
frontend/   React 19 + Vite + TypeScript
```

Deux bases distinctes côté backend :

- la **base applicative** (utilisateurs, exercices, tentatives) via JPA ;
- la **base bac à sable** interrogée par les étudiants : la **résidence universitaire et son restaurant** (25 tables, ~57 000 lignes, voir [docs/base-residence.md](docs/base-residence.md)), via un pool dédié en lecture seule.

### Strategy Pattern : exécution des requêtes

```
QueryExecutionStrategy            (interface : engine, isAvailable, execute, describeSchema)
├── SqlQueryExecutionStrategy     MySQL / H2, sur la base de la source de données de l'utilisateur
└── FirestoreQueryExecutionStrategy   à implémenter (déclarée indisponible pour l'instant)

QueryExecutionStrategyRegistry    sélectionne la stratégie selon le moteur de l'exercice
```

Ajouter un moteur = déclarer un nouveau bean `QueryExecutionStrategy` (et, si besoin, un `QueryQualityAnalyzer`).
Les règles de qualité SQL sont des beans `SqlQualityRule` indépendants : en ajouter une ne modifie pas l'analyseur.

### Organisation du backend (`com.learnink`)

| Package | Rôle |
|---|---|
| `auth`, `security`, `user` | Connexion, JWT, politique de mot de passe |
| `query` | Stratégies d'exécution (SQL, Firestore), validation lecture seule, schéma |
| `grading` | Comparaison des résultats, performance, règles de qualité, note finale |
| `exercise`, `submission`, `progress` | Exercices, tentatives, déblocage des niveaux |
| `admin` | Gestion des comptes étudiants |
| `seed` | Chargement des exercices (`resources/seed/exercises-*.json`) et comptes de démo |

### API

| Méthode | URL | Description |
|---|---|---|
| POST | `/api/auth/login` | Connexion |
| GET | `/api/auth/me` | Utilisateur courant |
| POST | `/api/auth/change-password` | Changement de mot de passe |
| GET | `/api/engines` | Moteurs disponibles |
| GET | `/api/engines/{engine}/schema` | Tables et colonnes interrogeables |
| GET | `/api/progress?engine=SQL` | Progression par niveau |
| GET | `/api/exercises/{id}` | Énoncé (jamais la solution) |
| POST | `/api/exercises/{id}/submissions` | Exécution + notation |
| GET | `/api/exercises/{id}/submissions` | Historique des tentatives |
| GET/POST | `/api/admin/students` | Liste / création d'étudiants (enseignant) |
| POST | `/api/admin/students/{id}/reset-password` | Nouveau mot de passe temporaire |

## Ajouter des exercices

Éditer `backend/src/main/resources/seed/exercises-residence.json` (ou créer un autre fichier `exercises-*.json`).
Chaque exercice indique sa source de données (`"datasource": 1` ; 1 si absent).
Les exercices sont mis à jour par leur `code` au démarrage, et chaque solution de référence est exécutée pour vérification : une erreur ou un résultat vide est signalé dans les logs.
Un exercice retiré du fichier est archivé (masqué), pas supprimé : l'historique des tentatives est conservé.

Pour afficher le résultat en graphique, ajouter à l'exercice :

```json
"chart": { "type": "BAR", "labelColumns": 1 }
```

`type` : `BAR`, `HORIZONTAL_BAR`, `LINE`, `PIE` ou `DOUGHNUT`. Les `labelColumns` premières colonnes du résultat forment le libellé de chaque point (ex. `2` pour année + mois, ou prénom + nom) ; les colonnes numériques suivantes sont tracées.

## Base bac à sable : la résidence universitaire

Le modèle est décrit dans [docs/base-residence.md](docs/base-residence.md).

| Fichier (`backend/src/main/resources/sandbox/residence/`) | Rôle |
|---|---|
| `schema.sql` | DDL portable H2 / MySQL |
| `data.sql` | Données générées (ne pas modifier à la main) |
| `h2/lecteur.sql` | Utilisateur en lecture seule pour H2 (développement) |

Les données sont générées de façon déterministe par `backend/tools/generate_sandbox_data.py`, qui garantit leur cohérence
(capacité des chambres, réservations uniquement par des résidents et dans la limite des places, incompatibilités déduites des ingrédients...).
Après une modification du générateur :

```bash
python3 backend/tools/generate_sandbox_data.py > backend/src/main/resources/sandbox/residence/data.sql
```

Options : `--etudiants` (420 candidats par défaut) et `--affluence` (multiplie le volume de réservations).

## Déploiement

L'application est livrée sous forme d'**une seule image Docker** ([Dockerfile.vercel](Dockerfile.vercel)) : le frontend React est
compilé puis servi par Spring Boot (même origine, donc pas de CORS). Monaco et Chart.js sont embarqués : aucun CDN n'est nécessaire.

| Fichier | Rôle |
|---|---|
| `Dockerfile.vercel` | Build frontend (Node 24) → build et tests backend (Maven, JDK 25) → image d'exécution JRE 25. Détecté automatiquement par Vercel |
| `vercel.json` | Région d'exécution : Francfort (`fra1`) |
| `deploy/base-externe.sql` | Préparation d'un serveur MySQL 8 externe : base applicative, bases des sources 1 et 2, deux utilisateurs |
| `docker-compose.yml`, `docker/mysql/` | Environnement complet en local (MySQL 8.4 + application) |

Au démarrage (profil `mysql`), sous un verrou partagé entre instances (`GET_LOCK`), l'application :
1. applique les migrations Flyway de la base applicative (`db/migration`), puis Hibernate vérifie le schéma ;
2. charge chaque base bac à sable **seulement si son jeu de données a changé** (empreinte SHA-256 conservée dans `sandbox_state`) :
   mettre à jour le jeu de données = redéployer ;
3. met à jour les exercices et crée le premier enseignant si `LEARNINK_TEACHER_EMAIL` / `LEARNINK_TEACHER_PASSWORD` sont définis
   (mot de passe à changer à la première connexion).

Santé : `GET /actuator/health`.

### Sources de données

Chaque utilisateur a un champ `datasource` (1, 2...) : c'est **un jeu de données distinct avec ses propres exercices**.
Un étudiant ne voit que les exercices de sa source, et ses requêtes s'exécutent sur la base correspondante.
L'enseignant choisit la source de chaque étudiant (page Étudiants), ainsi que la sienne pour consulter un autre parcours.

Ajouter un jeu de données (source 2) :
1. placer ses scripts dans `backend/src/main/resources/sandbox/<jeu>/` (`reset.sql`, `schema.sql`, `data.sql`, en SQL portable MySQL / H2) ;
2. les déclarer dans `learnink.sandbox.datasources.2.scripts` (profils `dev` et `mysql`) ;
3. ajouter ses exercices avec `"datasource": 2` dans un fichier `seed/exercises-<jeu>.json` ;
4. en production, définir `LEARNINK_SANDBOX_2_URL` (la source 2 est ignorée tant que cette variable est vide).

### Base de données externe (MySQL 8)

Exécuter une fois [deploy/base-externe.sql](deploy/base-externe.sql) (après avoir changé les mots de passe) avec un compte
administrateur. Il crée :

- `learnink` (application) et `learnink_sandbox_1`, `learnink_sandbox_2` (une base par source de données),
  qui peuvent aussi être répartis sur plusieurs serveurs ;
- un utilisateur applicatif avec tous les droits sur ces bases ;
- un utilisateur `lecteur` avec **uniquement** `SELECT` sur les bases bac à sable : c'est lui qui exécute les requêtes des étudiants.

Les fonctions Vercel n'ayant pas d'IP fixe, MySQL doit accepter les connexions depuis Internet : **TLS obligatoire**
(`?sslMode=VERIFY_IDENTITY` dans l'URL JDBC) et mots de passe forts. Chaque instance ouvre jusqu'à 5 connexions sur la
base applicative et 10 par source de données (`LEARNINK_DB_POOL_SIZE`, `LEARNINK_SANDBOX_n_POOL_SIZE`) : vérifier la
limite de connexions de l'offre choisie.

### Sur Vercel

1. Importer le dépôt dans Vercel (ou `vercel deploy`) : `Dockerfile.vercel` est détecté et l'image construite par Vercel.
2. Définir les variables d'environnement du projet (liste complète en tête de `application-mysql.yml`) :

| Variable | Valeur |
|---|---|
| `PORT` | `8080` (Vercel écoute le port 80 par défaut) |
| `SPRING_PROFILES_ACTIVE` | `mysql` |
| `LEARNINK_DB_URL` | `jdbc:mysql://<hôte>:<port>/learnink?sslMode=VERIFY_IDENTITY` |
| `LEARNINK_DB_USER`, `LEARNINK_DB_PASSWORD` | utilisateur applicatif |
| `LEARNINK_SANDBOX_1_URL` | `jdbc:mysql://<hôte>:<port>/learnink_sandbox_1?sslMode=VERIFY_IDENTITY` |
| `LEARNINK_SANDBOX_1_USER`, `LEARNINK_SANDBOX_1_PASSWORD` | `lecteur` et son mot de passe |
| `LEARNINK_SANDBOX_2_URL`, `LEARNINK_SANDBOX_2_NAME`, `..._USER`, `..._PASSWORD` | source 2, quand elle existe |
| `LEARNINK_JWT_SECRET` | chaîne aléatoire d'au moins 32 caractères |
| `LEARNINK_TEACHER_EMAIL`, `LEARNINK_TEACHER_PASSWORD` | premier compte enseignant |

3. Vérifier que la région des fonctions est bien Francfort (`fra1`, définie dans `vercel.json`) : proche de la base et dans l'UE.

À savoir :
- l'exécution de conteneurs est **en bêta** chez Vercel ;
- une instance sans trafic pendant 5 minutes est arrêtée : la première requête suivante attend le démarrage de la JVM
  (quelques secondes). Pendant un cours, le trafic continu garde l'instance active ;
- l'offre Hobby est réservée à un usage personnel et non commercial : vérifier qu'elle convient à un usage en établissement.

### En local avec Docker

Nécessite un moteur Docker avec Compose (Docker Desktop, OrbStack ou Colima).

```bash
cp .env.example .env
```

```bash
docker compose up --build
```

Puis http://localhost:8080 avec le compte enseignant défini dans `.env`.

## Reste à faire

- **Firestore** : implémenter `FirestoreQueryExecutionStrategy` (un projet / une base par étudiant), le langage de requête proposé aux étudiants et les exercices associés.
- Sauvegarde de la base applicative : vérifier les sauvegardes proposées par le fournisseur de la base externe.
- Limitation du nombre de tentatives de connexion.
