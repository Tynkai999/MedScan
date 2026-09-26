# Glossaire Médical, Technique et Métier — MedScan Enterprise

**Statut du document** : `[VALIDÉ]`  
**Date** : 2026-09-26  

---

## 1. Termes Métier & Médicaux

| Terme | Définition / Contexte MedScan |
| :--- | :--- |
| **Dossier Patient Numérique (DPR / EHR)** | Ensemble longitudinal des données médicales d'un patient (antécédents, consultations, diagnostics, examens, prescriptions, imagerie). |
| **Consentement Patient** | Accord explicite, révocable et paramétrable (périmètre, durée, demandeur) donné par un patient autorisant un praticien ou établissement à consulter son dossier. |
| **Break-Glass (Accès d'Urgence)** | Procédure dérogatoire d'accès aux données médicales vitales en cas d'urgence absolue, requérant une justification obligatoire et déclenchant un audit renforcé. |
| **MPI (Master Patient Index)** | Index Maître des Patients assurant l'identification unique, la réconciliation et le dédoublonnage des identités patient entre établissements. |
| **ANRP** | Agence Nationale de Régulation Pharmaceutique (ou autorité équivalente selon la juridiction) publiant le registre officiel des officines et médicaments autorisés. |
| **Paiement Escrow / Différé** | Mécanisme de séquestre garantissant que les fonds d'une commande ne sont libérés à la pharmacie et au livreur qu'après confirmation de bonne réception par le patient. |
| **Aide à la Décision Clinique (CDSS)** | Outil informatique d'analyse (ex: IA d'imagerie) apportant une recommandation consultative au médecin, sans valeur de décision autonome. |

---

## 2. Termes de Sécurité & Contrôle d'Accès

| Terme | Définition / Contexte MedScan |
| :--- | :--- |
| **Zero Trust** | Principe selon lequel aucune requête, aucun utilisateur ni aucun réseau interne n'est considéré comme sûr par défaut ; chaque accès est authentifié, autorisé et audité. |
| **RBAC (Role-Based Access Control)** | Contrôle d'accès basé sur les rôles affectés aux utilisateurs (ex: Médecin, Infirmier, Pharmacien, Admin). |
| **ABAC (Attribute-Based Access Control)** | Contrôle d'accès basé sur des attributs dynamiques (ex: département de garde, relation de soin active, heure, localisation, statut d'urgence). |
| **IDOR (Insecure Direct Object Reference)** | Vulnérabilité consistant à accéder à un objet en manipulant directement son identifiant sans validation de propriété/autorisation par le serveur. |
| **Multi-Tenancy** | Architecture permettant de servir plusieurs organisations clientes distinctes (tenants) tout en garantissant un cloisonnement strict de leurs données. |
| **Audit Trail (Piste d'Audit)** | Journal infalsifiable enregistrant l'auteur, l'heure, l'action, la ressource, la justification et le contexte de toute opération sensible. |

---

## 3. Termes Techniques & Interopérabilité

| Terme | Définition / Contexte MedScan |
| :--- | :--- |
| **HL7 FHIR** | *Fast Healthcare Interoperability Resources* — Standard moderne d'échange de données de santé basé sur des ressources RESTful JSON/XML. |
| **DICOM / DICOMweb** | *Digital Imaging and Communications in Medicine* — Standard mondial de stockage, échange et transmission d'imagerie médicale (WADO-RS, QIDO-RS, STOW-RS). |
| **PACS / VNA** | *Picture Archiving and Communication System* / *Vendor Neutral Archive* — Systèmes d'archivage et de communication d'imagerie médicale. |
| **OIDC / OAuth 2.0** | Protocoles standards de fédération d'identité et de délégation d'autorisation mis en œuvre via Keycloak. |
| **Offline-First** | Architecture permettant à l'application cliente d'exécuter des fonctionnalités essentielles sans réseau, avec synchronisation et réconciliation ultérieures. |
| **Grad-CAM** | *Gradient-weighted Class Activation Mapping* — Technique d'explicabilité produisant une carte thermique visuelle sur les zones d'une image médicale ayant motivé la prédiction de l'IA. |
