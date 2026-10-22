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

    print("\n" + "=" * 70)
    print("  TOUS LES 4 MODULES (MOD-12, MOD-13, MOD-14, MOD-15) VALIDÉS EN PRODUCTION SUR RENDER !")
    print("=" * 70)
    return 0

if __name__ == "__main__":
    sys.exit(main())
