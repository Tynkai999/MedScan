# Guide d'Authentification des Acteurs — MedScan Enterprise

**Statut du document** : `[VALIDÉ]`  
**Date** : 2026-09-26  
**Standards** : OAuth 2.0, OpenID Connect, JWT (RS256), Keycloak 26

---

## 1. Vue d'Ensemble

L'infrastructure d'authentification de MedScan Enterprise est désormais opérationnelle pour **l'ensemble des 10 acteurs du système**.  
Elle respecte strictement les principes directeurs du Prompt Maître :
* **Zero Trust** : Aucun utilisateur n'est considéré de confiance sans jeton validé cryptographiquement (RS256).
* **Privacy by Design** : Aucune donnée médicale n'est injectée dans les jetons JWT.
* **Isolation Multi-Tenant** : Le `tenant_id` est dérivé et activé dans `TenantContext` côté serveur et ne peut jamais être falsifié par le client.
* **RBAC & ABAC** : Les annotations `@RolesAllowed` protègent chaque point d'entrée selon le rôle de l'acteur.

---

## 2. Référentiel des 10 Acteurs du Système

Tous les comptes de démonstration et de développement partagent le mot de passe initial sécurisé : **`Password123!`**

| Acteur | Rôle Système | Identifiant (Email) | Tenant Associé | Droits & Prérogatives |
| :--- | :--- | :--- | :--- | :--- |
| **1. Patient** | `PATIENT` | `patient@medscan.org` | CH_OUAGADOUGOU | Consultation dossier personnel, consentement, rendez-vous. |
| **2. Médecin** | `DOCTOR` | `doctor@medscan.org` | CH_OUAGADOUGOU | Recherche patient, consultations, ordonnances, examens, break-glass. |
| **3. Radiologue** | `RADIOLOGIST` | `radiologist@medscan.org` | CH_OUAGADOUGOU | Téléversement imagerie, déclenchement IA, validation rapports. |
| **4. Infirmier** | `NURSE` | `nurse@medscan.org` | CH_OUAGADOUGOU | Constantes vitales, résumé de soins, administration traitements. |
| **5. Technicien Labo** | `LAB_TECHNICIAN` | `labtech@medscan.org` | LAB_CENTRAL | Import et validation des résultats d'analyses biologiques. |
| **6. Pharmacien** | `PHARMACIST` | `pharmacist@medscan.org` | PHARMACIE_AVENIR | Vérification ordonnances, gestion stocks, préparation colis. |
| **7. Livreur** | `DELIVERY_AGENT` | `delivery@medscan.org` | LOGISTIQUE_SANTE | Réception missions, navigation, remise sécurisée avec code OTP/QR. |
| **8. Admin Tenant** | `TENANT_ADMIN` | `tenantadmin@medscan.org` | CH_OUAGADOUGOU | Gestion des départements et du personnel de l'établissement. |
| **9. Super Admin** | `SUPER_ADMIN` | `superadmin@medscan.org` | MEDSCAN_PLATFORM | Administration globale, homologation organisations, supervision. |
| **10. Auditeur / DPO** | `AUDITOR` | `auditor@medscan.org` | MEDSCAN_PLATFORM | Consultation et export des pistes d'audit, revues de conformité. |

---

## 3. Endpoints REST d'Authentification

### 3.1 Connexion (`POST /api/v1/auth/login`)
* **Requête** :
  ```json
  {
    "username": "doctor@medscan.org",
    "password": "Password123!"
  }
  ```
* **Réponse (200 OK)** :
  ```json
  {
    "accessToken": "eyJhbGciOiJSUzI1NiIs...",
    "refreshToken": "eyJhbGciOiJSUzI1NiIs...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "user": {
      "id": "10000000-0000-0000-0000-000000000002",
      "username": "doctor@medscan.org",
      "email": "doctor@medscan.org",
      "displayName": "Dr. Seydou Traore (Médecin Référent)",
      "tenantId": "11111111-1111-1111-1111-111111111111",
      "tenantCode": "CH_OUAGADOUGOU",
      "roles": ["DOCTOR"],
      "permissions": ["patient:search", "consultation:write", "prescription:create", "exam:order", "break_glass:request"]
    }
  }
  ```

### 3.2 Profil Actif (`GET /api/v1/auth/me`)
* Nécessite l'en-tête `Authorization: Bearer <accessToken>`.
* Retourne le profil et le tenant vérifié côté serveur.

### 3.3 Rafraîchissement (`POST /api/v1/auth/refresh`)
* Permet de renouveler un jeton d'accès sans ressaisir le mot de passe.

---

## 4. Portails des Acteurs (`/api/v1/portal/*`)

Des points d'accès protégés par `@RolesAllowed` démontrent le contrôle d'accès effectif :
* `GET /api/v1/portal/patient` (Réservé à `PATIENT`)
* `GET /api/v1/portal/doctor` (Réservé à `DOCTOR`)
* `GET /api/v1/portal/radiologist` (Réservé à `RADIOLOGIST`)
* `GET /api/v1/portal/nurse` (Réservé à `NURSE`)
* `GET /api/v1/portal/lab-technician` (Réservé à `LAB_TECHNICIAN`)
* `GET /api/v1/portal/pharmacist` (Réservé à `PHARMACIST`)
* `GET /api/v1/portal/delivery` (Réservé à `DELIVERY_AGENT`)
* `GET /api/v1/portal/tenant-admin` (Réservé à `TENANT_ADMIN` et `SUPER_ADMIN`)
* `GET /api/v1/portal/super-admin` (Réservé à `SUPER_ADMIN`)
* `GET /api/v1/portal/auditor` (Réservé à `AUDITOR` et `SUPER_ADMIN`)

Toute tentative d'accès croisé (ex: un Patient tentant d'accéder au portail Médecin) est immédiatement rejetée avec un code HTTP `403 Forbidden`.

---

## 5. Exécution Locale avec Keycloak et Docker

Pour lancer Keycloak 26 et PostgreSQL 17 pré-configurés avec les 10 acteurs :
```bash
docker compose up -d
```
* **Console Keycloak** : `http://localhost:8081` (Identifiants : `admin` / `adminpassword`)
* **Fichier Realm importé** : [`keycloak/medscan-realm.json`](file:///Users/tpe4/Downloads/medscan-backend/keycloak/medscan-realm.json)
