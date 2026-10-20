-- Base "Résidence universitaire et son restaurant", interrogée par les étudiants.
-- DDL portable : exécutable tel quel sur MySQL 8 et sur H2 en mode MySQL.

-- 1. La résidence ----------------------------------------------------------

CREATE TABLE batiment (
    id      INT PRIMARY KEY,
    nom     VARCHAR(80) NOT NULL,
    adresse VARCHAR(150) NOT NULL
);

CREATE TABLE chambre (
    id          INT PRIMARY KEY,
    batiment_id INT NOT NULL,
    numero      VARCHAR(10) NOT NULL UNIQUE,
    etage       INT NOT NULL,
    capacite    INT NOT NULL,
    CONSTRAINT fk_chambre_batiment FOREIGN KEY (batiment_id) REFERENCES batiment (id)
);

CREATE TABLE etudiant (
    id             INT PRIMARY KEY,
    nom            VARCHAR(60) NOT NULL,
    prenom         VARCHAR(60) NOT NULL,
    date_naissance DATE NOT NULL,
    email          VARCHAR(150) NOT NULL UNIQUE
);

-- Historique des occupations : date_fin NULL = occupation en cours
CREATE TABLE occupation (
    id          INT PRIMARY KEY,
    etudiant_id INT NOT NULL,
    chambre_id  INT NOT NULL,
    date_debut  DATE NOT NULL,
    date_fin    DATE,
    CONSTRAINT fk_occupation_etudiant FOREIGN KEY (etudiant_id) REFERENCES etudiant (id),
    CONSTRAINT fk_occupation_chambre FOREIGN KEY (chambre_id) REFERENCES chambre (id)
);

-- 2. et 3. Repas, menus et plats ---------------------------------------------

CREATE TABLE type_repas (
    id      INT PRIMARY KEY,
    libelle VARCHAR(50) NOT NULL
);

CREATE TABLE menu (
    id  INT PRIMARY KEY,
    nom VARCHAR(100) NOT NULL
);

CREATE TABLE type_plat (
    id      INT PRIMARY KEY,
    libelle VARCHAR(50) NOT NULL
);

CREATE TABLE plat (
    id             INT PRIMARY KEY,
    type_plat_id   INT NOT NULL,
    nom            VARCHAR(100) NOT NULL UNIQUE,
    description    VARCHAR(255),
    prix_indicatif DECIMAL(6, 2) NOT NULL,
    CONSTRAINT fk_plat_type FOREIGN KEY (type_plat_id) REFERENCES type_plat (id)
);

-- Ordre des plats dans un menu
CREATE TABLE composition_menu (
    menu_id INT NOT NULL,
    plat_id INT NOT NULL,
    ordre   INT NOT NULL,
    PRIMARY KEY (menu_id, plat_id),
    CONSTRAINT fk_composition_menu FOREIGN KEY (menu_id) REFERENCES menu (id),
    CONSTRAINT fk_composition_plat FOREIGN KEY (plat_id) REFERENCES plat (id)
);

-- 9. Événements (déclarés avant les repas : un repas spécial peut être lié à un événement)
CREATE TABLE evenement (
    id             INT PRIMARY KEY,
    nom            VARCHAR(100) NOT NULL,
    date_evenement DATE NOT NULL,
    lieu           VARCHAR(100) NOT NULL
);

CREATE TABLE repas (
    id            INT PRIMARY KEY,
    type_repas_id INT NOT NULL,
    menu_id       INT NOT NULL,
    evenement_id  INT,
    date_heure    DATETIME NOT NULL,
    places_max    INT NOT NULL,
    CONSTRAINT fk_repas_type FOREIGN KEY (type_repas_id) REFERENCES type_repas (id),
    CONSTRAINT fk_repas_menu FOREIGN KEY (menu_id) REFERENCES menu (id),
    CONSTRAINT fk_repas_evenement FOREIGN KEY (evenement_id) REFERENCES evenement (id)
);

-- present NULL = repas pas encore servi
CREATE TABLE reservation (
    id               INT PRIMARY KEY,
    etudiant_id      INT NOT NULL,
    repas_id         INT NOT NULL,
    date_reservation DATETIME NOT NULL,
    annulee          BOOLEAN NOT NULL,
    present          BOOLEAN,
    CONSTRAINT uq_reservation UNIQUE (etudiant_id, repas_id),
    CONSTRAINT fk_reservation_etudiant FOREIGN KEY (etudiant_id) REFERENCES etudiant (id),
    CONSTRAINT fk_reservation_repas FOREIGN KEY (repas_id) REFERENCES repas (id)
);

-- 4. Contraintes alimentaires ------------------------------------------------

CREATE TABLE contrainte_alimentaire (
    id      INT PRIMARY KEY,
    libelle VARCHAR(60) NOT NULL
);

CREATE TABLE etudiant_contrainte (
    etudiant_id   INT NOT NULL,
    contrainte_id INT NOT NULL,
    PRIMARY KEY (etudiant_id, contrainte_id),
    CONSTRAINT fk_etudiant_contrainte_etudiant FOREIGN KEY (etudiant_id) REFERENCES etudiant (id),
    CONSTRAINT fk_etudiant_contrainte_contrainte FOREIGN KEY (contrainte_id) REFERENCES contrainte_alimentaire (id)
);

