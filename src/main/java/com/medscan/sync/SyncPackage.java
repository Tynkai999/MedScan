package com.medscan.sync;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.medscan.clinical.Consultation;
import com.medscan.clinical.Patient;
import com.medscan.clinical.Prescription;
import com.medscan.clinical.VitalSigns;

/**
 * Paquet de Données Cliniques Synchronisé / Coffre Hors-Ligne (MOD-10).
 * Contient l'ensemble des données requises pour le travail clinique hors-ligne
 * et la réconciliation différentielle (delta-sync).
 */
public record SyncPackage(
        Instant syncTimestamp,
        Instant sinceTimestamp,
        int totalRecords,
        List<Patient> patients,
        List<VitalSigns> vitalSigns,
        List<Consultation> consultations,
        List<Prescription> prescriptions,
        List<EmergencyCard> emergencyCards
) {
    public SyncPackage {
        if (patients == null) patients = Collections.emptyList();
        if (vitalSigns == null) vitalSigns = Collections.emptyList();
        if (consultations == null) consultations = Collections.emptyList();
        if (prescriptions == null) prescriptions = Collections.emptyList();
        if (emergencyCards == null) emergencyCards = Collections.emptyList();
        if (syncTimestamp == null) syncTimestamp = Instant.now();
        if (totalRecords <= 0) {
            totalRecords = patients.size() + vitalSigns.size() + consultations.size() + prescriptions.size();
        }
    }
}
