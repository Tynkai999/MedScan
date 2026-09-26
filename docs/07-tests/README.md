# Stratégie de Tests et d'Assurance Qualité — MedScan Enterprise

**Statut du dossier** : `[EN COURS]`  
**Date cible d'exécution** : Jour 6

---

## 1. Pyramide et Typologie des Tests

Conformément au Prompt Maître, aucune fonctionnalité n'est validée sans sa suite de tests complète :

1. **Tests Unitaires (JUnit 5 + Mockito)** :
   - Règles métier pures, calculs de scores probabilistes MPI, validation de schémas, politiques d'accès ABAC.
2. **Tests d'Intégration (Testcontainers + PostgreSQL + Keycloak)** :
   - Persistance JPA/Hibernate, exécution des contraintes multi-tenants RLS, validation des requêtes d'audit.
3. **Tests de Sécurité Spécifiques** :
   - Tests d'isolation inter-tenants (Tentative d'accès Org A vers Org B).
   - Tests IDOR (Patient A accédant au dossier de Patient B).
   - Tests de rejeu et d'altération de jetons JWT.
   - Tests d'injection SQL et upload malveillant.
4. **Tests d'Interopérabilité** :
   - Validation de conformité des ressources FHIR R4 via validateur HAPI.
   - Parsing et génération des segments HL7 v2 (MSH, PID, OBR, OBX).
5. **Tests de Résilience Offline** :
   - Simulation de coupures réseau, résolution de conflits de synchronisation, intégrité des données locales chiffrées.
