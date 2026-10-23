package com.medscan.certificate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.medscan.clinical.ClinicalService;
import com.medscan.clinical.Patient;
import com.medscan.messaging.MessagingNotificationService;
import com.medscan.security.tenant.Tenant;
import com.medscan.security.tenant.TenantRegistry;

/**
 * Service central de délivrance et certification des documents hospitaliers officiels (MOD-17).
 * Émet des ordonnances scellées, arrêts de travail, dispenses d'études et attestations de présence
 * munis du cachet numérique officiel et de l'empreinte cryptographique de l'hôpital.
 */
public class CertificateService {

    private final ClinicalService clinicalService;
    private final TenantRegistry tenantRegistry;
    private final MessagingNotificationService messagingService;
    private final Map<UUID, MedicalCertificate> certificatesById = new ConcurrentHashMap<>();
    private final Map<String, MedicalCertificate> certificatesByCode = new ConcurrentHashMap<>();
    private final AtomicInteger certificateCounter = new AtomicInteger(100);

    public CertificateService(ClinicalService clinicalService, TenantRegistry tenantRegistry, MessagingNotificationService messagingService) {
        this.clinicalService = (clinicalService != null) ? clinicalService : new ClinicalService();
        this.tenantRegistry = (tenantRegistry != null) ? tenantRegistry : new TenantRegistry();
        this.messagingService = (messagingService != null) ? messagingService : new MessagingNotificationService(this.clinicalService);
        initSeedCertificates();
    }

    public CertificateService() {
        this(new ClinicalService(), new TenantRegistry(), new MessagingNotificationService());
    }

    public MedicalCertificate issueCertificate(
            UUID patientId,
            UUID doctorId,
            String doctorName,
            String doctorLicenseNumber,
            UUID tenantId,
            String certificateType,
            String diagnosisSummary,
            Instant startDate,
            int durationDays,
            String recipientType,
            String recipientName,
            String restrictions
    ) {
        if (patientId == null) {
            throw new IllegalArgumentException("L'identifiant du patient est obligatoire.");
        }
        if (doctorId == null) {
            throw new IllegalArgumentException("L'identifiant du médecin prescripteur est obligatoire.");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("L'identifiant de la structure hospitalière est obligatoire.");
        }
        if (durationDays < 1) {
            throw new IllegalArgumentException("La durée de la dispense ou de l'arrêt doit être d'au moins 1 jour.");
        }

        Patient patient = clinicalService.findPatientById(patientId)
                .orElseThrow(() -> new IllegalArgumentException("Patient introuvable: " + patientId));

        Tenant tenant = tenantRegistry.findById(tenantId).orElse(null);
        String hospitalName = tenant != null ? tenant.name() : tenantRegistry.resolveTenantDisplayName(tenantId);
        String hospitalCode = tenant != null ? tenant.code() : "HOSPITAL";
        String hospitalAddress = tenant != null ? tenant.address() + ", " + tenant.city() : "Burkina Faso";
        String hospitalPhone = tenant != null ? tenant.phone() : "+226 25 00 00 00";
        String hospitalEmail = tenant != null ? tenant.email() : "contact@medscan.org";

        Instant now = Instant.now();
        Instant effectiveStart = (startDate != null) ? startDate : now;
        Instant effectiveEnd = effectiveStart.plus(durationDays, ChronoUnit.DAYS);

        String code = "CERT-2026-" + String.format("%04d", certificateCounter.incrementAndGet());
        String effectiveDoctorName = (doctorName != null && !doctorName.isBlank()) ? doctorName.trim() : "Dr. Médecin Praticien";
        String effectiveLicense = (doctorLicenseNumber != null && !doctorLicenseNumber.isBlank()) ? doctorLicenseNumber.trim() : "OM-BF-0000";

        // Génération du sceau d'hôpital avec hachage SHA-256
        String verificationHash = HospitalSeal.computeVerificationHash(tenantId, code, patientId, now, effectiveDoctorName);
        HospitalSeal seal = new HospitalSeal(
                tenantId,
                hospitalName,
                hospitalCode,
                hospitalAddress,
                hospitalPhone,
                hospitalEmail,
                "MS-SANTE-AGR-" + hospitalCode,
                now,
                effectiveDoctorName,
                effectiveLicense,
                verificationHash,
                "https://medscan-sluw.onrender.com/medscan/api/v1/certificates/verify?code=" + code + "&hash=" + verificationHash
        );

        MedicalCertificate cert = new MedicalCertificate(
                UUID.randomUUID(),
                code,
                patientId,
                patient.fullName(),
                patient.nationalId(),
                patient.birthDate() != null ? patient.birthDate().toString() : "1994-05-12",
                doctorId,
                effectiveDoctorName,
                effectiveLicense,
                tenantId,
                hospitalName,
                certificateType != null ? certificateType.trim().toUpperCase() : "SICK_LEAVE",
                diagnosisSummary != null ? diagnosisSummary.trim() : "État de santé nécessitant repos médical strict",
                effectiveStart,
                effectiveEnd,
                durationDays,
                recipientType != null ? recipientType.trim().toUpperCase() : "EMPLOYER",
                recipientName != null ? recipientName.trim() : "Direction des Ressources Humaines / Administration",
                restrictions != null ? restrictions.trim() : "Repos strict à domicile, cessation de toute activité professionnelle ou académique",
                seal,
                "ISSUED",
                now
        );

        certificatesById.put(cert.id(), cert);
        certificatesByCode.put(code.toUpperCase(), cert);

        // Notification automatique envoyée au patient pour téléchargement
        if (patient.linkedUserId() != null) {
            messagingService.dispatchNotification(
                    patient.linkedUserId(),
                    "PATIENT",
                    "CERTIFICATE_ISSUED",
                    "INFO",
                    "Justificatif médical officiel disponible (" + cert.certificateType() + ")",
                    "Le Dr. " + cert.doctorName() + " a émis votre certificat officiel muni du cachet de " + cert.hospitalName() +
                            " pour une durée de " + cert.durationDays() + " jour(s). Référence: " + cert.certificateCode(),
                    "MedicalCertificate",
                    cert.id().toString(),
                    tenantId
            );
        }

        clinicalService.logAudit(
                doctorId,
                effectiveDoctorName,
                "DOCTOR",
                tenantId,
                "MEDICAL_CERTIFICATE_ISSUED",
                "MedicalCertificate",
                cert.id().toString(),
                "SUCCESS",
                "Émission du certificat officiel " + code + " pour " + patient.fullName() + " (Durée: " + durationDays + " jours, Type: " + cert.certificateType() + ")"
        );

        return cert;
    }

