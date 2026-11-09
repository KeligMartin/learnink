-- Base « Entreprise de transport de colis », interrogée par les étudiants (source de données n°2).
-- DDL portable : exécutable tel quel sur MySQL 8 et sur H2 en mode MySQL.

-- 1. Clients, adresses et expéditions --------------------------------------------------------------

CREATE TABLE adresse (
    id          INT PRIMARY KEY,
    rue         VARCHAR(150) NOT NULL,
    code_postal VARCHAR(10) NOT NULL,
    ville       VARCHAR(60) NOT NULL
);

-- nom = nom du particulier ou raison sociale du professionnel
CREATE TABLE client (
    id                   INT PRIMARY KEY,
    type_client          VARCHAR(20) NOT NULL,
    nom                  VARCHAR(120) NOT NULL,
    email                VARCHAR(150) NOT NULL UNIQUE,
    telephone            VARCHAR(20) NOT NULL,
    adresse_principale_id INT NOT NULL,
    CONSTRAINT fk_client_adresse FOREIGN KEY (adresse_principale_id) REFERENCES adresse (id)
);

-- 11. Services, options et tarifs
CREATE TABLE service (
    id          INT PRIMARY KEY,
    nom         VARCHAR(60) NOT NULL,
    description VARCHAR(255) NOT NULL,
    conditions  VARCHAR(255),
    prix_base   DECIMAL(8, 2) NOT NULL,
    prix_kg     DECIMAL(8, 2) NOT NULL
);

CREATE TABLE option_expedition (
    id  INT PRIMARY KEY,
    nom VARCHAR(60) NOT NULL
);

-- Historique des prix d'une option : date_fin NULL = tarif en vigueur
CREATE TABLE tarif_option (
    id         INT PRIMARY KEY,
    option_id  INT NOT NULL,
    prix       DECIMAL(8, 2) NOT NULL,
    date_debut DATE NOT NULL,
    date_fin   DATE,
    CONSTRAINT fk_tarif_option FOREIGN KEY (option_id) REFERENCES option_expedition (id)
);

-- 3. Agences, entrepôts et trajets
CREATE TABLE agence (
    id        INT PRIMARY KEY,
    nom       VARCHAR(80) NOT NULL,
    ville     VARCHAR(60) NOT NULL,
    adresse   VARCHAR(150) NOT NULL,
    telephone VARCHAR(20) NOT NULL
);

CREATE TABLE entrepot (
    id           INT PRIMARY KEY,
    agence_id    INT NOT NULL,
    nom          VARCHAR(80) NOT NULL,
    capacite_max INT NOT NULL,
    CONSTRAINT fk_entrepot_agence FOREIGN KEY (agence_id) REFERENCES agence (id)
);

-- Deux agences peuvent être reliées par plusieurs trajets ; cout NULL = non renseigné
CREATE TABLE trajet (
    id                INT PRIMARY KEY,
    agence_depart_id  INT NOT NULL,
    agence_arrivee_id INT NOT NULL,
    libelle           VARCHAR(80) NOT NULL,
    distance_km       INT NOT NULL,
    duree_estimee_min INT NOT NULL,
    cout              DECIMAL(8, 2),
    CONSTRAINT fk_trajet_depart FOREIGN KEY (agence_depart_id) REFERENCES agence (id),
    CONSTRAINT fk_trajet_arrivee FOREIGN KEY (agence_arrivee_id) REFERENCES agence (id)
);

