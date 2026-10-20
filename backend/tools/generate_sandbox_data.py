#!/usr/bin/env python3
"""Génère le jeu de données "Résidence universitaire et son restaurant" (déterministe).

Usage :
    python3 tools/generate_sandbox_data.py > src/main/resources/sandbox/residence/data.sql

Les INSERT produits sont compatibles H2 (mode MySQL) et MySQL 8.
La date de référence ("aujourd'hui" pour le jeu de données) est REFERENCE_DATE : les repas postérieurs
n'ont pas encore été servis (reservation.present NULL) et les occupations en cours ont date_fin NULL.

Cohérence garantie par construction :
- la capacité des chambres n'est jamais dépassée et un étudiant n'occupe qu'une chambre à la fois ;
- seuls les étudiants résidant à la date du repas réservent, dans la limite des places ;
- les incompatibilités plat / contrainte sont déduites des ingrédients des plats ;
- les interventions et participations ne concernent que des résidents ;
- la somme des avances d'une dépense ne dépasse jamais son montant.
"""
import argparse
import random
import unicodedata
from datetime import date, datetime, time, timedelta

REFERENCE_DATE = date(2026, 9, 29)
HISTORY_START = date(2023, 9, 1)
SERVICE_PERIODS = [(date(2025, 9, 1), date(2026, 6, 30)), (date(2026, 9, 1), date(2026, 10, 16))]

# ---------------------------------------------------------------------------------------------------------------
# Référentiels
# ---------------------------------------------------------------------------------------------------------------

BATIMENTS = [
    ("Bâtiment A - Alan Turing", "1 allée des Sciences"),
    ("Bâtiment B - Ada Lovelace", "3 allée des Sciences"),
    ("Bâtiment C - Marie Curie", "5 allée des Sciences"),
    ("Bâtiment D - Grace Hopper", "7 allée des Sciences"),
]
CHAMBRES_PAR_BATIMENT = 40

TYPES_REPAS = ["Petit-déjeuner", "Déjeuner", "Dîner", "Repas spécial"]
TYPES_PLAT = ["Entrée", "Plat principal", "Accompagnement", "Dessert", "Boisson", "Petit-déjeuner"]
ORDRE_TYPE = {"Petit-déjeuner": 1, "Entrée": 1, "Plat principal": 2, "Accompagnement": 3, "Dessert": 4, "Boisson": 5}

# "Allergie au poisson" n'est déclarée par aucun étudiant (utile pour LEFT JOIN + COUNT(colonne))
CONTRAINTES = ["Végétarien", "Végétalien", "Sans gluten", "Sans porc", "Allergie aux arachides", "Allergie au lait",
               "Allergie au poisson"]
# Contrainte -> propriétés d'ingrédient qui rendent un plat incompatible
EXCLUSIONS = {
    "Végétarien": {"viande", "porc", "poisson"},
    "Végétalien": {"viande", "porc", "poisson", "lait", "oeuf", "miel"},
    "Sans gluten": {"gluten"},
    "Sans porc": {"porc"},
    "Allergie aux arachides": {"arachide"},
    "Allergie au lait": {"lait"},
    "Allergie au poisson": {"poisson"},
}

# nom, unité, propriétés, prix de référence par unité d'achat, unité d'achat, catégorie fournisseur
INGREDIENTS = [
    ("Farine", "g", {"gluten"}, 1.10, "kg", "epicerie"),
    ("Pâtes", "g", {"gluten"}, 1.60, "kg", "epicerie"),
    ("Riz", "g", set(), 1.90, "kg", "epicerie"),
    ("Semoule", "g", {"gluten"}, 1.70, "kg", "epicerie"),
    ("Pain", "g", {"gluten"}, 3.20, "kg", "boulangerie"),
    ("Céréales", "g", {"gluten"}, 4.50, "kg", "epicerie"),
    ("Pommes de terre", "g", set(), 0.90, "kg", "primeur"),
    ("Oeufs", "pièce", {"oeuf"}, 3.20, "douzaine", "ferme"),
    ("Lait", "ml", {"lait"}, 1.05, "L", "laiterie"),
    ("Beurre", "g", {"lait"}, 9.50, "kg", "laiterie"),
    ("Crème fraîche", "ml", {"lait"}, 4.20, "L", "laiterie"),
    ("Fromage râpé", "g", {"lait"}, 11.00, "kg", "laiterie"),
    ("Mozzarella", "g", {"lait"}, 9.00, "kg", "laiterie"),
    ("Yaourt nature", "pièce", {"lait"}, 2.40, "lot de 12", "laiterie"),
    ("Poulet", "g", {"viande"}, 8.50, "kg", "boucherie"),
    ("Boeuf haché", "g", {"viande"}, 12.00, "kg", "boucherie"),
    ("Jambon", "g", {"viande", "porc"}, 11.50, "kg", "boucherie"),
    ("Lardons", "g", {"viande", "porc"}, 10.00, "kg", "boucherie"),
    ("Saumon", "g", {"poisson"}, 22.00, "kg", "maree"),
    ("Cabillaud", "g", {"poisson"}, 18.00, "kg", "maree"),
    ("Thon", "g", {"poisson"}, 14.00, "kg", "maree"),
    ("Tofu", "g", set(), 7.50, "kg", "bio"),
    ("Pois chiches", "g", set(), 2.80, "kg", "bio"),
    ("Lentilles", "g", set(), 3.10, "kg", "bio"),
    ("Tomates", "g", set(), 2.90, "kg", "primeur"),
    ("Sauce tomate", "ml", set(), 2.20, "L", "epicerie"),
    ("Oignons", "g", set(), 1.40, "kg", "primeur"),
    ("Carottes", "g", set(), 1.20, "kg", "primeur"),
    ("Courgettes", "g", set(), 2.40, "kg", "primeur"),
    ("Salade verte", "g", set(), 3.50, "kg", "primeur"),
    ("Concombre", "g", set(), 2.10, "kg", "primeur"),
    ("Champignons", "g", set(), 5.20, "kg", "primeur"),
    ("Poivrons", "g", set(), 3.80, "kg", "primeur"),
    ("Ail", "g", set(), 7.00, "kg", "primeur"),
    ("Citron", "pièce", set(), 0.45, "pièce", "primeur"),
    ("Huile d'olive", "ml", set(), 8.50, "L", "epicerie"),
    ("Sucre", "g", set(), 1.30, "kg", "epicerie"),
    ("Chocolat noir", "g", set(), 12.00, "kg", "epicerie"),
    ("Pommes", "pièce", set(), 0.35, "pièce", "primeur"),
    ("Bananes", "pièce", set(), 0.30, "pièce", "primeur"),
    ("Fraises", "g", set(), 7.50, "kg", "primeur"),
    ("Cacahuètes", "g", {"arachide"}, 6.00, "kg", "epicerie"),
    ("Beurre de cacahuète", "g", {"arachide"}, 9.00, "kg", "bio"),
    ("Miel", "g", {"miel"}, 14.00, "kg", "ferme"),
    ("Confiture", "g", set(), 5.50, "kg", "epicerie"),
    ("Café", "g", set(), 16.00, "kg", "cafe"),
    ("Thé", "g", set(), 25.00, "kg", "cafe"),
    ("Jus d'orange", "ml", set(), 2.10, "L", "boissons"),
    ("Eau gazeuse", "ml", set(), 0.60, "L", "boissons"),
    ("Lait d'avoine", "ml", {"gluten"}, 2.30, "L", "bio"),
    ("Flocons d'avoine", "g", {"gluten"}, 3.20, "kg", "bio"),
    ("Safran", "g", set(), 9000.00, "kg", None),  # aucun fournisseur référencé
]

