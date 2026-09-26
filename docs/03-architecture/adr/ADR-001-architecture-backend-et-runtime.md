# ADR-001 — Architecture backend et runtime Jakarta EE

**Statut :** `[DÉCIDÉ]`  
**Date :** 2026-09-26  
**Décideur :** utilisateur du projet MedScan Enterprise

---

## 1. Contexte

Le contexte de référence MedScan impose un backend principal fondé sur Jakarta EE : Jakarta REST/JAX-RS, CDI, JPA/Hibernate, Bean Validation, OpenAPI, OAuth2/OIDC et JWT. Le workspace initial était un prototype Spring Boot 4.1.1 limité à une route de démonstration et sans composant de sécurité, de persistance ou d’intégration.

Le choix du socle doit précéder toute API métier, table, contrôleur ou modèle de données afin de ne pas introduire de dette d’architecture et de sécurité.

## 2. Décision

**Le backend principal MedScan utilisera les spécifications Jakarta EE.**

Le prototype Spring Boot n’est pas retenu comme fondation de l’application. Il sera remplacé au moment du bootstrap technique par un socle Jakarta aligné sur l’ADR et les décisions de sécurité associées.

WildFly 41 est retenu comme runtime initial. La documentation officielle indique que WildFly standard supporte Jakarta EE 11 et requiert Java SE 17 ou supérieur ; il est donc compatible avec Java 21 retenu dans le projet. La version exacte de l’image ou de la distribution sera épinglée dans la configuration de déploiement, jamais remplacée par un tag non déterministe.

Quarkus reste une alternative future possible, mais n’est pas le runtime retenu pour le socle V1. Spring Boot est rejeté car il s’écarte de la décision Jakarta EE confirmée.

## 3. Conséquences

- Le `pom.xml` et les sources Spring Boot seront remplacés par une structure Jakarta compatible avec le runtime retenu.
- Les futures API utiliseront Jakarta REST, CDI et Bean Validation ; les DTO ne seront pas les entités de persistance.
- La persistance PostgreSQL, les migrations, la configuration externalisée, Keycloak et les tests d’intégration seront ajoutés après les ADR dépendants.
- La route de démonstration Spring ne fait pas partie du socle cible.
- Aucun code métier ne doit commencer avant la validation de la stratégie tenant et des prérequis de sécurité.

## 4. ADR dépendants

- ADR-002 — Keycloak comme fournisseur d’identité central.
- ADR-003 — PostgreSQL, migrations et stratégie de données.
- ADR-004 — Isolation multi-tenant.
- ADR-005 — Autorisation métier, consentement et Break-Glass.
- ADR-006 — Audit, observabilité et conservation.
- ADR-007 — Stockage objet, chiffrement et gestion des fichiers.
