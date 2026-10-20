# Projet MCD — Le restaurant de la résidence universitaire

## Contexte

Vous êtes chargé de concevoir le **Modèle Conceptuel de Données (MCD)** d’un restaurant situé au sein d’une résidence universitaire.

Le restaurant accueille quotidiennement les étudiants de la résidence et propose différents repas. Son fonctionnement implique la gestion des étudiants, des chambres, des menus, des plats, des ingrédients, des réservations, mais également l’organisation du personnel étudiant et des événements.

Votre objectif est de **modéliser le système d’information** du restaurant sous la forme d’un MCD.

---

# 1. La résidence universitaire

La résidence est composée de plusieurs bâtiments.

Chaque bâtiment contient plusieurs chambres.

Les chambres ont une capacité qui peut varier : certaines accueillent une seule personne, d’autres plusieurs.

Des étudiants résident dans les chambres.

Pour chaque étudiant, la résidence souhaite conserver son nom, son prénom, sa date de naissance et son adresse e-mail universitaire.

Un étudiant peut changer de chambre au cours de son séjour dans la résidence.

La résidence souhaite conserver l'historique de ces changements afin de savoir **qui a occupé quelle chambre et pendant quelle période**.

Un étudiant peut également quitter la résidence puis y revenir ultérieurement.

---

# 2. Les repas du restaurant

Le restaurant propose des repas aux étudiants de la résidence.

Un repas correspond à une offre proposée à une date et une heure données.

Il peut s'agir par exemple :

- d'un petit-déjeuner ;
- d'un déjeuner ;
- d'un dîner ;
- d'un repas spécial organisé à l'occasion d'un événement.

Un repas possède un nombre maximal de places disponibles.

Les étudiants peuvent réserver un repas.

Pour chaque réservation, le restaurant souhaite notamment savoir :

- quel étudiant a réservé ;
- quel repas a été réservé ;
- à quelle date la réservation a été effectuée ;
- si la réservation a été annulée ;
- si l'étudiant est effectivement venu manger.

Un étudiant peut réserver plusieurs repas et un même repas peut être réservé par de nombreux étudiants.

---

# 3. Les menus et les plats

Chaque repas proposé par le restaurant correspond à un menu.

Un menu peut contenir plusieurs plats.

Un plat peut être présent dans plusieurs menus différents.

Un plat possède notamment :

- un nom ;
- une description ;
- un prix indicatif.

Les plats sont classés selon leur type : entrée, plat principal, accompagnement, dessert, boisson, etc.

L'ordre dans lequel les plats sont proposés dans un menu est important.

Par exemple, un menu peut être composé de :

> Entrée → Plat principal → Dessert → Boisson

Le même plat peut donc apparaître dans plusieurs menus, éventuellement avec un ordre différent.

---

# 4. Les contraintes alimentaires

Les étudiants peuvent déclarer leurs contraintes ou préférences alimentaires.

Par exemple :

- végétarien ;
- végétalien ;
- sans gluten ;
- sans porc ;
- allergie aux arachides ;
- allergie au lait.

Un étudiant peut avoir plusieurs contraintes alimentaires.

Une même contrainte peut concerner de nombreux étudiants.

Certains plats peuvent être incompatibles avec certaines contraintes.

Le restaurant souhaite pouvoir déterminer **si un plat peut être proposé à un étudiant donné**.

---

# 5. Les ingrédients

Chaque plat est préparé à partir de plusieurs ingrédients.

Un même ingrédient peut être utilisé dans plusieurs plats.

Pour chaque utilisation d'un ingrédient dans un plat, la quantité utilisée peut être différente.

Par exemple :

> Une pizza utilise 200 g de farine.  
> Une crêpe utilise 100 g de farine.

Le restaurant souhaite donc conserver les quantités nécessaires à la préparation de chaque plat.

Pour chaque ingrédient, on souhaite également connaître :

- son nom ;
- son unité de mesure ;
- la quantité actuellement disponible en stock ;
- une quantité minimale à conserver.

---

# 6. Les fournisseurs

Le restaurant travaille avec plusieurs fournisseurs.

Un fournisseur peut fournir plusieurs ingrédients.

Un même ingrédient peut être acheté auprès de plusieurs fournisseurs.

Le prix d'un ingrédient peut varier selon le fournisseur.

Le restaurant souhaite conserver les informations permettant de savoir :

- quel fournisseur propose quel ingrédient ;
- à quel prix ;
- dans quelle unité ;
- éventuellement depuis quelle date ce fournisseur est utilisé pour cet ingrédient.

---

# 7. Les étudiants qui travaillent au restaurant

Certains étudiants de la résidence travaillent ponctuellement au restaurant.

Ils peuvent effectuer différentes tâches :

- préparer les plats ;
- servir les repas ;
- tenir la caisse ;
- nettoyer ;
- réceptionner les livraisons ;
- organiser les événements.

Un étudiant peut effectuer plusieurs types de tâches.

Une tâche peut être effectuée par plusieurs étudiants.

Le restaurant souhaite conserver l'historique des interventions.

Pour chaque intervention, il faut notamment pouvoir connaître :

- l'étudiant concerné ;
- la tâche réalisée ;
- le jour ;
- l'heure de début ;
- l'heure de fin.

---

# 8. La formation des étudiants

Les nouveaux étudiants travaillant au restaurant doivent être formés.

Un étudiant expérimenté peut être chargé de former un autre étudiant.

Un étudiant peut former plusieurs autres étudiants.

Un étudiant peut également être formé par un autre étudiant.

La responsabilité de formation peut évoluer au cours du temps.

Le restaurant souhaite pouvoir connaître **qui a formé qui** et conserver cette information.

Il doit également être possible de retrouver la chaîne de formation.

Par exemple :

> Alice forme Bob.  
> Bob forme Charlie.  
> Charlie forme David.

On doit pouvoir représenter cette organisation dans le modèle.

---

# 9. Les événements

Le restaurant organise régulièrement des événements :

- soirée pizza ;
- repas de rentrée ;
- repas de Noël ;
- soirée internationale ;
- barbecue ;
- etc.

Un événement possède notamment un nom, une date et un lieu.

Plusieurs étudiants peuvent participer à l'organisation d'un même événement.

Un étudiant peut participer à plusieurs événements.

Pour chaque participation, le restaurant souhaite pouvoir connaître le rôle joué par l'étudiant.

Par exemple :

> Alice — responsable de l'organisation  
> Bob — cuisine  
> Charlie — décoration  
> David — service

---

# 10. Les dépenses liées aux événements

L'organisation d'un événement peut générer plusieurs dépenses.

Par exemple :

- achat de nourriture ;
- décoration ;
- location de matériel ;
- boissons ;
- impression d'affiches.

Une dépense possède notamment un montant, une date et une description.

Plusieurs étudiants peuvent avancer de l'argent pour une même dépense.

Un étudiant peut avancer de l'argent pour plusieurs dépenses.

Le restaurant souhaite pouvoir savoir **quelle personne a payé quelle partie de quelle dépense**.

Par exemple :

> Une dépense de 120 € est réalisée pour une soirée.  
> Alice avance 70 €.  
> Bob avance 50 €.

Le système doit permettre de conserver cette information.