-- priorite : Normale, Haute, Urgente ; prix_total = prix appliqué au moment de la création
CREATE TABLE expedition (
    id                     INT PRIMARY KEY,
    reference              VARCHAR(20) NOT NULL UNIQUE,
    expediteur_id          INT NOT NULL,
    destinataire_id        INT NOT NULL,
    adresse_depart_id      INT NOT NULL,
    adresse_destination_id INT NOT NULL,
    agence_depart_id       INT NOT NULL,
    agence_arrivee_id      INT NOT NULL,
    service_id             INT NOT NULL,
    date_creation          DATETIME NOT NULL,
    date_livraison_prevue  DATE NOT NULL,
    priorite               VARCHAR(20) NOT NULL,
    prix_total             DECIMAL(8, 2) NOT NULL,
    CONSTRAINT fk_expedition_expediteur FOREIGN KEY (expediteur_id) REFERENCES client (id),
    CONSTRAINT fk_expedition_destinataire FOREIGN KEY (destinataire_id) REFERENCES client (id),
    CONSTRAINT fk_expedition_adresse_depart FOREIGN KEY (adresse_depart_id) REFERENCES adresse (id),
    CONSTRAINT fk_expedition_adresse_destination FOREIGN KEY (adresse_destination_id) REFERENCES adresse (id),
    CONSTRAINT fk_expedition_agence_depart FOREIGN KEY (agence_depart_id) REFERENCES agence (id),
    CONSTRAINT fk_expedition_agence_arrivee FOREIGN KEY (agence_arrivee_id) REFERENCES agence (id),
    CONSTRAINT fk_expedition_service FOREIGN KEY (service_id) REFERENCES service (id)
);

-- Association N:N porteuse d'un attribut : le prix de l'option au moment de l'expédition
CREATE TABLE expedition_option (
    expedition_id INT NOT NULL,
    option_id     INT NOT NULL,
    prix_applique DECIMAL(8, 2) NOT NULL,
    PRIMARY KEY (expedition_id, option_id),
    CONSTRAINT fk_expedition_option_expedition FOREIGN KEY (expedition_id) REFERENCES expedition (id),
    CONSTRAINT fk_expedition_option_option FOREIGN KEY (option_id) REFERENCES option_expedition (id)
);

CREATE TABLE colis (
    id            INT PRIMARY KEY,
    expedition_id INT NOT NULL,
    numero        INT NOT NULL,
    poids_kg      DECIMAL(6, 2) NOT NULL,
    longueur_cm   INT NOT NULL,
    largeur_cm    INT NOT NULL,
    hauteur_cm    INT NOT NULL,
    fragile       BOOLEAN NOT NULL,
    CONSTRAINT fk_colis_expedition FOREIGN KEY (expedition_id) REFERENCES expedition (id)
);

-- 10. Employés, hiérarchie et affectations ---------------------------------------------------------

-- fonction : Livreur, Agent de tri, Responsable d'agence, Technicien, Agent service client, Direction
CREATE TABLE employe (
    id            INT PRIMARY KEY,
    matricule     VARCHAR(10) NOT NULL UNIQUE,
    nom           VARCHAR(60) NOT NULL,
    prenom        VARCHAR(60) NOT NULL,
    date_embauche DATE NOT NULL,
    fonction      VARCHAR(40) NOT NULL,
    statut        VARCHAR(20) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    telephone     VARCHAR(20) NOT NULL
);

-- Association réflexive : employe_id est encadré par responsable_id ; date_fin NULL = en cours
CREATE TABLE hierarchie (
    id             INT PRIMARY KEY,
    employe_id     INT NOT NULL,
    responsable_id INT NOT NULL,
    date_debut     DATE NOT NULL,
    date_fin       DATE,
    CONSTRAINT fk_hierarchie_employe FOREIGN KEY (employe_id) REFERENCES employe (id),
    CONSTRAINT fk_hierarchie_responsable FOREIGN KEY (responsable_id) REFERENCES employe (id)
);

CREATE TABLE affectation_agence (
    id         INT PRIMARY KEY,
    employe_id INT NOT NULL,
    agence_id  INT NOT NULL,
    date_debut DATE NOT NULL,
    date_fin   DATE,
    CONSTRAINT fk_affectation_agence_employe FOREIGN KEY (employe_id) REFERENCES employe (id),
    CONSTRAINT fk_affectation_agence_agence FOREIGN KEY (agence_id) REFERENCES agence (id)
);

