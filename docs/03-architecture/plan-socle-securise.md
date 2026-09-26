# Plan de préparation du socle sécurisé

**Statut : [EN COURS]**  
**Aucun code métier n’est inclus dans ce plan.**

## Décisions à verrouiller avant le bootstrap

| Décision | Proposition | Impact si non tranchée |
|---|---|---|
| Runtime Jakarta EE | Choisir un runtime compatible avec Java 21 ; WildFly est recommandé par ADR-001, Quarkus reste une alternative à arbitrer. | Structure Maven, packaging et déploiement. |
| Isolation tenant | Schéma PostgreSQL partagé, `tenant_id` obligatoire, RLS et contrôles applicatifs complémentaires. | Modèle de données, migrations, tests et administration. |
| Identité | Keycloak, realm séparé par environnement et jetons OIDC pour clients Web/mobile. | Sécurité, profils de test, claims et déploiement local. |
| Données de santé | Juridiction et résidence déterminées avant données réelles. | Conservation, chiffrement, sous-traitance et conformité. |
| Stockage de fichiers | Stockage objet privé et chiffré, hors PostgreSQL. | Contrat d’upload, antivirus, URL temporaires et IAM. |

## Backlog de bootstrap

### B-001 — Gouvernance du dépôt et configuration

- Initialiser un dépôt Git après autorisation explicite.
- Ajouter une politique de branches, une CI minimale et un scan de secrets.
- Documenter les variables de configuration sans jamais publier de valeur secrète.
- Définir `local`, `test`, `staging` et `production` avec secrets injectés au runtime.

**Terminé lorsque :** un clone neuf peut construire le projet sans secret, et aucun secret n’est suivi dans les fichiers sources.

### B-002 — Migration vers Jakarta EE

- Remplacer le parent et les starters Spring Boot par une structure Maven Jakarta EE compatible avec le runtime choisi.
- Produire un WAR déployable et retirer la route de démonstration Spring.
- Ajouter Jakarta REST, CDI, Bean Validation, JPA/Hibernate, MicroProfile Config, Health et OpenAPI selon la compatibilité du runtime.
- Créer une vérification de santé non sensible, protégée selon son exposition.

**Terminé lorsque :** l’artefact se construit et se déploie dans un environnement local documenté.

### B-003 — Dépendances locales sûres

- Exécuter PostgreSQL et Keycloak localement avec des données non sensibles.
- Appliquer les migrations de manière reproductible sur une base vide.
- Utiliser un conteneur ou une configuration locale pour le serveur Jakarta et documenter les ports non secrets.

**Terminé lorsque :** les services démarrent sans configuration codée en dur ni exposition publique de la base.

### B-004 — Contexte de sécurité et tenancy

- Valider signature, émetteur, audience, expiration et algorithme des JWT.
- Dériver le contexte tenant et utilisateur côté serveur ; ne jamais le recevoir comme autorité depuis le corps de requête.
- Définir une couche d’autorisation métier testable pour RBAC, ABAC et contrôle objet.
- Configurer RLS si la stratégie partagée est validée, et utiliser un rôle DB applicatif à privilèges limités.

**Terminé lorsque :** les tests prouvent les refus sans jeton, avec jeton invalide, hors tenant et hors rôle.

### B-005 — Audit et données minimales

- Introduire un schéma d’audit append-only avec identité, tenant, ressource, action, résultat, corrélation et justification.
- Modéliser organisations, appartenances et patient minimal, avec provenance des données.
- Définir le lien entre décision d’accès, consentement et événement d’audit.

**Terminé lorsque :** une lecture autorisée et une tentative refusée génèrent des traces métier minimisées.

### B-006 — Premier vertical slice

- Authentification Keycloak.
- Appartenance organisationnelle et tenant actif.
- Patient minimal isolé par tenant.
- Lecture contrôlée par rôle, contexte et consentement.
- Audit de succès et de refus.

**Terminé lorsque :** les tests d’intégration avec PostgreSQL et Keycloak démontrent le flux nominal ainsi que les principaux abus bloqués.

## Critères de sécurité de sortie

- Aucun mot de passe, jeton ou secret dans Git, les journaux ou les réponses API.
- Aucun accès client direct à PostgreSQL, stockage objet ou contenu clinique.
- Aucun endpoint métier sans validation d’identité et autorisation objet/tenant.
- Les tests ne contiennent que des données synthétiques.
- Les dépendances, licences et vulnérabilités sont revues dans la chaîne de livraison.

## Limites assumées du premier slice

L’IA, DICOM, FHIR, pharmacie, paiement, livraison, messagerie, téléconsultation, offline et SMS/USSD seront documentés comme interfaces futures. Ils ne seront pas simulés comme fonctionnels afin d’éviter une fausse promesse de sécurité ou de conformité.
