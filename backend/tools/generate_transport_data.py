#!/usr/bin/env python3
"""Génère le jeu de données « Entreprise de transport de colis » (déterministe).

Usage :
    python3 tools/generate_transport_data.py > src/main/resources/sandbox/transport/data.sql

Les INSERT produits sont compatibles H2 (mode MySQL) et MySQL 8.
Date de référence (« aujourd'hui » pour le jeu de données) : REFERENCE. Les colis encore en route à cette date
n'ont ni livraison ni stockage terminé (date_sortie NULL), les opérations en cours n'ont pas d'arrivée réelle.

Cohérence garantie par construction :
- chaque colis suit le plus court chemin entre son agence de départ et son agence d'arrivée ;
- une opération de transport regroupe tous les colis d'un même trajet du même soir ;
- les événements de suivi, stockages, opérations et tentatives d'un colis s'enchaînent dans le temps ;
- les tournées et leurs arrêts sont reconstruits à partir des tentatives de livraison (un livreur de l'agence,
  avec le véhicule qui lui est affecté ce jour-là, ou un véhicule de réserve s'il est indisponible) ;
- le prix d'une expédition et de ses options est celui en vigueur à sa date de création.
"""
import heapq
import random
import unicodedata
from collections import defaultdict
from datetime import date, datetime, time, timedelta

REFERENCE = datetime(2026, 11, 2, 8, 0)
DEBUT = date(2026, 1, 5)
FIN = date(2026, 10, 31)

# ------------------------------------------------------------------------------------------------------------
# Référentiels
# ------------------------------------------------------------------------------------------------------------

AGENCES = [
    ("Agence Paris Nord", "Paris", "12 rue de la Chapelle", "01 40 12 30 30"),
    ("Agence Paris Sud", "Paris", "85 avenue d'Italie", "01 45 80 21 21"),
    ("Agence Lyon", "Lyon", "40 rue de Gerland", "04 72 10 44 00"),
    ("Agence Marseille", "Marseille", "15 boulevard de Paris", "04 91 22 10 10"),
    ("Agence Lille", "Lille", "8 rue de Tournai", "03 20 15 60 60"),
    ("Agence Bordeaux", "Bordeaux", "120 quai de Bacalan", "05 56 40 70 70"),
    ("Agence Nantes", "Nantes", "3 boulevard de Chantenay", "02 40 30 50 50"),
    ("Agence Rennes", "Rennes", "27 route de Lorient", "02 99 40 80 80"),
    ("Agence Toulouse", "Toulouse", "60 chemin de la Flambère", "05 61 30 90 90"),
]

# ville -> (code postal, agence de rattachement, poids dans la population de clients)
VILLES = {
    "Paris": ("75018", 1, 14), "Saint-Denis": ("93200", 1, 3), "Créteil": ("94000", 2, 3),
    "Boulogne-Billancourt": ("92100", 2, 3), "Lyon": ("69007", 3, 9), "Villeurbanne": ("69100", 3, 3),
    "Marseille": ("13002", 4, 8), "Aix-en-Provence": ("13100", 4, 3), "Lille": ("59000", 5, 6),
    "Roubaix": ("59100", 5, 2), "Bordeaux": ("33000", 6, 6), "Mérignac": ("33700", 6, 2),
    "Nantes": ("44000", 7, 6), "Saint-Herblain": ("44800", 7, 2), "Rennes": ("35000", 8, 5),
    "Cesson-Sévigné": ("35510", 8, 1), "Toulouse": ("31000", 9, 7), "Blagnac": ("31700", 9, 2),
}
# Paris intra-muros : moitié Nord, moitié Sud
PARIS_SUD = ("75013", 2)

# (agence A, agence B, distance km, variante éventuelle en km)
LIAISONS = [
    (1, 2, 25, None), (1, 5, 225, None), (2, 3, 465, 505), (2, 8, 350, None), (2, 7, 385, None),
    (2, 6, 585, 640), (3, 4, 315, 410), (6, 9, 245, None), (9, 4, 405, None), (7, 8, 110, None),
    (7, 6, 345, None),
]

STATUTS = [
    ("Colis pris en charge", False), ("Colis arrivé dans une agence", False), ("Colis en cours de tri", False),
    ("Colis expédié vers une autre agence", False), ("Colis en cours de livraison", False), ("Colis livré", False),
    ("Livraison échouée", True), ("Colis retourné à l'expéditeur", True),
]
PRIS, ARRIVE, TRI, EXPEDIE, EN_LIVRAISON, LIVRE, ECHEC, RETOUR = range(1, 9)

# nom, description, conditions, prix de base, prix au kg, délai (jours ouvrés), poids dans les ventes
SERVICES = [
    ("Livraison standard", "Livraison à domicile en 2 à 4 jours ouvrés", None, 6.90, 0.45, 3, 50),
    ("Livraison express", "Livraison le jour ouvré suivant", "Commande avant 17 h", 14.90, 0.80, 1, 18),
    ("Livraison en point relais", "Retrait en point relais", "Colis de 20 kg maximum", 4.90, 0.35, 4, 17),
    ("Livraison sur rendez-vous", "Livraison sur un créneau choisi par le destinataire", None, 11.90, 0.50, 3, 7),
    ("Livraison de colis volumineux", "Colis lourds ou encombrants, livraison à deux", "Au-delà de 30 kg", 24.90, 0.60, 5, 8),
]
OPTIONS = ["Assurance", "Remise contre signature", "Livraison à horaire précis"]
# option -> [(prix, début, fin)]
TARIFS = {
    1: [(2.50, date(2025, 1, 1), date(2026, 5, 31)), (2.90, date(2026, 6, 1), None)],
    2: [(1.50, date(2025, 1, 1), date(2026, 3, 31)), (1.80, date(2026, 4, 1), None)],
    3: [(4.00, date(2025, 1, 1), None)],
}
SPECIALISATIONS = ["Colis volumineux", "Produits fragiles", "Livraison express", "Livraison sur rendez-vous"]
TYPES_VEHICULE = ["Camion", "Camionnette", "Vélo-cargo", "Véhicule électrique"]

