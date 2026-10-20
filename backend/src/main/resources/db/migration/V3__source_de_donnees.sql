-- Source de données : chaque utilisateur et chaque exercice sont rattachés à un jeu de données (1, 2...).
ALTER TABLE app_user ADD COLUMN datasource INTEGER NOT NULL DEFAULT 1;
ALTER TABLE exercise ADD COLUMN datasource INTEGER NOT NULL DEFAULT 1;
CREATE INDEX idx_exercise_engine_datasource ON exercise (engine, datasource);
