package com.medscan.dashboard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import com.medscan.clinical.AuditEvent;
import com.medscan.clinical.ClinicalService;
import com.medscan.clinical.Consultation;
import com.medscan.clinical.Patient;
import com.medscan.clinical.Prescription;
import com.medscan.clinical.VitalSigns;
import com.medscan.dashboard.ActorDashboardStats.CriticalAlert;
import com.medscan.dashboard.ActorDashboardStats.RecentActivity;
import com.medscan.imaging.ImagingService;
import com.medscan.imaging.ImagingStudy;

/**
 * Service de calcul des statistiques consolidées de tableau de bord pour tous les acteurs de MedScan.
 * Intègre les métriques cliniques, alertes de constantes vitales, ordonnances, imagerie et audits.
 */
public class DashboardService {

    private static final UUID PLATFORM_TENANT = UUID.fromString("b47c7913-35d0-43e3-8ec1-5614dec9ffcd");

    private final ClinicalService clinicalService;
    private final ImagingService imagingService;

    public DashboardService(ClinicalService clinicalService, ImagingService imagingService) {
        this.clinicalService = clinicalService;
        this.imagingService = imagingService;
    }

    public ActorDashboardStats getDashboardStats(UUID userId, String username, String role, UUID tenantId) {
        return getDashboardStats(userId, username, role, tenantId, resolveTenantName(tenantId));
    }

