# Spécifications d'API — MedScan Enterprise

**Statut du dossier** : `[VALIDÉ & PRÊT POUR INTÉGRATION]`  
**Audience** : Développeurs Backend, Développeurs Frontend Web & Mobile, DevOps, Auditeurs de Sécurité  
**Standards Internationaux** : OpenAPI 3.1.0, RFC 7807 (Problem Details), RFC 7519 (JWT), RFC 6750 (Bearer Auth), OWASP API Security

---

## 1. Documentation & Guides Pratiques

Le dossier `docs/06-api/` contient l'ensemble des documents opérationnels pour le backend, les tests et le frontend :

1. [**Guide de Déploiement en Ligne**](GUIDE_DEPLOIEMENT_EN_LIGNE.md) :
   - Mise en ligne rapide en 2 minutes via tunnel HTTPS (Ngrok / Cloudflare).
   - Déploiement Cloud PaaS 100% managé (Render / Railway / Fly.io).
   - Déploiement souverain sur serveur dédié VPS Linux (Docker Compose + Caddy avec SSL automatique Let's Encrypt).
2. [**Guide d'Intégration Frontend & Mobile**](FRONTEND_INTEGRATION_GUIDE.md) :
   - Architecture Zero Trust et flux d'authentification JWT RS256.
   - Matrice des 10 acteurs du système et identifiants de test préconfigurés.
   - Spécification détaillée des endpoints (`/v1/health`, `/v1/auth/login`, `/v1/auth/me`, `/v1/auth/refresh`, `/v1/portal/*`).
   - Format d'erreur standardisé conforme RFC 7807 (Problem Details).
   - Client TypeScript / React / Next.js complet avec intercepteurs Axios et auto-refresh.
   - Client Dart / Flutter complet avec Dio et `flutter_secure_storage`.
   - Recommandations OWASP Top 10 pour le stockage des tokens.
3. [**Guide de Test Postman**](TESTER_SUR_POSTMAN.md) :
   - Procédure pas-à-pas pour importer et exécuter la collection `postman/MedScan_Enterprise.postman_collection.json`.
   - Scripts d'automatisation des tokens pour tester les 10 rôles en 1 clic.
4. [**Spécification OpenAPI 3.1 (Machine-Readable)**](../../src/main/resources/META-INF/openapi.yaml) :
   - Spécification OpenAPI 3.1.0 du contrat d'API.

---

## 2. Principes Directeurs de Sécurité

1. **Format & Standard** : RESTful JSON conforme aux standards OpenAPI 3.1.
2. **Authentification** : Bearer JWT signé en RS256 avec validation cryptographique stricte.
3. **Protection contre l'IDOR** : Aucun paramètre d'identification client (`{patientId}`, `{organizationId}`) n'est traité sans vérification de contexte de sécurité et d'autorisation ABAC/RBAC.
4. **Gestion d'Erreurs Conforme RFC 7807 (Problem Details)** : Réponses d'erreur standardisées sans fuite de stack trace ou de données sensibles.
5. **CORS Préconfiguré** : Le backend autorise les origines nécessaires pour le développement Web et mobile sans blocage navigateur.
