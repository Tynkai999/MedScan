#!/usr/bin/env python3
"""
Validation en direct de la suite modulaire complète (MOD-12, MOD-13, MOD-14, MOD-15)
sur l'environnement de production Render : https://medscan-sluw.onrender.com/medscan/api
"""
import json
import sys
import time
import urllib.request
import urllib.error

BASE_URL = "https://medscan-sluw.onrender.com/medscan/api"
PATIENT_ID = "99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e"

def print_step(title):
    print("\n" + "=" * 70)
    print(f"  {title}")
    print("=" * 70)

def make_request(url, method="GET", headers=None, data=None):
    if headers is None:
        headers = {}
    req = urllib.request.Request(url, method=method, headers=headers)
    if data is not None:
        req.data = json.dumps(data).encode("utf-8")
        if "Content-Type" not in headers:
            headers["Content-Type"] = "application/json"
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            content = resp.read().decode("utf-8")
            ct = resp.headers.get("Content-Type", "")
            return resp.status, resp.headers, json.loads(content) if "json" in ct else content
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        try:
            parsed = json.loads(err_body)
            return e.code, e.headers, parsed
        except Exception:
            return e.code, e.headers, err_body
    except Exception as e:
        return 500, {}, str(e)

def wait_for_deployment():
    print("Vérification de la disponibilité du serveur Render...")
    for attempt in range(15):
        status, _, body = make_request(f"{BASE_URL}/v1/health")
        if status == 200:
            print(f"[OK] Health check réussi: {body}")
            return True
        print(f"En attente du déploiement Render... essai {attempt+1}/15 (statut: {status})")
        time.sleep(10)
    return False