PRENOMS = ["Emma", "Lucas", "Léa", "Hugo", "Chloé", "Louis", "Manon", "Nathan", "Inès", "Jules", "Camille",
           "Arthur", "Sarah", "Adam", "Jade", "Gabriel", "Lina", "Raphaël", "Zoé", "Tom", "Louise", "Enzo",
           "Clara", "Théo", "Anna", "Noah", "Juliette", "Mathis", "Yanis", "Nina", "Ethan", "Paul", "Eva",
           "Rayan", "Yasmine", "Mehdi", "Thomas", "Pauline", "Nicolas", "Laura", "Antoine", "Céline", "Julien"]
NOMS = ["Martin", "Bernard", "Thomas", "Petit", "Robert", "Richard", "Durand", "Leroy", "Simon", "Laurent",
        "Michel", "Garcia", "David", "Bertrand", "Roux", "Vincent", "Fournier", "Girard", "Bonnet", "Dupont",
        "Lambert", "Fontaine", "Rousseau", "Blanc", "Guérin", "Muller", "Henry", "Perrin", "Morin", "Mathieu",
        "Clément", "Gauthier", "Nguyen", "Diallo", "Haddad", "Rossi", "Silva", "Lemoine", "Marchand", "Chevalier"]
PROFESSIONNELS = ["Boulangerie", "Librairie", "Pharmacie", "Atelier", "Fromagerie", "Cave", "Quincaillerie",
                  "Boutique", "Fleuriste", "Imprimerie", "Garage", "Opticien", "Épicerie fine", "Bijouterie"]
RUES = ["rue de la République", "avenue Jean Jaurès", "rue Victor Hugo", "boulevard Gambetta", "rue de la Paix",
        "rue Pasteur", "avenue de la Gare", "rue des Lilas", "place du Marché", "rue Nationale", "rue du Moulin",
        "allée des Tilleuls", "rue Émile Zola", "avenue Foch", "rue Voltaire", "chemin des Vignes"]


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
    return "".join(c for c in unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode().lower()
                   if c.isalnum() or c in ".-")


def jours_ouvres(depart, n):
    """n jours ouvrés (lundi-samedi) après depart."""
    jour = depart
    while n > 0:
        jour += timedelta(days=1)
        if jour.weekday() != 6:
            n -= 1
    return jour


def prochain_jour_ouvre(jour):
    jour += timedelta(days=1)
    while jour.weekday() == 6:
        jour += timedelta(days=1)
    return jour


