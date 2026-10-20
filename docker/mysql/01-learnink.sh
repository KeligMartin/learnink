#!/bin/bash
# Exécuté une seule fois, à la création du volume de données.
# L'image officielle a déjà créé MYSQL_DATABASE (learnink) et MYSQL_USER avec tous les droits dessus.
set -euo pipefail
: "${LEARNINK_SANDBOX_PASSWORD:?LEARNINK_SANDBOX_PASSWORD doit être défini}"

mysql --protocol=socket -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS learnink_sandbox_1 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS learnink_sandbox_2 CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- L'application charge les jeux de données (uniquement lorsqu'ils changent)
GRANT ALL PRIVILEGES ON learnink_sandbox_1.* TO '${MYSQL_USER}'@'%';
GRANT ALL PRIVILEGES ON learnink_sandbox_2.* TO '${MYSQL_USER}'@'%';

-- Utilisateur des requêtes étudiantes : lecture seule, nombre de connexions limité
CREATE USER IF NOT EXISTS 'lecteur'@'%' IDENTIFIED BY '${LEARNINK_SANDBOX_PASSWORD}' WITH MAX_USER_CONNECTIONS 30;
GRANT SELECT ON learnink_sandbox_1.* TO 'lecteur'@'%';
GRANT SELECT ON learnink_sandbox_2.* TO 'lecteur'@'%';
SQL