-- 6. Formation entre livreurs (association réflexive) ; resultat : Validée, Non validée, En cours
CREATE TABLE formation (
    id           INT PRIMARY KEY,
    formateur_id INT NOT NULL,
    forme_id     INT NOT NULL,
    date_debut   DATE NOT NULL,
    date_fin     DATE,
    resultat     VARCHAR(20) NOT NULL,
    CONSTRAINT fk_formation_formateur FOREIGN KEY (formateur_id) REFERENCES employe (id),
    CONSTRAINT fk_formation_forme FOREIGN KEY (forme_id) REFERENCES employe (id)
);

-- 5. Spécialisations, véhicules ----------------------------------------------------------------------

CREATE TABLE specialisation (
    id      INT PRIMARY KEY,
    libelle VARCHAR(60) NOT NULL
);

CREATE TABLE livreur_specialisation (
    employe_id        INT NOT NULL,
    specialisation_id INT NOT NULL,
    date_obtention    DATE NOT NULL,
    PRIMARY KEY (employe_id, specialisation_id),
    CONSTRAINT fk_livreur_specialisation_employe FOREIGN KEY (employe_id) REFERENCES employe (id),
    CONSTRAINT fk_livreur_specialisation_specialisation FOREIGN KEY (specialisation_id) REFERENCES specialisation (id)
);

CREATE TABLE type_vehicule (
    id      INT PRIMARY KEY,
    libelle VARCHAR(40) NOT NULL
);

CREATE TABLE vehicule (
    id               INT PRIMARY KEY,
    immatriculation  VARCHAR(12) NOT NULL UNIQUE,
    type_vehicule_id INT NOT NULL,
    agence_id        INT NOT NULL,
    capacite_kg      INT NOT NULL,
    CONSTRAINT fk_vehicule_type FOREIGN KEY (type_vehicule_id) REFERENCES type_vehicule (id),
    CONSTRAINT fk_vehicule_agence FOREIGN KEY (agence_id) REFERENCES agence (id)
);

-- Historique : quel livreur utilisait quel véhicule, et quand
CREATE TABLE affectation_vehicule (
    id          INT PRIMARY KEY,
    vehicule_id INT NOT NULL,
    employe_id  INT NOT NULL,
    date_debut  DATE NOT NULL,
    date_fin    DATE,
    CONSTRAINT fk_affectation_vehicule_vehicule FOREIGN KEY (vehicule_id) REFERENCES vehicule (id),
    CONSTRAINT fk_affectation_vehicule_employe FOREIGN KEY (employe_id) REFERENCES employe (id)
);

-- motif : Panne, Maintenance
CREATE TABLE indisponibilite_vehicule (
    id          INT PRIMARY KEY,
    vehicule_id INT NOT NULL,
    date_debut  DATE NOT NULL,
    date_fin    DATE NOT NULL,
    motif       VARCHAR(20) NOT NULL,
    CONSTRAINT fk_indisponibilite_vehicule FOREIGN KEY (vehicule_id) REFERENCES vehicule (id)
);

-- 2. Suivi des colis et stockage --------------------------------------------------------------------

-- est_anomalie : l'événement signale un problème (livraison échouée, retour à l'expéditeur)
CREATE TABLE statut_suivi (
    id           INT PRIMARY KEY,
    libelle      VARCHAR(60) NOT NULL,
    est_anomalie BOOLEAN NOT NULL
);

-- agence_id NULL : événement hors agence (remise au destinataire)
CREATE TABLE evenement_suivi (
    id          INT PRIMARY KEY,
    colis_id    INT NOT NULL,
    statut_id   INT NOT NULL,
    date_heure  DATETIME NOT NULL,
    agence_id   INT,
    entrepot_id INT,
    commentaire VARCHAR(255),
    CONSTRAINT fk_evenement_colis FOREIGN KEY (colis_id) REFERENCES colis (id),
    CONSTRAINT fk_evenement_statut FOREIGN KEY (statut_id) REFERENCES statut_suivi (id),
    CONSTRAINT fk_evenement_agence FOREIGN KEY (agence_id) REFERENCES agence (id),
    CONSTRAINT fk_evenement_entrepot FOREIGN KEY (entrepot_id) REFERENCES entrepot (id)
);