    public Optional<MedicalCertificate> findCertificateById(UUID id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(certificatesById.get(id));
    }

    public Optional<MedicalCertificate> findCertificateByCode(String code) {
        if (code == null) return Optional.empty();
        return Optional.ofNullable(certificatesByCode.get(code.trim().toUpperCase()));
    }

    public List<MedicalCertificate> listCertificates(UUID tenantId, UUID patientId, UUID doctorId) {
        return certificatesById.values().stream()
                .filter(c -> patientId == null || c.patientId().equals(patientId))
                .filter(c -> doctorId == null || c.doctorId().equals(doctorId))
                .filter(c -> tenantId == null || c.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(MedicalCertificate::createdAt).reversed())
                .collect(Collectors.toList());
    }

    public boolean verifyCertificate(String code, String hash) {
        if (code == null || hash == null) return false;
        MedicalCertificate cert = certificatesByCode.get(code.trim().toUpperCase());
        if (cert == null || !"ISSUED".equalsIgnoreCase(cert.status())) return false;
        return cert.hospitalSeal() != null && hash.equalsIgnoreCase(cert.hospitalSeal().verificationHash());
    }

    public String renderCertificateHtml(UUID id) {
        MedicalCertificate cert = findCertificateById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificat médical introuvable: " + id));
        return CertificateJsonMapper.renderOfficialPrintableHtml(cert);
    }

    private void initSeedCertificates() {
        UUID seedHospitalOuaga = UUID.fromString("e2241595-e068-46f7-8e82-ab2b9dd3c18a");
        UUID seedDoctorId = UUID.fromString("179a11bf-92a3-438a-9d7a-a711385c8ef0");
        UUID seedPatientFatouId = UUID.fromString("99f10bda-a5e3-4ce6-a0bb-6cc09f2a280e");

        UUID c1Id = UUID.fromString("c2222222-4444-6666-8888-000000000001");
        String code1 = "CERT-2026-0042";
        Instant stamp1 = Instant.now().minus(1, ChronoUnit.DAYS);

        HospitalSeal seal1 = new HospitalSeal(
                seedHospitalOuaga,
                "Centre Hospitalier Universitaire de Ouagadougou",
                "CH_OUAGADOUGOU",
                "Avenue de l'Hôpital, Ouagadougou",
                "+226 25 30 66 44",
                "contact@ch-ouaga.bf",
                "MS-SANTE-AGR-CH_OUAGADOUGOU",
                stamp1,
                "Dr. Seydou Traore",
                "OM-BF-5412",
                HospitalSeal.computeVerificationHash(seedHospitalOuaga, code1, seedPatientFatouId, stamp1, "Dr. Seydou Traore"),
                "https://medscan-sluw.onrender.com/medscan/api/v1/certificates/verify?code=" + code1
        );

        MedicalCertificate cert1 = new MedicalCertificate(
                c1Id,
                code1,
                seedPatientFatouId,
                "Fatou Ouedraogo",
                "B12345678",
                "1994-05-12",
                seedDoctorId,
                "Dr. Seydou Traore",
                "OM-BF-5412",
                seedHospitalOuaga,
                "Centre Hospitalier Universitaire de Ouagadougou",
                "SICK_LEAVE",
                "Infection respiratoire aiguë sévère (accès palustre fébrile avec décompensation asthmatiforme)",
                stamp1,
                stamp1.plus(4, ChronoUnit.DAYS),
                4,
                "EMPLOYER",
                "Société Nationale des Télécommunications (ONATEL) - Direction RH",
                "Repos strict à domicile avec éviction professionnelle et hydratation soutenue",
                seal1,
                "ISSUED",
                stamp1
        );

        certificatesById.put(c1Id, cert1);
        certificatesByCode.put(code1, cert1);
    }
}
