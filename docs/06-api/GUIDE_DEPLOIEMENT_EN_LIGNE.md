# Guide de Déploiement en Ligne — MedScan Enterprise

**Statut du document** : `[VALIDÉ]`  
**Date** : 2026-09-26  
**Audience** : Développeurs Backend, Développeurs Frontend, DevOps  
**Standards de référence** : OWASP Deployment Guidelines, ISO/IEC 27001, TLS 1.3

---

## 1. Vue d'Ensemble des Options de Déploiement

Pour permettre à votre développeur frontend (Web ou Mobile Flutter) d'accéder au backend en ligne avec une connexion sécurisée **HTTPS**, trois approches sont à votre disposition selon vos besoins :

```
┌────────────────────────────────────────────────────────────────────────┐
│                        OPTIONS DE MISE EN LIGNE                        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
    ┌───────────────────────────────┼───────────────────────────────┐
    ▼                               ▼                               ▼
[ OPTION 1 : TUNNEL INSTANTANÉ ] [ OPTION 2 : CLOUD MANAGE ]   [ OPTION 3 : SERVEUR DEDIE / VPS ]
• 2 minutes chrono             • Render / Railway / Fly.io   • VPS Ubuntu (OVH, Hetzner, AWS)
• Test direct depuis Eclipse   • URL HTTPS permanente        • Docker Compose + Caddy SSL
• Idéal pour tests immédiats   • Zéro gestion de serveur     • Contrôle total de la souveraineté
```

---

## 2. Option 1 : Tunnel Temporaire Instantané (Ngrok / Cloudflare) — *2 Minutes*

Si votre serveur tourne dans **Eclipse** sur votre machine locale et que vous souhaitez donner un accès HTTPS immédiat à votre développeur front sans rien installer sur le cloud :

1. Démarrez [`MedscanServer.java`](file:///Users/tpe4/Downloads/medscan-backend/src/main/java/com/medscan/server/MedscanServer.java) dans Eclipse (port 8080).
2. Dans un terminal, lancez un tunnel [ngrok](https://ngrok.com/) :
   ```bash
   ngrok http 8080
   ```
3. Ngrok génère une URL publique HTTPS sécurisée, par exemple :  
   `https://abc1-23-45.ngrok-free.app`
4. Donnez cette URL à votre développeur frontend :  
   **Base URL Front** : `https://abc1-23-45.ngrok-free.app/medscan/api`
5. Votre développeur peut immédiatement envoyer des requêtes depuis son application Web ou Flutter et consommer votre API en direct !

---

## 3. Option 2 : Déploiement Cloud 100% Managé (Render / Railway / Fly.io) — *Recommandé pour Développement & Recette*

Cette solution héberge le conteneur en ligne 24h/24 avec un certificat SSL/TLS automatique gratuit.

### Déploiement sur Render (gratuit & ultra simple) :
1. Créez un compte sur [Render.com](https://render.com/).
2. Cliquez sur **New +** ➔ **Web Service**.
3. Liez votre dépôt Git (GitHub / GitLab) ou choisissez **Deploy an existing image**.
4. Configurez :
   * **Environment** : `Docker` (il détecte automatiquement le [`Dockerfile`](file:///Users/tpe4/Downloads/medscan-backend/Dockerfile) à la racine).
   * **Instance Type** : `Free` ou `Starter`.
   * **Variables d'environnement** :
     * `PORT` = `8080`
5. Cliquez sur **Deploy Web Service**.
6. Render compile le projet et vous fournit une URL HTTPS permanente :  
   `https://medscan-backend.onrender.com`
7. **Base URL pour le frontend** :  
   `https://medscan-backend.onrender.com/medscan/api`

---

## 4. Option 3 : Déploiement VPS Dédié (Production & Souveraineté des Données)

Pour une mise en production répondant aux exigences strictes de souveraineté des données de santé (cadrage Afrique / Burkina Faso) :

### Prérequis sur le serveur (Ubuntu 22.04 / 24.04 LTS) :
* Docker et Docker Compose installés :
  ```bash
  sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2
  ```
* Un nom de domaine ou sous-domaine pointant vers l'adresse IP de votre serveur (ex: `api.medscan.org`).

### Procédure de déploiement en 3 commandes :
1. Clonez le projet sur le serveur :
   ```bash
   git clone <url-du-depot> /opt/medscan-backend
   cd /opt/medscan-backend
   ```
2. Définissez votre nom de domaine dans l'environnement :
   ```bash
   export DOMAIN_NAME=api.medscan.org
   ```
3. Lancez les conteneurs avec le Docker Compose de production :
   ```bash
   docker compose -f docker-compose.prod.yml up -d --build
   ```

### Ce qui est configuré automatiquement :
* **Backend MedScan** : compile et s'exécute sous Java 21 Alpine avec utilisateur non-root.
* **Caddy Reverse Proxy** :
  * Écoute sur les ports `80` et `443`.
  * Obtient et renouvelle automatiquement le certificat SSL **Let's Encrypt** (HTTPS).
  * Active la compression gzip/zstandard pour minimiser la consommation de données mobiles.
  * Injecte les en-têtes de sécurité recommandés par l'OWASP (HSTS, nosniff, DENY).

---

## 5. Vérification du Déploiement

Une fois en ligne, vérifiez que le service répond correctement :
```bash
curl -i https://<votre-domaine-ou-url>/medscan/api/v1/health
```
**Réponse attendue :**
```http
HTTP/2 200
content-type: application/json; charset=UTF-8

{"status":"UP"}
```
Le backend est maintenant opérationnel et prêt à être raccordé aux applications clientes.