def main():
    rnd = random.Random(2027)
    total = 0
    print("-- Fichier généré par tools/generate_transport_data.py : ne pas modifier à la main.")
    print(f"-- Date de référence du jeu de données : {REFERENCE.date().isoformat()}\n")

    # --- Adresses et clients --------------------------------------------------------------------------------
    adresses = []

    def nouvelle_adresse(ville):
        cp, agence, _ = VILLES[ville]
        if ville == "Paris" and rnd.random() < 0.5:
            cp, agence = PARIS_SUD
        rue = f"{rnd.randrange(1, 140)} {rnd.choice(RUES)}"
        adresses.append((len(adresses) + 1, rue, cp, ville, agence))
        return len(adresses)

    villes = list(VILLES)
    poids_villes = [VILLES[v][2] for v in villes]
    clients, emails, noms_pros = [], set(), set()
    for i in range(1, 321):
        ville = rnd.choices(villes, weights=poids_villes)[0]
        adresse = nouvelle_adresse(ville)
        if rnd.random() < 0.3:
            type_client = "Professionnel"
            nom = f"{rnd.choice(PROFESSIONNELS)} {rnd.choice(NOMS)}"
            while nom in noms_pros:  # raisons sociales uniques
                nom = f"{rnd.choice(PROFESSIONNELS)} {rnd.choice(NOMS)}"
            noms_pros.add(nom)
            email = f"contact@{slug(nom.replace(' ', '-'))}-{i}.fr"
        else:
            type_client = "Particulier"
            prenom, nom_famille = rnd.choice(PRENOMS), rnd.choice(NOMS)
            nom = f"{prenom} {nom_famille}"
            email = f"{slug(prenom)}.{slug(nom_famille)}{i}@exemple.fr"
        telephone = f"0{rnd.choice([6, 7])} {rnd.randrange(10, 99)} {rnd.randrange(10, 99)} {rnd.randrange(10, 99)} {rnd.randrange(10, 99)}"
        clients.append((i, type_client, nom, email, telephone, adresse))
    # Adresses secondaires (expéditions depuis ou vers une autre adresse que l'adresse principale)
    secondaires = [nouvelle_adresse(rnd.choices(villes, weights=poids_villes)[0]) for _ in range(120)]
    agence_adresse = {a[0]: a[4] for a in adresses}

    # Clients professionnels très actifs (expéditeurs réguliers)
    pros = [c[0] for c in clients if c[1] == "Professionnel"]
    activite = {c[0]: (rnd.choice([1, 1, 2, 3, 8, 14]) if c[1] == "Professionnel" else rnd.choice([1, 1, 1, 2]))
                for c in clients}

    # --- Services, options, tarifs ------------------------------------------------------------------------
    services = [(i + 1, n, d, c, b, k) for i, (n, d, c, b, k, _, _) in enumerate(SERVICES)]
    tarifs = []
    for option_id, periodes in TARIFS.items():
        for prix, debut, fin in periodes:
            tarifs.append((len(tarifs) + 1, option_id, prix, debut, fin))

    def prix_option(option_id, jour):
        for _, oid, prix, debut, fin in tarifs:
            if oid == option_id and debut <= jour and (fin is None or jour <= fin):
                return prix
        raise ValueError((option_id, jour))

    # --- Agences, entrepôts, trajets ----------------------------------------------------------------------
    agences = [(i + 1, n, v, a, t) for i, (n, v, a, t) in enumerate(AGENCES)]
    entrepots, entrepots_agence = [], defaultdict(list)
    for agence_id, nom, ville, _, _ in agences:
        for k in range(1 if agence_id in (2, 8) else 2 if agence_id in (1, 3, 4) else rnd.choice([1, 2])):
            entrepot_id = len(entrepots) + 1
            entrepots.append((entrepot_id, agence_id, f"Entrepôt {nom.replace('Agence ', '')} {k + 1}",
                              rnd.choice([1500, 2500, 4000, 6000])))
            entrepots_agence[agence_id].append(entrepot_id)

    trajets, principal, variantes = [], {}, {}
    for a, b, km, variante in LIAISONS:
        for depart, arrivee in ((a, b), (b, a)):
            for libelle, distance in (("Itinéraire principal", km), ("Itinéraire bis", variante)):
                if distance is None:
                    continue
                trajet_id = len(trajets) + 1
                duree = round(distance / 78 * 60) + 30
                cout = None if rnd.random() < 0.2 else round(distance * rnd.uniform(1.25, 1.45), 2)
                trajets.append((trajet_id, depart, arrivee, libelle, distance, duree, cout))
                (principal if libelle == "Itinéraire principal" else variantes)[(depart, arrivee)] = trajet_id
    trajet_par_id = {t[0]: t for t in trajets}

    graphe = defaultdict(list)
    for (depart, arrivee), trajet_id in principal.items():
        graphe[depart].append((arrivee, trajet_par_id[trajet_id][4]))

    def plus_court_chemin(source, cible):
        distances, precedent, file = {source: 0}, {}, [(0, source)]
        while file:
            d, noeud = heapq.heappop(file)
            if noeud == cible:
                break
            for voisin, km in graphe[noeud]:
                if d + km < distances.get(voisin, 10 ** 9):
                    distances[voisin], precedent[voisin] = d + km, noeud
                    heapq.heappush(file, (d + km, voisin))
        chemin, noeud = [cible], cible
        while noeud != source:
            noeud = precedent[noeud]
            chemin.append(noeud)
        return list(reversed(chemin))

    # --- Employés ---------------------------------------------------------------------------------------------
    employes, affectations, hierarchies, formations = [], [], [], []
    agence_employe, identites = {}, set()

    def embaucher(prenom, nom, fonction, agence_id, embauche, statut=None):
        # Pas d'homonymes parmi les employés : ils apparaissent comme libellés dans les exercices
        while (prenom, nom) in identites:
            prenom, nom = rnd.choice(PRENOMS), rnd.choice(NOMS)
        identites.add((prenom, nom))
        employe_id = len(employes) + 1
        statut = statut or rnd.choices(["CDI", "CDD", "Intérim"], weights=[80, 15, 5])[0]
        employes.append((employe_id, f"TC{employe_id:04d}", nom, prenom, embauche, fonction, statut,
                         f"{slug(prenom)}.{slug(nom)}@transcolis.fr",
                         f"06 {rnd.randrange(10, 99)} {rnd.randrange(10, 99)} {rnd.randrange(10, 99)} {rnd.randrange(10, 99)}"))
        agence_employe[employe_id] = agence_id
        return employe_id

    def date_embauche(min_annee=2012):
        return date(min_annee, 1, 1) + timedelta(days=rnd.randrange(0, (date(2026, 6, 30) - date(min_annee, 1, 1)).days))

    direction = embaucher("Claire", "Fontaine", "Direction", 1, date(2010, 9, 1), "CDI")
    responsables = {a[0]: embaucher(rnd.choice(PRENOMS), rnd.choice(NOMS), "Responsable d'agence", a[0],
                                    date_embauche(2012), "CDI") for a in agences}
    livreurs_agence = defaultdict(list)
    # La chaîne de formation de l'énoncé, à l'agence Paris Nord
    chaine = [("Marc", "Dupuis", date(2019, 3, 4)), ("Sophie", "Laurent", date(2021, 5, 10)),
              ("Karim", "Benali", date(2023, 2, 6)), ("Julie", "Morel", date(2025, 9, 1))]
    for prenom, nom, embauche in chaine:
        livreurs_agence[1].append(embaucher(prenom, nom, "Livreur", 1, embauche, "CDI"))
    for agence_id, *_ in agences:
        for _ in range(5 if agence_id != 1 else 2):
            livreurs_agence[agence_id].append(
                embaucher(rnd.choice(PRENOMS), rnd.choice(NOMS), "Livreur", agence_id, date_embauche(2014)))
        for _ in range(2):
            embaucher(rnd.choice(PRENOMS), rnd.choice(NOMS), "Agent de tri", agence_id, date_embauche(2014))
    for agence_id in (2, 3, 5, 6):
        embaucher(rnd.choice(PRENOMS), rnd.choice(NOMS), "Technicien", agence_id, date_embauche(2014))
    service_client = [embaucher(rnd.choice(PRENOMS), rnd.choice(NOMS), "Agent service client", a, date_embauche(2016))
                      for a in (1, 1, 1, 3, 3, 3)]

    # Affectations d'agence (certains employés ont été mutés) et hiérarchie (le responsable change avec l'agence)
    for employe_id, _, _, _, embauche, fonction, *_ in employes:
        agence_actuelle = agence_employe[employe_id]
        mute = fonction in ("Livreur", "Agent de tri", "Agent service client") and employe_id > 4 \
            and embauche < date(2024, 1, 1) and rnd.random() < 0.12
        debut = embauche
        if mute:
            ancienne = rnd.choice([a[0] for a in agences if a[0] != agence_actuelle])
            mutation = embauche + timedelta(days=rnd.randrange(200, (date(2026, 6, 1) - embauche).days))
            affectations.append([None, employe_id, ancienne, embauche, mutation - timedelta(days=1)])
            if fonction != "Direction":
                hierarchies.append([None, employe_id, responsables[ancienne], embauche, mutation - timedelta(days=1)])
            debut = mutation
        affectations.append([None, employe_id, agence_actuelle, debut, None])
        if fonction == "Direction":
            continue
        chef = direction if fonction == "Responsable d'agence" else responsables[agence_actuelle]
        hierarchies.append([None, employe_id, chef, debut, None])
    for liste in (affectations, hierarchies):
        liste.sort(key=lambda r: (r[3], r[1]))
        for i, row in enumerate(liste, start=1):
            row[0] = i

    def agence_le(employe_id, jour):
        for _, eid, agence_id, debut, fin in affectations:
            if eid == employe_id and debut <= jour and (fin is None or jour <= fin):
                return agence_id
        return None

    # Formation : la chaîne Marc -> Sophie -> Karim -> Julie, puis chaque livreur récent par un plus ancien
    embauche_de = {e[0]: e[4] for e in employes}
    marc, sophie, karim, julie = livreurs_agence[1][:4]
    for formateur, forme in ((marc, sophie), (sophie, karim), (karim, julie)):
        debut = embauche_de[forme]
        fin = debut + timedelta(days=45)
        formations.append([None, formateur, forme, debut, fin, "Validée"])
    tous_livreurs = [e for e in employes if e[5] == "Livreur"]
    for livreur in tous_livreurs:
        if livreur[0] in (marc, sophie, karim, julie) or livreur[4] < date(2018, 1, 1):
            continue
        agence_id = agence_employe[livreur[0]]
        anciens = [l for l in livreurs_agence[agence_id] if embauche_de[l] + timedelta(days=365) <= livreur[4]]
        if not anciens:
            continue
        debut = livreur[4]
        formateur = rnd.choice(anciens)
        if rnd.random() < 0.12:
            # Formation non validée, reprise avec un autre formateur
            formations.append([None, formateur, livreur[0], debut, debut + timedelta(days=30), "Non validée"])
            autres = [l for l in anciens if l != formateur] or anciens
            formateur, debut = rnd.choice(autres), debut + timedelta(days=31)
        fin = debut + timedelta(days=rnd.randrange(30, 60))
        formations.append([None, formateur, livreur[0], debut, fin if fin < REFERENCE.date() else None,
                           "Validée" if fin < REFERENCE.date() else "En cours"])
    formations.sort(key=lambda r: (r[3], r[2]))
    for i, row in enumerate(formations, start=1):
        row[0] = i

    specialites = []
    for livreur in tous_livreurs:
        for specialisation_id in rnd.sample(range(1, 5), rnd.choice([0, 1, 1, 2])):
            obtention = livreur[4] + timedelta(days=rnd.randrange(60, 900))
            specialites.append((livreur[0], specialisation_id, min(obtention, date(2026, 9, 30))))

    # --- Véhicules, affectations, indisponibilités --------------------------------------------------------
    vehicules, plaques = [], set()

    def plaque():
        lettres = "ABCDEFGHJKLMNPQRSTVWXYZ"
        while True:
            p = f"{rnd.choice(lettres)}{rnd.choice(lettres)}-{rnd.randrange(100, 999)}-{rnd.choice(lettres)}{rnd.choice(lettres)}"
            if p not in plaques:
                plaques.add(p)
                return p

    camions, livraison_vehicules = defaultdict(list), defaultdict(list)
    for agence_id, *_ in agences:
        for _ in range(2 if agence_id in (1, 2, 3) else 1):
            vehicule_id = len(vehicules) + 1
            vehicules.append((vehicule_id, plaque(), 1, agence_id, rnd.choice([3500, 7500, 12000])))
            camions[agence_id].append(vehicule_id)
        for _ in range(len(livreurs_agence[agence_id]) + 1):
            type_id = rnd.choices([2, 3, 4], weights=[60, 15 if agence_id in (1, 3, 5, 7) else 0, 25])[0]
            capacite = {2: rnd.choice([800, 1000, 1200, 1500]), 3: rnd.choice([100, 150, 200]),
                        4: rnd.choice([500, 600, 900])}[type_id]
            vehicule_id = len(vehicules) + 1
            vehicules.append((vehicule_id, plaque(), type_id, agence_id, capacite))
            livraison_vehicules[agence_id].append(vehicule_id)

    indisponibilites = []
    for _ in range(40):
        vehicule_id = rnd.randrange(1, len(vehicules) + 1)
        debut = DEBUT + timedelta(days=rnd.randrange(0, (FIN - DEBUT).days))
        motif = rnd.choices(["Panne", "Maintenance"], weights=[40, 60])[0]
        indisponibilites.append((len(indisponibilites) + 1, vehicule_id, debut,
                                 debut + timedelta(days=rnd.randrange(1, 10)), motif))
    indispo_par_vehicule = defaultdict(list)
    for _, vehicule_id, debut, fin, _ in indisponibilites:
        indispo_par_vehicule[vehicule_id].append((debut, fin))

    def disponible(vehicule_id, jour):
        return all(not (d <= jour <= f) for d, f in indispo_par_vehicule[vehicule_id])

    # Rotation trimestrielle des véhicules de livraison entre les livreurs de chaque agence
    periodes = [(date(2026, 1, 1), date(2026, 3, 31)), (date(2026, 4, 1), date(2026, 6, 30)),
                (date(2026, 7, 1), date(2026, 9, 30)), (date(2026, 10, 1), None)]
    affectations_vehicule, vehicule_de = [], {}
    for agence_id, *_ in agences:
        for debut, fin in periodes:
            livreurs = [l for l in livreurs_agence[agence_id] if embauche_de[l] <= (fin or REFERENCE.date())]
            parc = livraison_vehicules[agence_id][:]
            rnd.shuffle(parc)
            for livreur, vehicule_id in zip(livreurs, parc):
                debut_affectation = max(debut, embauche_de[livreur])
                affectations_vehicule.append((len(affectations_vehicule) + 1, vehicule_id, livreur,
                                              debut_affectation, fin))
                vehicule_de[(livreur, debut)] = vehicule_id

    def vehicule_affecte(livreur, jour):
        for debut, fin in periodes:
            if debut <= jour and (fin is None or jour <= fin):
                return vehicule_de.get((livreur, debut))
        return None

    # --- Expéditions et parcours des colis ----------------------------------------------------------------
    expeditions, expedition_options, colis_rows = [], [], []
    evenements = []          # [colis, statut, datetime, agence, entrepot, commentaire]
    stockages = []           # [colis, entrepot, entree, sortie]
    operations = {}          # (trajet, jour) -> [id, trajet, vehicule, depart, prevue, reelle]
    operation_colis = []
    a_livrer = defaultdict(list)   # (agence, jour) -> [(colis, adresse, resultat, essai, expedition)]
    perdus, colis_info = [], {}

    def operation(trajet_id, jour):
        cle = (trajet_id, jour)
        if cle not in operations:
            _, depart_agence, _, _, _, duree, _ = trajet_par_id[trajet_id]
            depart = datetime.combine(jour, time(20, 0)) + timedelta(minutes=rnd.randrange(0, 40))
            prevue = depart + timedelta(minutes=duree)
            tirage = rnd.random()
            retard = rnd.randrange(-30, 6) if tirage < 0.75 else rnd.randrange(30, 180) if tirage < 0.95 \
                else rnd.randrange(240, 600)
            reelle = prevue + timedelta(minutes=retard)
            parc = [v for v in camions[depart_agence] if disponible(v, jour)] or livraison_vehicules[depart_agence]
            operations[cle] = [len(operations) + 1, trajet_id, rnd.choice(parc), depart, prevue,
                               reelle if reelle < REFERENCE else None]
        return operations[cle]

    def stocker(colis_id, agence_id, entree):
        stockage = [colis_id, rnd.choice(entrepots_agence[agence_id]), entree, None]
        stockages.append(stockage)
        return stockage

    jours = []
    jour = DEBUT
    while jour <= FIN:
        if jour.weekday() != 6:
            jours.append(jour)
        jour += timedelta(days=1)

    expediteurs = [c[0] for c in clients]
    poids_expediteurs = [activite[c] for c in expediteurs]
    service_poids = [s[6] for s in SERVICES]
    numero_expedition = 0
    for jour in jours:
        saison = 1.3 if jour.month in (3, 9, 10) else 0.85 if jour.month == 8 else 1.0
        for _ in range(round(rnd.uniform(5, 11) * saison)):
            numero_expedition += 1
            expedition_id = numero_expedition
            special = expedition_id == 42
            expediteur = rnd.choices(expediteurs, weights=poids_expediteurs)[0]
            destinataire = rnd.choice([c for c in expediteurs if c != expediteur])
            if special:
                # Expédition de démonstration : Lille -> Marseille, 3 colis, une première tentative manquée
                expediteur = next(c[0] for c in clients if adresses[c[5] - 1][3] == "Lille")
                destinataire = next(c[0] for c in clients if adresses[c[5] - 1][3] == "Marseille")
            adresse_depart = clients[expediteur - 1][5] if rnd.random() < 0.9 or special else rnd.choice(secondaires)
            adresse_destination = clients[destinataire - 1][5] if rnd.random() < 0.85 or special \
                else rnd.choice(secondaires)
            agence_depart, agence_arrivee = agence_adresse[adresse_depart], agence_adresse[adresse_destination]
            service_id = 1 if special else rnd.choices(range(1, 6), weights=service_poids)[0]
            priorite = rnd.choices(["Normale", "Haute", "Urgente"], weights=[75, 18, 7])[0]
            if priorite == "Urgente" and rnd.random() < 0.7:
                service_id = 2
            creation = datetime.combine(jour, time(rnd.randrange(8, 18), rnd.randrange(0, 60)))
            prevue = jours_ouvres(jour, SERVICES[service_id - 1][5])
            nb_colis = 3 if special else rnd.choices([1, 2, 3, 4], weights=[70, 20, 7, 3])[0]
            poids_total, colis_ids = 0.0, []
            for numero in range(1, nb_colis + 1):
                colis_id = len(colis_rows) + 1
                if service_id == 5:
                    poids = round(rnd.uniform(30, 70), 2)
                else:
                    poids = round(min(rnd.lognormvariate(1.2, 0.9), 30.0) + 0.2, 2)
                cote = max(15, round(poids ** 0.33 * 22))
                colis_rows.append((colis_id, expedition_id, numero, poids, cote + rnd.randrange(0, 30),
                                   cote, max(5, cote - rnd.randrange(0, 15)), rnd.random() < 0.12))
                poids_total += poids
                colis_ids.append(colis_id)
            options_choisies = [o for o, p in ((1, 0.2), (2, 0.15), (3, 0.3 if service_id == 4 else 0.04))
                                if rnd.random() < p]
            prix = SERVICES[service_id - 1][3] + SERVICES[service_id - 1][4] * poids_total
            for option_id in options_choisies:
                applique = prix_option(option_id, jour)
                expedition_options.append((expedition_id, option_id, applique))
                prix += applique
            expeditions.append((expedition_id, f"EXP-2026-{expedition_id:05d}", expediteur, destinataire,
                                adresse_depart, adresse_destination, agence_depart, agence_arrivee, service_id,
                                creation, prevue, priorite, round(prix, 2)))

            chemin = plus_court_chemin(agence_depart, agence_arrivee)
            perdu = not special and rnd.random() < 0.004
            for colis_id in colis_ids:
                colis_info[colis_id] = (expedition_id, adresse_destination, prevue)
                t = creation + timedelta(hours=rnd.randrange(1, 4))
                if t >= REFERENCE:
                    continue
                evenements.append([colis_id, PRIS, t, agence_depart, None, None])
                stockage = stocker(colis_id, agence_depart, t)
                t += timedelta(minutes=rnd.randrange(30, 180))
                if t >= REFERENCE:
                    continue
                evenements.append([colis_id, TRI, t, agence_depart, stockage[1], None])
                en_route = True
                for a, b in zip(chemin, chemin[1:]):
                    trajet_id = principal[(a, b)]
                    if (a, b) in variantes and rnd.random() < 0.15:
                        trajet_id = variantes[(a, b)]
                    jour_depart = t.date() if t.time() < time(19, 0) else t.date() + timedelta(days=1)
                    op = operation(trajet_id, jour_depart)
                    if op[3] >= REFERENCE:
                        en_route = False
                        break
                    operation_colis.append((op[0], colis_id))
                    evenements.append([colis_id, EXPEDIE, op[3], a, None, None])
                    stockage[3] = op[3]
                    if perdu:
                        perdus.append((colis_id, op[3]))
                        en_route = False
                        break
                    if op[5] is None:
                        en_route = False
                        break
                    t = op[5]
                    stockage = stocker(colis_id, b, t)
                    evenements.append([colis_id, ARRIVE, t, b, stockage[1], None])
                    t += timedelta(minutes=rnd.randrange(30, 120))
                    if t >= REFERENCE:
                        en_route = False
                        break
                    evenements.append([colis_id, TRI, t, b, stockage[1], None])
                if not en_route:
                    continue
                jour_livraison = t.date() if t.time() < time(7, 0) else prochain_jour_ouvre(t.date())
                if jour_livraison.weekday() == 6:
                    jour_livraison = prochain_jour_ouvre(jour_livraison)
                # Résultats des tentatives successives
                essai, resultats = 1, []
                while True:
                    if special:
                        resultat = "Destinataire absent" if essai == 1 else "Livré"
                    else:
                        resultat = rnd.choices(["Livré", "Destinataire absent", "Adresse incorrecte", "Refusé"],
                                               weights=[88, 8, 2, 2])[0]
                    resultats.append(resultat)
                    if resultat == "Livré" or resultat == "Refusé" or essai == 3 \
                            or (resultat == "Adresse incorrecte" and essai == 2):
                        break
                    essai += 1
                colis_info[colis_id] = (expedition_id, adresse_destination, prevue, agence_arrivee,
                                        jour_livraison, resultats, stockage)

    # --- Tournées : regroupement des tentatives par agence et par jour -------------------------------------
    tournees, arrets, arrets_colis, tentatives = [], [], [], []
    a_planifier = defaultdict(list)
    for colis_id, info in colis_info.items():
        if len(info) < 7:
            continue
        expedition_id, adresse, prevue, agence_id, jour_livraison, resultats, stockage = info
        a_planifier[(agence_id, jour_livraison)].append((colis_id, adresse, resultats, 0, stockage))

    livre_le = {}
    while a_planifier:
        cle = min(a_planifier)
        lot = a_planifier.pop(cle)
        agence_id, jour = cle
        if datetime.combine(jour, time(8, 0)) >= REFERENCE:
            continue
        livreurs = [l for l in livreurs_agence[agence_id] if embauche_de[l] <= jour and agence_le(l, jour) == agence_id] \
            or livreurs_agence[agence_id][:]
        rnd.shuffle(livreurs)
        rnd.shuffle(lot)
        groupes = [lot[i:i + 14] for i in range(0, len(lot), 14)]
        for numero_groupe, groupe in enumerate(groupes):
            livreur = livreurs[numero_groupe % len(livreurs)]
            vehicule_id = vehicule_affecte(livreur, jour)
            if vehicule_id is None or not disponible(vehicule_id, jour):
                reserve = [v for v in livraison_vehicules[agence_id] if disponible(v, jour)]
                vehicule_id = reserve[0] if reserve else livraison_vehicules[agence_id][0]
            tournee_id = len(tournees) + 1
            depart = datetime.combine(jour, time(8, 0)) + timedelta(minutes=rnd.randrange(0, 75))
            par_adresse = defaultdict(list)
            for element in groupe:
                par_adresse[element[1]].append(element)
            ordre_adresses = list(par_adresse)
            rnd.shuffle(ordre_adresses)
            interrompue = len(ordre_adresses) > 3 and rnd.random() < 0.03
            t = depart
            for ordre, adresse in enumerate(ordre_adresses, start=1):
                arret_id = len(arrets) + 1
                arrets.append((arret_id, tournee_id, ordre, adresse))
                t += timedelta(minutes=rnd.randrange(10, 30))
                for colis_id, _, resultats, essai, stockage in par_adresse[adresse]:
                    arrets_colis.append((arret_id, colis_id))
                    evenements.append([colis_id, EN_LIVRAISON, depart, agence_id, None, None])
                    stockage[3] = depart
                    resultat = resultats[essai]
                    if resultat == "Livré":
                        receptionnaire = rnd.choice(["Destinataire", "Destinataire", "Destinataire", "Voisin",
                                                     "Gardien", "Collègue"])
                        mode = rnd.choice(["Signature", "Signature", "Code de retrait", "Photo"])
                        tentatives.append([colis_id, tournee_id, livreur, t, resultat, None, receptionnaire, mode])
                        evenements.append([colis_id, LIVRE, t, None, None, None])
                        livre_le[colis_id] = t
                        continue
                    commentaire = {"Destinataire absent": "Destinataire absent, avis de passage déposé",
                                   "Adresse incorrecte": "Adresse introuvable ou incomplète",
                                   "Refusé": "Colis refusé par le destinataire"}[resultat]
                    tentatives.append([colis_id, tournee_id, livreur, t, resultat, commentaire, None, None])
                    retour_agence = t + timedelta(hours=3)
                    evenements.append([colis_id, ECHEC, t, agence_id, None, commentaire])
                    if retour_agence >= REFERENCE:
                        continue
                    nouveau_stockage = stocker(colis_id, agence_id, retour_agence)
                    if essai + 1 < len(resultats):
                        suivant = prochain_jour_ouvre(jour)
                        a_planifier[(agence_id, suivant)].append(
                            (colis_id, adresse, resultats, essai + 1, nouveau_stockage))
                    else:
                        retour = datetime.combine(prochain_jour_ouvre(jour), time(10, 0))
                        if retour < REFERENCE:
                            evenements.append([colis_id, RETOUR, retour, agence_id, None,
                                               "Retour à l'expéditeur après " + str(len(resultats)) + " tentative(s)"])
                            nouveau_stockage[3] = retour
            fin = t + timedelta(minutes=30)
            if interrompue:
                tournees.append((tournee_id, agence_id, livreur, vehicule_id, jour, depart.time(), t.time(),
                                 "Interrompue", rnd.choice(["Panne du véhicule", "Intempéries", "Accident sans gravité"])))
            else:
                tournees.append((tournee_id, agence_id, livreur, vehicule_id, jour, depart.time(), fin.time(),
                                 "Terminée", None))

    # --- Incidents et réclamations -------------------------------------------------------------------------
    incidents, reclamations, interventions, liens = [], [], [], []
    incident_operation = {}
    for op in operations.values():
        operation_id, trajet_id, vehicule_id, depart, prevue, reelle = op
        if reelle is not None and (reelle - prevue) > timedelta(minutes=150):
            type_incident = "Panne de véhicule" if (reelle - prevue) > timedelta(minutes=300) else "Retard de transport"
            incidents.append([len(incidents) + 1, type_incident, prevue, trajet_par_id[trajet_id][1], operation_id,
                              f"Arrivée avec {int((reelle - prevue).total_seconds() // 60)} min de retard"])
            incident_operation[operation_id] = len(incidents)
    colis_par_operation = defaultdict(list)
    for operation_id, colis_id in operation_colis:
        colis_par_operation[colis_id].append(operation_id)
    endommages = []
    for colis_id, moment in livre_le.items():
        if rnd.random() < 0.01:
            agence_id = colis_info[colis_id][3]
            incidents.append([len(incidents) + 1, "Colis endommagé", moment, agence_id, None,
                              "Emballage abîmé constaté à la livraison"])
            endommages.append((colis_id, len(incidents), moment))
    erreurs_tri = []
    for _ in range(12):
        moment = datetime.combine(DEBUT + timedelta(days=rnd.randrange(0, 280)), time(rnd.randrange(6, 20), 0))
        incidents.append([len(incidents) + 1, "Erreur de tri", moment, rnd.randrange(1, len(agences) + 1), None,
                          "Colis orienté vers une mauvaise agence puis réacheminé"])
        erreurs_tri.append((len(incidents), moment))

    def ouvrir(expedition_id, type_reclamation, ouverture, description, incidents_lies):
        if ouverture >= REFERENCE:
            return
        reclamation_id = len(reclamations) + 1
        cloture = None
        if ouverture < REFERENCE - timedelta(days=20) and rnd.random() < 0.9:
            cloture = ouverture + timedelta(days=rnd.randrange(2, 18), hours=rnd.randrange(0, 8))
            etat = "Clôturée"
        else:
            etat = rnd.choice(["Ouverte", "En cours"])
        reclamations.append((reclamation_id, expedition_id, type_reclamation, ouverture, description, etat, cloture))
        fin = cloture or REFERENCE
        for k in range(rnd.randrange(1, 5)):
            moment = ouverture + (fin - ouverture) * (k + 1) / 5
            interventions.append((len(interventions) + 1, reclamation_id, rnd.choice(service_client),
                                  moment.replace(microsecond=0),
                                  ["Prise en compte de la réclamation", "Demande d'informations au client",
                                   "Enquête auprès de l'agence", "Proposition de geste commercial",
                                   "Réponse définitive au client"][min(k, 4)]))
        for incident_id in sorted(set(incidents_lies)):
            liens.append((reclamation_id, incident_id))

    expedition_de = {c[0]: c[1] for c in colis_rows}
    deja = set()
    for colis_id, moment in perdus:
        expedition_id = expedition_de[colis_id]
        if expedition_id not in deja:
            deja.add(expedition_id)
            ouvrir(expedition_id, "Colis perdu", moment + timedelta(days=rnd.randrange(6, 12)),
                   "Colis toujours pas reçu, suivi bloqué", [])
    for colis_id, incident_id, moment in endommages:
        if rnd.random() < 0.7:
            ouvrir(expedition_de[colis_id], "Colis endommagé", moment + timedelta(days=1),
                   "Contenu abîmé à la réception", [incident_id])
    for colis_id, moment in livre_le.items():
        expedition_id = expedition_de[colis_id]
        prevue = colis_info[colis_id][2]
        if expedition_id in deja:
            continue
        if moment.date() >= prevue + timedelta(days=2) and rnd.random() < 0.3:
            deja.add(expedition_id)
            lies = [incident_operation[o] for o in colis_par_operation[colis_id] if o in incident_operation]
            ouvrir(expedition_id, "Retard de livraison", moment + timedelta(days=rnd.randrange(0, 3)),
                   "Colis livré après la date prévue", lies)
        elif rnd.random() < 0.003:
            deja.add(expedition_id)
            ouvrir(expedition_id, "Livraison déclarée effectuée mais colis non reçu",
                   moment + timedelta(days=1), "Le destinataire indique ne pas avoir reçu le colis", [])
    for incident_id, moment in erreurs_tri:
        candidates = [e for e in expeditions if abs((e[9] - moment).days) <= 3 and e[0] not in deja]
        for expedition in rnd.sample(candidates, min(len(candidates), rnd.choice([1, 2, 3]))):
            deja.add(expedition[0])
            ouvrir(expedition[0], "Erreur de destination", moment + timedelta(days=2),
                   "Colis arrivé dans une mauvaise ville", [incident_id])

    # --- Écriture -------------------------------------------------------------------------------------------
    total += insert("adresse", ["id", "rue", "code_postal", "ville"], [a[:4] for a in adresses])
    total += insert("client", ["id", "type_client", "nom", "email", "telephone", "adresse_principale_id"], clients)
    total += insert("service", ["id", "nom", "description", "conditions", "prix_base", "prix_kg"], services)
    total += insert("option_expedition", ["id", "nom"], [(i + 1, o) for i, o in enumerate(OPTIONS)])
    total += insert("tarif_option", ["id", "option_id", "prix", "date_debut", "date_fin"], tarifs)
    total += insert("agence", ["id", "nom", "ville", "adresse", "telephone"], agences)
    total += insert("entrepot", ["id", "agence_id", "nom", "capacite_max"], entrepots)
    total += insert("trajet", ["id", "agence_depart_id", "agence_arrivee_id", "libelle", "distance_km",
                               "duree_estimee_min", "cout"], trajets)
    total += insert("expedition", ["id", "reference", "expediteur_id", "destinataire_id", "adresse_depart_id",
                                   "adresse_destination_id", "agence_depart_id", "agence_arrivee_id", "service_id",
                                   "date_creation", "date_livraison_prevue", "priorite", "prix_total"], expeditions)
    total += insert("expedition_option", ["expedition_id", "option_id", "prix_applique"], expedition_options)
    total += insert("colis", ["id", "expedition_id", "numero", "poids_kg", "longueur_cm", "largeur_cm",
                              "hauteur_cm", "fragile"], colis_rows)
    total += insert("employe", ["id", "matricule", "nom", "prenom", "date_embauche", "fonction", "statut", "email",
                                "telephone"], employes)
    total += insert("hierarchie", ["id", "employe_id", "responsable_id", "date_debut", "date_fin"],
                    [tuple(r) for r in hierarchies])
    total += insert("affectation_agence", ["id", "employe_id", "agence_id", "date_debut", "date_fin"],
                    [tuple(r) for r in affectations])
    total += insert("formation", ["id", "formateur_id", "forme_id", "date_debut", "date_fin", "resultat"],
                    [tuple(r) for r in formations])
    total += insert("specialisation", ["id", "libelle"], [(i + 1, s) for i, s in enumerate(SPECIALISATIONS)])
    total += insert("livreur_specialisation", ["employe_id", "specialisation_id", "date_obtention"], specialites)
    total += insert("type_vehicule", ["id", "libelle"], [(i + 1, t) for i, t in enumerate(TYPES_VEHICULE)])
    total += insert("vehicule", ["id", "immatriculation", "type_vehicule_id", "agence_id", "capacite_kg"], vehicules)
    total += insert("affectation_vehicule", ["id", "vehicule_id", "employe_id", "date_debut", "date_fin"],
                    affectations_vehicule)
    total += insert("indisponibilite_vehicule", ["id", "vehicule_id", "date_debut", "date_fin", "motif"],
                    indisponibilites)
    total += insert("statut_suivi", ["id", "libelle", "est_anomalie"],
                    [(i + 1, l, a) for i, (l, a) in enumerate(STATUTS)])
    evenements.sort(key=lambda e: (e[2], e[0], e[1]))
    total += insert("evenement_suivi", ["id", "colis_id", "statut_id", "date_heure", "agence_id", "entrepot_id",
                                        "commentaire"], [(i + 1, *e) for i, e in enumerate(evenements)])
    stockages.sort(key=lambda s: (s[2], s[0]))
    total += insert("stockage", ["id", "colis_id", "entrepot_id", "date_entree", "date_sortie"],
                    [(i + 1, *s) for i, s in enumerate(stockages)])
    # Seules les opérations parties avant la date de référence (elles transportent toutes au moins un colis)
    utilisees = sorted(tuple(o) for o in operations.values() if o[3] < REFERENCE)
    total += insert("operation_transport", ["id", "trajet_id", "vehicule_id", "date_depart", "arrivee_prevue",
                                            "arrivee_reelle"], utilisees)
    total += insert("operation_colis", ["operation_id", "colis_id"], sorted(set(operation_colis)))
    personnel = []
    for op in utilisees:
        agence_id = trajet_par_id[op[1]][1]
        equipe = [e[0] for e in employes if e[5] in ("Livreur", "Agent de tri")
                  and agence_le(e[0], op[3].date()) == agence_id and e[4] <= op[3].date()]
        choisis = rnd.sample(equipe, min(len(equipe), 2 if rnd.random() < 0.5 else 1))
        for k, employe_id in enumerate(choisis):
            personnel.append((op[0], employe_id, "Chauffeur" if k == 0 else "Convoyeur"))
    total += insert("operation_personnel", ["operation_id", "employe_id", "role"], personnel)
    total += insert("tournee", ["id", "agence_id", "livreur_id", "vehicule_id", "date_tournee", "heure_depart",
                                "heure_fin", "statut", "commentaire"], tournees)
    total += insert("arret", ["id", "tournee_id", "ordre", "adresse_id"], arrets)
    total += insert("arret_colis", ["arret_id", "colis_id"], arrets_colis)
    tentatives.sort(key=lambda t: (t[3], t[0]))
    total += insert("tentative_livraison", ["id", "colis_id", "tournee_id", "livreur_id", "date_heure", "resultat",
                                            "commentaire", "receptionnaire", "mode_confirmation"],
                    [(i + 1, *t) for i, t in enumerate(tentatives)])
    total += insert("incident", ["id", "type", "date_heure", "agence_id", "operation_id", "description"],
                    [tuple(i) for i in incidents])
    total += insert("reclamation", ["id", "expedition_id", "type", "date_ouverture", "description", "etat",
                                    "date_cloture"], reclamations)
    total += insert("intervention", ["id", "reclamation_id", "employe_id", "date_heure", "description"],
                    interventions)
    total += insert("reclamation_incident", ["reclamation_id", "incident_id"], liens)
    print(f"-- Total : {total} lignes")


if __name__ == "__main__":
    main()
