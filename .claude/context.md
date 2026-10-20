### Plateform de e-learing (SQL & NoSQL)

### Fonctionnalités
- Interface web avec IDE
- Choix entre base de données SQL ou firebase (firestore à créer pour chaque étudiant)
- Uniquement de la lecture (SELECT)
- 3 niveaux de difficultés
- Feedback en direct sur l'interface (note sur 10 en fonction de l'efficacité de la requête)

### Architecture

- Monorepo
- Java & SpringBoot (ou Scala & Play) pour le backend
- Base de données mariadb
- React pour le frontend
- Authentification email / mdp
- Reset de mdp à la premiere connexion
- Finir le niveau 1 pour passer au niveau 2 etc

### Volumétrie
- 40 étudiants max en parallèle
- 10 tables avec 100 000 lignes au total maximum