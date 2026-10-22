package com.medscan.lab;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.medscan.clinical.ClinicalService;
import com.medscan.clinical.Patient;
import com.medscan.interop.fhir.FhirConstants;
import com.medscan.security.jwt.JsonHelper;

/**
 * Service central du Laboratoire d'Analyses Médicales & Biologie Clinique (MOD-13).
 * Assure la traçabilité des prélèvements, la saisie des résultats, la détection des valeurs
 * critiques avec alerte DPO/médecin, et l'exportation au standard HL7 FHIR R4 Laboratory Observation.
 */
public class LabService {

    private final ClinicalService clinicalService;
    private final Map<UUID, LabOrder> ordersById = new ConcurrentHashMap<>();
    private final Map<String, LabOrder> ordersByCode = new ConcurrentHashMap<>();
    private final Map<UUID, List<LabOrder>> ordersByPatientId = new ConcurrentHashMap<>();
    private final AtomicInteger orderSequence = new AtomicInteger(100);

    private static final UUID HOSPITAL_TENANT = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
    private static final UUID PATIENT_FATOU_ID = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e");
    private static final UUID PATIENT_IBRAHIM_ID = UUID.fromString("dababb25-8dc9-402c-b527-15d9a4b1f380");
    private static final UUID DOCTOR_TRAORE_ID = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");
    private static final UUID LAB_TECH_ID = UUID.fromString("1e4a2c5b-8d3f-4e6a-9b7c-0a1d2e3f4a5b");

    public LabService(ClinicalService clinicalService) {
        this.clinicalService = (clinicalService != null) ? clinicalService : new ClinicalService();
        initSeedLabOrders();
    }

    public LabService() {
        this(new ClinicalService());
    }

    /**
     * Prescription d'une analyse biologique par un médecin (DOCTOR).
     */
    public LabOrder createLabOrder(UUID patientId, String testCategory, String testName,
                                   String priority, String clinicalIndication, UUID prescriberId,
                                   String prescriberName, UUID prescriberTenantId) {
        if (patientId == null || testName == null || testName.isBlank()) {
            throw new IllegalArgumentException("Patient et intitulé de l'analyse requis.");
        }

        Patient patient = clinicalService.findPatientById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable: " + patientId));

        String code = "LAB-2026-" + String.format("%04d", orderSequence.incrementAndGet());

        LabOrder order = new LabOrder(
                UUID.randomUUID(),
                code,
                prescriberTenantId != null ? prescriberTenantId : HOSPITAL_TENANT,
                patientId,
                patient.fullName(),
                prescriberId != null ? prescriberId : DOCTOR_TRAORE_ID,
                prescriberName != null ? prescriberName : "Médecin Prescripteur",
                null,
                null,
                testCategory != null ? testCategory.toUpperCase() : "BIOCHEMISTRY",
                testName,
                priority != null ? priority.toUpperCase() : "ROUTINE",
                "ORDERED",
                clinicalIndication,
                null,
                null,
                Collections.emptyList(),
                false,
                Instant.now()
        );

        saveOrder(order);

        clinicalService.logAudit(prescriberId, prescriberName, "DOCTOR", order.tenantId(),
                "LAB_ORDER_CREATED", "LabOrder", order.orderCode(), "SUCCESS",
                "Prescription d'analyse biologique " + order.orderCode() + " (" + order.testName() + ") pour " + patient.fullName());

        return order;
    }

    /**
     * Prélèvement de l'échantillon sanguin / biologique par un infirmier ou technicien.
     */
    public LabOrder collectSample(UUID orderId, UUID actorId, String actorUsername, String actorRole, UUID actorTenantId) {
        LabOrder existing = getOrderOrThrow(orderId);
        LabOrder updated = existing.withSampleCollected(Instant.now());
        saveOrder(updated);

        clinicalService.logAudit(actorId, actorUsername, actorRole, actorTenantId,
                "LAB_SAMPLE_COLLECTED", "LabOrder", existing.orderCode(), "SUCCESS",
                "Prélèvement biologique effectué pour le dossier " + existing.orderCode());

        return updated;
    }

    /**
     * Saisie et validation officielle des résultats par le technicien de laboratoire (LAB_TECHNICIAN).
     */
    public LabOrder validateResults(UUID orderId, UUID technicianId, String technicianName,
                                    List<LabResultItem> results, UUID actorTenantId) {
        LabOrder existing = getOrderOrThrow(orderId);
        LabOrder validated = existing.withResultsValidated(technicianId, technicianName, results, Instant.now());
        saveOrder(validated);

        String alertDetail = validated.hasCriticalAlert() ? " [ALERTE CRITIQUE DÉTECTÉE]" : "";

        clinicalService.logAudit(technicianId, technicianName, "LAB_TECHNICIAN", actorTenantId,
                "LAB_RESULTS_VALIDATED", "LabOrder", existing.orderCode(), "SUCCESS",
                "Validation officielle des résultats de biologie médicale pour " + existing.orderCode() + alertDetail);

        return validated;
    }

