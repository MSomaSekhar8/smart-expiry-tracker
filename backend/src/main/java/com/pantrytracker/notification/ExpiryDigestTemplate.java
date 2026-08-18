package com.pantrytracker.notification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the digest email body. Plain Java string templating on purpose —
 * no template engine dependency for one email.
 */
public final class ExpiryDigestTemplate {

    private ExpiryDigestTemplate() {}

    public record DigestLine(String name, LocalDate expiryDate, NotificationType type, long daysLeft) {}

    public static String render(List<DigestLine> expiringSoon, List<DigestLine> expired) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Inter,Arial,sans-serif;max-width:560px;margin:0 auto;color:#1f2937\">");
        sb.append("<h1 style=\"font-size:20px;margin:0 0 4px\">Pantry Tracker</h1>");
        sb.append("<p style=\"color:#6b7280;margin:0 0 20px\">Your daily expiry digest</p>");

        if (expiringSoon.isEmpty() && expired.isEmpty()) {
            sb.append("<p>Nothing is expiring — all clear.</p>");
        } else {
            if (!expiringSoon.isEmpty()) {
                sb.append("<h2 style=\"font-size:15px;color:#b45309\">Expiring soon</h2>");
                renderTable(sb, expiringSoon);
            }
            if (!expired.isEmpty()) {
                sb.append("<h2 style=\"font-size:15px;color:#b91c1c\">Already expired</h2>");
                renderTable(sb, expired);
            }
        }
        sb.append("<p style=\"color:#9ca3af;font-size:12px;margin-top:24px\">Log in to review your pantry.</p>");
        sb.append("</div>");
        return sb.toString();
    }

    private static void renderTable(StringBuilder sb, List<DigestLine> lines) {
        sb.append("<table style=\"width:100%;border-collapse:collapse;font-size:14px\">");
        sb.append("<thead><tr style=\"text-align:left;color:#6b7280;font-size:12px\">")
          .append("<th style=\"padding:4px 8px\">Item</th><th style=\"padding:4px 8px\">Expires</th></tr></thead>");
        sb.append("<tbody>");
        for (DigestLine line : lines) {
            sb.append("<tr><td style=\"padding:6px 8px;border-top:1px solid #e5e7eb\">")
              .append(escape(line.name()))
              .append("</td><td style=\"padding:6px 8px;border-top:1px solid #e5e7eb\">")
              .append(line.expiryDate())
              .append(" (").append(line.daysLeft() >= 0 ? line.daysLeft() + " day(s)" : "today")
              .append(")</td></tr>");
        }
        sb.append("</tbody></table>");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** @return every line collected — kept separate so callers can pick per type. */
    public static List<DigestLine> merge(List<DigestLine> expiringSoon, List<DigestLine> expired) {
        List<DigestLine> all = new ArrayList<>(expiringSoon);
        all.addAll(expired);
        return all;
    }
}