-- Périodes de présence d'un colis dans un entrepôt ; date_sortie NULL = encore stocké
CREATE TABLE stockage (
    id          INT PRIMARY KEY,
    colis_id    INT NOT NULL,
    entrepot_id INT NOT NULL,
    date_entree DATETIME NOT NULL,
    date_sortie DATETIME,
    CONSTRAINT fk_stockage_colis FOREIGN KEY (colis_id) REFERENCES colis (id),
    CONSTRAINT fk_stockage_entrepot FOREIGN KEY (entrepot_id) REFERENCES entrepot (id)
);

-- 7. Opérations de transport entre agences ------------------------------------------------------------

-- arrivee_reelle NULL = opération en cours
CREATE TABLE operation_transport (
    id             INT PRIMARY KEY,
    trajet_id      INT NOT NULL,
    vehicule_id    INT NOT NULL,
    date_depart    DATETIME NOT NULL,
    arrivee_prevue DATETIME NOT NULL,
    arrivee_reelle DATETIME,
    CONSTRAINT fk_operation_trajet FOREIGN KEY (trajet_id) REFERENCES trajet (id),
    CONSTRAINT fk_operation_vehicule FOREIGN KEY (vehicule_id) REFERENCES vehicule (id)
);

CREATE TABLE operation_colis (
    operation_id INT NOT NULL,
    colis_id     INT NOT NULL,
    PRIMARY KEY (operation_id, colis_id),
    CONSTRAINT fk_operation_colis_operation FOREIGN KEY (operation_id) REFERENCES operation_transport (id),
    CONSTRAINT fk_operation_colis_colis FOREIGN KEY (colis_id) REFERENCES colis (id)
);

-- role : Chauffeur, Convoyeur
CREATE TABLE operation_personnel (
    operation_id INT NOT NULL,
    employe_id   INT NOT NULL,
    role         VARCHAR(20) NOT NULL,
    PRIMARY KEY (operation_id, employe_id),
    CONSTRAINT fk_operation_personnel_operation FOREIGN KEY (operation_id) REFERENCES operation_transport (id),
    CONSTRAINT fk_operation_personnel_employe FOREIGN KEY (employe_id) REFERENCES employe (id)
);

-- 4. Tournées de livraison et 8. tentatives ----------------------------------------------------------

-- statut : Terminée, Interrompue ; heure_fin NULL si interrompue
CREATE TABLE tournee (
    id           INT PRIMARY KEY,
    agence_id    INT NOT NULL,
    livreur_id   INT NOT NULL,
    vehicule_id  INT NOT NULL,
    date_tournee DATE NOT NULL,
    heure_depart TIME NOT NULL,
    heure_fin    TIME,
    statut       VARCHAR(20) NOT NULL,
    commentaire  VARCHAR(255),
    CONSTRAINT fk_tournee_agence FOREIGN KEY (agence_id) REFERENCES agence (id),
    CONSTRAINT fk_tournee_livreur FOREIGN KEY (livreur_id) REFERENCES employe (id),
    CONSTRAINT fk_tournee_vehicule FOREIGN KEY (vehicule_id) REFERENCES vehicule (id)
);

-- ordre : position de l'arrêt dans l'itinéraire de la tournée
CREATE TABLE arret (
    id         INT PRIMARY KEY,
    tournee_id INT NOT NULL,
    ordre      INT NOT NULL,
    adresse_id INT NOT NULL,
    CONSTRAINT fk_arret_tournee FOREIGN KEY (tournee_id) REFERENCES tournee (id),
    CONSTRAINT fk_arret_adresse FOREIGN KEY (adresse_id) REFERENCES adresse (id)
);