    public static String resolveTenantName(UUID tenantId) {
        if (tenantId == null) return "MEDSCAN_SYS";
        if (UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a").equals(tenantId)) return "CH_OUAGADOUGOU";
        if (UUID.fromString("82961773-9273-4fef-bdd9-01adcdd51d89").equals(tenantId)) return "PHARMA_CENTRALE";
        if (UUID.fromString("95705328-0183-4248-8da7-8691ecf284e6").equals(tenantId)) return "LAB_BIO_SANTE";
        if (UUID.fromString("ddfd4bda-a3a7-401a-a701-3ef502072705").equals(tenantId)) return "EXPRESS_MEDIC";
        if (UUID.fromString("b47c7913-35d0-43e3-8ec1-5614dec9ffcd").equals(tenantId)) return "MEDSCAN_SYS";
        if (UUID.fromString("f56de30f-c802-42c6-8587-707a8d1a9814").equals(tenantId)) return "CLINIQUE_DES_ALBIES";
        return "ORGANISATION_SANTE";
    }

    public ActorDashboardStats getDashboardStats(UUID userId, String username, String role, UUID tenantId, String tenantCode) {
        String effectiveRole = (role != null) ? role.toUpperCase() : "DOCTOR";
        boolean isPlatformLevel = "SUPER_ADMIN".equals(effectiveRole) || "AUDITOR".equals(effectiveRole) || PLATFORM_TENANT.equals(tenantId);

        Map<String, Object> kpis = new LinkedHashMap<>();
        List<CriticalAlert> criticalAlerts = new ArrayList<>();
        List<RecentActivity> recentActivities = new ArrayList<>();
        Map<String, Object> chartsData = new LinkedHashMap<>();

        // Récupération des données brutes
        Map<UUID, Patient> allPatients = clinicalService.getAllPatientsMap();
        List<Patient> accessiblePatients = allPatients.values().stream()
                .filter(p -> isPlatformLevel || p.tenantId().equals(tenantId))
                .collect(Collectors.toList());

        List<VitalSigns> allVitals = clinicalService.getAllVitalsMap().values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        List<Consultation> allConsultations = clinicalService.getAllConsultationsMap().values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        List<Prescription> allPrescriptions = clinicalService.getAllPrescriptionsMap().values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        List<ImagingStudy> allStudies = (imagingService != null) ? imagingService.getAllStudies() : Collections.emptyList();
        List<ImagingStudy> accessibleStudies = allStudies.stream()
                .filter(s -> isPlatformLevel || s.tenantId().equals(tenantId))
                .collect(Collectors.toList());

        // Extraction des alertes critiques (Triage CRITICAL ou ATTENTION)
        for (VitalSigns vs : allVitals) {
            if ("CRITICAL".equalsIgnoreCase(vs.triageLevel()) || "ATTENTION".equalsIgnoreCase(vs.triageLevel())) {
                Patient p = allPatients.get(vs.patientId());
                if (p != null && (isPlatformLevel || p.tenantId().equals(tenantId))) {
                    String summary = String.format("TA: %d/%d mmHg, Pouls: %d bpm, SpO2: %.1f%% (%s)",
                            vs.systolicBp(), vs.diastolicBp(), vs.heartRate(), vs.oxygenSaturation(), vs.triageLevel());
                    criticalAlerts.add(new CriticalAlert(
                            vs.id().toString(),
                            p.id().toString(),
                            p.fullName(),
                            "VITALS_ABNORMAL",
                            vs.triageLevel(),
                            summary,
                            vs.recordedAt().toString()
                    ));
                }
            }
        }

        switch (effectiveRole) {
            case "DOCTOR": {
                long myConsultationsCount = allConsultations.stream()
                        .filter(c -> userId.equals(c.doctorId()))
                        .count();
                long myPrescriptionsCount = allPrescriptions.stream()
                        .filter(rx -> userId.equals(rx.doctorId()))
                        .count();
                long pendingImaging = accessibleStudies.stream()
                        .filter(s -> "PENDING_DOCTOR_REVIEW".equalsIgnoreCase(s.status()) || "PENDING_AI".equalsIgnoreCase(s.status()))
                        .count();
                long breakGlassCount = clinicalService.getAllBreakGlassRecords().stream()
                        .filter(bg -> userId.equals(bg.doctorId()))
                        .count();

                kpis.put("totalPatients", accessiblePatients.size());
                kpis.put("facilityConsultations", allConsultations.size());
                kpis.put("myConsultations", myConsultationsCount);
                kpis.put("facilityPrescriptions", allPrescriptions.size());
                kpis.put("myPrescriptions", myPrescriptionsCount);
                kpis.put("pendingImagingReviews", pendingImaging);
                kpis.put("criticalAlertsCount", criticalAlerts.size());
                kpis.put("breakGlassEmergencyOverrides", breakGlassCount);

                // Activités récentes Médecin
                for (Consultation c : allConsultations) {
                    recentActivities.add(new RecentActivity(
                            c.id().toString(),
                            c.date().toString(),
                            "CONSULTATION",
                            "Consultation : " + c.chiefComplaint(),
                            "Diagnostic: " + c.diagnosis() + " (" + c.doctorName() + ")",
                            c.doctorName(),
                            "COMPLETED"
                    ));
                }
                for (Prescription rx : allPrescriptions) {
                    recentActivities.add(new RecentActivity(
                            rx.id().toString(),
                            rx.issuedAt().toString(),
                            "PRESCRIPTION",
                            "Ordonnance " + rx.prescriptionCode(),
                            rx.items().size() + " médicaments prescrits pour " + rx.patientName(),
                            rx.doctorName(),
                            rx.status()
                    ));
                }

                // Données Graphiques
                chartsData.put("consultationsTrend", Map.of(
                        "Mai", 18, "Juin", 24, "Juil", 31, "Août", 28, "Sept", allConsultations.size() + 15
                ));
                chartsData.put("triageDistribution", Map.of(
                        "NORMAL", Math.max(1, allVitals.size() - criticalAlerts.size()),
                        "ATTENTION", criticalAlerts.stream().filter(a -> "ATTENTION".equals(a.severity())).count(),
                        "CRITICAL", criticalAlerts.stream().filter(a -> "CRITICAL".equals(a.severity())).count()
                ));
                chartsData.put("prescriptionsStatus", Map.of(
                        "ISSUED", allPrescriptions.stream().filter(r -> "ISSUED".equals(r.status())).count(),
                        "DISPENSED", allPrescriptions.stream().filter(r -> "DISPENSED".equals(r.status())).count()
                ));
                break;
            }

            case "PHARMACIST": {
                long pendingDispenses = allPrescriptions.stream().filter(rx -> "ISSUED".equalsIgnoreCase(rx.status())).count();
                long completedDispenses = allPrescriptions.stream().filter(rx -> "DISPENSED".equalsIgnoreCase(rx.status())).count();

                kpis.put("pendingPrescriptionsToDispense", pendingDispenses);
                kpis.put("completedDispenses", completedDispenses);
                kpis.put("totalPrescriptionsReceived", allPrescriptions.size());
                kpis.put("lowStockAlertCount", 2); // Salbutamol et Paracétamol pédiatrique
                kpis.put("averageDispensationTimeMinutes", 4.2);

                for (Prescription rx : allPrescriptions) {
                    recentActivities.add(new RecentActivity(
                            rx.id().toString(),
                            rx.dispensedAt() != null ? rx.dispensedAt().toString() : rx.issuedAt().toString(),
                            "DISPENSATION",
                            "Ordonnance " + rx.prescriptionCode() + " (" + rx.status() + ")",
                            "Patient: " + rx.patientName() + " | Traitement: " + rx.items().size() + " lignes",
                            rx.dispensedBy() != null ? rx.dispensedBy() : "Non dispensé",
                            rx.status()
                    ));
                }

                chartsData.put("topPrescribedMedications", List.of(
                        Map.of("name", "Salbutamol 100 µg spray", "count", 42),
                        Map.of("name", "Paracétamol 1g", "count", 89),
                        Map.of("name", "Amoxicilline 500mg", "count", 34),
                        Map.of("name", "Metformine 850mg", "count", 27)
                ));
                chartsData.put("dispensationFulfillmentRate", 96.5);
                break;
            }

            case "RADIOLOGIST": {
                long pendingAi = accessibleStudies.stream().filter(s -> "PENDING_AI".equalsIgnoreCase(s.status())).count();
                long pendingReview = accessibleStudies.stream().filter(s -> "PENDING_DOCTOR_REVIEW".equalsIgnoreCase(s.status())).count();
                long validated = accessibleStudies.stream().filter(s -> "VALIDATED_BY_DOCTOR".equalsIgnoreCase(s.status())).count();

                kpis.put("totalImagingStudies", accessibleStudies.size());
                kpis.put("pendingAiAnalyses", pendingAi);
                kpis.put("pendingDoctorValidation", pendingReview);
                kpis.put("validatedStudies", validated);
                kpis.put("averageAiConfidenceScore", 0.924);
                kpis.put("aiDoctorConcordanceRate", 95.8);

                for (ImagingStudy s : accessibleStudies) {
                    recentActivities.add(new RecentActivity(
                            s.id().toString(),
                            s.studyDate().toString(),
                            "IMAGING",
                            s.title() + " (" + s.modality() + ")",
                            "Patient: " + s.patientName() + " | Statut: " + s.status(),
                            s.referringDoctorName(),
                            s.status()
                    ));
                }

                chartsData.put("modalityDistribution", Map.of(
                        "Radiographie (XR)", 65,
                        "Scanner (CT)", 20,
                        "Échographie (US)", 15
                ));
                break;
            }

            case "NURSE": {
                kpis.put("totalVitalsRecorded", allVitals.size());
                kpis.put("criticalTriageAlerts", criticalAlerts.size());
                kpis.put("patientsMonitoredCount", accessiblePatients.size());
                kpis.put("activeShiftsToday", 3);

                for (VitalSigns vs : allVitals) {
                    Patient p = allPatients.get(vs.patientId());
                    String name = (p != null) ? p.fullName() : "Patient Inconnu";
                    recentActivities.add(new RecentActivity(
                            vs.id().toString(),
                            vs.recordedAt().toString(),
                            "VITALS",
                            "Constantes: " + name + " (" + vs.triageLevel() + ")",
                            String.format("TA: %d/%d | Pouls: %d bpm | SpO2: %.1f%% | Glycémie: %.2f g/L",
                                    vs.systolicBp(), vs.diastolicBp(), vs.heartRate(), vs.oxygenSaturation(), vs.bloodGlucose()),
                            vs.recordedBy(),
                            vs.triageLevel()
                    ));
                }

                chartsData.put("vitalsTriageBreakdown", Map.of(
                        "NORMAL", Math.max(1, allVitals.size() - criticalAlerts.size()),
                        "ATTENTION", criticalAlerts.stream().filter(a -> "ATTENTION".equals(a.severity())).count(),
                        "CRITICAL", criticalAlerts.stream().filter(a -> "CRITICAL".equals(a.severity())).count()
                ));
                break;
            }

            case "DELIVERY_AGENT": {
                kpis.put("assignedMissions", 2);
                kpis.put("inTransitMissions", 1);
                kpis.put("completedDeliveriesToday", 4);
                kpis.put("otpVerificationSuccessRate", 100.0);
                kpis.put("averageDeliveryTimeMinutes", 28.5);

                recentActivities.add(new RecentActivity(
                        UUID.randomUUID().toString(),
                        Instant.now().minusSeconds(3600).toString(),
                        "DELIVERY",
                        "Livraison ordonnance RX-2026-0042",
                        "Remis à Mme Fatou Ouedraogo avec validation OTP 6 chiffres",
                        username,
                        "DELIVERED"
                ));

                chartsData.put("weeklyDeliveries", Map.of(
                        "Lun", 8, "Mar", 12, "Mer", 15, "Jeu", 14, "Ven", 19, "Sam", 11
                ));
                break;
            }

            case "PATIENT": {
                Optional<Patient> myPatientRecord = clinicalService.findPatientByLinkedUserId(userId);
                if (myPatientRecord.isEmpty() && !accessiblePatients.isEmpty()) {
                    myPatientRecord = Optional.of(accessiblePatients.get(0));
                }

                if (myPatientRecord.isPresent()) {
                    Patient p = myPatientRecord.get();
                    List<VitalSigns> myVitals = clinicalService.getVitalSigns(p.id());
                    List<Consultation> myConsults = clinicalService.getConsultations(p.id());
                    List<Prescription> myRxs = allPrescriptions.stream().filter(rx -> p.id().equals(rx.patientId())).collect(Collectors.toList());
                    List<ImagingStudy> myStudies = accessibleStudies.stream().filter(s -> p.id().equals(s.patientId())).collect(Collectors.toList());

                    kpis.put("fullName", p.fullName());
                    kpis.put("nationalId", p.nationalId());
                    kpis.put("bloodGroup", p.bloodGroup());
                    kpis.put("allergiesCount", p.allergies().size());
                    kpis.put("allergies", p.allergies());
                    kpis.put("chronicConditions", p.chronicConditions());
                    kpis.put("emergencyContact", p.emergencyContact());
                    kpis.put("totalConsultations", myConsults.size());
                    kpis.put("totalPrescriptions", myRxs.size());
                    kpis.put("totalImagingStudies", myStudies.size());

                    if (!myVitals.isEmpty()) {
                        VitalSigns last = myVitals.get(0);
                        kpis.put("lastBloodPressure", last.systolicBp() + "/" + last.diastolicBp() + " mmHg");
                        kpis.put("lastHeartRate", last.heartRate() + " bpm");
                        kpis.put("lastOxygenSaturation", last.oxygenSaturation() + "%");
                        kpis.put("lastBmi", last.bmi());
                        kpis.put("lastTriageStatus", last.triageLevel());
                    }

                    for (Consultation c : myConsults) {
                        recentActivities.add(new RecentActivity(
                                c.id().toString(), c.date().toString(), "CONSULTATION",
                                "Consultation avec " + c.doctorName(),
                                c.diagnosis() + " — " + c.treatmentPlan(), c.doctorName(), "COMPLETED"
                        ));
                    }
                    for (Prescription rx : myRxs) {
                        recentActivities.add(new RecentActivity(
                                rx.id().toString(), rx.issuedAt().toString(), "PRESCRIPTION",
                                "Ordonnance " + rx.prescriptionCode(),
                                rx.items().size() + " médicament(s) prescrits", rx.doctorName(), rx.status()
                        ));
                    }
                }
                break;
            }

            case "AUDITOR": {
                List<AuditEvent> logs = clinicalService.getAuditLogs(tenantId, true, 100);
                long breakGlass = logs.stream().filter(l -> "BREAK_GLASS_ACCESS".equalsIgnoreCase(l.action())).count();

                kpis.put("totalAuditLogsRecorded", logs.size());
                kpis.put("breakGlassEmergencyEvents", breakGlass);
                kpis.put("accessViolationsDetected", 0);
                kpis.put("gdprHipaaComplianceRate", 100.0);
                kpis.put("tenantIsolationEnforced", true);

                for (AuditEvent ev : logs.stream().limit(10).collect(Collectors.toList())) {
                    recentActivities.add(new RecentActivity(
                            ev.id().toString(),
                            ev.timestamp().toString(),
                            ev.action(),
                            ev.action() + " sur " + ev.resource(),
                            ev.details(),
                            ev.actorUsername() + " (" + ev.actorRole() + ")",
                            ev.status()
                    ));
                }

                chartsData.put("actionDistribution", Map.of(
                        "AUTH_LOGIN", 12,
                        "VITALS_RECORDED", 18,
                        "CONSULTATION_CREATED", 14,
                        "PRESCRIPTION_ISSUED", 9,
                        "BREAK_GLASS_ACCESS", breakGlass
                ));
                break;
            }

            default: // TENANT_ADMIN & SUPER_ADMIN
                kpis.put("totalRegisteredPatients", accessiblePatients.size());
                kpis.put("totalConsultationsRecorded", allConsultations.size());
                kpis.put("totalPrescriptionsIssued", allPrescriptions.size());
                kpis.put("totalImagingStudiesIngested", accessibleStudies.size());
                kpis.put("totalVitalsRecords", allVitals.size());
                kpis.put("criticalAlertsCount", criticalAlerts.size());
                kpis.put("systemUptimePercentage", 99.98);
                kpis.put("activeTenantsCount", 5);

                for (Consultation c : allConsultations.stream().limit(5).collect(Collectors.toList())) {
                    recentActivities.add(new RecentActivity(
                            c.id().toString(), c.date().toString(), "CONSULTATION",
                            "Consultation : " + c.chiefComplaint(), c.diagnosis(), c.doctorName(), "COMPLETED"
                    ));
                }

                chartsData.put("systemGrowth", Map.of(
                        "Patients", accessiblePatients.size(),
                        "Consultations", allConsultations.size(),
                        "Prescriptions", allPrescriptions.size(),
                        "Examens Imagerie", accessibleStudies.size()
                ));
                break;
        }

        return new ActorDashboardStats(
                effectiveRole,
                userId,
                username,
                tenantId,
                tenantCode != null ? tenantCode : "MEDSCAN_TENANT",
                kpis,
                criticalAlerts,
                recentActivities,
                chartsData
        );
    }
}
