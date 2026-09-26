# Contraintes Techniques, Réglementaires et Modèle de Risques Initial

**Statut du document** : `[VALIDÉ]`  
**Date** : 2026-09-26  
**Cadres de Référence** : OWASP Top 10 API Security, OWASP ASVS 4.0, IEC 81001-5-1, ISO/IEC 27001, ISO 14971

---

## 1. Contraintes du Projet

### 1.1 Contraintes Techniques
1. **Connectivité Intermittente & Basse Bande Passante** :
   - Présence fréquente de zones blanches ou de connexions mobiles instables (2G/3G/4G).
   - Impératif de minimisation des charges utiles HTTP (compression Gzip/Brotli, sélection fine des champs, payload JSON compacts).
   - Mode Offline-First avec synchronisation résiliente et reprise après interruption.
2. **Hétérogénéité des Terminaux** :
   - Smartphones d'entrée/milieu de gamme pour le grand public (mémoire et stockage restreints).
   - Population sans smartphone nécessitant l'accès aux services de base via SMS transactionnels ou menus interactifs USSD.
3. **Séparation Découplée des Traitements Lourds** :
   - Les calculs d'IA (inférence PyTorch, segmentation MONAI) et le transcodage d'images volumineuses ne doivent jamais saturer le pool de threads du serveur applicatif transactionnel.

### 1.2 Contraintes Réglementaires et Éthiques
1. **Souveraineté et Localisation des Données de Santé** :
   - Conformité aux législations nationales sur les données de santé à caractère personnel.
   - Les données de santé identifiables ne doivent pas être hébergées dans des juridictions incompatibles sans chiffrement strict de bout en bout dont le client détient les clés.
2. **Qualification des Logiciels Dispositifs Médicaux (SaMD)** :
   - Le module d'IA agit strictement comme Système d'Aide à la Décision Clinique (CDSS).
   - Aucune ordonnance ni résultat d'imagerie ne doit être automatiquement validé sans acte explicite d'un médecin diplômé.
3. **Séparation Stricte du Secret Bancaire et Médical** :
   - Aucune donnée de paiement bancaire ne transite par les tables médicales.
   - Aucun libellé diagnostique ou pathologie n'est transmis au prestataire de paiement (PSP) ni affiché sur le colis remis au livreur.

---

## 2. Matrice Préliminaire des Risques et Modèle de Menaces (Threat Model)

Nous appliquons la méthodologie **STRIDE** adaptée au secteur de la santé :

| ID Risque | Menace Identifiée | Catégorie STRIDE | Impact | Probabilité | Mesure d'Atténuation Obligatoire | Statut |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **THREAT-01** | **Accès non autorisé / IDOR (Insecure Direct Object Reference)** : Un utilisateur consulte le dossier d'un tiers en modifiant le `patientId` dans l'URL. | Information Disclosure | Critique | Élevée | Validation systématique en couche service : identité de l'acteur + lien de soin actif + consentement valide + appartenance tenant. Ne jamais se fier aux IDs clients. | `[DÉCIDÉ]` |
| **THREAT-02** | **Fuite Inter-Tenant** : Une clinique accède aux dossiers médicaux hébergés par un hôpital concurrent. | Information Disclosure | Critique | Moyenne | Cloisonnement strict au niveau de la persistance (PostgreSQL Row-Level Security) et validation du `tenant_id` contextuel via le token. | `[DÉCIDÉ]` |
| **THREAT-03** | **Élévation de Privilège** : Un technicien ou infirmier modifie des prescriptions ou des rapports médicaux réservés aux médecins. | Elevation of Privilege | Élevée | Moyenne | Contrôle RBAC granulaire couplé à des annotations de sécurité sur les méthodes de service et vérification des claims Keycloak. | `[DÉCIDÉ]` |
| **THREAT-04** | **Détournement du mécanisme Break-Glass** : Utilisation de l'accès d'urgence pour espionner des personnalités publiques ou proches sans urgence médicale. | Repudiation / Abuse | Élevée | Moyenne | Obligation de saisie d'un motif textuel circonstancié, notification push/SMS immédiate au patient, journalisation inaltérable et rapport d'audit hebdomadaire automatique pour le DPO. | `[DÉCIDÉ]` |
| **THREAT-05** | **Upload de Fichiers Malveillants** : Téléversement de scripts ou exécutables déguisés en images médicales (JPEG/DICOM). | Tampering / Remote Exec | Critique | Moyenne | Whitelist stricte d'extensions, vérification du 'magic number' binaire (MIME type réel), génération d'un nom UUID aléatoire côté serveur, stockage sur bucket S3 privé isolé sans droit d'exécution. | `[DÉCIDÉ]` |
| **THREAT-06** | **Attaque SSRF (Server-Side Request Forgery)** : Requêtes malveillantes via les webhooks de paiement ou de pharmacie vers le réseau interne de l'hôpital. | Information Disclosure / DoS | Élevée | Faible | Whitelist d'URLs externes, interdiction formelle d'appels vers des adresses IP privées (RFC 1918), validation stricte des schémas d'URL. | `[DÉCIDÉ]` |
| **THREAT-07** | **Vol ou Falsification de Token JWT** : Interception de token sur réseau non sécurisé ou terminal compromis. | Spoofing | Critique | Faible | TLS 1.3 obligatoire, durée de vie des jetons d'accès très courte (ex: 5 à 15 min), rotation avec Refresh Token chiffré, utilisation de PKCE sur les clients Flutter. | `[DÉCIDÉ]` |
| **THREAT-08** | **Perte ou Vol de Smartphone avec Données Offline** : Récupération des données médicales stockées dans le cache du téléphone. | Information Disclosure | Élevée | Élevée | Chiffrement AES-256 de la base locale (SQLCipher / Hive sécurisé), clé dérivée de l'authentification biométrique/PIN, effacement automatique après expiration ou révocation à distance. | `[DÉCIDÉ]` |
| **THREAT-09** | **Attaque par Rejeu sur les Webhooks** : Réémission d'un webhook de paiement ou de livraison pour valider frauduleusement une commande. | Tampering | Élevée | Moyenne | Signature cryptographique HMAC-SHA256 avec clé secrète partagée, inclusion d'un timestamp valide max. 5 minutes, vérification d'idempotence via identifiant unique d'événement. | `[DÉCIDÉ]` |
| **THREAT-10** | **Empoisonnement des Données d'Entraînement ou Inférence IA** : Fourniture d'images altérées pour fausser l'analyse de détection du cancer. | Tampering | Critique | Faible | Traçabilité de l'empreinte cryptographique (hash SHA-256) de l'image transmise à l'IA, validation de l'intégrité avant inférence, signature du rapport par le praticien. | `[DÉCIDÉ]` |