# nom, type, prix, description, [(ingrédient, quantité)]
PLATS = [
    # Entrées
    ("Salade verte", "Entrée", 1.50, "Salade de saison, vinaigrette à l'huile d'olive",
     [("Salade verte", 80), ("Huile d'olive", 10)]),
    ("Carottes râpées", "Entrée", 1.50, "Carottes râpées au citron",
     [("Carottes", 120), ("Citron", 0.25), ("Huile d'olive", 10)]),
    ("Taboulé", "Entrée", 2.00, "Semoule, tomates, concombre et citron",
     [("Semoule", 60), ("Tomates", 50), ("Concombre", 40), ("Citron", 0.25), ("Huile d'olive", 10)]),
    ("Salade de lentilles", "Entrée", 2.00, "Lentilles vertes et oignons rouges",
     [("Lentilles", 70), ("Oignons", 20), ("Huile d'olive", 10)]),
    ("Oeuf mayonnaise", "Entrée", 1.80, "Deux demi-oeufs durs et mayonnaise maison",
     [("Oeufs", 1), ("Huile d'olive", 15)]),
    ("Velouté de courgettes", "Entrée", 2.20, "Velouté crémeux de courgettes",
     [("Courgettes", 180), ("Crème fraîche", 30), ("Oignons", 20)]),
    ("Houmous et crudités", "Entrée", 2.50, "Houmous maison, bâtonnets de carottes",
     [("Pois chiches", 80), ("Carottes", 60), ("Citron", 0.25), ("Ail", 3), ("Huile d'olive", 15)]),
    ("Salade niçoise", "Entrée", 3.00, "Salade, thon, oeuf et tomates",
     [("Salade verte", 50), ("Thon", 60), ("Oeufs", 1), ("Tomates", 60)]),
    ("Soupe à l'oignon", "Entrée", 2.50, "Gratinée au fromage",
     [("Oignons", 150), ("Pain", 30), ("Fromage râpé", 25), ("Beurre", 10)]),
    ("Concombre au yaourt", "Entrée", 1.80, "Concombre et sauce yaourt à la menthe",
     [("Concombre", 120), ("Yaourt nature", 0.5)]),
    # Plats principaux
    ("Poulet rôti", "Plat principal", 5.50, "Cuisse de poulet rôtie à l'ail",
     [("Poulet", 220), ("Huile d'olive", 10), ("Ail", 4)]),
    ("Steak haché", "Plat principal", 5.00, "Steak haché de boeuf",
     [("Boeuf haché", 150)]),
    ("Lasagnes bolognaises", "Plat principal", 6.00, "Lasagnes au boeuf et béchamel",
     [("Pâtes", 90), ("Boeuf haché", 100), ("Sauce tomate", 80), ("Fromage râpé", 30), ("Lait", 80),
      ("Farine", 10), ("Beurre", 10)]),
    ("Spaghetti bolognaise", "Plat principal", 5.00, "Spaghetti, sauce tomate au boeuf",
     [("Pâtes", 110), ("Boeuf haché", 90), ("Sauce tomate", 90), ("Oignons", 20)]),
    ("Pâtes carbonara", "Plat principal", 5.00, "Lardons, crème et parmesan",
     [("Pâtes", 110), ("Lardons", 60), ("Crème fraîche", 50), ("Oeufs", 1), ("Fromage râpé", 20)]),
    ("Quiche lorraine", "Plat principal", 4.50, "Quiche aux lardons",
     [("Farine", 60), ("Beurre", 30), ("Oeufs", 1.5), ("Lardons", 50), ("Crème fraîche", 60), ("Lait", 40)]),
    ("Pizza margherita", "Plat principal", 5.00, "Tomate, mozzarella, huile d'olive",
     [("Farine", 120), ("Sauce tomate", 60), ("Mozzarella", 80), ("Huile d'olive", 10)]),
    ("Pizza reine", "Plat principal", 6.00, "Tomate, mozzarella, jambon, champignons",
     [("Farine", 120), ("Sauce tomate", 60), ("Mozzarella", 70), ("Jambon", 50), ("Champignons", 40)]),
    ("Saumon grillé", "Plat principal", 7.00, "Pavé de saumon grillé au citron",
     [("Saumon", 150), ("Citron", 0.5), ("Huile d'olive", 10)]),
    ("Cabillaud à la crème", "Plat principal", 6.50, "Dos de cabillaud, sauce crème",
     [("Cabillaud", 150), ("Crème fraîche", 50)]),
    ("Curry de pois chiches", "Plat principal", 4.50, "Curry doux aux pois chiches et tomates",
     [("Pois chiches", 150), ("Tomates", 100), ("Oignons", 40), ("Ail", 4)]),
    ("Chili sin carne", "Plat principal", 4.50, "Lentilles, poivrons et tomates épicés",
     [("Lentilles", 120), ("Tomates", 100), ("Poivrons", 60), ("Oignons", 30)]),
    ("Tofu sauté aux légumes", "Plat principal", 5.00, "Tofu mariné et légumes croquants",
     [("Tofu", 150), ("Courgettes", 80), ("Poivrons", 60), ("Carottes", 60), ("Huile d'olive", 10)]),
    ("Couscous végétarien", "Plat principal", 5.00, "Semoule, pois chiches et légumes",
     [("Semoule", 100), ("Pois chiches", 80), ("Carottes", 60), ("Courgettes", 60), ("Tomates", 50)]),
    ("Poulet au curry", "Plat principal", 5.50, "Émincé de poulet sauce curry",
     [("Poulet", 180), ("Tomates", 60), ("Oignons", 30), ("Crème fraîche", 30)]),
    ("Gratin de courgettes", "Plat principal", 4.50, "Courgettes gratinées au fromage",
     [("Courgettes", 250), ("Crème fraîche", 50), ("Fromage râpé", 30)]),
    ("Omelette aux champignons", "Plat principal", 4.00, "Omelette de trois oeufs",
     [("Oeufs", 3), ("Champignons", 60), ("Beurre", 10)]),
    ("Burger maison", "Plat principal", 6.50, "Pain brioché, steak, fromage, crudités",
     [("Pain", 80), ("Boeuf haché", 150), ("Fromage râpé", 20), ("Salade verte", 15), ("Tomates", 30)]),
    ("Poulet satay", "Plat principal", 6.00, "Brochettes de poulet sauce cacahuète",
     [("Poulet", 180), ("Beurre de cacahuète", 30), ("Cacahuètes", 15)]),
    ("Dahl de lentilles", "Plat principal", 4.00, "Lentilles corail mijotées",
     [("Lentilles", 130), ("Tomates", 80), ("Oignons", 30), ("Ail", 4)]),
    ("Hachis parmentier", "Plat principal", 5.50, "Purée gratinée au boeuf",
     [("Pommes de terre", 250), ("Boeuf haché", 100), ("Lait", 50), ("Beurre", 15), ("Fromage râpé", 20)]),
    ("Croque-monsieur", "Plat principal", 4.00, "Jambon, fromage, pain de mie",
     [("Pain", 90), ("Jambon", 40), ("Fromage râpé", 30), ("Beurre", 10)]),
    ("Paella au safran", "Plat principal", 7.50, "Riz au safran, poulet et poivrons",
     [("Riz", 100), ("Safran", 0.05), ("Poulet", 120), ("Poivrons", 50)]),
    ("Plat du chef", "Plat principal", 6.00, "Surprise du chef, selon l'inspiration du jour", []),
    # Accompagnements
    ("Riz blanc", "Accompagnement", 1.00, "Riz basmati", [("Riz", 80)]),
    ("Frites", "Accompagnement", 1.50, "Frites maison", [("Pommes de terre", 200), ("Huile d'olive", 20)]),
    ("Purée maison", "Accompagnement", 1.50, "Purée de pommes de terre",
     [("Pommes de terre", 200), ("Lait", 60), ("Beurre", 15)]),
    ("Légumes vapeur", "Accompagnement", 1.50, "Carottes et courgettes vapeur",
     [("Carottes", 80), ("Courgettes", 80)]),
    ("Pâtes au beurre", "Accompagnement", 1.20, "Coquillettes au beurre", [("Pâtes", 90), ("Beurre", 10)]),
    ("Semoule nature", "Accompagnement", 1.00, "Semoule fine", [("Semoule", 80), ("Huile d'olive", 5)]),
    ("Poêlée de champignons", "Accompagnement", 1.80, "Champignons à l'ail",
     [("Champignons", 120), ("Ail", 3), ("Huile d'olive", 10)]),
    # Desserts
    ("Yaourt", "Dessert", 0.80, "Yaourt nature", [("Yaourt nature", 1)]),
    ("Pomme", "Dessert", 0.60, "Fruit de saison", [("Pommes", 1)]),
    ("Banane", "Dessert", 0.60, "Fruit", [("Bananes", 1)]),
    ("Mousse au chocolat", "Dessert", 2.00, "Mousse au chocolat noir",
     [("Chocolat noir", 40), ("Oeufs", 1), ("Sucre", 10)]),
    ("Crêpe au sucre", "Dessert", 1.50, "Crêpe maison",
     [("Farine", 40), ("Oeufs", 0.5), ("Lait", 80), ("Sucre", 10), ("Beurre", 5)]),
    ("Tarte aux pommes", "Dessert", 2.00, "Tarte fine aux pommes",
     [("Farine", 40), ("Beurre", 20), ("Pommes", 1), ("Sucre", 15)]),
    ("Salade de fruits", "Dessert", 1.80, "Pommes, bananes, fraises",
     [("Pommes", 0.5), ("Bananes", 0.5), ("Fraises", 50), ("Sucre", 5)]),
    ("Cookie aux cacahuètes", "Dessert", 1.50, "Cookie maison",
     [("Farine", 30), ("Beurre", 15), ("Sucre", 15), ("Cacahuètes", 15), ("Oeufs", 0.25)]),
    ("Fraises au sucre", "Dessert", 1.50, "Fraises fraîches", [("Fraises", 120), ("Sucre", 10)]),
    ("Compote de pommes", "Dessert", 1.00, "Compote maison", [("Pommes", 1.5), ("Sucre", 10)]),
    # Boissons
    ("Eau gazeuse", "Boisson", 0.80, "Bouteille de 50 cl", [("Eau gazeuse", 500)]),
    ("Jus d'orange", "Boisson", 1.20, "Verre de 25 cl", [("Jus d'orange", 250)]),
    ("Café", "Boisson", 0.80, "Expresso", [("Café", 8)]),
    ("Thé", "Boisson", 0.80, "Thé noir ou vert", [("Thé", 3)]),
    ("Chocolat chaud", "Boisson", 1.20, "Lait chocolaté",
     [("Lait", 200), ("Chocolat noir", 20), ("Sucre", 10)]),
    ("Smoothie banane-fraise", "Boisson", 2.00, "Au lait d'avoine",
     [("Bananes", 1), ("Fraises", 60), ("Lait d'avoine", 150)]),
    # Petit-déjeuner
    ("Tartines beurre-confiture", "Petit-déjeuner", 1.20, "Pain, beurre et confiture",
     [("Pain", 60), ("Beurre", 10), ("Confiture", 20)]),
    ("Croissant", "Petit-déjeuner", 1.00, "Croissant pur beurre", [("Farine", 50), ("Beurre", 25)]),
    ("Bol de céréales", "Petit-déjeuner", 1.20, "Céréales et lait", [("Céréales", 40), ("Lait", 150)]),
    ("Porridge", "Petit-déjeuner", 1.50, "Flocons d'avoine au lait d'avoine",
     [("Flocons d'avoine", 50), ("Lait d'avoine", 200), ("Miel", 10)]),
    ("Pancakes au miel", "Petit-déjeuner", 1.80, "Trois pancakes et miel",
     [("Farine", 60), ("Oeufs", 1), ("Lait", 80), ("Miel", 15)]),
    ("Tartine au beurre de cacahuète", "Petit-déjeuner", 1.30, "Pain complet",
     [("Pain", 60), ("Beurre de cacahuète", 20)]),
    ("Fruits frais", "Petit-déjeuner", 1.00, "Banane et pomme", [("Bananes", 1), ("Pommes", 1)]),
]
PLATS_JAMAIS_PROPOSES = {"Poulet satay", "Paella au safran"}