    public Optional<LabOrder> findById(UUID id) {
        return Optional.ofNullable(ordersById.get(id));
    }

    public Optional<LabOrder> findByCode(String code) {
        return Optional.ofNullable(ordersByCode.get(code));
    }

    public List<LabOrder> listOrdersForPatient(UUID patientId) {
        return ordersByPatientId.getOrDefault(patientId, Collections.emptyList()).stream()
                .sorted(Comparator.comparing(LabOrder::createdAt).reversed())
                .collect(Collectors.toList());
    }

    public List<LabOrder> listPendingOrders(UUID tenantId) {
        return ordersById.values().stream()
                .filter(o -> tenantId == null || o.tenantId().equals(tenantId))
                .filter(o -> "ORDERED".equalsIgnoreCase(o.status()) || "SAMPLE_COLLECTED".equalsIgnoreCase(o.status()) || "IN_ANALYSIS".equalsIgnoreCase(o.status()))
                .sorted(Comparator.comparing(LabOrder::createdAt))
                .collect(Collectors.toList());
    }

    public List<LabOrder> listAllOrders(UUID tenantId) {
        return ordersById.values().stream()
                .filter(o -> tenantId == null || o.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(LabOrder::createdAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Exporte les résultats d'un examen de laboratoire au standard HL7 FHIR R4 (catégorie: laboratory).
     */
    public String toFhirLaboratoryBundle(LabOrder order, String baseUrl) {
        String base = (baseUrl != null) ? baseUrl.replaceAll("/+$", "") : "https://medscan-sluw.onrender.com/medscan/api/fhir/r4";
        List<String> observationEntries = new ArrayList<>();

        if (order.results() != null) {
            for (LabResultItem item : order.results()) {
                String obsJson = "{\n" +
                        "  \"resourceType\": \"Observation\",\n" +
                        "  \"id\": \"" + item.id() + "\",\n" +
                        "  \"status\": \"final\",\n" +
                        "  \"category\": [\n" +
                        "    {\n" +
                        "      \"coding\": [\n" +
                        "        {\n" +
                        "          \"system\": \"http://terminology.hl7.org/CodeSystem/observation-category\",\n" +
                        "          \"code\": \"laboratory\",\n" +
                        "          \"display\": \"Laboratory\"\n" +
                        "        }\n" +
                        "      ]\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"code\": {\n" +
                        "    \"coding\": [\n" +
                        "      {\n" +
                        "        \"system\": \"" + FhirConstants.SYSTEM_LOINC + "\",\n" +
                        "        \"code\": \"" + (item.loincCode() != null ? item.loincCode() : "unknown") + "\",\n" +
                        "        \"display\": \"" + JsonHelper.escape(item.paramName()) + "\"\n" +
                        "      }\n" +
                        "    ],\n" +
                        "    \"text\": \"" + JsonHelper.escape(item.paramName()) + "\"\n" +
                        "  },\n" +
                        "  \"subject\": {\n" +
                        "    \"reference\": \"Patient/" + order.patientId() + "\",\n" +
                        "    \"display\": \"" + JsonHelper.escape(order.patientName()) + "\"\n" +
                        "  },\n" +
                        "  \"effectiveDateTime\": \"" + (order.validatedAt() != null ? order.validatedAt() : Instant.now()) + "\",\n";

                if (item.numericValue() != null) {
                    obsJson += "  \"valueQuantity\": {\n" +
                            "    \"value\": " + item.numericValue() + ",\n" +
                            "    \"unit\": \"" + JsonHelper.escape(item.unit() != null ? item.unit() : "") + "\"\n" +
                            "  },\n";
                } else {
                    obsJson += "  \"valueString\": \"" + JsonHelper.escape(item.textValue() != null ? item.textValue() : "") + "\",\n";
                }

                if (item.refIntervalLow() != null || item.refIntervalHigh() != null) {
                    obsJson += "  \"referenceRange\": [\n" +
                            "    {\n" +
                            "      \"low\": { \"value\": " + item.refIntervalLow() + " },\n" +
                            "      \"high\": { \"value\": " + item.refIntervalHigh() + " },\n" +
                            "      \"text\": \"Intervalle de référence normal\"\n" +
                            "    }\n" +
                            "  ],\n";
                }

                obsJson += "  \"interpretation\": [\n" +
                        "    {\n" +
                        "      \"text\": \"" + JsonHelper.escape(item.interpretation() != null ? item.interpretation() : (item.isCritical() ? "CRITIQUE" : (item.isAbnormal() ? "ANORMAL" : "NORMAL"))) + "\"\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}";

                observationEntries.add(obsJson);
            }
        }

        StringBuilder bundle = new StringBuilder();
        bundle.append("{\n");
        bundle.append("  \"resourceType\": \"Bundle\",\n");
        bundle.append("  \"id\": \"bundle-lab-").append(order.orderCode()).append("\",\n");
        bundle.append("  \"type\": \"searchset\",\n");
        bundle.append("  \"total\": ").append(observationEntries.size()).append(",\n");
        bundle.append("  \"entry\": [\n");

        List<String> entryBlocks = new ArrayList<>();
        for (String obs : observationEntries) {
            entryBlocks.add("    {\n      \"resource\": " + obs.replace("\n", "\n      ") + "\n    }");
        }
        bundle.append(String.join(",\n", entryBlocks));
        bundle.append("\n  ]\n}");

        return bundle.toString();
    }

    private LabOrder getOrderOrThrow(UUID id) {
        LabOrder o = ordersById.get(id);
        if (o == null) {
            throw new IllegalArgumentException("Prescription d'analyse introuvable: " + id);
        }
        return o;
    }

    private void saveOrder(LabOrder order) {
        ordersById.put(order.id(), order);
        ordersByCode.put(order.orderCode(), order);

        ordersByPatientId.computeIfAbsent(order.patientId(), k -> new CopyOnWriteArrayList<>());
        ordersByPatientId.get(order.patientId()).removeIf(o -> o.id().equals(order.id()));
        ordersByPatientId.get(order.patientId()).add(order);
    }

    private void initSeedLabOrders() {
        // 1. Recherche Paludisme (Goutte épaisse & TDR) pour Fatou Ouedraogo
        UUID order1Id = UUID.fromString("b1111111-2222-3333-4444-555555555551");
        List<LabResultItem> results1 = List.of(
                LabResultItem.ofText("PLASMODIUM_FALCIPARUM", "Goutte Épaisse (Plasmodium Falciparum)", "50311-0",
                        "POSITIF (Présence de trophozoïtes)", true, true, "Paludisme à Plasmodium falciparum confirmé"),
                LabResultItem.ofNumeric("PARASITE_DENSITY", "Densité parasitaire", "58444-1",
                        12500.0, "trophozoïtes/µL", 0.0, 0.0, 0.0, 100000.0, "Charge parasitaire modérée"),
                LabResultItem.ofNumeric("HEMOGLOBIN", "Taux d'Hémoglobine", "718-7",
                        11.4, "g/dL", 12.0, 16.0, 7.0, 20.0, "Anémie microcytaire légère réactionnelle")
        );

        LabOrder o1 = new LabOrder(
                order1Id,
                "LAB-2026-0042",
                HOSPITAL_TENANT,
                PATIENT_FATOU_ID,
                "Fatou Ouedraogo",
                DOCTOR_TRAORE_ID,
                "Dr. Seydou Traore",
                LAB_TECH_ID,
                "Tech. Ousmane Sanou",
                "PARASITOLOGY",
                "Goutte Épaisse & Test Rapide Paludisme (TDR)",
                "URGENT",
                "VALIDATED",
                "Syndrome fébrile aigu avec frissons et céphalées intenses",
                Instant.now().minus(2, ChronoUnit.DAYS),
                Instant.now().minus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                results1,
                true, // Alerte critique paludisme
                Instant.now().minus(2, ChronoUnit.DAYS)
        );
        saveOrder(o1);

        // 2. Bilan Rénal & Glycémie pour Ibrahim Compaore (Suivi HTA/Diabète)
        UUID order2Id = UUID.fromString("b1111111-2222-3333-4444-555555555552");
        List<LabResultItem> results2 = List.of(
                LabResultItem.ofNumeric("FASTING_GLUCOSE", "Glycémie à jeun", "2339-0",
                        1.42, "g/L", 0.70, 1.10, 0.45, 2.50, "Hyperglycémie modérée à jeun"),
                LabResultItem.ofNumeric("CREATININE", "Créatininémie", "2160-0",
                        10.8, "mg/L", 7.0, 12.0, 4.0, 30.0, "Fonction rénale normale"),
                LabResultItem.ofNumeric("HBA1C", "Hémoglobine glyquée (HbA1c)", "4548-4",
                        7.2, "%", 4.0, 6.0, 3.0, 10.0, "Diabète de type 2 modérément équilibré")
        );

        LabOrder o2 = new LabOrder(
                order2Id,
                "LAB-2026-0043",
                HOSPITAL_TENANT,
                PATIENT_IBRAHIM_ID,
                "Ibrahim Compaore",
                DOCTOR_TRAORE_ID,
                "Dr. Seydou Traore",
                LAB_TECH_ID,
                "Tech. Ousmane Sanou",
                "BIOCHEMISTRY",
                "Bilan Métabolique & Surveillance Diabète",
                "ROUTINE",
                "VALIDATED",
                "Suivi trimestriel Diabète Type 2 & HTA",
                Instant.now().minus(5, ChronoUnit.DAYS),
                Instant.now().minus(5, ChronoUnit.DAYS).plus(3, ChronoUnit.HOURS),
                results2,
                false,
                Instant.now().minus(5, ChronoUnit.DAYS)
        );
        saveOrder(o2);
    }
}
