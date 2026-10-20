-- Utilisateur utilisé par l'application pour exécuter les requêtes des étudiants :
-- il ne possède que le droit de lecture (équivalent MySQL : GRANT SELECT ON learnink_sandbox_1.* TO 'lecteur'@'%').
CREATE USER lecteur PASSWORD 'lecteur';
GRANT SELECT ON SCHEMA PUBLIC TO lecteur;