CREATE TABLE plat_incompatibilite (
    plat_id       INT NOT NULL,
    contrainte_id INT NOT NULL,
    PRIMARY KEY (plat_id, contrainte_id),
    CONSTRAINT fk_incompatibilite_plat FOREIGN KEY (plat_id) REFERENCES plat (id),
    CONSTRAINT fk_incompatibilite_contrainte FOREIGN KEY (contrainte_id) REFERENCES contrainte_alimentaire (id)
);

-- 5. et 6. Ingrédients et fournisseurs ---------------------------------------

CREATE TABLE ingredient (
    id             INT PRIMARY KEY,
    nom            VARCHAR(80) NOT NULL UNIQUE,
    unite          VARCHAR(20) NOT NULL,
    stock          DECIMAL(10, 2) NOT NULL,
    stock_minimum  DECIMAL(10, 2) NOT NULL
);

CREATE TABLE plat_ingredient (
    plat_id       INT NOT NULL,
    ingredient_id INT NOT NULL,
    quantite      DECIMAL(8, 2) NOT NULL,
    PRIMARY KEY (plat_id, ingredient_id),
    CONSTRAINT fk_plat_ingredient_plat FOREIGN KEY (plat_id) REFERENCES plat (id),
    CONSTRAINT fk_plat_ingredient_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredient (id)
);

CREATE TABLE fournisseur (
    id        INT PRIMARY KEY,
    nom       VARCHAR(100) NOT NULL,
    ville     VARCHAR(60) NOT NULL,
    email     VARCHAR(150) NOT NULL,
    telephone VARCHAR(20)
);

-- prix exprimé pour une unite_achat ; date_debut NULL = date inconnue
CREATE TABLE fournisseur_ingredient (
    fournisseur_id INT NOT NULL,
    ingredient_id  INT NOT NULL,
    prix           DECIMAL(8, 2) NOT NULL,
    unite_achat    VARCHAR(20) NOT NULL,
    date_debut     DATE,
    PRIMARY KEY (fournisseur_id, ingredient_id),
    CONSTRAINT fk_fournisseur_ingredient_fournisseur FOREIGN KEY (fournisseur_id) REFERENCES fournisseur (id),
    CONSTRAINT fk_fournisseur_ingredient_ingredient FOREIGN KEY (ingredient_id) REFERENCES ingredient (id)
);

-- 7. et 8. Personnel étudiant et formation -----------------------------------

CREATE TABLE tache (
    id      INT PRIMARY KEY,
    libelle VARCHAR(60) NOT NULL
);

CREATE TABLE intervention (
    id          INT PRIMARY KEY,
    etudiant_id INT NOT NULL,
    tache_id    INT NOT NULL,
    jour        DATE NOT NULL,
    heure_debut TIME NOT NULL,
    heure_fin   TIME NOT NULL,
    CONSTRAINT fk_intervention_etudiant FOREIGN KEY (etudiant_id) REFERENCES etudiant (id),
    CONSTRAINT fk_intervention_tache FOREIGN KEY (tache_id) REFERENCES tache (id)
);

-- Association réflexive : formateur_id forme forme_id ; date_fin NULL = formation en cours
CREATE TABLE formation (
    id           INT PRIMARY KEY,
    formateur_id INT NOT NULL,
    forme_id     INT NOT NULL,
    date_debut   DATE NOT NULL,
    date_fin     DATE,
    CONSTRAINT fk_formation_formateur FOREIGN KEY (formateur_id) REFERENCES etudiant (id),
    CONSTRAINT fk_formation_forme FOREIGN KEY (forme_id) REFERENCES etudiant (id)
);

-- 9. et 10. Organisation des événements et dépenses --------------------------

CREATE TABLE participation_evenement (
    etudiant_id  INT NOT NULL,
    evenement_id INT NOT NULL,
    role         VARCHAR(60) NOT NULL,
    PRIMARY KEY (etudiant_id, evenement_id),
    CONSTRAINT fk_participation_etudiant FOREIGN KEY (etudiant_id) REFERENCES etudiant (id),
    CONSTRAINT fk_participation_evenement FOREIGN KEY (evenement_id) REFERENCES evenement (id)
);

CREATE TABLE depense (
    id           INT PRIMARY KEY,
    evenement_id INT NOT NULL,
    description  VARCHAR(150) NOT NULL,
    montant      DECIMAL(8, 2) NOT NULL,
    date_depense DATE NOT NULL,
    CONSTRAINT fk_depense_evenement FOREIGN KEY (evenement_id) REFERENCES evenement (id)
);

-- Part d'une dépense avancée par un étudiant
CREATE TABLE avance_depense (
    depense_id  INT NOT NULL,
    etudiant_id INT NOT NULL,
    montant     DECIMAL(8, 2) NOT NULL,
    PRIMARY KEY (depense_id, etudiant_id),
    CONSTRAINT fk_avance_depense FOREIGN KEY (depense_id) REFERENCES depense (id),
    CONSTRAINT fk_avance_etudiant FOREIGN KEY (etudiant_id) REFERENCES etudiant (id)
);

-- Index utiles aux requêtes courantes
CREATE INDEX idx_occupation_etudiant ON occupation (etudiant_id);
CREATE INDEX idx_repas_date ON repas (date_heure);
CREATE INDEX idx_reservation_repas ON reservation (repas_id);
CREATE INDEX idx_intervention_etudiant ON intervention (etudiant_id);
