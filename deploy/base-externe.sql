-- Préparation d'un serveur MySQL 8 externe pour Learnink.
-- À exécuter UNE FOIS avec un compte administrateur, après avoir remplacé les mots de passe :
--
--   mysql -h <hôte> -P <port> -u <admin> -p < deploy/base-externe.sql
--
-- Les deux sources de données peuvent être sur le même serveur (comme ici) ou sur des serveurs différents :
-- dans ce cas, exécuter sur chaque serveur la partie qui le concerne.
-- L'application crée ensuite elle-même ses tables : migrations Flyway (base applicative) et jeux de données
-- (bases bac à sable, rechargés uniquement lorsqu'ils changent).

-- Base applicative : comptes, exercices, progression ------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS learnink CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'learnink'@'%' IDENTIFIED BY 'mot-de-passe-application';
GRANT ALL PRIVILEGES ON learnink.* TO 'learnink'@'%';

-- Sources de données 1 et 2 : une base bac à sable par jeu de données ---------------------------------------------
CREATE DATABASE IF NOT EXISTS learnink_sandbox_1 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS learnink_sandbox_2 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- L'utilisateur applicatif charge les jeux de données
GRANT ALL PRIVILEGES ON learnink_sandbox_1.* TO 'learnink'@'%';
GRANT ALL PRIVILEGES ON learnink_sandbox_2.* TO 'learnink'@'%';

-- Utilisateur des requêtes étudiantes : lecture seule sur les bases bac à sable, aucun accès à la base applicative.
-- Limite de connexions : prévoir LEARNINK_SANDBOX_n_POOL_SIZE x nombre d'instances.
CREATE USER IF NOT EXISTS 'lecteur'@'%' IDENTIFIED BY 'mot-de-passe-lecteur' WITH MAX_USER_CONNECTIONS 30;
GRANT SELECT ON learnink_sandbox_1.* TO 'lecteur'@'%';
GRANT SELECT ON learnink_sandbox_2.* TO 'lecteur'@'%';
