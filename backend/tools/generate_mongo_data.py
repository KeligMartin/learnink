"""
Génère les jeux de données MongoDB (un fichier JSON par collection) à partir des bases bac à sable SQL.

    python3 tools/generate_mongo_data.py

Les documents suivent un modèle « NoSQL » : les informations lues ensemble sont imbriquées ou recopiées
(référence étendue), les relations volumineuses ou partagées restent des références (*_id) à suivre avec $lookup.
Les dates sont au format Extended JSON ({"$date": ...}), compris par mongoimport, Compass et Atlas.
"""
import json
from collections import defaultdict
from pathlib import Path

from sql_dump import read_tables

ROOT = Path(__file__).resolve().parent.parent / 'src/main/resources'


def date(value):
    """'2025-09-01' ou '2025-09-01 07:30:00' -> date MongoDB (heure telle quelle, en UTC)."""
    if value is None:
        return None
    if len(value) == 10:
        value += ' 00:00:00'
    return {'$date': value.replace(' ', 'T') + 'Z'}


def index(rows, key='id'):
    return {row[key]: row for row in rows}


def group(rows, key):
    out = defaultdict(list)
    for row in rows:
        out[row[key]].append(row)
    return out


def write(dataset, collections):
    folder = ROOT / 'mongo' / dataset
    folder.mkdir(parents=True, exist_ok=True)
    for old in folder.glob('*.json'):
        old.unlink()
    total = 0
    for name, docs in collections.items():
        lines = ',\n'.join(json.dumps(doc, ensure_ascii=False, separators=(', ', ': ')) for doc in docs)
        (folder / f'{name}.json').write_text(f'[\n{lines}\n]\n', encoding='utf-8')
        total += len(docs)
        print(f'  {dataset}/{name}.json : {len(docs)} document(s)')
    print(f'{dataset} : {len(collections)} collections, {total} documents')


