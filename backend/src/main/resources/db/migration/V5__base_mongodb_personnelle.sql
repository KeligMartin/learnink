-- Moteur NoSQL : MongoDB remplace Firestore, chaque étudiant peut renseigner la chaîne de connexion de sa base.
ALTER TABLE app_user ADD COLUMN mongo_uri VARCHAR(500) NULL;
UPDATE exercise SET engine = 'MONGODB' WHERE engine = 'FIRESTORE';
