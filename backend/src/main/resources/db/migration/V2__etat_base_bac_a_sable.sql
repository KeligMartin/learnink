-- Empreinte du jeu de données chargé dans la base bac à sable : évite de la recharger à chaque démarrage.
CREATE TABLE sandbox_state (
    id        INTEGER     NOT NULL,
    checksum  VARCHAR(64) NOT NULL,
    loaded_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;