# ------------------------------------------------------------------------------------------------- résidence
def residence():
    t = read_tables(ROOT / 'sandbox/residence/data.sql')
    batiments = index(t['batiment'])
    chambres = index(t['chambre'])
    etudiants = index(t['etudiant'])
    type_plat = {r['id']: r['libelle'] for r in t['type_plat']}
    type_repas = {r['id']: r['libelle'] for r in t['type_repas']}
    contraintes = {r['id']: r['libelle'] for r in t['contrainte_alimentaire']}
    ingredients = index(t['ingredient'])
    fournisseurs = index(t['fournisseur'])
    plats = index(t['plat'])
    menus = index(t['menu'])
    taches = {r['id']: r['libelle'] for r in t['tache']}

    def personne(etudiant_id):
        e = etudiants[etudiant_id]
        return {'_id': e['id'], 'prenom': e['prenom'], 'nom': e['nom']}

    c = {}
    c['batiment'] = [{'_id': b['id'], 'nom': b['nom'], 'adresse': b['adresse']} for b in t['batiment']]
    c['chambre'] = [{'_id': ch['id'], 'numero': ch['numero'], 'etage': ch['etage'], 'capacite': ch['capacite'],
                     'batiment': {'_id': ch['batiment_id'], 'nom': batiments[ch['batiment_id']]['nom']}}
                    for ch in t['chambre']]

    contraintes_etudiant = group(t['etudiant_contrainte'], 'etudiant_id')
    c['etudiant'] = [{'_id': e['id'], 'nom': e['nom'], 'prenom': e['prenom'], 'date_naissance': date(e['date_naissance']),
                      'email': e['email'],
                      'contraintes': [contraintes[ec['contrainte_id']] for ec in contraintes_etudiant[e['id']]]}
                     for e in t['etudiant']]
    c['contrainte_alimentaire'] = [{'_id': k, 'libelle': v} for k, v in contraintes.items()]

    c['occupation'] = []
    for o in t['occupation']:
        ch = chambres[o['chambre_id']]
        c['occupation'].append({'_id': o['id'], 'etudiant': personne(o['etudiant_id']),
                                'chambre': {'_id': ch['id'], 'numero': ch['numero'],
                                            'batiment': batiments[ch['batiment_id']]['nom']},
                                'date_debut': date(o['date_debut']), 'date_fin': date(o['date_fin'])})

    ingr_plat = group(t['plat_ingredient'], 'plat_id')
    incompat = group(t['plat_incompatibilite'], 'plat_id')
    c['plat'] = [{'_id': p['id'], 'nom': p['nom'], 'type': type_plat[p['type_plat_id']], 'description': p['description'],
                  'prix_indicatif': p['prix_indicatif'],
                  'ingredients': [{'ingredient_id': pi['ingredient_id'], 'nom': ingredients[pi['ingredient_id']]['nom'],
                                   'quantite': pi['quantite'], 'unite': ingredients[pi['ingredient_id']]['unite']}
                                  for pi in ingr_plat[p['id']]],
                  'incompatibilites': [contraintes[x['contrainte_id']] for x in incompat[p['id']]]}
                 for p in t['plat']]

    offres = group(t['fournisseur_ingredient'], 'ingredient_id')
    c['ingredient'] = [{'_id': i['id'], 'nom': i['nom'], 'unite': i['unite'], 'stock': i['stock'],
                        'stock_minimum': i['stock_minimum'],
                        'fournisseurs': [{'fournisseur_id': f['fournisseur_id'], 'nom': fournisseurs[f['fournisseur_id']]['nom'],
                                          'prix': f['prix'], 'unite_achat': f['unite_achat'], 'date_debut': date(f['date_debut'])}
                                         for f in offres[i['id']]]}
                       for i in t['ingredient']]
    c['fournisseur'] = [{'_id': f['id'], 'nom': f['nom'], 'ville': f['ville'], 'email': f['email'],
                         'telephone': f['telephone']} for f in t['fournisseur']]

    compo = group(t['composition_menu'], 'menu_id')
    c['menu'] = [{'_id': m['id'], 'nom': m['nom'],
                  'plats': [{'plat_id': cm['plat_id'], 'nom': plats[cm['plat_id']]['nom'],
                             'type': type_plat[plats[cm['plat_id']]['type_plat_id']], 'ordre': cm['ordre']}
                            for cm in sorted(compo[m['id']], key=lambda x: x['ordre'])]}
                 for m in t['menu']]

    participants = group(t['participation_evenement'], 'evenement_id')
    depenses = group(t['depense'], 'evenement_id')
    avances = group(t['avance_depense'], 'depense_id')
    c['evenement'] = [{'_id': ev['id'], 'nom': ev['nom'], 'date_evenement': date(ev['date_evenement']), 'lieu': ev['lieu'],
                       'participants': [{**personne(p['etudiant_id']), 'role': p['role']} for p in participants[ev['id']]],
                       'depenses': [{'description': d['description'], 'montant': d['montant'],
                                     'date_depense': date(d['date_depense']),
                                     'avances': [{'etudiant_id': a['etudiant_id'], 'montant': a['montant']}
                                                 for a in avances[d['id']]]}
                                    for d in depenses[ev['id']]]}
                      for ev in t['evenement']]

    # Les repas référencent leur événement (evenement_id) : la relation se suit avec $lookup
    c['repas'] = [{'_id': r['id'], 'type': type_repas[r['type_repas_id']],
                   'menu': {'_id': r['menu_id'], 'nom': menus[r['menu_id']]['nom']},
                   'evenement_id': r['evenement_id'], 'date_heure': date(r['date_heure']), 'places_max': r['places_max']}
                  for r in t['repas']]
    repas = index(t['repas'])
    # Réservation : référence étendue vers l'étudiant et le repas (les champs lus avec la réservation sont recopiés)
    c['reservation'] = [{'_id': r['id'],
                         'etudiant': personne(r['etudiant_id']),
                         'repas': {'_id': r['repas_id'], 'type': type_repas[repas[r['repas_id']]['type_repas_id']],
                                   'date_heure': date(repas[r['repas_id']]['date_heure'])},
                         'date_reservation': date(r['date_reservation']), 'annulee': r['annulee'], 'present': r['present']}
                        for r in t['reservation']]
    c['intervention'] = [{'_id': i['id'], 'etudiant_id': i['etudiant_id'], 'tache': taches[i['tache_id']],
                          'jour': date(i['jour']), 'heure_debut': i['heure_debut'][:5], 'heure_fin': i['heure_fin'][:5]}
                         for i in t['intervention']]
    # Formation : association réflexive conservée sous forme de références vers la collection etudiant
    c['formation'] = [{'_id': f['id'], 'formateur_id': f['formateur_id'], 'forme_id': f['forme_id'],
                       'date_debut': date(f['date_debut']), 'date_fin': date(f['date_fin'])} for f in t['formation']]
    write('residence', c)