MENUS_EVENEMENTS = {
    "Menu soirée pizza": ["Salade verte", "Pizza margherita", "Pizza reine", "Mousse au chocolat", "Eau gazeuse"],
    "Menu de rentrée": ["Taboulé", "Poulet rôti", "Frites", "Tarte aux pommes", "Jus d'orange"],
    "Menu de Noël": ["Velouté de courgettes", "Saumon grillé", "Purée maison", "Mousse au chocolat", "Café"],
    "Menu soirée internationale": ["Houmous et crudités", "Couscous végétarien", "Curry de pois chiches",
                                   "Riz blanc", "Salade de fruits", "Thé"],
    "Menu barbecue": ["Salade de lentilles", "Burger maison", "Frites", "Fraises au sucre", "Eau gazeuse"],
    "Menu crêpes": ["Crêpe au sucre", "Chocolat chaud", "Jus d'orange"],
}

# nom, date, lieu, menu du repas spécial (None = pas de repas associé)
EVENEMENTS = [
    ("Repas de rentrée 2025", date(2025, 9, 12), "Restaurant", "Menu de rentrée"),
    ("Soirée pizza d'automne", date(2025, 10, 17), "Restaurant", "Menu soirée pizza"),
    ("Soirée jeux de société", date(2025, 11, 21), "Salle commune du bâtiment A", None),
    ("Repas de Noël", date(2025, 12, 17), "Restaurant", "Menu de Noël"),
    ("Soirée crêpes de la Chandeleur", date(2026, 2, 2), "Salle commune du bâtiment B", "Menu crêpes"),
    ("Soirée internationale", date(2026, 3, 20), "Restaurant", "Menu soirée internationale"),
    ("Soirée pizza de printemps", date(2026, 4, 10), "Terrasse du bâtiment C", "Menu soirée pizza"),
    ("Barbecue de fin d'année", date(2026, 6, 12), "Jardin de la résidence", "Menu barbecue"),
    ("Repas de rentrée 2026", date(2026, 9, 11), "Restaurant", "Menu de rentrée"),
    ("Soirée pizza d'automne 2026", date(2026, 10, 9), "Restaurant", "Menu soirée pizza"),
]
ROLES = ["Cuisine", "Décoration", "Service", "Communication", "Animation", "Installation"]
DEPENSES_TYPES = [
    ("Achat de nourriture", 60, 260),
    ("Boissons", 25, 120),
    ("Décoration", 15, 80),
    ("Location de matériel", 40, 150),
    ("Impression d'affiches", 10, 45),
]

