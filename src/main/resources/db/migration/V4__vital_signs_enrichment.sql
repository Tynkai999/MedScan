-- =====================================================================
-- Migration V4 : Enrichissement des Constantes Vitales & Tableau de Bord
-- MOD-03 (Dossier Médical Longitudinal) & Vue Clinique d'Urgence
-- =====================================================================

ALTER TABLE medscan.vital_signs
    ADD COLUMN IF NOT EXISTS height_cm NUMERIC(5,1),
    ADD COLUMN IF NOT EXISTS bmi NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS oxygen_saturation NUMERIC(4,1),
    ADD COLUMN IF NOT EXISTS respiratory_rate INT,
    ADD COLUMN IF NOT EXISTS pain_scale INT,
    ADD COLUMN IF NOT EXISTS blood_group VARCHAR(16),
    ADD COLUMN IF NOT EXISTS allergies TEXT[],
    ADD COLUMN IF NOT EXISTS chronic_conditions TEXT[],
    ADD COLUMN IF NOT EXISTS emergency_contact VARCHAR(256),
    ADD COLUMN IF NOT EXISTS triage_level VARCHAR(32) DEFAULT 'NORMAL',
    ADD COLUMN IF NOT EXISTS notes TEXT;

COMMENT ON COLUMN medscan.vital_signs.blood_group IS 'Groupe sanguin du patient (A+, O+, etc.)';
COMMENT ON COLUMN medscan.vital_signs.allergies IS 'Allergies critiques connues pour la sécurité des soins';
COMMENT ON COLUMN medscan.vital_signs.oxygen_saturation IS 'Saturation pulsée en oxygène SpO2 en pourcentage';
COMMENT ON COLUMN medscan.vital_signs.bmi IS 'Indice de masse corporelle calculé';
COMMENT ON COLUMN medscan.vital_signs.triage_level IS 'Niveau de triage d urgence clinique (NORMAL, ATTENTION, CRITICAL)';
