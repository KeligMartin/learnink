-- Base applicative Learnink (MySQL 8) : utilisateurs, exercices et tentatives.
-- Correspond aux entités JPA ; Hibernate vérifie le schéma au démarrage (ddl-auto: validate).

CREATE TABLE app_user (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    email                VARCHAR(180) NOT NULL,
    password_hash        VARCHAR(255) NOT NULL,
    role                 VARCHAR(20)  NOT NULL,
    must_change_password BIT          NOT NULL,
    created_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE exercise (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    code                VARCHAR(50)   NOT NULL,
    engine              VARCHAR(20)   NOT NULL,
    level               INTEGER       NOT NULL,
    position            INTEGER       NOT NULL,
    title               VARCHAR(150)  NOT NULL,
    statement           VARCHAR(4000) NOT NULL,
    reference_query     VARCHAR(4000) NOT NULL,
    order_matters       BIT           NOT NULL,
    chart_type          VARCHAR(20),
    chart_label_columns INTEGER,
    archived            BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    CONSTRAINT uk_exercise_code UNIQUE (code)
) ENGINE = InnoDB;

CREATE TABLE submission (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    user_id           BIGINT         NOT NULL,
    exercise_id       BIGINT         NOT NULL,
    query             VARCHAR(10000) NOT NULL,
    score             FLOAT(53)      NOT NULL,
    correct           BIT            NOT NULL,
    execution_time_ms FLOAT(53),
    created_at        DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_submission_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_submission_exercise FOREIGN KEY (exercise_id) REFERENCES exercise (id)
) ENGINE = InnoDB;

CREATE INDEX idx_submission_user_exercise ON submission (user_id, exercise_id);
