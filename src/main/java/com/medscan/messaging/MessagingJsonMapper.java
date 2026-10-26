package com.medscan.messaging;

import java.util.List;
import java.util.stream.Collectors;

import com.medscan.security.jwt.JsonHelper;

public final class MessagingJsonMapper {

    private MessagingJsonMapper() {}

    public static String messageToJson(ClinicalMessage m) {
        if (m == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(m.id()).append("\",");
        sb.append("\"conversationId\":\"").append(m.conversationId()).append("\",");
        sb.append("\"tenantId\":\"").append(m.tenantId()).append("\",");
        sb.append("\"senderId\":\"").append(m.senderId()).append("\",");
        sb.append("\"senderName\":\"").append(JsonHelper.escape(m.senderName())).append("\",");
        sb.append("\"senderRole\":\"").append(JsonHelper.escape(m.senderRole())).append("\",");
        sb.append("\"recipientId\":\"").append(m.recipientId()).append("\",");
        sb.append("\"recipientName\":\"").append(JsonHelper.escape(m.recipientName())).append("\",");
        sb.append("\"recipientRole\":\"").append(JsonHelper.escape(m.recipientRole())).append("\",");
        sb.append("\"patientId\":").append(m.patientId() != null ? "\"" + m.patientId() + "\"" : "null").append(",");
        sb.append("\"subject\":").append(m.subject() != null ? "\"" + JsonHelper.escape(m.subject()) + "\"" : "null").append(",");
        sb.append("\"content\":\"").append(JsonHelper.escape(m.content())).append("\",");
        sb.append("\"priority\":\"").append(JsonHelper.escape(m.priority())).append("\",");
        sb.append("\"readAt\":").append(m.readAt() != null ? "\"" + m.readAt() + "\"" : "null").append(",");
        sb.append("\"sentAt\":\"").append(m.sentAt()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String messagesListToJson(List<ClinicalMessage> list) {
        if (list == null) return "[]";
        return "[" + list.stream().map(MessagingJsonMapper::messageToJson).collect(Collectors.joining(",")) + "]";
    }

    public static String conversationSummaryToJson(MessagingNotificationService.ConversationSummary c) {
        if (c == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(c.id()).append("\",");
        sb.append("\"participantId\":\"").append(c.participantId()).append("\",");
        sb.append("\"participantName\":\"").append(JsonHelper.escape(c.participantName())).append("\",");
        sb.append("\"participantRole\":\"").append(JsonHelper.escape(c.participantRole())).append("\",");
        sb.append("\"unreadCount\":").append(c.unreadCount()).append(",");
        sb.append("\"lastMessage\":").append(messageToJson(c.lastMessage()));
        sb.append("}");
        return sb.toString();
    }

    public static String conversationSummariesListToJson(List<MessagingNotificationService.ConversationSummary> list) {
        if (list == null) return "[]";
        return "[" + list.stream().map(MessagingJsonMapper::conversationSummaryToJson).collect(Collectors.joining(",")) + "]";
    }

    public static String contactToJson(MessagingNotificationService.MessagingContact c) {
        if (c == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(c.id()).append("\",");
        sb.append("\"name\":\"").append(JsonHelper.escape(c.name())).append("\",");
        sb.append("\"role\":\"").append(JsonHelper.escape(c.role())).append("\",");
        sb.append("\"details\":\"").append(JsonHelper.escape(c.details())).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String contactsListToJson(List<MessagingNotificationService.MessagingContact> list) {
        if (list == null) return "[]";
        return "[" + list.stream().map(MessagingJsonMapper::contactToJson).collect(Collectors.joining(",")) + "]";
    }

    public static String notificationToJson(InAppNotification n) {
        if (n == null) return "null";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":\"").append(n.id()).append("\",");
        sb.append("\"tenantId\":\"").append(n.tenantId()).append("\",");
        sb.append("\"recipientUserId\":\"").append(n.recipientUserId()).append("\",");
        sb.append("\"recipientRole\":\"").append(JsonHelper.escape(n.recipientRole())).append("\",");
        sb.append("\"category\":\"").append(JsonHelper.escape(n.category())).append("\",");
        sb.append("\"severity\":\"").append(JsonHelper.escape(n.severity())).append("\",");
        sb.append("\"title\":\"").append(JsonHelper.escape(n.title())).append("\",");
        sb.append("\"message\":\"").append(JsonHelper.escape(n.message())).append("\",");
        sb.append("\"referenceEntityType\":").append(n.referenceEntityType() != null ? "\"" + JsonHelper.escape(n.referenceEntityType()) + "\"" : "null").append(",");
        sb.append("\"referenceEntityId\":").append(n.referenceEntityId() != null ? "\"" + JsonHelper.escape(n.referenceEntityId()) + "\"" : "null").append(",");
        sb.append("\"isRead\":").append(n.isRead()).append(",");
        sb.append("\"readAt\":").append(n.readAt() != null ? "\"" + n.readAt() + "\"" : "null").append(",");
        sb.append("\"createdAt\":\"").append(n.createdAt()).append("\"");
        sb.append("}");
        return sb.toString();
    }

    public static String notificationsListToJson(List<InAppNotification> list) {
        if (list == null) return "[]";
        return "[" + list.stream().map(MessagingJsonMapper::notificationToJson).collect(Collectors.joining(",")) + "]";
    }
}
