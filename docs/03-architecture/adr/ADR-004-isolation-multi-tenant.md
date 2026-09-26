# ADR-004 — Isolation multi-tenant par schéma partagé et RLS

**Statut :** `[DÉCIDÉ]`  
**Date :** 2026-09-26

## Décision

MedScan utilise initialement une base PostgreSQL et un schéma partagé. Chaque donnée appartenant à une organisation contient un `tenant_id` obligatoire. PostgreSQL Row-Level Security (RLS) applique l’isolation au niveau données, tandis que le backend applique en complément les contrôles d’autorisation métier.

Le contexte tenant est établi exclusivement côté serveur à partir de l’identité validée et de l’appartenance active. Il ne peut jamais être imposé par un champ, paramètre ou en-tête client non vérifié.

## Motivation

Ce modèle fournit une isolation en profondeur tout en évitant le coût d’exploitation des schémas ou bases par organisation au stade initial. Il est compatible avec une évolution vers une stratégie plus isolée si les obligations réglementaires, contractuelles ou de volumétrie l’exigent.

## Conséquences

- Chaque table tenant-aware possède une colonne `tenant_id` non nulle, indexée et contrôlée par clé étrangère lorsque pertinent.
- Chaque transaction de données tenant-aware définit le contexte PostgreSQL requis par les politiques RLS.
- Les requêtes administratives inter-tenant ne sont jamais implicites et utilisent un rôle, une justification et un audit dédiés.
- Les tests couvrent les succès intra-tenant et les refus inter-tenant, y compris en cas de manipulation d’identifiant.
- Les caches, fichiers, événements et intégrations externes portent également un contexte tenant contrôlé.

## Alternatives rejetées pour V1

- Schéma PostgreSQL par tenant : plus coûteux pour migrations et supervision.
- Base PostgreSQL par tenant : isolation forte mais coût d’exploitation disproportionné avant validation des besoins.

Ces alternatives restent réévaluables si une obligation de souveraineté, un client institutionnel ou un niveau de séparation contractuelle l’impose.
