package com.medscan.interop.fhir;

/**
 * Constantes et référentiels de terminologie médicale HL7 FHIR R4 (MOD-07).
 * Conforme aux standards internationaux LOINC, RxNorm, UCUM et HL7 Terminology.
 */
public final class FhirConstants {

    private FhirConstants() {}

    public static final String FHIR_VERSION = "4.0.1";
    public static final String MIME_FHIR_JSON = "application/fhir+json; charset=UTF-8";
    public static final String MIME_JSON = "application/json; charset=UTF-8";

    // Systèmes de codification terminologiques officiels
    public static final String SYSTEM_LOINC = "http://loinc.org";
    public static final String SYSTEM_RXNORM = "http://www.nlm.nih.gov/research/umls/rxnorm";
    public static final String SYSTEM_UCUM = "http://unitsofmeasure.org";
    public static final String SYSTEM_OBS_CATEGORY = "http://terminology.hl7.org/CodeSystem/observation-category";
    public static final String SYSTEM_MED_STATUS = "http://hl7.org/fhir/CodeSystem/medicationrequest-status";
    public static final String SYSTEM_MED_INTENT = "http://hl7.org/fhir/CodeSystem/medicationrequest-intent";
    public static final String SYSTEM_CONTACT_ROLE = "http://terminology.hl7.org/CodeSystem/v2-0131";
    public static final String SYSTEM_NATIONAL_ID = "urn:oid:medscan:national-id";
    public static final String SYSTEM_PRESCRIPTION_CODE = "urn:oid:medscan:prescription-code";

    // Extensions FHIR personnalisées MedScan
    public static final String EXT_BLOOD_GROUP = "http://medscan.org/fhir/StructureDefinition/blood-group";
    public static final String EXT_ALLERGIES = "http://medscan.org/fhir/StructureDefinition/allergies";
    public static final String EXT_CHRONIC = "http://medscan.org/fhir/StructureDefinition/chronic-conditions";
    public static final String EXT_BMI = "http://medscan.org/fhir/StructureDefinition/bmi";
    public static final String EXT_BMI_CATEGORY = "http://medscan.org/fhir/StructureDefinition/bmi-category";

    // Codes LOINC majeurs pour les constantes vitales & biométriques
    public static final String LOINC_BP_PANEL = "85354-9";       // Blood pressure panel
    public static final String LOINC_BP_SYSTOLIC = "8480-6";     // Systolic blood pressure
    public static final String LOINC_BP_DIASTOLIC = "8462-4";    // Diastolic blood pressure
    public static final String LOINC_HEART_RATE = "8867-4";      // Heart rate
    public static final String LOINC_BODY_TEMP = "8310-5";       // Body temperature
    public static final String LOINC_BODY_WEIGHT = "29463-7";    // Body weight
    public static final String LOINC_BODY_HEIGHT = "8302-2";     // Body height
    public static final String LOINC_BMI = "39156-5";            // Body mass index
    public static final String LOINC_SPO2 = "2708-6";            // Oxygen saturation in arterial blood
    public static final String LOINC_RESP_RATE = "9279-1";       // Respiratory rate
    public static final String LOINC_GLUCOSE = "2339-0";         // Glucose in blood
}
