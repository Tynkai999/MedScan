-- =====================================================================
-- Migration V5 : Biométrie, Calcul Automatique de l'IMC & Champs Personnalisés
-- MOD-03 (Dossier Médical Dynamique) & Notes Cliniques de Consultation
-- Conforme : RLS Multi-Tenant, Zero Trust, ISO 27001, HIPAA
-- =====================================================================

-- 1. Enrichissement de la table medscan.patient
ALTER TABLE medscan.patient
    ADD COLUMN IF NOT EXISTS age INT,
    ADD COLUMN IF NOT EXISTS weight_kg NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS height_cm NUMERIC(5,1),
    ADD COLUMN IF NOT EXISTS bmi NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS bmi_category VARCHAR(64),
    ADD COLUMN IF NOT EXISTS custom_fields JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN medscan.patient.age IS 'Âge du patient calculé automatiquement ou saisi';
COMMENT ON COLUMN medscan.patient.weight_kg IS 'Poids du patient en kilogrammes';
COMMENT ON COLUMN medscan.patient.height_cm IS 'Taille du patient en centimètres';
COMMENT ON COLUMN medscan.patient.bmi IS 'Indice de Masse Corporelle calculé automatiquement (kg/m²)';
COMMENT ON COLUMN medscan.patient.bmi_category IS 'Classification clinique OMS de la corpulence';
COMMENT ON COLUMN medscan.patient.custom_fields IS 'Champs personnalisés dynamiques clé-valeur pour adapter le dossier au profil patient';
COMMENT ON COLUMN medscan.patient.updated_at IS 'Date et heure de la dernière mise à niveau des données du patient';

-- 2. Enrichissement de la table medscan.consultation
ALTER TABLE medscan.consultation
    ADD COLUMN IF NOT EXISTS weight_kg NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS height_cm NUMERIC(5,1),
    ADD COLUMN IF NOT EXISTS bmi NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS bmi_category VARCHAR(64),
    ADD COLUMN IF NOT EXISTS systolic_bp INT,
    ADD COLUMN IF NOT EXISTS diastolic_bp INT,
    ADD COLUMN IF NOT EXISTS heart_rate INT,
    ADD COLUMN IF NOT EXISTS temperature NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS oxygen_saturation NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS custom_fields JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN medscan.consultation.bmi IS 'Indice de Masse Corporelle calculé en direct lors de la consultation';
COMMENT ON COLUMN medscan.consultation.bmi_category IS 'Classification clinique OMS du patient au moment de l examen';
COMMENT ON COLUMN medscan.consultation.custom_fields IS 'Champs cliniques personnalisés saisis par le médecin lors de la consultation';

-- 3. Enrichissement de la table medscan.vital_signs
ALTER TABLE medscan.vital_signs
    ADD COLUMN IF NOT EXISTS bmi_category VARCHAR(64),
    ADD COLUMN IF NOT EXISTS custom_fields JSONB NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN medscan.vital_signs.bmi_category IS 'Classification de l IMC au moment de la prise de constantes';
COMMENT ON COLUMN medscan.vital_signs.custom_fields IS 'Champs personnalisés saisis lors de la relève des constantes';
