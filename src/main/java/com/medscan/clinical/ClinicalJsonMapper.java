package com.medscan.clinical;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.medscan.security.jwt.JsonHelper;

/**
 * Sérialiseur et désérialiseur JSON pour les entités cliniques (zéro dépendance externe).
 */
public final class ClinicalJsonMapper {

    private ClinicalJsonMapper() {
    }

    public static String toJson(Patient p) {
        if (p == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(p.id()).append("\",");
        sb.append("\"nationalId\":\"").append(JsonHelper.escape(p.nationalId())).append("\",");
        sb.append("\"firstName\":\"").append(JsonHelper.escape(p.firstName())).append("\",");
        sb.append("\"lastName\":\"").append(JsonHelper.escape(p.lastName())).append("\",");
        sb.append("\"fullName\":\"").append(JsonHelper.escape(p.fullName())).append("\",");
        sb.append("\"birthDate\":\"").append(JsonHelper.escape(p.birthDate())).append("\",");
        sb.append("\"gender\":\"").append(JsonHelper.escape(p.gender())).append("\",");
        sb.append("\"bloodGroup\":\"").append(JsonHelper.escape(p.bloodGroup())).append("\",");
        sb.append("\"phone\":\"").append(JsonHelper.escape(p.phone())).append("\",");
        sb.append("\"emergencyContact\":\"").append(JsonHelper.escape(p.emergencyContact())).append("\",");
        sb.append("\"allergies\":[")
                .append(p.allergies().stream().map(a -> "\"" + JsonHelper.escape(a) + "\"").collect(Collectors.joining(",")))
                .append("],");
        sb.append("\"chronicConditions\":[")
                .append(p.chronicConditions().stream().map(c -> "\"" + JsonHelper.escape(c) + "\"").collect(Collectors.joining(",")))
                .append("],");
        sb.append("\"tenantId\":\"").append(p.tenantId()).append("\",");
        sb.append("\"createdAt\":\"").append(p.createdAt()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String toPatientListJson(List<Patient> list) {
        return "[" + list.stream().map(ClinicalJsonMapper::toJson).collect(Collectors.joining(",")) + "]";
    }

    public static String toJson(VitalSigns v) {
        if (v == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(v.id()).append("\",");
        sb.append("\"patientId\":\"").append(v.patientId()).append("\",");
        sb.append("\"recordedAt\":\"").append(v.recordedAt()).append("\",");
        sb.append("\"systolicBp\":").append(v.systolicBp()).append(",");
        sb.append("\"diastolicBp\":").append(v.diastolicBp()).append(",");
        sb.append("\"heartRate\":").append(v.heartRate()).append(",");
        sb.append("\"temperature\":").append(v.temperature()).append(",");
        sb.append("\"weightKg\":").append(v.weightKg()).append(",");
        sb.append("\"bloodGlucose\":").append(v.bloodGlucose()).append(",");
        sb.append("\"recordedBy\":\"").append(JsonHelper.escape(v.recordedBy())).append("\",");
        sb.append("\"recordedByRole\":\"").append(JsonHelper.escape(v.recordedByRole())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String toVitalsListJson(List<VitalSigns> list) {
        return "[" + list.stream().map(ClinicalJsonMapper::toJson).collect(Collectors.joining(",")) + "]";
    }

    public static String toJson(Consultation c) {
        if (c == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(c.id()).append("\",");
        sb.append("\"patientId\":\"").append(c.patientId()).append("\",");
        sb.append("\"doctorId\":\"").append(c.doctorId()).append("\",");
        sb.append("\"doctorName\":\"").append(JsonHelper.escape(c.doctorName())).append("\",");
        sb.append("\"tenantId\":\"").append(c.tenantId()).append("\",");
        sb.append("\"date\":\"").append(c.date()).append("\",");
        sb.append("\"chiefComplaint\":\"").append(JsonHelper.escape(c.chiefComplaint())).append("\",");
        sb.append("\"examinationNotes\":\"").append(JsonHelper.escape(c.examinationNotes())).append("\",");
        sb.append("\"diagnosis\":\"").append(JsonHelper.escape(c.diagnosis())).append("\",");
        sb.append("\"treatmentPlan\":\"").append(JsonHelper.escape(c.treatmentPlan())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String toConsultationListJson(List<Consultation> list) {
        return "[" + list.stream().map(ClinicalJsonMapper::toJson).collect(Collectors.joining(",")) + "]";
    }

    public static String toJson(PrescriptionItem item) {
        if (item == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(item.id()).append("\",");
        sb.append("\"medicationName\":\"").append(JsonHelper.escape(item.medicationName())).append("\",");
        sb.append("\"dosage\":\"").append(JsonHelper.escape(item.dosage())).append("\",");
        sb.append("\"frequency\":\"").append(JsonHelper.escape(item.frequency())).append("\",");
        sb.append("\"durationDays\":").append(item.durationDays()).append(",");
        sb.append("\"instructions\":\"").append(JsonHelper.escape(item.instructions())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String toJson(Prescription rx) {
        if (rx == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(rx.id()).append("\",");
        sb.append("\"prescriptionCode\":\"").append(JsonHelper.escape(rx.prescriptionCode())).append("\",");
        sb.append("\"patientId\":\"").append(rx.patientId()).append("\",");
        sb.append("\"patientName\":\"").append(JsonHelper.escape(rx.patientName())).append("\",");
        sb.append("\"doctorId\":\"").append(rx.doctorId()).append("\",");
        sb.append("\"doctorName\":\"").append(JsonHelper.escape(rx.doctorName())).append("\",");
        sb.append("\"tenantId\":\"").append(rx.tenantId()).append("\",");
        sb.append("\"issuedAt\":\"").append(rx.issuedAt()).append("\",");
        sb.append("\"status\":\"").append(JsonHelper.escape(rx.status())).append("\",");
        sb.append("\"items\":[")
                .append(rx.items().stream().map(ClinicalJsonMapper::toJson).collect(Collectors.joining(",")))
                .append("],");
        sb.append("\"dispensedBy\":").append(rx.dispensedBy() != null ? "\"" + JsonHelper.escape(rx.dispensedBy()) + "\"" : "null").append(",");
        sb.append("\"dispensedAt\":").append(rx.dispensedAt() != null ? "\"" + rx.dispensedAt() + "\"" : "null");
        sb.append("}");
        return sb.toString();
    }

    public static String toPrescriptionListJson(List<Prescription> list) {
        return "[" + list.stream().map(ClinicalJsonMapper::toJson).collect(Collectors.joining(",")) + "]";
    }

    public static String toDossierJson(Patient p, List<VitalSigns> vitals, List<Consultation> consultations, List<Prescription> prescriptions) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"patient\":").append(toJson(p)).append(",");
        sb.append("\"vitalSigns\":").append(toVitalsListJson(vitals)).append(",");
        sb.append("\"consultations\":").append(toConsultationListJson(consultations)).append(",");
        sb.append("\"prescriptions\":").append(toPrescriptionListJson(prescriptions));
        sb.append("}");
        return sb.toString();
    }

    public static String toJson(AuditEvent e) {
        if (e == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(e.id()).append("\",");
        sb.append("\"timestamp\":\"").append(e.timestamp()).append("\",");
        sb.append("\"actorId\":\"").append(e.actorId()).append("\",");
        sb.append("\"actorUsername\":\"").append(JsonHelper.escape(e.actorUsername())).append("\",");
        sb.append("\"actorRole\":\"").append(JsonHelper.escape(e.actorRole())).append("\",");
        sb.append("\"tenantId\":\"").append(e.tenantId()).append("\",");
        sb.append("\"action\":\"").append(JsonHelper.escape(e.action())).append("\",");
        sb.append("\"resource\":\"").append(JsonHelper.escape(e.resource())).append("\",");
        sb.append("\"resourceId\":\"").append(JsonHelper.escape(e.resourceId())).append("\",");
        sb.append("\"status\":\"").append(JsonHelper.escape(e.status())).append("\",");
        sb.append("\"details\":\"").append(JsonHelper.escape(e.details())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String toAuditListJson(List<AuditEvent> list) {
        return "[" + list.stream().map(ClinicalJsonMapper::toJson).collect(Collectors.joining(",")) + "]";
    }

    // ==========================================
    // PARSERS
    // ==========================================

    public static Patient parsePatient(String body, UUID tenantId) {
        String nationalId = JsonHelper.getString(body, "nationalId");
        if (nationalId == null || nationalId.isBlank()) {
            nationalId = "BFA-" + (int)(Math.random() * 900000 + 100000);
        }
        String firstName = JsonHelper.getString(body, "firstName");
        String lastName = JsonHelper.getString(body, "lastName");
        String birthDate = JsonHelper.getString(body, "birthDate");
        String gender = JsonHelper.getString(body, "gender");
        String bloodGroup = JsonHelper.getString(body, "bloodGroup");
        String phone = JsonHelper.getString(body, "phone");
        String emergencyContact = JsonHelper.getString(body, "emergencyContact");

        List<String> allergies = JsonHelper.getStringList(body, "allergies");
        List<String> chronicConditions = JsonHelper.getStringList(body, "chronicConditions");

        return new Patient(
                UUID.randomUUID(),
                nationalId,
                firstName != null ? firstName : "Prénom",
                lastName != null ? lastName : "Nom",
                birthDate != null ? birthDate : "1990-01-01",
                gender != null ? gender : "U",
                bloodGroup != null ? bloodGroup : "Inconnu",
                phone != null ? phone : "",
                emergencyContact != null ? emergencyContact : "",
                allergies,
                chronicConditions,
                tenantId,
                null,
                Instant.now()
        );
    }

    public static VitalSigns parseVitalSigns(String body, UUID patientId, String recordedBy, String role) {
        Integer sys = JsonHelper.getInt(body, "systolicBp");
        Integer dia = JsonHelper.getInt(body, "diastolicBp");
        Integer hr = JsonHelper.getInt(body, "heartRate");
        Double temp = JsonHelper.getDouble(body, "temperature");
        Double weight = JsonHelper.getDouble(body, "weightKg");
        Double glucose = JsonHelper.getDouble(body, "bloodGlucose");

        return new VitalSigns(
                UUID.randomUUID(),
                patientId,
                Instant.now(),
                sys != null ? sys : 120,
                dia != null ? dia : 80,
                hr != null ? hr : 70,
                temp != null ? temp : 37.0,
                weight != null ? weight : 65.0,
                glucose != null ? glucose : 1.0,
                recordedBy,
                role
        );
    }

    public static Consultation parseConsultation(String body, UUID patientId, UUID doctorId, String doctorName, UUID tenantId) {
        String chiefComplaint = JsonHelper.getString(body, "chiefComplaint");
        String examinationNotes = JsonHelper.getString(body, "examinationNotes");
        String diagnosis = JsonHelper.getString(body, "diagnosis");
        String treatmentPlan = JsonHelper.getString(body, "treatmentPlan");

        return new Consultation(
                UUID.randomUUID(),
                patientId,
                doctorId,
                doctorName,
                tenantId,
                Instant.now(),
                chiefComplaint != null ? chiefComplaint : "Consultation générale",
                examinationNotes != null ? examinationNotes : "",
                diagnosis != null ? diagnosis : "En cours d'investigation",
                treatmentPlan != null ? treatmentPlan : ""
        );
    }

    public static Prescription parsePrescription(String body, UUID patientId, String patientName, UUID doctorId, String doctorName, UUID tenantId) {
        String code = "RX-2026-" + String.format("%04d", (int)(Math.random() * 9000 + 1000));
        String medication = JsonHelper.getString(body, "medicationName");
        String dosage = JsonHelper.getString(body, "dosage");
        String frequency = JsonHelper.getString(body, "frequency");
        Integer duration = JsonHelper.getInt(body, "durationDays");
        String instructions = JsonHelper.getString(body, "instructions");

        List<PrescriptionItem> items = new ArrayList<>();
        if (medication != null && !medication.isBlank()) {
            items.add(new PrescriptionItem(
                    UUID.randomUUID(),
                    medication,
                    dosage != null ? dosage : "1 prise",
                    frequency != null ? frequency : "3x / jour",
                    duration != null ? duration : 7,
                    instructions != null ? instructions : "Prendre pendant les repas"
            ));
        } else {
            items.add(new PrescriptionItem(
                    UUID.randomUUID(),
                    "Traitement prescrit",
                    "Standard",
                    "Quotidien",
                    5,
                    "Selon avis médical"
            ));
        }

        return new Prescription(
                UUID.randomUUID(),
                code,
                patientId,
                patientName,
                doctorId,
                doctorName,
                tenantId,
                Instant.now(),
                "ISSUED",
                items,
                null,
                null
        );
    }
}
