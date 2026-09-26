# Guide Pratique : Tester l'API MedScan sur Postman depuis Eclipse

**Statut du document** : `[VALIDÉ]`  
**Date** : 2026-09-26  

Ce guide vous explique pas à pas comment démarrer le backend MedScan directement depuis **Eclipse** et exécuter les tests d'authentification et d'autorisations multi-acteurs dans **Postman**.

---

## Étape 1 : Démarrer le Serveur MedScan dans Eclipse

Le projet intègre un serveur autonome prêt à l'emploi qui exécute l'intégralité du moteur de sécurité, des filtres JWT RS256, du contexte multi-tenant et des portails métiers.

1. Dans **Eclipse**, ouvrez l'explorateur de paquets (*Package Explorer*).
2. Naviguez vers le fichier :  
   `src/main/java` -> `com.medscan.server` -> [`MedscanServer.java`](file:///Users/tpe4/Downloads/medscan-backend/src/main/java/com/medscan/server/MedscanServer.java)
3. Faites un **clic droit** sur `MedscanServer.java`.
4. Sélectionnez : **Run As** -> **Java Application**.
5. Dans la vue **Console** d'Eclipse, vous verrez s'afficher :
   ```text
   ================================================================================
     MEDSCAN ENTERPRISE — SERVEUR DE DÉVELOPPEMENT DÉMARRÉ AVEC SUCCÈS !
   ================================================================================
     Base URL        : http://localhost:8080/medscan/api
     Health Check    : GET  http://localhost:8080/medscan/api/v1/health
     Login (Auth)    : POST http://localhost:8080/medscan/api/v1/auth/login
     Profil (/me)    : GET  http://localhost:8080/medscan/api/v1/auth/me
     Portail Médecin : GET  http://localhost:8080/medscan/api/v1/portal/doctor
     Portail Patient : GET  http://localhost:8080/medscan/api/v1/portal/patient
   ================================================================================
     Prêt pour les tests POSTMAN ! (Appuyez sur Ctrl+C ou Stop pour arrêter)
   ================================================================================
   ```

> [!TIP]
> Si le port 8080 est déjà utilisé sur votre machine par une autre application, vous pouvez changer le port dans les *Run Configurations* d'Eclipse en ajoutant l'argument VM : `-Dmedscan.port=8085`.

---

## Étape 2 : Importer la Collection & l'Environnement dans Postman

Deux fichiers prêts à l'importation ont été créés dans le dossier [`postman/`](file:///Users/tpe4/Downloads/medscan-backend/postman/) :
1. [`MedScan_Enterprise.postman_collection.json`](file:///Users/tpe4/Downloads/medscan-backend/postman/MedScan_Enterprise.postman_collection.json) (Collection des requêtes)
2. [`MedScan_Local.postman_environment.json`](file:///Users/tpe4/Downloads/medscan-backend/postman/MedScan_Local.postman_environment.json) (Variables d'environnement)

### Procédure d'importation dans Postman :
1. Lancez **Postman**.
2. Cliquez sur le bouton **Import** (en haut à gauche).
3. Glissez-déposez les deux fichiers situés dans `/Users/tpe4/Downloads/medscan-backend/postman/` (ou parcourez votre disque).
4. En haut à droite de Postman, dans le sélecteur d'environnement, choisissez :  
   **`MedScan — Local (Eclipse)`**.

---

## Étape 3 : Exécuter les Tests dans Postman

La collection est organisée en 4 dossiers logiques :

### 1. Dossier `01 - Health Check`
* Ouvrez **Vérification de Santé (Health)** et cliquez sur **Send**.
* **Résultat attendu** : Code HTTP `200 OK` avec la réponse :
  ```json
  {
    "status": "UP"
  }
  ```

### 2. Dossier `02 - Authentification des 10 Acteurs`
* Ouvrez la requête **01. Connexion — Médecin (DOCTOR)** (`doctor@medscan.org`).
* Cliquez sur **Send**.
* **Résultat attendu** : Code HTTP `200 OK`. Le corps de réponse contient les jetons `accessToken` et `refreshToken`, ainsi que le profil utilisateur.
* **Fonctionnalité automatique intégrée** : Le script de test Postman extrait automatiquement l'`accessToken` et l'enregistre dans la variable `{{bearer_token}}` pour toutes les requêtes suivantes !
* Testez la requête **11. Qui suis-je ? (GET /v1/auth/me)** : elle renvoie instantanément les informations du médecin connecté avec son tenant d'attachement.
* Vous pouvez tester de la même manière la connexion des 9 autres acteurs (`Patient`, `Radiologue`, `Pharmacien`, `Livreur`, `Infirmier`, `Labo`, etc.).

### 3. Dossier `03 - Portails Métier des Acteurs (RBAC)`
* Chaque requête utilise automatiquement le jeton `{{bearer_token}}` de l'acteur connecté.
* Si vous venez de vous connecter en tant que **Médecin** :
  * Cliquez sur **Portail Médecin (GET /v1/portal/doctor)** -> **200 OK**.
* Si vous vous connectez ensuite en tant que **Pharmacien** (`pharmacist@medscan.org`) :
  * Cliquez sur **Portail Pharmacie (GET /v1/portal/pharmacist)** -> **200 OK**.

### 4. Dossier `04 - Tests Négatifs de Sécurité`
Ce dossier démontre la robustesse des barrières de sécurité :
* **Rejet 403 (RBAC)** : Si vous êtes connecté en tant que Patient et que vous tentez d'appeler le portail Médecin, l'API répond immédiatement `403 Forbidden` :
  ```json
  {
    "type": "https://medscan.org/errors/forbidden",
    "title": "Accès interdit",
    "status": 403,
    "detail": "Vos habilitations ne vous permettent pas d'accéder au Portail Médecin. Rôle requis: DOCTOR"
  }
  ```
* **Rejet 401 (Absence de jeton)** : L'appel sans en-tête Authorization renvoie `401 Unauthorized`.
* **Rejet 401 (Jeton falsifié)** : L'appel avec un faux jeton échoue à la vérification de signature RS256 et renvoie `401 Unauthorized`.

---

## Arrêter le Serveur dans Eclipse

Pour arrêter le serveur, cliquez simplement sur le bouton carré rouge **Terminate** dans la vue **Console** d'Eclipse.
