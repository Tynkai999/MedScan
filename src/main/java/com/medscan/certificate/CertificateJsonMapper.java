package com.medscan.certificate;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import com.medscan.security.jwt.JsonHelper;

/**
 * Mappeur JSON et générateur de documents officiels HTML imprimables scellés par l'hôpital (MOD-17).
 */
public final class CertificateJsonMapper {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy à HH:mm 'UTC'")
            .withZone(ZoneId.of("UTC"));

    private CertificateJsonMapper() {}

    public static String certificateToJson(MedicalCertificate cert) {
        if (cert == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(cert.id()).append("\",");
        sb.append("\"certificateCode\":\"").append(JsonHelper.escape(cert.certificateCode())).append("\",");
        sb.append("\"patientId\":\"").append(cert.patientId()).append("\",");
        sb.append("\"patientName\":\"").append(JsonHelper.escape(cert.patientName())).append("\",");
        sb.append("\"patientNationalId\":\"").append(JsonHelper.escape(cert.patientNationalId())).append("\",");
        sb.append("\"patientBirthDate\":\"").append(JsonHelper.escape(cert.patientBirthDate())).append("\",");
        sb.append("\"doctorId\":\"").append(cert.doctorId()).append("\",");
        sb.append("\"doctorName\":\"").append(JsonHelper.escape(cert.doctorName())).append("\",");
        sb.append("\"doctorLicenseNumber\":\"").append(JsonHelper.escape(cert.doctorLicenseNumber())).append("\",");
        sb.append("\"tenantId\":\"").append(cert.tenantId()).append("\",");
        sb.append("\"hospitalName\":\"").append(JsonHelper.escape(cert.hospitalName())).append("\",");
        sb.append("\"certificateType\":\"").append(JsonHelper.escape(cert.certificateType())).append("\",");
        sb.append("\"diagnosisSummary\":\"").append(JsonHelper.escape(cert.diagnosisSummary())).append("\",");
        sb.append("\"startDate\":\"").append(cert.startDate()).append("\",");
        sb.append("\"endDate\":\"").append(cert.endDate()).append("\",");
        sb.append("\"durationDays\":").append(cert.durationDays()).append(",");
        sb.append("\"recipientType\":\"").append(JsonHelper.escape(cert.recipientType())).append("\",");
        sb.append("\"recipientName\":\"").append(JsonHelper.escape(cert.recipientName())).append("\",");
        sb.append("\"restrictions\":\"").append(JsonHelper.escape(cert.restrictions())).append("\",");
        sb.append("\"hospitalSeal\":").append(sealToJson(cert.hospitalSeal())).append(",");
        sb.append("\"status\":\"").append(JsonHelper.escape(cert.status())).append("\",");
        sb.append("\"createdAt\":\"").append(cert.createdAt()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String sealToJson(HospitalSeal seal) {
        if (seal == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"tenantId\":").append(seal.tenantId() != null ? "\"" + seal.tenantId() + "\"" : "null").append(",");
        sb.append("\"hospitalName\":\"").append(JsonHelper.escape(seal.hospitalName())).append("\",");
        sb.append("\"hospitalCode\":\"").append(JsonHelper.escape(seal.hospitalCode())).append("\",");
        sb.append("\"hospitalAddress\":\"").append(JsonHelper.escape(seal.hospitalAddress())).append("\",");
        sb.append("\"hospitalPhone\":\"").append(JsonHelper.escape(seal.hospitalPhone())).append("\",");
        sb.append("\"hospitalEmail\":\"").append(JsonHelper.escape(seal.hospitalEmail())).append("\",");
        sb.append("\"accreditationNumber\":\"").append(JsonHelper.escape(seal.accreditationNumber())).append("\",");
        sb.append("\"stampedAt\":\"").append(seal.stampedAt()).append("\",");
        sb.append("\"stampedByDoctorName\":\"").append(JsonHelper.escape(seal.stampedByDoctorName())).append("\",");
        sb.append("\"doctorLicenseNumber\":\"").append(JsonHelper.escape(seal.doctorLicenseNumber())).append("\",");
        sb.append("\"verificationHash\":\"").append(JsonHelper.escape(seal.verificationHash())).append("\",");
        sb.append("\"verificationUrl\":\"").append(JsonHelper.escape(seal.verificationUrl())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String listToJson(List<MedicalCertificate> list) {
        if (list == null) return "[]";
        return "[" + list.stream().map(CertificateJsonMapper::certificateToJson).collect(Collectors.joining(",")) + "]";
    }

    /**
     * Génère un document officiel HTML prêt pour impression / PDF avec le cachet d'hôpital institutionnel.
     */
    public static String renderOfficialPrintableHtml(MedicalCertificate cert) {
        String typeTitle = switch (cert.certificateType()) {
            case "SICK_LEAVE" -> "CERTIFICAT MÉDICAL D'ARRÊT DE TRAVAIL";
            case "STUDENT_DISPENSE" -> "DISPENSE MÉDICALE ÉTUDIANTE / SCOLAIRE";
            case "ATTENDANCE_PROOF" -> "ATTESTATION DE PRÉSENCE & CONSULTATION";
            case "PRESCRIPTION_CERTIFIED" -> "ORDONNANCE MÉDICALE CERTIFIÉE AVEC SCEAU";
            default -> "CERTIFICAT MÉDICAL OFFICIEL";
        };

        String startStr = DATE_FMT.format(cert.startDate());
        String endStr = DATE_FMT.format(cert.endDate());
        String issueStr = DATETIME_FMT.format(cert.createdAt());
        HospitalSeal seal = cert.hospitalSeal();

        return "<!DOCTYPE html>\n" +
                "<html lang=\"fr\">\n" +
                "<head>\n" +
                "  <meta charset=\"UTF-8\">\n" +
                "  <title>" + typeTitle + " - " + cert.certificateCode() + "</title>\n" +
                "  <style>\n" +
                "    body { font-family: 'Helvetica Neue', Arial, sans-serif; color: #1e293b; background: #f8fafc; margin: 0; padding: 24px; }\n" +
                "    .cert-container { max-width: 800px; margin: 0 auto; background: #ffffff; padding: 48px; border: 2px solid #cbd5e1; box-shadow: 0 10px 25px rgba(0,0,0,0.06); position: relative; border-radius: 6px; }\n" +
                "    .header { display: flex; justify-content: space-between; border-bottom: 2px solid #0f766e; padding-bottom: 16px; margin-bottom: 24px; }\n" +
                "    .hospital-info h2 { margin: 0 0 4px; color: #0f766e; font-size: 20px; text-transform: uppercase; letter-spacing: 0.5px; }\n" +
                "    .hospital-info p { margin: 2px 0; font-size: 13px; color: #475569; }\n" +
                "    .rep-header { text-align: right; font-size: 11px; color: #334155; font-weight: bold; }\n" +
                "    .doc-title { text-align: center; margin: 28px 0; font-size: 22px; font-weight: 800; color: #0f172a; text-decoration: underline; text-underline-offset: 6px; }\n" +
                "    .cert-body { font-size: 15px; line-height: 1.8; color: #1e293b; text-align: justify; margin-bottom: 32px; }\n" +
                "    .highlight { font-weight: bold; color: #0f766e; }\n" +
                "    .meta-box { background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 6px; padding: 14px 18px; margin: 20px 0; font-size: 14px; }\n" +
                "    .signatures-row { display: flex; justify-content: space-between; align-items: flex-start; margin-top: 40px; }\n" +
                "    .signature-block { width: 42%; text-align: center; font-size: 13px; }\n" +
                "    .stamp-circle {\n" +
                "      width: 150px; height: 150px; border: 3px double #0f766e; border-radius: 50%; margin: 12px auto;\n" +
                "      display: flex; flex-direction: column; justify-content: center; align-items: center;\n" +
                "      color: #0f766e; text-transform: uppercase; font-weight: bold; font-size: 9px; line-height: 1.2;\n" +
                "      transform: rotate(-6deg); background: rgba(15, 118, 110, 0.04); box-shadow: 0 0 0 2px rgba(15, 118, 110, 0.2);\n" +
                "    }\n" +
                "    .stamp-star { color: #e11d48; font-size: 12px; margin: 2px 0; }\n" +
                "    .security-footer { border-top: 1px dashed #cbd5e1; margin-top: 48px; padding-top: 16px; font-size: 11px; color: #64748b; display: flex; justify-content: space-between; }\n" +
                "    .qr-block { font-family: monospace; font-size: 11px; background: #f1f5f9; padding: 6px 10px; border-radius: 4px; }\n" +
                "    .print-btn { display: block; margin: 20px auto; padding: 12px 28px; background: #0f766e; color: #fff; font-size: 16px; font-weight: bold; border: none; border-radius: 8px; cursor: pointer; box-shadow: 0 4px 12px rgba(15,118,110,0.3); }\n" +
                "    .print-btn:hover { background: #115e59; }\n" +
                "    @media print {\n" +
                "      body { background: #fff; padding: 0; }\n" +
                "      .cert-container { border: none; box-shadow: none; padding: 0; width: 100%; max-width: none; }\n" +
                "      .print-btn { display: none; }\n" +
                "    }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "  <button class=\"print-btn\" onclick=\"window.print()\">🖨️ Imprimer / Télécharger en PDF</button>\n" +
                "  <div class=\"cert-container\">\n" +
                "    <div class=\"header\">\n" +
                "      <div class=\"hospital-info\">\n" +
                "        <h2>" + (seal != null ? seal.hospitalName() : cert.hospitalName()) + "</h2>\n" +
                "        <p><strong>Adresse :</strong> " + (seal != null ? seal.hospitalAddress() : "Burkina Faso") + "</p>\n" +
                "        <p><strong>Tél :</strong> " + (seal != null ? seal.hospitalPhone() : "+226 25 00 00 00") + " | <strong>Email :</strong> " + (seal != null ? seal.hospitalEmail() : "contact@medscan.org") + "</p>\n" +
                "        <p><strong>Agrément Ministériel :</strong> " + (seal != null ? seal.accreditationNumber() : "MS-SANTE-BF") + "</p>\n" +
                "      </div>\n" +
                "      <div class=\"rep-header\">\n" +
                "        RÉPUBLIQUE DU BURKINA FASO<br>\n" +
                "        Unité - Progrès - Justice<br>\n" +
                "        MINISTÈRE DE LA SANTÉ\n" +
                "      </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <div class=\"doc-title\">" + typeTitle + "</div>\n" +
                "\n" +
                "    <div class=\"cert-body\">\n" +
                "      Je soussigné, <span class=\"highlight\">" + cert.doctorName() + "</span>, Docteur en Médecine, inscrit au Tableau de l'Ordre National des Médecins sous le N° <span class=\"highlight\">" + cert.doctorLicenseNumber() + "</span>, exerçant au sein de l'établissement <span class=\"highlight\">" + cert.hospitalName() + "</span>,<br><br>\n" +
                "      Certifie avoir examiné ce jour, en consultation médicale :<br>\n" +
                "      <strong>Patient(e) :</strong> <span class=\"highlight\">" + cert.patientName() + "</span><br>\n" +
                "      <strong>Identifiant National / N° CNI :</strong> " + cert.patientNationalId() + " &nbsp;&nbsp;|&nbsp;&nbsp; <strong>Date de naissance :</strong> " + cert.patientBirthDate() + "<br><br>\n" +
                "      Et atteste que son état de santé justifie médicalement :<br>\n" +
                "      <div class=\"meta-box\">\n" +
                "        <strong>Motif médical légal :</strong> " + cert.diagnosisSummary() + "<br>\n" +
                "        <strong>Période de repos / dispense :</strong> du <span class=\"highlight\">" + startStr + "</span> au <span class=\"highlight\">" + endStr + "</span> inclus.<br>\n" +
                "        <strong>Durée totale :</strong> <span class=\"highlight\">" + cert.durationDays() + " jour(s)</span>.<br>\n" +
                "        <strong>Consignes & restrictions :</strong> " + cert.restrictions() + "<br>\n" +
                "        <strong>Destinataire :</strong> " + cert.recipientName() + " (" + cert.recipientType() + ")\n" +
                "      </div>\n" +
                "      Le présent certificat est délivré à l'intéressé(e) pour faire valoir ce que de droit auprès de son employeur, de son administration ou de son établissement d'enseignement.\n" +
                "    </div>\n" +
                "\n" +
                "    <div class=\"signatures-row\">\n" +
                "      <div class=\"signature-block\">\n" +
                "        <strong>Le Médecin Traitant</strong><br><br>\n" +
                "        <span style=\"font-style: italic; color: #0f766e;\">Signé numériquement</span><br>\n" +
                "        <strong>" + cert.doctorName() + "</strong><br>\n" +
                "        N° Ordre: " + cert.doctorLicenseNumber() + "\n" +
                "      </div>\n" +
                "      <div class=\"signature-block\">\n" +
                "        <strong>Sceau & Cachet Officiel de l'Hôpital</strong>\n" +
                "        <div class=\"stamp-circle\">\n" +
                "          <span>" + (seal != null ? seal.hospitalCode() : "MEDSCAN_SYS") + "</span>\n" +
                "          <span class=\"stamp-star\">★ CACHET OFFICIEL ★</span>\n" +
                "          <span>" + (seal != null ? seal.accreditationNumber() : "MS-SANTE-BF") + "</span>\n" +
                "          <span style=\"font-size: 8px; margin-top: 2px;\">" + startStr + "</span>\n" +
                "        </div>\n" +
                "        <span style=\"font-size: 11px; color: #475569;\">Direction Médicale Hospitalière</span>\n" +
                "      </div>\n" +
                "    </div>\n" +
                "\n" +
                "    <div class=\"security-footer\">\n" +
                "      <div>\n" +
                "        <strong>Réf. Document :</strong> " + cert.certificateCode() + "<br>\n" +
                "        <strong>Date d'émission :</strong> " + issueStr + "\n" +
                "      </div>\n" +
                "      <div class=\"qr-block\">\n" +
                "        🔒 EMPREINTE DE VÉRIFICATION SHA-256 :<br>\n" +
                "        <strong>" + (seal != null ? seal.verificationHash() : "VALID_SEAL_V1") + "</strong><br>\n" +
                "        Vérifiable sur : medscan.org/verify\n" +
                "      </div>\n" +
                "    </div>\n" +
                "  </div>\n" +
                "</body>\n" +
                "</html>";
    }
}
