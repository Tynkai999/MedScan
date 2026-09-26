# ADR-003 — PostgreSQL et migrations versionnées

**Statut :** `[DÉCIDÉ]`  
**Date :** 2026-09-26

## Décision

PostgreSQL est le système de données relationnel principal de MedScan. Toute évolution de schéma est effectuée par des migrations versionnées et reproductibles ; l’application ne crée ni ne modifie implicitement le schéma en production.

Flyway est retenu pour exécuter les migrations au démarrage contrôlé des environnements et dans la chaîne de livraison. Les versions de base, pilote et outil de migration seront épinglées dans le build.

## Motivation

Le domaine contient des relations fortement contraintes, des transactions cliniques, de l’audit et une exigence d’isolation multi-tenant. PostgreSQL fournit les contraintes relationnelles, les transactions et les politiques RLS nécessaires au modèle retenu.

## Conséquences de sécurité

- La base de données n’est jamais exposée directement à Internet.
- Les accès applicatifs et de migration utilisent des comptes à privilèges séparés.
- Les contraintes, index, clés étrangères et politiques RLS font partie des migrations revues.
- Les sauvegardes, le chiffrement au repos, la rétention et la restauration feront l’objet d’un ADR d’exploitation avant toute donnée réelle.
- Les données de santé de production ne sont jamais utilisées dans les environnements de test.

## Alternatives rejetées

- Gestion automatique du schéma par ORM : rejetée, car insuffisamment traçable et risquée en production.
- NoSQL comme source de vérité clinique : rejetée pour le noyau relationnel à forte exigence d’intégrité.