CREATE TABLE arret_colis (
    arret_id INT NOT NULL,
    colis_id INT NOT NULL,
    PRIMARY KEY (arret_id, colis_id),
    CONSTRAINT fk_arret_colis_arret FOREIGN KEY (arret_id) REFERENCES arret (id),
    CONSTRAINT fk_arret_colis_colis FOREIGN KEY (colis_id) REFERENCES colis (id)
);

-- resultat : Livré, Destinataire absent, Adresse incorrecte, Refusé
-- receptionnaire et mode_confirmation (Signature, Code de retrait, Photo) renseignés si le colis est livré
CREATE TABLE tentative_livraison (
    id                INT PRIMARY KEY,
    colis_id          INT NOT NULL,
    tournee_id        INT NOT NULL,
    livreur_id        INT NOT NULL,
    date_heure        DATETIME NOT NULL,
    resultat          VARCHAR(30) NOT NULL,
    commentaire       VARCHAR(255),
    receptionnaire    VARCHAR(120),
    mode_confirmation VARCHAR(20),
    CONSTRAINT fk_tentative_colis FOREIGN KEY (colis_id) REFERENCES colis (id),
    CONSTRAINT fk_tentative_tournee FOREIGN KEY (tournee_id) REFERENCES tournee (id),
    CONSTRAINT fk_tentative_livreur FOREIGN KEY (livreur_id) REFERENCES employe (id)
);

-- 9. Incidents et réclamations -------------------------------------------------------------------------

-- type : Retard de transport, Panne de véhicule, Erreur de tri, Colis endommagé
CREATE TABLE incident (
    id           INT PRIMARY KEY,
    type         VARCHAR(40) NOT NULL,
    date_heure   DATETIME NOT NULL,
    agence_id    INT NOT NULL,
    operation_id INT,
    description  VARCHAR(255) NOT NULL,
    CONSTRAINT fk_incident_agence FOREIGN KEY (agence_id) REFERENCES agence (id),
    CONSTRAINT fk_incident_operation FOREIGN KEY (operation_id) REFERENCES operation_transport (id)
);

-- etat : Ouverte, En cours, Clôturée ; date_cloture NULL tant que la réclamation n'est pas clôturée
CREATE TABLE reclamation (
    id             INT PRIMARY KEY,
    expedition_id  INT NOT NULL,
    type           VARCHAR(60) NOT NULL,
    date_ouverture DATETIME NOT NULL,
    description    VARCHAR(255) NOT NULL,
    etat           VARCHAR(20) NOT NULL,
    date_cloture   DATETIME,
    CONSTRAINT fk_reclamation_expedition FOREIGN KEY (expedition_id) REFERENCES expedition (id)
);

CREATE TABLE intervention (
    id             INT PRIMARY KEY,
    reclamation_id INT NOT NULL,
    employe_id     INT NOT NULL,
    date_heure     DATETIME NOT NULL,
    description    VARCHAR(255) NOT NULL,
    CONSTRAINT fk_intervention_reclamation FOREIGN KEY (reclamation_id) REFERENCES reclamation (id),
    CONSTRAINT fk_intervention_employe FOREIGN KEY (employe_id) REFERENCES employe (id)
);

-- Une réclamation peut concerner plusieurs incidents, un incident peut causer plusieurs réclamations
CREATE TABLE reclamation_incident (
    reclamation_id INT NOT NULL,
    incident_id    INT NOT NULL,
    PRIMARY KEY (reclamation_id, incident_id),
    CONSTRAINT fk_reclamation_incident_reclamation FOREIGN KEY (reclamation_id) REFERENCES reclamation (id),
    CONSTRAINT fk_reclamation_incident_incident FOREIGN KEY (incident_id) REFERENCES incident (id)
);

CREATE INDEX idx_evenement_colis ON evenement_suivi (colis_id);
CREATE INDEX idx_colis_expedition ON colis (expedition_id);
CREATE INDEX idx_expedition_date ON expedition (date_creation);
CREATE INDEX idx_tentative_colis ON tentative_livraison (colis_id);
