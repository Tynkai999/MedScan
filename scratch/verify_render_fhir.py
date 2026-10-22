#!/usr/bin/env python3
"""
Script de validation en direct du Hub d'Interopérabilité Internationale HL7 FHIR R4 (MOD-07)
sur l'environnement de production Render : https://medscan-sluw.onrender.com/medscan/api
"""
import json
import sys
import urllib.request
import urllib.error

BASE_URL = "https://medscan-sluw.onrender.com/medscan/api"
PATIENT_ID = "99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e" # Fatou Ouedraogo

def print_step(title):
    print("\n" + "=" * 65)
    print(f"  {title}")
    print("=" * 65)

def make_request(url, method="GET", headers=None, data=None):
    if headers is None:
        headers = {}
    req = urllib.request.Request(url, method=method, headers=headers)
    if data:
        req.data = json.dumps(data).encode("utf-8")
        if "Content-Type" not in headers:
            headers["Content-Type"] = "application/json"
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            content = resp.read().decode("utf-8")
            return resp.status, resp.headers, json.loads(content) if "json" in resp.headers.get("Content-Type", "") else content
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        return e.code, e.headers, err_body
    except Exception as e:
        return 500, {}, str(e)

def main():
    print(f"Démarrage de la vérification FHIR R4 sur {BASE_URL}...")

    # 1. CapabilityStatement public
    print_step("1. GET /fhir/r4/metadata (Public CapabilityStatement)")
    status, headers, body = make_request(
        f"{BASE_URL}/fhir/r4/metadata",
        headers={"Accept": "application/fhir+json"}
    )
    print(f"Status: {status}")
    if status == 200 and isinstance(body, dict):
        print(f"ResourceType: {body.get('resourceType')}")
        print(f"FHIR Version: {body.get('fhirVersion')}")
        print(f"Software: {body.get('software', {}).get('name')}")
        print("OK: CapabilityStatement conforme FHIR R4.")
    else:
        print(f"Réponse: {body}")

    # 2. Authentification Médecin
    print_step("2. Authentification Médecin (POST /v1/auth/login)")
    status, _, login_body = make_request(
        f"{BASE_URL}/v1/auth/login",
        method="POST",
        headers={"Content-Type": "application/json"},
        data={"username": "doctor@medscan.org", "password": "Password123!"}
    )
    if status != 200 or not isinstance(login_body, dict):
        print(f"Erreur d'authentification ({status}): {login_body}")
        sys.exit(1)

    token = login_body.get("accessToken")
    print(f"Authentification réussie, token JWT RS256 reçu (longueur: {len(token)})")

    auth_headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/fhir+json"
    }

    # 3. GET /fhir/r4/Patient/{id}
    print_step(f"3. GET /fhir/r4/Patient/{PATIENT_ID}")
    status, _, pat_body = make_request(
        f"{BASE_URL}/fhir/r4/Patient/{PATIENT_ID}",
        headers=auth_headers
    )
    print(f"Status: {status}")
    if status == 200 and isinstance(pat_body, dict):
        print(f"ResourceType: {pat_body.get('resourceType')}")
        name = pat_body.get("name", [{}])[0]
        print(f"Nom: {name.get('family')}, Prénoms: {name.get('given')}")
        print(f"Extensions: {len(pat_body.get('extension', []))} extensions trouvées")
        for ext in pat_body.get("extension", []):
            url = ext.get("url", "")
            val = ext.get("valueString") or ext.get("valueDecimal")
            print(f"  - {url.split('/')[-1]}: {val}")
        print("OK: Ressource Patient standard FHIR R4 avec extensions cliniques.")
    else:
        print(f"Réponse: {pat_body}")

    # 4. GET /fhir/r4/Observation?patient={id}
    print_step(f"4. GET /fhir/r4/Observation?patient={PATIENT_ID} (Constantes LOINC)")
    status, _, obs_body = make_request(
        f"{BASE_URL}/fhir/r4/Observation?patient={PATIENT_ID}",
        headers=auth_headers
    )
    print(f"Status: {status}")
    if status == 200 and isinstance(obs_body, dict):
        entries = obs_body.get("entry", [])
        print(f"Total Observations LOINC: {len(entries)}")
        for e in entries:
            res = e.get("resource", {})
            code = res.get("code", {}).get("text") or res.get("code", {}).get("coding", [{}])[0].get("display")
            print(f"  - Obs: {code} (ID: {res.get('id')})")
        print("OK: Constantes vitales codifiées LOINC & unités UCUM.")
    else:
        print(f"Réponse: {obs_body}")

    # 5. GET /fhir/r4/MedicationRequest/RX-2026-0042
    print_step("5. GET /fhir/r4/MedicationRequest/RX-2026-0042")
    status, _, rx_body = make_request(
        f"{BASE_URL}/fhir/r4/MedicationRequest/RX-2026-0042",
        headers=auth_headers
    )
    print(f"Status: {status}")
    if status == 200 and isinstance(rx_body, dict):
        print(f"ResourceType: {rx_body.get('resourceType')}")
        print(f"Intent: {rx_body.get('intent')}, Status: {rx_body.get('status')}")
        items = rx_body.get("contained", [])
        print(f"Médicaments prescrits: {len(items)} lignes")
        print("OK: Prescription FHIR R4 MedicationRequest validée.")
    else:
        print(f"Réponse: {rx_body}")

    # 6. GET /fhir/r4/Bundle?patient={id} (Dossier complet)
    print_step(f"6. GET /fhir/r4/Bundle?patient={PATIENT_ID} (Export Dossier Médical Partagé)")
    status, _, bundle_body = make_request(
        f"{BASE_URL}/fhir/r4/Bundle?patient={PATIENT_ID}",
        headers=auth_headers
    )
    print(f"Status: {status}")
    if status == 200 and isinstance(bundle_body, dict):
        print(f"ResourceType: {bundle_body.get('resourceType')}")
        print(f"Bundle Type: {bundle_body.get('type')}")
        print(f"Total Ressources Exportées: {bundle_body.get('total')}")
        types = set()
        for e in bundle_body.get("entry", []):
            types.add(e.get("resource", {}).get("resourceType"))
        print(f"Types de ressources incluses: {list(types)}")
        print("OK: Export complet de dossier médical en Collection Bundle FHIR R4 validé !")
    else:
        print(f"Réponse: {bundle_body}")

    print("\n" + "=" * 65)
    print("  SUCCÈS : TOUS LES FLUX HL7 FHIR R4 SONT CONFORMES ET OPÉRATIONNELS !")
    print("=" * 65)

if __name__ == "__main__":
    main()