FOURNISSEURS = [
    ("Primeurs du Val", "Angers", "primeur"),
    ("Boucherie Delaunay", "Rennes", "boucherie"),
    ("La Marée Bretonne", "Lorient", "maree"),
    ("Laiterie des Prés", "Vitré", "laiterie"),
    ("Moulin et Boulangerie Hamon", "Rennes", "boulangerie"),
    ("Ferme des Collines", "Fougères", "ferme"),
    ("Épicerie Bio Locale", "Rennes", "bio"),
    ("Grossiste Restauration Ouest", "Nantes", "grossiste"),
    ("Boissons & Cie", "Nantes", "boissons"),
    ("Brûlerie Durand", "Saint-Malo", "cafe"),
    ("Épicerie Fine Lemoine", "Rennes", "epicerie"),
]

TACHES = ["Préparer les plats", "Servir les repas", "Tenir la caisse", "Nettoyer",
          "Réceptionner les livraisons", "Organiser les événements"]

PRENOMS = ["Emma", "Lucas", "Léa", "Hugo", "Chloé", "Louis", "Manon", "Nathan", "Inès", "Jules", "Camille",
           "Arthur", "Sarah", "Adam", "Jade", "Gabriel", "Lina", "Raphaël", "Zoé", "Tom", "Louise", "Enzo",
           "Clara", "Théo", "Anna", "Noah", "Juliette", "Mathis", "Lou", "Maël", "Rose", "Sacha", "Ambre",
           "Yanis", "Nina", "Ethan", "Mila", "Paul", "Eva", "Rayan", "Yasmine", "Karim", "Mei", "Kenji",
           "Sofia", "Diego", "Amina", "Olivia", "Lukas", "Ana"]
