# ADR-002 : Utilisation de Keycloak comme Fournisseur Central d'Identité (IAM)

**Statut** : `[VALIDÉ]`  
**Date** : 2026-09-26  
**Auteurs** : Agent IA Principal (Architecte & Ingénieur Sécurité)

---

## 1. Contexte
MedScan Enterprise manipule des données médicales hautement confidentielles et interconnecte plusieurs types d'utilisateurs (patients, médecins, radiologues, pharmaciens, livreurs, administrateurs) ainsi que des systèmes tiers.  
Le principe Zero Trust et les normes de cybersécurité en santé (OWASP ASVS, IEC 81001-5-1) interdisent la gestion directe ou le stockage de mots de passe par les applications métier.

---

## 2. Décision
**Keycloak** est retenu comme Identity Provider (IdP) centralisé pour l'ensemble de l'écosystème MedScan Enterprise.

* **Standards mis en œuvre** :
  - OpenID Connect (OIDC) & OAuth 2.0.
  - Authorization Code Flow avec extension **PKCE** (Proof Key for Code Exchange) pour les applications mobiles Flutter et les frontends Web.
  - Authentification Multi-Facteurs (MFA / TOTP) obligatoire pour tous les profils soignants et administrateurs.
  - Jetons d'accès JWT signés (RS256 / EdDSA) avec durée de vie courte (max. 15 minutes) et rotation des Refresh Tokens.

---

## 3. Séparation des Responsabilités
* **Keycloak** : Authentification primaire, cycle de vie des identifiants, politique de mots de passe, MFA, sessions SSO, fédération éventuelle d'identités institutionnelles.
* **MedScan Backend** : Contexte métier, rattachement organisationnel/départemental, habilitations fines (RBAC + ABAC), validation des consentements, contrôle au niveau ressource (anti-IDOR), audit trail médical.

---

## 4. Conséquences
* Aucun mot de passe ni secret d'authentification utilisateur n'est stocké dans la base PostgreSQL de MedScan.
* Le backend vérifie la signature des jetons JWT via les clés publiques publiées par le endpoint JWKS de Keycloak.
* Les tests automatisés s'appuieront sur Testcontainers avec l'image officielle Keycloak ou des bouchons OIDC validés.
