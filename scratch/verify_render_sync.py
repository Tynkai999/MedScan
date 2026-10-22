#!/usr/bin/env python3
"""
Script de validation en direct des endpoints MOD-10 (Synchronisation Hors-Ligne & Delta-Sync)
sur l'environnement de production Render.
"""

import urllib.request
import urllib.error
import json
import time
import uuid

RENDER_URL = "https://medscan-sluw.onrender.com/medscan/api"

def make_request(url, method="GET", data=None, token=None):
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    
    body = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        try:
            return e.code, json.loads(err_body)
        except Exception:
            return e.code, {"raw": err_body}
    except Exception as e:
        return 0, {"error": str(e)}

def wait_for_deploy(max_attempts=30, delay_sec=10):
    print(f"[*] Attente du déploiement Render sur {RENDER_URL} ...")
    for i in range(max_attempts):
        status, res = make_request(f"{RENDER_URL}/v1/health")
        if status == 200 and res.get("status") == "UP":
            print(f"[+] Serveur opérationnel (Tentative {i+1})")
            return True
        print(f"[-] Tentative {i+1}/{max_attempts}: en cours de démarrage... ({status})")
        time.sleep(delay_sec)
    return False

def run_tests():
    print("=" * 70)
    print("   VALIDATION EN DIRECT DU MODULE MOD-10 (OFFLINE SYNC) SUR RENDER")
    print("=" * 70)

    # 1. Login Médecin
    print("[1] Authentification du Médecin (doctor@medscan.org)...")
    status, auth = make_request(f"{RENDER_URL}/v1/auth/login", method="POST", data={
        "username": "doctor@medscan.org",
        "password": "Password123!"
    })
    if status != 200 or "accessToken" not in auth:
        print(f"[FAIL] Échec connexion médecin: {status} -> {auth}")
        return False
    doctor_token = auth["accessToken"]
    print(f"[OK] Token JWT RS256 obtenu avec succès.")

    # 2. GET /v1/sync/vault (Coffre Hors-Ligne)
    print("\n[2] Téléchargement du Coffre Hors-Ligne (GET /v1/sync/vault)...")
    status, vault = make_request(f"{RENDER_URL}/v1/sync/vault", token=doctor_token)
    if status != 200:
        print(f"[-] Statut {status} reçu. Le nouveau binaire MOD-10 est peut-être encore en cours de build sur Render.")
        return False
    print(f"[OK] Statut 200 reçu.")
    print(f"     Total enregistrements : {vault.get('totalRecords')}")
    print(f"     Patients synchronisés : {len(vault.get('patients', []))}")
    print(f"     Constantes vitales    : {len(vault.get('vitalSigns', []))}")
    print(f"     Fiches d'urgence      : {len(vault.get('emergencyCards', []))}")

    # 3. GET /v1/sync/emergency-card/{patientId}
    fatou_id = "99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e"
    print(f"\n[3] Consultation de la Fiche d'Urgence Autonome ({fatou_id})...")
    status, card = make_request(f"{RENDER_URL}/v1/sync/emergency-card/{fatou_id}", token=doctor_token)
    if status != 200:
        print(f"[FAIL] Échec récupération fiche d'urgence: {status} -> {card}")
        return False
    print(f"[OK] Fiche d'urgence reçue :")
    print(f"     Nom complet    : {card.get('fullName')}")
    print(f"     Groupe sanguin : {card.get('bloodGroup')}")
    print(f"     Allergies      : {card.get('allergies')}")
    print(f"     Empreinte SHA  : {card.get('integrityHash')}")

    # 4. POST /v1/sync/push (Lot Hors-Ligne avec Idempotence)
    op_id = str(uuid.uuid4())
    batch_id = str(uuid.uuid4())
    print(f"\n[4] Ingestion de Lot Hors-Ligne (POST /v1/sync/push - opId: {op_id})...")
    batch_payload = {
        "batchId": batch_id,
        "deviceId": "render-verifier-tablet-01",
        "clientTimestamp": "2026-10-22T21:00:00Z",
        "operations": [
            {
                "operationId": op_id,
                "entityType": "VITAL_SIGNS",
                "action": "CREATE",
                "occurredAt": "2026-10-22T20:50:00Z",
                "payload": {
                    "patientId": fatou_id,
                    "systolicBp": 124,
                    "diastolicBp": 81,
                    "heartRate": 73,
                    "temperature": 36.9,
                    "notes": "Mesure sur le terrain validée Render"
                }
            }
        ]
    }

    status, push1 = make_request(f"{RENDER_URL}/v1/sync/push", method="POST", data=batch_payload, token=doctor_token)
    if status != 200:
        print(f"[FAIL] Échec du push de synchronisation: {status} -> {push1}")
        return False
    print(f"[OK] Premier Push réussi :")
    print(f"     Opérations appliquées : {push1.get('appliedCount')}")
    print(f"     Doublons ignorés      : {push1.get('duplicateCount')}")
    print(f"     Erreurs               : {push1.get('errorCount')}")

    # 5. POST /v1/sync/push (Test Idempotence / Rejeu réseau)
    print(f"\n[5] Test d'Idempotence Stricte (Rejeu du même lot opId: {op_id})...")
    status, push2 = make_request(f"{RENDER_URL}/v1/sync/push", method="POST", data=batch_payload, token=doctor_token)
    if status != 200:
        print(f"[FAIL] Échec second push: {status} -> {push2}")
        return False
    print(f"[OK] Second Push dédupliqué :")
    print(f"     Opérations appliquées : {push2.get('appliedCount')} (Attendu: 0)")
    print(f"     Doublons ignorés      : {push2.get('duplicateCount')} (Attendu: 1)")
    print(f"     Statut résultat       : {push2.get('results', [{}])[0].get('status')} (Attendu: DUPLICATE_IGNORED)")

    if push2.get("appliedCount") == 0 and push2.get("duplicateCount") == 1:
        print("\n" + "=" * 70)
        print("  >>> SUCCÈS TOTAL : LE MODULE MOD-10 EST 100% OPÉRATIONNEL SUR RENDER <<<")
        print("=" * 70)
        return True
    else:
        print("[FAIL] Déduplication idempotente non conforme.")
        return False

if __name__ == "__main__":
    if wait_for_deploy():
        run_tests()
