package com.medscan.referral;

import java.util.List;
import java.util.stream.Collectors;

import com.medscan.certificate.HospitalSeal;
import com.medscan.security.jwt.JsonHelper;

/**
 * Sérialiseur JSON dédié aux orientations et transferts médicaux inter-services (MOD-16).
 */
public final class ReferralJsonMapper {

    private ReferralJsonMapper() {}

    public static String referralToJson(MedicalReferral ref) {
        if (ref == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(ref.id()).append("\",");
        sb.append("\"referralCode\":\"").append(JsonHelper.escape(ref.referralCode())).append("\",");
        sb.append("\"patientId\":\"").append(ref.patientId()).append("\",");
        sb.append("\"patientName\":\"").append(JsonHelper.escape(ref.patientName())).append("\",");
        sb.append("\"patientPhone\":").append(ref.patientPhone() != null ? "\"" + JsonHelper.escape(ref.patientPhone()) + "\"" : "null").append(",");
        sb.append("\"sourceTenantId\":\"").append(ref.sourceTenantId()).append("\",");
        sb.append("\"sourceTenantName\":\"").append(JsonHelper.escape(ref.sourceTenantName())).append("\",");
        sb.append("\"sourceDepartment\":\"").append(JsonHelper.escape(ref.sourceDepartment())).append("\",");
        sb.append("\"referringDoctorId\":\"").append(ref.referringDoctorId()).append("\",");
        sb.append("\"referringDoctorName\":\"").append(JsonHelper.escape(ref.referringDoctorName())).append("\",");
        sb.append("\"targetTenantId\":\"").append(ref.targetTenantId()).append("\",");
        sb.append("\"targetTenantName\":\"").append(JsonHelper.escape(ref.targetTenantName())).append("\",");
        sb.append("\"targetDepartment\":\"").append(JsonHelper.escape(ref.targetDepartment())).append("\",");
        sb.append("\"targetDoctorId\":").append(ref.targetDoctorId() != null ? "\"" + ref.targetDoctorId() + "\"" : "null").append(",");
        sb.append("\"targetDoctorName\":").append(ref.targetDoctorName() != null ? "\"" + JsonHelper.escape(ref.targetDoctorName()) + "\"" : "null").append(",");
        sb.append("\"reason\":\"").append(JsonHelper.escape(ref.reason())).append("\",");
        sb.append("\"priority\":\"").append(JsonHelper.escape(ref.priority())).append("\",");
        sb.append("\"clinicalSummary\":\"").append(JsonHelper.escape(ref.clinicalSummary())).append("\",");
        sb.append("\"status\":\"").append(JsonHelper.escape(ref.status())).append("\",");
        sb.append("\"rejectionReason\":").append(ref.rejectionReason() != null ? "\"" + JsonHelper.escape(ref.rejectionReason()) + "\"" : "null").append(",");
        sb.append("\"hospitalSeal\":").append(sealToJson(ref.hospitalSeal())).append(",");
        sb.append("\"createdAt\":\"").append(ref.createdAt()).append("\",");
        sb.append("\"updatedAt\":\"").append(ref.updatedAt()).append("\"");
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

    public static String listToJson(List<MedicalReferral> list) {
        if (list == null) return "[]";
        return "[" + list.stream().map(ReferralJsonMapper::referralToJson).collect(Collectors.joining(",")) + "]";
    }
}
