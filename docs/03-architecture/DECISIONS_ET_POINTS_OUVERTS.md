# Décisions Prises, Points Ouverts & Analyse d'Impacts

**Statut du document** : `[EN COURS]`  
**Date** : 2026-09-26  

---

## 1. Registre des Décisions Déjà Prises

| Réf | Décision | Statut | Motivation | Risques & Alternatives |
| :--- | :--- | :--- | :--- | :--- |
| **DEC-001** | **Identity Provider centralisé avec Keycloak** | `[VALIDÉ]` | Déléguer l'authentification, MFA, OIDC, PKCE sans gérer de secrets utilisateurs dans le code applicatif. | Alternative : Auth maison (Rejetée : trop risquée, non conforme OWASP/OIDC). |
| **DEC-002** | **PostgreSQL comme SGBD relationnel principal** | `[VALIDÉ]` | Support ACID robuste, gestion JSONB, contraintes d'intégrité strictes, support RLS (Row-Level Security) pour multi-tenancy. | Alternative : NoSQL (Rejetée : intégrité des relations médicales prioritaire). |
| **DEC-003** | **Architecture Découplée pour le Service IA (Python/FastAPI)** | `[VALIDÉ]` | Écosystème PyTorch/MONAI riche en Python, isolation des ressources CPU/GPU, résilience du backend Jakarta EE. | Alternative : Inférence in-JVM (Rejetée : support de modèles Deep Learning limité et risque de freeze mémoire). |
| **DEC-004** | **IA strictement consultative (Aide à la Décision)** | `[VALIDÉ]` | Responsabilité juridique et éthique médicale : validation obligatoire par un praticien de santé. | Risque : lenteur d'adoption praticien. Mesure : UX ultra-ergonomique avec Grad-CAM explicable. |
| **DEC-005** | **Approche Zero Trust & Contrôle d'Autorisation Multi-Couches** | `[VALIDÉ]` | Vérification systématique Tenant + Département + Rôle + Permissions + Consentement + Anti-IDOR. | Risque : surcharge sur les requêtes. Mesure : cache de sessions/claims optimisé et politiques déclaratives. |
| **DEC-006** | **Paiement différé / Escrow pour la Pharmacie & Livraison** | `[VALIDÉ]` | Éviter tout versement non justifié avant confirmation par OTP/QR de la réception des médicaments. | Risque : litiges livraison. Mesure : protocole d'arbitrage et preuve cryptographique. |
| **DEC-007** | **WildFly 41 comme runtime initial** | `[DÉCIDÉ]` | Aligner le socle sur Jakarta EE 11 et Java 21, conformément au brief et à ADR-001. | Risque : configuration de déploiement à maîtriser. Mesure : versions épinglées, environnement local reproductible et tests d’intégration. |
| **DEC-008** | **Schéma PostgreSQL partagé avec RLS** | `[DÉCIDÉ]` | Assurer l’isolation tenant en profondeur avec un coût d’exploitation adapté au démarrage. | Risque : fuite si contexte mal propagé. Mesure : `tenant_id` obligatoire, RLS, tests négatifs et audit. |
| **DEC-009** | **Cadrage initial Burkina Faso** | `[DÉCIDÉ]` | Définir une juridiction pilote avant l’étude des obligations santé, consentement et pharmacie. | Risque : obligation légale non vérifiée. Mesure : validation avec sources officielles et expertise compétente avant production. |
| **DEC-010** | **Authentification & RBAC Multi-Acteurs** | `[VALIDÉ]` | Authentification OIDC / JWT RS256 pour les 10 acteurs du système, validation par filtres JAX-RS et protection @RolesAllowed. | Risque : élévation de privilège. Mesure : immutabilité du tenant et vérification stricte des rôles par requête. |

---

## 2. Points Ouverts & Décisions Techniques à Arbitrer

### Point Ouvert n°1 : Choix du runtime Jakarta EE
* **Constat** : L'utilisateur a confirmé le 2026-09-26 que le backend MedScan s'aligne sur Jakarta EE. Le squelette Spring Boot 4.1.1 du workspace ne sera donc pas conservé comme fondation applicative.
* **Statut** : `[DÉCIDÉ]` pour Jakarta EE ; `[À VALIDER]` pour le runtime précis.
* **Options de runtime** :
  1. *WildFly standard Jakarta EE* : candidat privilégié, car explicitement envisagé dans le brief et directement aligné avec la plateforme Jakarta EE.
  2. *Quarkus fondé sur les APIs Jakarta* : alternative à évaluer pour ses caractéristiques opérationnelles, sans le présenter comme un serveur Jakarta EE complet.
* **Recommandation** : suivre ADR-001 et retenir WildFly avec une version stable compatible Java 21 après vérification documentaire au moment du bootstrap. Spring Boot est rejeté pour ce projet.

### Décision n°2 : Stratégie de multi-tenancy PostgreSQL
* **Statut** : `[DÉCIDÉ]`
* **Décision** : Base et schéma PostgreSQL partagés, colonne `tenant_id` obligatoire, Row-Level Security (RLS) et contrôles applicatifs d’autorisation en défense additionnelle.
* **Motivation** : Cette approche apporte une isolation en profondeur avec un coût d’exploitation adapté au stade initial. Les stratégies par schéma ou par base restent réévaluables si une exigence réglementaire ou contractuelle l’impose.
* **Référence** : ADR-004.

### Point Ouvert n°3 : Algorithme de Rapprochement du Master Patient Index (MPI)
* **Statut** : `[PROPOSÉ]`
* **Options** :
  - *Rapprochement déterministe strict* : Numéro d'identité nationale / numéro de sécurité sociale unique (souvent manquant ou fragmenté en contexte africain).
  - *Rapprochement hybride probabiliste* : Algorithme de type Fellegi-Sunter combinant Nom, Prénom, Phonex/Soundex, Date de naissance, Genre, Téléphone mobile, et Ville de résidence avec un score de similarité (Jaro-Winkler).
* **Recommandation de l'Architecte** : Approche hybride avec réconciliation manuelle assistée dès que le score probabiliste se situe dans une zone d'incertitude (ex: entre 75% et 90%).

### Point Ouvert n°4 : Passerelle SMS / USSD pour le Mode Omnicanal
* **Statut** : `[À VALIDER]`
* **Information manquante** : Quels opérateurs télécoms ou agrégateurs SMS/USSD (ex: Africa's Talking, Twilio, Infobip, Orange API, etc.) seront ciblés en priorité ?
* **Impact** : Définition des connecteurs d'intégration de la passerelle omnicanale.
* **Décision nécessaire** : Concevoir une interface d'abstraction `TelecomGatewayService` indépendante du fournisseur spécifique.