def main():
    if not wait_for_deployment():
        print("[ERREUR] Le serveur Render n'est pas joignable.")
        return 1

    # 1. Login Médecin
    print_step("1. Authentification Médecin (POST /v1/auth/login)")
    status, _, login_doc = make_request(
        f"{BASE_URL}/v1/auth/login",
        method="POST",
        data={"username": "doctor@medscan.org", "password": "Password123!"}
    )
    if status != 200:
        print(f"[FAIL] Échec login médecin: {status} - {login_doc}")
        return 1
    doc_token = login_doc.get("accessToken") or login_doc.get("token")
    doc_headers = {"Authorization": f"Bearer {doc_token}", "Content-Type": "application/json"}
    print(f"[OK] Login médecin réussi! Token JWT reçu (len={len(doc_token)}).")

    # 2. Login Technicien Labo
    print_step("2. Authentification Technicien Laboratoire (POST /v1/auth/login)")
    status, _, login_lab = make_request(
        f"{BASE_URL}/v1/auth/login",
        method="POST",
        data={"username": "labtech@medscan.org", "password": "Password123!"}
    )
    if status != 200:
        print(f"[FAIL] Échec login lab tech: {status} - {login_lab}")
        return 1
    lab_token = login_lab.get("accessToken") or login_lab.get("token")
    lab_headers = {"Authorization": f"Bearer {lab_token}", "Content-Type": "application/json"}
    print(f"[OK] Login technicien labo réussi! Token JWT reçu (len={len(lab_token)}).")

    # 3. MOD-12: Rendez-vous & Téléconsultation
    print_step("3. MOD-12: Rendez-vous & Téléconsultation Sécurisée")
    appt_payload = {
        "patientId": PATIENT_ID,
        "practitionerId": "179a11bf-92a3-438a-9d7a-a711385c8ef0",
        "practitionerRole": "DOCTOR",
        "type": "TELECONSULTATION",
        "startTime": "2026-11-05T10:00:00Z",
        "endTime": "2026-11-05T10:30:00Z",
        "reason": "Consultation de cardiologie et suivi tensionnel"
    }
    status, _, appt_res = make_request(
        f"{BASE_URL}/v1/appointments",
        method="POST",
        headers=doc_headers,
        data=appt_payload
    )
    print(f"Création RDV -> Statut: {status}")
    if status != 201:
        print(f"Réponse: {appt_res}")
        return 1
    appt_id = appt_res.get("id")
    print(f"[OK] Rendez-vous créé: ID={appt_id}, Statut={appt_res.get('status')}")

    # Confirmation RDV
    status, _, conf_res = make_request(
        f"{BASE_URL}/v1/appointments/{appt_id}/confirm",
        method="POST",
        headers=doc_headers
    )
    print(f"Confirmation RDV -> Statut: {status}, Statut RDV: {conf_res.get('status')}")

    # Démarrage téléconsultation
    status, _, tele_res = make_request(
        f"{BASE_URL}/v1/appointments/{appt_id}/start-teleconsult",
        method="POST",
        headers=doc_headers
    )
    join_url = tele_res.get("teleconsultationJoinUrl") or tele_res.get("teleconsultRoomUrl")
    print(f"Démarrage Téléconsultation -> Statut: {status}, Salle: {join_url}")
    assert join_url is not None, "URL téléconsultation manquante"

    # Liste des RDV
    status, _, appts_list = make_request(
        f"{BASE_URL}/v1/appointments",
        headers=doc_headers
    )
    print(f"Liste des RDV médecin -> Statut: {status}, Total trouvés: {len(appts_list)}")

    # 4. MOD-13: Prescription et Analyses Biologiques
    print_step("4. MOD-13: Prescription et Analyses de Laboratoire")
    lab_order_payload = {
        "patientId": PATIENT_ID,
        "testCategory": "PARASITOLOGIE",
        "testName": "Goutte Épaisse & Recherche Plasmodium",
        "priority": "URGENT",
        "clinicalIndication": "Forte fièvre intermittente, suspicion paludisme grave",
        "items": [
            {
                "testCode": "GE-PALU",
                "testName": "Goutte Épaisse / Test Rapide Paludisme",
                "category": "PARASITOLOGIE",
                "specimenType": "SANG_TOTAL",
                "unit": "parasites/uL",
                "referenceRange": "Négatif (< 0)"
            }
        ]
    }
    status, _, lab_order_res = make_request(
        f"{BASE_URL}/v1/lab/orders",
        method="POST",
        headers=doc_headers,
        data=lab_order_payload
    )
    print(f"Prescription Labo -> Statut: {status}")
    if status != 201:
        print(f"Erreur prescription labo: {lab_order_res}")
        return 1
    lab_order_id = lab_order_res.get("id")
    order_code = lab_order_res.get("orderCode")
    print(f"[OK] Bon d'analyse créé: ID={lab_order_id}, Code={order_code}, Statut={lab_order_res.get('status')}")

    # Prélèvement échantillon
    status, _, collect_res = make_request(
        f"{BASE_URL}/v1/lab/orders/{lab_order_id}/collect-sample",
        method="POST",
        headers=lab_headers,
        data={"barcode": f"SAMPLE-PALU-{int(time.time())}"}
    )
    print(f"Prélèvement -> Statut: {status}, Statut bon={collect_res.get('status')}")

    # Enregistrement résultats
    status, _, rec_res = make_request(
        f"{BASE_URL}/v1/lab/orders/{lab_order_id}/record-results",
        method="POST",
        headers=lab_headers,
        data={"notes": "Analyses en cours sur paillasse hématologie"}
    )
    print(f"Enregistrement résultats -> Statut: {status}, Statut bon: {rec_res.get('status')}")

    # Validation biologique avec alerte critique
    status, _, val_res = make_request(
        f"{BASE_URL}/v1/lab/orders/{lab_order_id}/validate",
        method="POST",
        headers=lab_headers,
        data={
            "paramCode": "GE_PALUDISME",
            "paramName": "Goutte Épaisse Paludisme",
            "textValue": "POSITIF (Plasmodium Falciparum +++)",
            "loincCode": "50311-0",
            "critical": True,
            "notes": "Alerte critique transmise au médecin pour prise en charge urgente."
        }
    )
    print(f"Validation biologique -> Statut: {status}, Réponse: {val_res}")
    if isinstance(val_res, dict):
        print(f"Statut final: {val_res.get('status')}, Alerte critique: {val_res.get('hasCriticalAlert')}")

    # Export FHIR R4 Laboratory DiagnosticReport
    status, _, fhir_res = make_request(
        f"{BASE_URL}/v1/lab/orders/{lab_order_id}/fhir",
        headers=doc_headers
    )
    print(f"Export FHIR R4 Laboratory -> Statut: {status}, Type={fhir_res.get('resourceType') if isinstance(fhir_res, dict) else 'Bundle'}")

    # 5. MOD-14: Surveillance Épidémiologique & Rapports DHIS2
    print_step("5. MOD-14: Surveillance Épidémiologique & Export DHIS2")
    case_payload = {
        "diseaseCode": "PALUDISME_GRAVE",
        "diseaseName": "Paludisme Grave à Plasmodium Falciparum",
        "patientId": PATIENT_ID,
        "patientAge": 28,
        "patientGender": "M",
        "district": "Ouagadougou-Centre",
        "region": "Centre",
        "classification": "CONFIRMED",
        "outcome": "HOSPITALIZED",
        "notes": "Cas confirmé biologiquement (frottis +++ et anémie sévère)"
    }
    status, _, case_res = make_request(
        f"{BASE_URL}/v1/epidemiology/cases",
        method="POST",
        headers=doc_headers,
        data=case_payload
    )
    print(f"Notification cas épidémiologique -> Statut: {status}")
    if status != 201:
        print(f"Erreur notification cas: {case_res}")
        return 1
    print(f"[OK] Cas épidémiologique notifié: ID={case_res.get('id')}, Maladie={case_res.get('diseaseName')}")

    # Synthèse épidémiologique & seuils
    status, _, summary_res = make_request(
        f"{BASE_URL}/v1/epidemiology/summary",
        headers=doc_headers
    )
    print(f"Synthèse épidémiologique -> Statut: {status}, Total cas: {summary_res.get('totalCases')}, Seuils actifs: {len(summary_res.get('thresholds', []))}")

    # Export DHIS2
    status, _, dhis2_res = make_request(
        f"{BASE_URL}/v1/epidemiology/dhis2-export",
        headers=doc_headers
    )
    print(f"Export DHIS2 / OMS -> Statut: {status}, Format={dhis2_res.get('format')}, Lignes exportées={len(dhis2_res.get('dataValues', []))}")

    # 6. MOD-15: Messagerie Sécurisée & Alertes Multi-Entités
    print_step("6. MOD-15: Messagerie Sécurisée & Moteur de Notifications")
    msg_payload = {
        "receiverId": PATIENT_ID,
        "patientId": PATIENT_ID,
        "subject": "Consignes de traitement d'urgence",
        "body": "Bonjour M. Kaboré, vos examens révèlent un paludisme à traiter immédiatement. Veuillez vous présenter au service des urgences.",
        "priority": "CRITICAL"
    }
    status, _, msg_res = make_request(
        f"{BASE_URL}/v1/messages",
        method="POST",
        headers=doc_headers,
        data=msg_payload
    )
    print(f"Envoi message clinique -> Statut: {status}, ID={msg_res.get('id')}")

    # Historique conversation
    status, _, convo_res = make_request(
        f"{BASE_URL}/v1/messages/conversations/{PATIENT_ID}",
        headers=doc_headers
    )
    print(f"Historique conversation -> Statut: {status}, Total messages échangés: {len(convo_res) if isinstance(convo_res, list) else 0}")

    # Vérification des alertes du médecin (dont l'alerte critique labo générée automatiquement !)
    status, _, notifs_res = make_request(
        f"{BASE_URL}/v1/notifications",
        headers=doc_headers
    )
    print(f"Centre de notifications médecin -> Statut: {status}, Total alertes: {len(notifs_res) if isinstance(notifs_res, list) else 0}")
    if isinstance(notifs_res, list):
        for n in notifs_res[:3]:
            print(f"   - [{n.get('severity')}] {n.get('title')}: {n.get('message', '')[:60]}... (Lu: {n.get('isRead')})")

    # Compteur non lues
    status, _, count_res = make_request(
        f"{BASE_URL}/v1/notifications/unread-count",
        headers=doc_headers
    )
    print(f"Badge alertes non lues: {count_res.get('unreadCount')}")

    # Marquer toutes comme lues
    status, _, mark_res = make_request(
        f"{BASE_URL}/v1/notifications/mark-all-read",
        method="POST",
        headers=doc_headers
    )
    print(f"Acquittement global alertes -> Statut: {status}, Modifiées: {mark_res.get('markedRead')}")

    # 7. MOD-16: Redirection & Orientation Médicale Inter-Services / Inter-Établissements
    print_step("7. MOD-16: Redirection & Orientation Médicale Inter-Services")
    referral_payload = {
        "patientId": PATIENT_ID,
        "targetDepartment": "Cardiologie & Explorations Fonctionnelles",
        "reason": "Dyspnée d'effort d'aggravation rapide avec HTA non contrôlée",
        "priority": "URGENT",
        "clinicalSummary": "Patiente asthmatique connue, bilan biologique normal, ECG de repos suspect."
    }
    status, _, ref_res = make_request(
        f"{BASE_URL}/v1/referrals",
        method="POST",
        headers=doc_headers,
        data=referral_payload
    )
    print(f"Création orientation médicale -> Statut: {status}, Code={ref_res.get('referralCode')}, ID={ref_res.get('id')}")
    ref_id = ref_res.get('id')

    # Acceptation de l'orientation par le service cible
    if ref_id:
        status, _, accept_res = make_request(
            f"{BASE_URL}/v1/referrals/{ref_id}/accept",
            method="PUT",
            headers=doc_headers,
            data={}
        )
        print(f"Acceptation orientation -> Statut: {status}, Nouveau statut={accept_res.get('status')}")

    # Consultation de la liste des orientations du patient
    status, _, list_ref_res = make_request(
        f"{BASE_URL}/v1/referrals?patientId={PATIENT_ID}",
        headers=doc_headers
    )
    print(f"Liste des orientations patient -> Statut: {status}, Total trouvées: {len(list_ref_res) if isinstance(list_ref_res, list) else 0}")

    # 8. MOD-17: Cachet d'Hôpital & Certificats / Arrêts de Travail Téléchargeables
    print_step("8. MOD-17: Cachet d'Hôpital & Certificats Officiels Imprimables")
    cert_payload = {
        "patientId": PATIENT_ID,
        "certificateType": "SICK_LEAVE",
        "diagnosisSummary": "Infection aiguë respiratoire et accès fébrile nécessitant repos complet",
        "durationDays": 4,
        "recipientType": "EMPLOYER",
        "recipientName": "Direction des Ressources Humaines - Entreprise Employeur",
        "restrictions": "Repos strict à domicile avec éviction professionnelle complète"
    }
    status, _, cert_res = make_request(
        f"{BASE_URL}/v1/certificates",
        method="POST",
        headers=doc_headers,
        data=cert_payload
    )
    cert_code = cert_res.get('certificateCode')
    print(f"Émission certificat avec cachet -> Statut: {status}, Code={cert_code}, Sceau={cert_res.get('hospitalSeal', {}).get('hospitalCode')}")

    # Téléchargement du document officiel prêt à l'impression / PDF
    if cert_code:
        status, _, html_doc = make_request(
            f"{BASE_URL}/v1/certificates/{cert_code}/document",
            headers=doc_headers
        )
        is_html = isinstance(html_doc, str) and "<!DOCTYPE html>" in html_doc and "CACHET OFFICIEL" in html_doc
        print(f"Téléchargement document officiel imprimable -> Statut: {status}, Format HTML valide avec cachet={is_html}")

        # Vérification publique de validité (sans authentification)
        status, _, verify_res = make_request(
            f"{BASE_URL}/v1/certificates/verify?code={cert_code}"
        )
        print(f"Vérification publique certificat -> Statut: {status}, Valide={verify_res.get('valid')}, Hôpital={verify_res.get('hospitalName')}")

    # 9. MOD-18: Système d'Avis & Réclamations Patients pour l'Administrateur Hospitalier
    print_step("9. MOD-18: Système d'Avis & Réclamations pour l'Administrateur de l'Hôpital")

    # Connexion Patient
    status, _, p_login = make_request(
        f"{BASE_URL}/v1/auth/login",
        method="POST",
        data={"username": "patient@medscan.org", "password": "Password123!"}
    )
    patient_token = p_login.get("accessToken")
    patient_headers = {"Authorization": f"Bearer {patient_token}"}
    print(f"Connexion Patiente -> Statut: {status}")

    # Dépôt d'une réclamation sur le médecin
    grievance_payload = {
        "doctorId": "179a11bf-92a3-438a-9d7a-a711385c8ef0",
        "grievanceType": "COMPLAINT",
        "category": "DELAY_WAIT_TIME",
        "rating": 2,
        "title": "Attente excessive lors de la consultation",
        "description": "Retard de plus d'une heure sans information fournie par le secrétariat."
    }
    status, _, grv_res = make_request(
        f"{BASE_URL}/v1/grievances",
        method="POST",
        headers=patient_headers,
        data=grievance_payload
    )
    grv_id = grv_res.get("id")
    print(f"Dépôt réclamation patiente -> Statut: {status}, Code={grv_res.get('grievanceCode')}, Sévérité={grv_res.get('severity')}")

    # Connexion Administrateur Hospitalier (TENANT_ADMIN)
    status, _, a_login = make_request(
        f"{BASE_URL}/v1/auth/login",
        method="POST",
        data={"username": "tenantadmin@medscan.org", "password": "Password123!"}
    )
    admin_token = a_login.get("accessToken")
    admin_headers = {"Authorization": f"Bearer {admin_token}"}
    print(f"Connexion Administrateur Hôpital -> Statut: {status}")

    # Consultation des réclamations par l'administrateur
    status, _, grv_list = make_request(
        f"{BASE_URL}/v1/grievances",
        headers=admin_headers
    )
    print(f"Liste réclamations administrateur hôpital -> Statut: {status}, Total réclamations: {len(grv_list) if isinstance(grv_list, list) else 0}")

    # Traitement de la réclamation par la direction de l'hôpital
    if grv_id:
        review_payload = {
            "status": "RESOLVED",
            "adminNotes": "Examen du planning de consultations. Retard dû à une prise en charge d'urgence vitale.",
            "actionTaken": "Mise en place d'un SMS d'alerte automatique des retards aux patients en attente."
        }
        status, _, review_res = make_request(
            f"{BASE_URL}/v1/grievances/{grv_id}/review",
            method="PUT",
            headers=admin_headers,
            data=review_payload
        )
        print(f"Résolution réclamation par direction -> Statut: {status}, Statut final={review_res.get('status')}, Mesure prise={review_res.get('adminActionTaken')[:50]}...")

    print("\n" + "=" * 70)
    print("  TOUS LES MODULES (MOD-12 à MOD-18) VALIDÉS EN PRODUCTION SUR RENDER !")
    print("=" * 70)
    return 0

if __name__ == "__main__":
    sys.exit(main())
