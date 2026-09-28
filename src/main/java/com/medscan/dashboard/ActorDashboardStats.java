package com.medscan.dashboard;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Modèle de statistiques de tableau de bord dynamique pour les différents acteurs de santé (MOD-02 / MOD-03).
 */
public record ActorDashboardStats(
        String role,
        UUID userId,
        String username,
        UUID tenantId,
        String tenantName,
        Map<String, Object> kpis,
        List<CriticalAlert> criticalAlerts,
        List<RecentActivity> recentActivities,
        Map<String, Object> chartsData
) {
    public record CriticalAlert(
            String id,
            String patientId,
            String patientName,
            String alertType,
            String severity, // "CRITICAL", "ATTENTION"
            String summary,
            String recordedAt
    ) {}

    public record RecentActivity(
            String id,
            String timestamp,
            String activityType,
            String title,
            String description,
            String actorName,
            String status
    ) {}
}
