-- Empreinte des fichiers traités par les tâches de démarrage (ex. chargement des exercices) :
-- une tâche dont les fichiers n'ont pas changé est sautée, ce qui accélère les démarrages à froid.
CREATE TABLE startup_state (
    task       VARCHAR(50) NOT NULL,
    checksum   VARCHAR(64) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (task)
) ENGINE = InnoDB;