NOMS = ["Martin", "Bernard", "Dubois", "Thomas", "Robert", "Richard", "Petit", "Durand", "Leroy", "Moreau",
        "Simon", "Laurent", "Lefebvre", "Michel", "Garcia", "David", "Bertrand", "Roux", "Vincent", "Fournier",
        "Morel", "Girard", "André", "Mercier", "Dupont", "Lambert", "Bonnet", "François", "Martinez", "Legrand",
        "Garnier", "Faure", "Rousseau", "Blanc", "Guérin", "Muller", "Henry", "Roussel", "Nicolas", "Perrin",
        "Nguyen", "Benali", "Diallo", "Silva", "Rossi", "Schmidt", "Kowalski", "Tanaka", "Haddad", "Costa"]


# ---------------------------------------------------------------------------------------------------------------
# Outils
# ---------------------------------------------------------------------------------------------------------------

def sql(value):
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        return f"{value:.2f}"
    if isinstance(value, datetime):
        return "TIMESTAMP '" + value.strftime("%Y-%m-%d %H:%M:%S") + "'"
    if isinstance(value, date):
        return "DATE '" + value.isoformat() + "'"
    if isinstance(value, time):
        return "TIME '" + value.strftime("%H:%M:%S") + "'"
    return "'" + str(value).replace("'", "''") + "'"


def insert(table, columns, rows, batch=500):
    print(f"-- {table} : {len(rows)} ligne(s)")
    for start in range(0, len(rows), batch):
        chunk = rows[start:start + batch]
        values = ",\n".join("  (" + ", ".join(sql(v) for v in row) + ")" for row in chunk)
        print(f"INSERT INTO {table} ({', '.join(columns)}) VALUES\n{values};")
    print()
    return len(rows)


def slug(text):
    ascii_text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode()
    return "".join(c for c in ascii_text.lower() if c.isalnum() or c == "-")


def days(start, end):
    current = start
    while current <= end:
        yield current
        current += timedelta(days=1)


def overlaps(a_start, a_end, b_start, b_end):
    """Intervalles de dates fermés ; une fin None signifie "en cours"."""
    a_end = a_end or date.max
    b_end = b_end or date.max
    return a_start <= b_end and b_start <= a_end


