package com.nimbusfs.client.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for formatting timestamps and storage sizes in the UI.
 */
public class FormatUtil {

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SHORT_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    private FormatUtil() {}

    public static String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0) return "—";
        LocalDateTime dt = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        return dt.format(DATE_FMT);
    }

    public static String formatTimestampShort(long epochMillis) {
        if (epochMillis <= 0) return "—";
        LocalDateTime dt = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        return dt.format(SHORT_FMT);
    }

    public static String formatBytes(long bytes) {
        if (bytes >= 1_099_511_627_776L) return String.format("%.2f TB", bytes / 1_099_511_627_776.0);
        if (bytes >= 1_073_741_824L)     return String.format("%.2f GB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576L)         return String.format("%.2f MB", bytes / 1_048_576.0);
        if (bytes >= 1_024L)             return String.format("%.1f KB", bytes / 1_024.0);
        return bytes + " B";
    }

    public static String formatUsagePercent(double fraction) {
        return String.format("%.1f%%", fraction * 100.0);
    }
}