# ------------------------------------------------------------------------------------------------- transport
def transport():
    t = read_tables(ROOT / 'sandbox/transport/data.sql')
    adresses = index(t['adresse'])
    clients = index(t['client'])
    agences = index(t['agence'])
    entrepots = index(t['entrepot'])
    services = index(t['service'])
    options = index(t['option_expedition'])
    employes = index(t['employe'])
    statuts = index(t['statut_suivi'])
    specialisations = {r['id']: r['libelle'] for r in t['specialisation']}
    types_vehicule = {r['id']: r['libelle'] for r in t['type_vehicule']}
    trajets = index(t['trajet'])
    expeditions = index(t['expedition'])

    def adresse(adresse_id):
        a = adresses[adresse_id]
        return {'rue': a['rue'], 'code_postal': a['code_postal'], 'ville': a['ville']}

    def agence_ref(agence_id):
        if agence_id is None:
            return None
        a = agences[agence_id]
        return {'_id': a['id'], 'nom': a['nom'], 'ville': a['ville']}

    def personne(employe_id):
        e = employes[employe_id]
        return {'_id': e['id'], 'prenom': e['prenom'], 'nom': e['nom']}

    c = {}
    par_agence = group(t['entrepot'], 'agence_id')
    c['agence'] = [{'_id': a['id'], 'nom': a['nom'], 'ville': a['ville'], 'adresse': a['adresse'], 'telephone': a['telephone'],
                    'entrepots': [{'_id': e['id'], 'nom': e['nom'], 'capacite_max': e['capacite_max']} for e in par_agence[a['id']]]}
                   for a in t['agence']]
    c['adresse'] = [{'_id': a['id'], 'rue': a['rue'], 'code_postal': a['code_postal'], 'ville': a['ville']} for a in t['adresse']]
    c['client'] = [{'_id': cl['id'], 'type_client': cl['type_client'], 'nom': cl['nom'], 'email': cl['email'],
                    'telephone': cl['telephone'], 'adresse': adresse(cl['adresse_principale_id'])} for cl in t['client']]
    c['service'] = [{'_id': s['id'], 'nom': s['nom'], 'description': s['description'], 'conditions': s['conditions'],
                     'prix_base': s['prix_base'], 'prix_kg': s['prix_kg']} for s in t['service']]
    tarifs = group(t['tarif_option'], 'option_id')
    c['option_expedition'] = [{'_id': o['id'], 'nom': o['nom'],
                               'tarifs': [{'prix': x['prix'], 'date_debut': date(x['date_debut']), 'date_fin': date(x['date_fin'])}
                                          for x in tarifs[o['id']]]} for o in t['option_expedition']]

    opts = group(t['expedition_option'], 'expedition_id')
    c['expedition'] = [{'_id': x['id'], 'reference': x['reference'],
                        'expediteur': {'_id': x['expediteur_id'], 'nom': clients[x['expediteur_id']]['nom']},
                        'destinataire': {'_id': x['destinataire_id'], 'nom': clients[x['destinataire_id']]['nom']},
                        'adresse_depart': adresse(x['adresse_depart_id']),
                        'adresse_destination': adresse(x['adresse_destination_id']),
                        'agence_depart': agence_ref(x['agence_depart_id']), 'agence_arrivee': agence_ref(x['agence_arrivee_id']),
                        'service': {'_id': x['service_id'], 'nom': services[x['service_id']]['nom']},
                        'date_creation': date(x['date_creation']), 'date_livraison_prevue': date(x['date_livraison_prevue']),
                        'priorite': x['priorite'], 'prix_total': x['prix_total'],
                        'options': [{'nom': options[o['option_id']]['nom'], 'prix_applique': o['prix_applique']}
                                    for o in opts[x['id']]]}
                       for x in t['expedition']]

    # Colis : l'historique de suivi et les périodes de stockage, propres à chaque colis, y sont imbriqués
    suivi = group(sorted(t['evenement_suivi'], key=lambda e: (e['date_heure'], e['id'])), 'colis_id')
    stockages = group(sorted(t['stockage'], key=lambda s: (s['date_entree'], s['id'])), 'colis_id')
    c['colis'] = []
    for co in t['colis']:
        x = expeditions[co['expedition_id']]
        c['colis'].append({
            '_id': co['id'], 'numero': co['numero'], 'poids_kg': co['poids_kg'], 'longueur_cm': co['longueur_cm'],
            'largeur_cm': co['largeur_cm'], 'hauteur_cm': co['hauteur_cm'], 'fragile': co['fragile'],
            'expedition': {'_id': x['id'], 'reference': x['reference'], 'service': services[x['service_id']]['nom']},
            'suivi': [{'date_heure': date(e['date_heure']), 'statut': statuts[e['statut_id']]['libelle'],
                       'est_anomalie': statuts[e['statut_id']]['est_anomalie'],
                       'agence': agences[e['agence_id']]['nom'] if e['agence_id'] else None,
                       'entrepot': entrepots[e['entrepot_id']]['nom'] if e['entrepot_id'] else None,
                       'commentaire': e['commentaire']} for e in suivi[co['id']]],
            'stockages': [{'entrepot': entrepots[s['entrepot_id']]['nom'],
                           'agence': agences[entrepots[s['entrepot_id']]['agence_id']]['nom'],
                           'date_entree': date(s['date_entree']), 'date_sortie': date(s['date_sortie'])}
                          for s in stockages[co['id']]]})

    specs = group(t['livreur_specialisation'], 'employe_id')
    affect = group(t['affectation_agence'], 'employe_id')
    chefs = group(t['hierarchie'], 'employe_id')
    c['employe'] = [{'_id': e['id'], 'matricule': e['matricule'], 'nom': e['nom'], 'prenom': e['prenom'],
                     'date_embauche': date(e['date_embauche']), 'fonction': e['fonction'], 'statut': e['statut'],
                     'email': e['email'], 'telephone': e['telephone'],
                     'specialisations': [{'libelle': specialisations[s['specialisation_id']],
                                          'date_obtention': date(s['date_obtention'])} for s in specs[e['id']]],
                     'affectations': [{'agence': {'_id': a['agence_id'], 'nom': agences[a['agence_id']]['nom']},
                                       'date_debut': date(a['date_debut']), 'date_fin': date(a['date_fin'])}
                                      for a in affect[e['id']]],
                     'responsables': [{'employe_id': h['responsable_id'], 'date_debut': date(h['date_debut']),
                                       'date_fin': date(h['date_fin'])} for h in chefs[e['id']]]}
                    for e in t['employe']]
    # Formation : association réflexive conservée sous forme de références vers la collection employe
    c['formation'] = [{'_id': f['id'], 'formateur_id': f['formateur_id'], 'forme_id': f['forme_id'],
                       'date_debut': date(f['date_debut']), 'date_fin': date(f['date_fin']), 'resultat': f['resultat']}
                      for f in t['formation']]

    indispo = group(t['indisponibilite_vehicule'], 'vehicule_id')
    affv = group(t['affectation_vehicule'], 'vehicule_id')
    c['vehicule'] = [{'_id': v['id'], 'immatriculation': v['immatriculation'], 'type': types_vehicule[v['type_vehicule_id']],
                      'agence': {'_id': v['agence_id'], 'nom': agences[v['agence_id']]['nom']}, 'capacite_kg': v['capacite_kg'],
                      'indisponibilites': [{'date_debut': date(i['date_debut']), 'date_fin': date(i['date_fin']),
                                            'motif': i['motif']} for i in indispo[v['id']]],
                      'affectations': [{'livreur': personne(a['employe_id']), 'date_debut': date(a['date_debut']),
                                        'date_fin': date(a['date_fin'])} for a in affv[v['id']]]}
                     for v in t['vehicule']]
    c['trajet'] = [{'_id': tr['id'], 'agence_depart': agence_ref(tr['agence_depart_id']),
                    'agence_arrivee': agence_ref(tr['agence_arrivee_id']), 'libelle': tr['libelle'],
                    'distance_km': tr['distance_km'], 'duree_estimee_min': tr['duree_estimee_min'], 'cout': tr['cout']}
                   for tr in t['trajet']]

    op_colis = group(t['operation_colis'], 'operation_id')
    op_perso = group(t['operation_personnel'], 'operation_id')
    c['operation_transport'] = []
    for o in t['operation_transport']:
        tr = trajets[o['trajet_id']]
        c['operation_transport'].append({
            '_id': o['id'],
            'trajet': {'_id': tr['id'], 'libelle': tr['libelle'], 'agence_depart': agences[tr['agence_depart_id']]['nom'],
                       'agence_arrivee': agences[tr['agence_arrivee_id']]['nom']},
            'vehicule_id': o['vehicule_id'], 'date_depart': date(o['date_depart']), 'arrivee_prevue': date(o['arrivee_prevue']),
            'arrivee_reelle': date(o['arrivee_reelle']),
            'equipe': [{**personne(p['employe_id']), 'role': p['role']} for p in op_perso[o['id']]],
            'colis_ids': [oc['colis_id'] for oc in op_colis[o['id']]]})

    arrets = group(sorted(t['arret'], key=lambda a: a['ordre']), 'tournee_id')
    arret_colis = group(t['arret_colis'], 'arret_id')
    c['tournee'] = [{'_id': to['id'], 'agence': {'_id': to['agence_id'], 'nom': agences[to['agence_id']]['nom']},
                     'livreur': personne(to['livreur_id']), 'vehicule_id': to['vehicule_id'],
                     'date_tournee': date(to['date_tournee']), 'heure_depart': (to['heure_depart'] or '')[:5] or None,
                     'heure_fin': (to['heure_fin'] or '')[:5] or None, 'statut': to['statut'], 'commentaire': to['commentaire'],
                     'arrets': [{'ordre': a['ordre'], 'adresse': adresse(a['adresse_id']),
                                 'colis_ids': [ac['colis_id'] for ac in arret_colis[a['id']]]} for a in arrets[to['id']]]}
                    for to in t['tournee']]
    c['tentative_livraison'] = [{'_id': x['id'], 'colis_id': x['colis_id'], 'tournee_id': x['tournee_id'],
                                 'livreur': personne(x['livreur_id']), 'date_heure': date(x['date_heure']),
                                 'resultat': x['resultat'], 'commentaire': x['commentaire'],
                                 'receptionnaire': x['receptionnaire'], 'mode_confirmation': x['mode_confirmation']}
                                for x in t['tentative_livraison']]
    c['incident'] = [{'_id': i['id'], 'type': i['type'], 'date_heure': date(i['date_heure']),
                      'agence': agences[i['agence_id']]['nom'] if i['agence_id'] else None,
                      'operation_id': i['operation_id'], 'description': i['description']} for i in t['incident']]
    interventions = group(t['intervention'], 'reclamation_id')
    liens = group(t['reclamation_incident'], 'reclamation_id')
    c['reclamation'] = [{'_id': r['id'], 'expedition': {'_id': r['expedition_id'],
                                                        'reference': expeditions[r['expedition_id']]['reference']},
                         'type': r['type'], 'date_ouverture': date(r['date_ouverture']), 'description': r['description'],
                         'etat': r['etat'], 'date_cloture': date(r['date_cloture']),
                         'interventions': [{'employe': personne(i['employe_id']), 'date_heure': date(i['date_heure']),
                                            'description': i['description']} for i in interventions[r['id']]],
                         'incident_ids': [l['incident_id'] for l in liens[r['id']]]}
                        for r in t['reclamation']]
    write('transport', c)


if __name__ == '__main__':
    residence()
    transport()