# ---------------------------------------------------------------------------------------------------------------
# Génération
# ---------------------------------------------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--etudiants", type=int, default=420)
    parser.add_argument("--affluence", type=float, default=1.0,
                        help="multiplie la probabilité de réservation (volumétrie des réservations)")
    args = parser.parse_args()
    rnd = random.Random(2026)
    total = 0

    print("-- Fichier généré par tools/generate_sandbox_data.py : ne pas modifier à la main.")
    print(f"-- Date de référence du jeu de données : {REFERENCE_DATE.isoformat()}\n")

    # --- Résidence -------------------------------------------------------------------------------------------
    total += insert("batiment", ["id", "nom", "adresse"], [(i + 1, n, a) for i, (n, a) in enumerate(BATIMENTS)])

    chambres = []
    for b in range(len(BATIMENTS)):
        lettre = "ABCD"[b]
        for n in range(CHAMBRES_PAR_BATIMENT):
            etage = n // 10
            capacite = rnd.choices([1, 2, 3], weights=[70, 25, 5])[0]
            chambres.append((len(chambres) + 1, b + 1, f"{lettre}{etage}{n % 10 + 1:02d}", etage, capacite))
    total += insert("chambre", ["id", "batiment_id", "numero", "etage", "capacite"], chambres)

    # Étudiants : les quatre premiers forment la chaîne de formation de l'énoncé (Alice -> Bob -> Charlie -> David)
    identites = [("Martin", "Alice"), ("Lefèvre", "Bob"), ("Dubois", "Charlie"), ("Moreau", "David")]
    while len(identites) < args.etudiants:
        identites.append((rnd.choice(NOMS), rnd.choice(PRENOMS)))
    candidats = []
    for i, (nom, prenom) in enumerate(identites, start=1):
        naissance = date(1998, 1, 1) + timedelta(days=rnd.randrange(0, 8 * 365))
        candidats.append((i, nom, prenom, naissance))

    # Occupations : séjours de 4 à 30 mois, changements de chambre, départs puis retours
    capacite = {c[0]: c[4] for c in chambres}
    occupations_par_chambre = {c[0]: [] for c in chambres}
    occupations = []

    def chambre_libre(debut, fin, exclure=None):
        libres = [c for c in capacite if c != exclure]
        rnd.shuffle(libres)
        for c in libres:
            occupants = sum(1 for (d, f) in occupations_par_chambre[c] if overlaps(d, f, debut, fin))
            if occupants < capacite[c]:
                return c
        return None

    def occuper(etudiant_id, debut, fin, exclure=None):
        chambre = chambre_libre(debut, fin, exclure)
        if chambre is None:
            return None
        occupations_par_chambre[chambre].append((debut, fin))
        occupations.append([None, etudiant_id, chambre, debut, fin])
        return chambre

    for etudiant_id, *_ in candidats:
        if etudiant_id <= 4:  # Alice, Bob, Charlie et David résident depuis la rentrée 2024
            debut = date(2024, 9, 1) + timedelta(days=7 * (etudiant_id - 1))
            occuper(etudiant_id, debut, None)
            continue
        debut = HISTORY_START + timedelta(days=rnd.randrange(0, (REFERENCE_DATE - HISTORY_START).days - 20))
        fin = debut + timedelta(days=rnd.randrange(120, 900))
        fin = None if fin >= REFERENCE_DATE else fin
        if rnd.random() < 0.2 and (fin or REFERENCE_DATE) - debut > timedelta(days=120):
            # Changement de chambre en cours de séjour
            bascule = debut + timedelta(days=rnd.randrange(60, ((fin or REFERENCE_DATE) - debut).days - 30))
            premiere = occuper(etudiant_id, debut, bascule - timedelta(days=1))
            if premiere is not None:
                occuper(etudiant_id, bascule, fin, exclure=premiere)
        else:
            occuper(etudiant_id, debut, fin)
        if fin is not None and rnd.random() < 0.12:
            # Départ puis retour quelques mois plus tard
            retour = fin + timedelta(days=rnd.randrange(60, 300))
            if retour < REFERENCE_DATE - timedelta(days=30):
                fin_retour = retour + timedelta(days=rnd.randrange(90, 400))
                occuper(etudiant_id, retour, None if fin_retour >= REFERENCE_DATE else fin_retour)

    # Seuls les étudiants ayant obtenu une chambre sont enregistrés (identifiants renumérotés)
    loges = sorted({o[1] for o in occupations})
    nouvel_id = {ancien: i for i, ancien in enumerate(loges, start=1)}
    etudiants = []
    for ancien, nom, prenom, naissance in candidats:
        if ancien in nouvel_id:
            i = nouvel_id[ancien]
            email = f"{slug(prenom)}.{slug(nom)}{'' if i <= 4 else i}@etu.univ-exemple.fr"
            etudiants.append((i, nom, prenom, naissance, email))
    total += insert("etudiant", ["id", "nom", "prenom", "date_naissance", "email"], etudiants)

    for occupation in occupations:
        occupation[1] = nouvel_id[occupation[1]]
    occupations.sort(key=lambda o: (o[3], o[1]))
    for i, occupation in enumerate(occupations, start=1):
        occupation[0] = i
    total += insert("occupation", ["id", "etudiant_id", "chambre_id", "date_debut", "date_fin"],
                    [tuple(o) for o in occupations])

    sejours = {}
    for _, etudiant_id, _, debut, fin in occupations:
        sejours.setdefault(etudiant_id, []).append((debut, fin))

    def reside(etudiant_id, jour):
        return any(overlaps(d, f, jour, jour) for d, f in sejours.get(etudiant_id, []))

    residents_par_jour = {}

    def residents(jour):
        if jour not in residents_par_jour:
            residents_par_jour[jour] = [e for e in sejours if reside(e, jour)]
        return residents_par_jour[jour]

    # --- Référentiels du restaurant ------------------------------------------------------------------------
    total += insert("type_repas", ["id", "libelle"], [(i + 1, t) for i, t in enumerate(TYPES_REPAS)])
    total += insert("type_plat", ["id", "libelle"], [(i + 1, t) for i, t in enumerate(TYPES_PLAT)])
    total += insert("contrainte_alimentaire", ["id", "libelle"], [(i + 1, c) for i, c in enumerate(CONTRAINTES)])

    ingredient_ids = {ing[0]: i + 1 for i, ing in enumerate(INGREDIENTS)}
    ingredients = []
    for i, (nom, unite, _, _, _, _) in enumerate(INGREDIENTS, start=1):
        minimum = {"g": rnd.choice([2000, 5000, 10000]), "ml": rnd.choice([2000, 5000, 10000]),
                   "pièce": rnd.choice([30, 60, 120])}[unite]
        stock = round(minimum * rnd.uniform(0.3, 4.0) if rnd.random() < 0.8 else minimum * rnd.uniform(0.05, 0.9))
        ingredients.append((i, nom, unite, float(stock), float(minimum)))
    total += insert("ingredient", ["id", "nom", "unite", "stock", "stock_minimum"], ingredients)

    type_plat_ids = {t: i + 1 for i, t in enumerate(TYPES_PLAT)}
    plat_ids = {p[0]: i + 1 for i, p in enumerate(PLATS)}
    plat_type = {p[0]: p[1] for p in PLATS}
    total += insert("plat", ["id", "type_plat_id", "nom", "description", "prix_indicatif"],
                    [(plat_ids[n], type_plat_ids[t], n, d, float(prix)) for n, t, prix, d, _ in PLATS])
    total += insert("plat_ingredient", ["plat_id", "ingredient_id", "quantite"],
                    [(plat_ids[n], ingredient_ids[ing], float(q)) for n, _, _, _, compo in PLATS for ing, q in compo])

    proprietes = {ing[0]: ing[2] for ing in INGREDIENTS}
    incompatibilites = []
    for nom, _, _, _, compo in PLATS:
        props = set().union(*(proprietes[ing] for ing, _ in compo)) if compo else set()
        for c, contrainte in enumerate(CONTRAINTES, start=1):
            if props & EXCLUSIONS[contrainte]:
                incompatibilites.append((plat_ids[nom], c))
    total += insert("plat_incompatibilite", ["plat_id", "contrainte_id"], incompatibilites)

    # Contraintes des étudiants (Alice : végétarienne et allergique au lait)
    etudiant_contraintes = [(1, 1), (1, 6)]
    taux = {1: 0.10, 2: 0.03, 3: 0.05, 4: 0.12, 5: 0.03, 6: 0.04}
    for etudiant_id, *_ in etudiants[1:]:
        for c, probabilite in taux.items():
            if rnd.random() < probabilite:
                etudiant_contraintes.append((etudiant_id, c))
    total += insert("etudiant_contrainte", ["etudiant_id", "contrainte_id"], etudiant_contraintes)

    # --- Fournisseurs ---------------------------------------------------------------------------------------
    fournisseurs = [(i + 1, n, v, f"contact@{slug(n.replace(' ', '-'))}.fr", f"02 {rnd.randrange(10, 99)} "
                     f"{rnd.randrange(10, 99)} {rnd.randrange(10, 99)} {rnd.randrange(10, 99)}")
                    for i, (n, v, _) in enumerate(FOURNISSEURS)]
    total += insert("fournisseur", ["id", "nom", "ville", "email", "telephone"], fournisseurs)
    par_categorie = {cat: i + 1 for i, (_, _, cat) in enumerate(FOURNISSEURS)}
    grossiste = par_categorie["grossiste"]
    offres = []
    for nom, _, _, prix, unite_achat, categorie in INGREDIENTS:
        if categorie is None:
            continue
        candidats = [par_categorie[categorie]]
        if rnd.random() < 0.65:
            candidats.append(grossiste)
        if categorie in ("primeur", "epicerie") and rnd.random() < 0.3:
            candidats.append(par_categorie["bio"])
        for fournisseur_id in dict.fromkeys(candidats):
            facteur = 0.85 if fournisseur_id == grossiste else 1.25 if fournisseur_id == par_categorie["bio"] else 1.0
            depuis = None if rnd.random() < 0.2 else date(2021, 1, 1) + timedelta(days=rnd.randrange(0, 1900))
            offres.append((fournisseur_id, ingredient_ids[nom], round(prix * facteur * rnd.uniform(0.92, 1.1), 2),
                           unite_achat, depuis))
    total += insert("fournisseur_ingredient", ["fournisseur_id", "ingredient_id", "prix", "unite_achat", "date_debut"],
                    offres)

    # --- Menus -----------------------------------------------------------------------------------------------
    par_type = {}
    for nom, type_plat, *_ in PLATS:
        if nom not in PLATS_JAMAIS_PROPOSES:
            par_type.setdefault(type_plat, []).append(nom)

    menus, compositions = [], []

    def ajouter_menu(nom, plats, ordonner=True):
        menu_id = len(menus) + 1
        menus.append((menu_id, nom))
        ordonnes = sorted(plats, key=lambda p: ORDRE_TYPE[plat_type[p]]) if ordonner else plats
        for ordre, plat in enumerate(ordonnes, start=1):
            compositions.append((menu_id, plat_ids[plat], ordre))
        return menu_id

    menus_petit_dej = []
    for i in range(1, 7):
        choix = rnd.sample(par_type["Petit-déjeuner"], 2) + [rnd.choice(["Café", "Thé", "Chocolat chaud", "Jus d'orange"])]
        menus_petit_dej.append(ajouter_menu(f"Petit-déjeuner n°{i}", choix))
    menus_midi_soir = []
    for plat_principal in par_type["Plat principal"]:
        choix = [plat_principal, rnd.choice(par_type["Dessert"]), rnd.choice(["Eau gazeuse", "Jus d'orange"])]
        if rnd.random() < 0.75:
            choix.append(rnd.choice(par_type["Entrée"]))
        if not plat_principal.startswith(("Pizza", "Lasagnes", "Quiche", "Croque", "Burger", "Gratin", "Hachis")):
            choix.append(rnd.choice(par_type["Accompagnement"]))
        menus_midi_soir.append(ajouter_menu(f"Menu {plat_principal.lower()}", list(dict.fromkeys(choix))))
    menus_evenements = {nom: ajouter_menu(nom, plats, ordonner=False) for nom, plats in MENUS_EVENEMENTS.items()}
    total += insert("menu", ["id", "nom"], menus)
    total += insert("composition_menu", ["menu_id", "plat_id", "ordre"], compositions)

    # --- Événements -----------------------------------------------------------------------------------------
    total += insert("evenement", ["id", "nom", "date_evenement", "lieu"],
                    [(i + 1, n, d, l) for i, (n, d, l, _) in enumerate(EVENEMENTS)])

    # --- Repas et réservations -----------------------------------------------------------------------------
    repas = []
    horaires = {1: (time(7, 30), 80), 2: (time(12, 0), 140), 3: (time(19, 0), 110)}
    for debut, fin in SERVICE_PERIODS:
        for jour in days(debut, fin):
            if jour.weekday() == 6:  # fermé le dimanche
                continue
            for type_id in (1, 2, 3):
                if type_id == 3 and jour.weekday() == 5:  # pas de dîner le samedi
                    continue
                heure, places = horaires[type_id]
                menu = rnd.choice(menus_petit_dej if type_id == 1 else menus_midi_soir)
                repas.append([None, type_id, menu, None, datetime.combine(jour, heure), places])
    for e, (_, jour, _, menu) in enumerate(EVENEMENTS, start=1):
        if menu is not None:
            repas.append([None, 4, menus_evenements[menu], e, datetime.combine(jour, time(19, 30)), 70])
    repas.sort(key=lambda r: r[4])
    for i, r in enumerate(repas, start=1):
        r[0] = i
    total += insert("repas", ["id", "type_repas_id", "menu_id", "evenement_id", "date_heure", "places_max"],
                    [tuple(r) for r in repas])

    # Chaque étudiant a ses habitudes : certains mangent souvent au restaurant, d'autres jamais
    habitude = {e[0]: rnd.choice([0, 0.3, 0.6, 1.0, 1.0, 1.3, 1.6]) for e in etudiants}
    fiabilite = {e[0]: rnd.choice([0.995, 0.99, 0.98, 0.97, 0.93]) for e in etudiants}
    base = {1: 0.22, 2: 0.55, 3: 0.40, 4: 0.60}
    reservations = []
    for repas_id, type_id, _, _, moment, places in repas:
        jour = moment.date()
        candidats = [e for e in residents(jour) if rnd.random() < base[type_id] * habitude[e] * args.affluence]
        rnd.shuffle(candidats)
        confirmees = 0
        for etudiant_id in candidats:
            annulee = rnd.random() < 0.07
            if not annulee and confirmees >= places:
                continue  # repas complet
            confirmees += 0 if annulee else 1
            reserve_le = moment - timedelta(hours=rnd.randrange(2, 24 * 7), minutes=rnd.randrange(0, 60))
            if annulee:
                present = False
            elif jour >= REFERENCE_DATE:
                present = None
            else:
                present = rnd.random() < fiabilite[etudiant_id]
            reservations.append((len(reservations) + 1, etudiant_id, repas_id, reserve_le, annulee, present))
    total += insert("reservation", ["id", "etudiant_id", "repas_id", "date_reservation", "annulee", "present"],
                    reservations)

    # --- Personnel étudiant, interventions et formation ------------------------------------------------------
    total += insert("tache", ["id", "libelle"], [(i + 1, t) for i, t in enumerate(TACHES)])

    candidats_travail = [e for e in sejours if e > 4 and reside(e, date(2025, 10, 1))]
    travailleurs = [1, 2, 3, 4] + rnd.sample(candidats_travail, min(40, len(candidats_travail)))
    # Date d'embauche : la chaîne Alice -> Bob -> Charlie -> David, puis les autres au fil de l'année
    embauche = {1: date(2025, 9, 1), 2: date(2025, 9, 15), 3: date(2025, 10, 6), 4: date(2025, 11, 3)}
    for etudiant_id in travailleurs[4:]:
        embauche[etudiant_id] = date(2025, 9, 1) + timedelta(days=rnd.randrange(0, 200))
    competences = {e: rnd.sample(range(1, 6), rnd.randrange(1, 4)) for e in travailleurs}
    competences[1] = [1, 2, 6]

    formations = []
    formes = sorted(travailleurs, key=lambda e: embauche[e])
    racines = {1}
    for etudiant_id in formes:
        if etudiant_id in racines:
            continue
        if etudiant_id in (2, 3, 4):
            formateur = etudiant_id - 1
        else:
            anciens = [e for e in formes if embauche[e] + timedelta(days=21) <= embauche[etudiant_id]
                       and reside(e, embauche[etudiant_id])]
            if not anciens:
                racines.add(etudiant_id)
                continue
            formateur = rnd.choice(anciens)
        debut = embauche[etudiant_id]
        if etudiant_id not in (2, 3, 4) and rnd.random() < 0.15:
            # Changement de formateur en cours de formation
            relais = debut + timedelta(days=rnd.randrange(7, 15))
            formations.append([None, formateur, etudiant_id, debut, relais - timedelta(days=1)])
            autres = [e for e in formes if e not in (formateur, etudiant_id) and embauche[e] < debut
                      and reside(e, relais)]
            if autres:
                formateur = rnd.choice(autres)
            debut = relais
        fin = debut + timedelta(days=rnd.randrange(14, 45))
        formations.append([None, formateur, etudiant_id, debut, None if fin >= REFERENCE_DATE else fin])
    for i, f in enumerate(formations, start=1):
        f[0] = i
    total += insert("formation", ["id", "formateur_id", "forme_id", "date_debut", "date_fin"],
                    [tuple(f) for f in formations])

    creneaux = {1: [(time(6, 30), time(9, 30))], 2: [(time(11, 0), time(14, 30))], 3: [(time(18, 0), time(21, 30))],
                4: [(time(17, 30), time(23, 0))]}
    interventions = []
    for _, type_id, _, evenement_id, moment, _ in repas:
        jour = moment.date()
        if jour >= REFERENCE_DATE:
            continue
        disponibles = [e for e in travailleurs if embauche[e] <= jour and reside(e, jour)]
        for tache_id in (1, 2, 3, 4):
            equipe = [e for e in disponibles if tache_id in competences[e]]
            for etudiant_id in rnd.sample(equipe, min(len(equipe), 1 if type_id == 1 else 2 if type_id != 4 else 3)):
                debut, fin = creneaux[type_id][0]
                interventions.append((etudiant_id, tache_id, jour, debut, fin))
        if jour.weekday() in (0, 3):  # livraisons le lundi et le jeudi matin
            equipe = [e for e in disponibles if 5 in competences[e]] or disponibles
            if equipe and type_id == 1:
                interventions.append((rnd.choice(equipe), 5, jour, time(8, 0), time(10, 0)))
    for (_, jour, _, _) in EVENEMENTS:
        if jour < REFERENCE_DATE:
            equipe = [e for e in travailleurs if 6 in competences[e] and reside(e, jour)]
            for etudiant_id in rnd.sample(equipe, min(2, len(equipe))):
                interventions.append((etudiant_id, 6, jour - timedelta(days=1), time(16, 0), time(19, 0)))
    interventions.sort(key=lambda i: (i[2], i[3], i[1], i[0]))
    total += insert("intervention", ["id", "etudiant_id", "tache_id", "jour", "heure_debut", "heure_fin"],
                    [(i + 1, *row) for i, row in enumerate(interventions)])

    # --- Organisation des événements et dépenses ------------------------------------------------------------
    participations, depenses, avances = [], [], []
    for e, (_, jour, _, _) in enumerate(EVENEMENTS, start=1):
        presents = [x for x in residents(jour)]
        organisateurs = rnd.sample(presents, min(len(presents), rnd.randrange(4, 10)))
        if e in (2, 7) and reside(1, jour):  # Alice organise les soirées pizza
            organisateurs = [1] + [o for o in organisateurs if o != 1]
        for rang, etudiant_id in enumerate(organisateurs):
            role = "Responsable de l'organisation" if rang == 0 else rnd.choice(ROLES)
            participations.append((etudiant_id, e, role))
        if jour >= REFERENCE_DATE:
            continue
        for description, minimum, maximum in rnd.sample(DEPENSES_TYPES, rnd.randrange(2, 6)):
            montant = round(rnd.uniform(minimum, maximum), 2)
            depense_id = len(depenses) + 1
            depenses.append((depense_id, e, description, montant, jour - timedelta(days=rnd.randrange(0, 10))))
            tirage = rnd.random()
            if tirage < 0.1:
                continue  # payée directement par le restaurant
            payeurs = rnd.sample(organisateurs, min(len(organisateurs), rnd.randrange(1, 4)))
            couvert = montant if tirage < 0.85 else round(montant * rnd.uniform(0.4, 0.9), 2)
            parts = [rnd.uniform(1, 3) for _ in payeurs]
            restant = couvert
            for i, etudiant_id in enumerate(payeurs):
                part = restant if i == len(payeurs) - 1 else round(couvert * parts[i] / sum(parts), 2)
                restant = round(restant - part, 2)
                avances.append((depense_id, etudiant_id, round(part, 2)))
    total += insert("participation_evenement", ["etudiant_id", "evenement_id", "role"], participations)
    total += insert("depense", ["id", "evenement_id", "description", "montant", "date_depense"], depenses)
    total += insert("avance_depense", ["depense_id", "etudiant_id", "montant"], avances)

    print(f"-- Total : {total} lignes")


if __name__ == "__main__":
    main()
