package com.wms.po.domain.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Date/Time Utility Functions.
 *
 * Replaces SQL functions:
 * - FN-030: Date/Time functions (9 total)
 *   - fnc_GetBusinessDay
 *   - fnc_GetWeekNumber
 *   - fnc_IsHoliday
 *   - fnc_AddBusinessDays
 *   - fnc_GetQuarter
 *   - fnc_GetFiscalYear
 *   - fnc_DateDiffWorkdays
 *   - fnc_GetWeekStart
 *   - fnc_GetMonthEnd
 */
public final class DateTimeUtils {

    private DateTimeUtils() {}

    // Common formatters
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    public static final DateTimeFormatter COMPACT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    public static final DateTimeFormatter ISO_LOCAL = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Get the current business day.
     * If weekend, returns next Monday.
     *
     * Replaces: fnc_GetBusinessDay
     */
    public static LocalDate getBusinessDay() {
        return getBusinessDay(LocalDate.now());
    }

    public static LocalDate getBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY) {
            return date.plusDays(2);
        } else if (day == DayOfWeek.SUNDAY) {
            return date.plusDays(1);
        }
        return date;
    }

    /**
     * Get ISO week number for a date.
     *
     * Replaces: fnc_GetWeekNumber
     */
    public static int getWeekNumber(LocalDate date) {
        return date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
    }

    /**
     * Check if date is a weekend.
     * Note: For actual holiday checking, you'd need a holiday calendar.
     *
     * Replaces: fnc_IsHoliday (basic weekend check)
     */
    public static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    /**
     * Add business days to a date (skipping weekends).
     *
     * Replaces: fnc_AddBusinessDays
     */
    public static LocalDate addBusinessDays(LocalDate date, int days) {
        if (days == 0) return date;

        int direction = days > 0 ? 1 : -1;
        int remaining = Math.abs(days);
        LocalDate result = date;

        while (remaining > 0) {
            result = result.plusDays(direction);
            if (!isWeekend(result)) {
                remaining--;
            }
        }

        return result;
    }

    /**
     * Get calendar quarter (1-4).
     *
     * Replaces: fnc_GetQuarter
     */
    public static int getQuarter(LocalDate date) {
        return (date.getMonthValue() - 1) / 3 + 1;
    }

    /**
     * Get fiscal year (assuming April start).
     * Configurable fiscal year start month.
     *
     * Replaces: fnc_GetFiscalYear
     */
    public static int getFiscalYear(LocalDate date) {
        return getFiscalYear(date, Month.APRIL);
    }

    public static int getFiscalYear(LocalDate date, Month fiscalYearStartMonth) {
        int year = date.getYear();
        if (date.getMonthValue() < fiscalYearStartMonth.getValue()) {
            return year;
        }
        return year + 1;
    }

    /**
     * Calculate working days between two dates.
     *
     * Replaces: fnc_DateDiffWorkdays
     */
    public static long getWorkdaysBetween(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            LocalDate temp = start;
            start = end;
            end = temp;
        }

        long workdays = 0;
        LocalDate current = start;

        while (!current.isAfter(end)) {
            if (!isWeekend(current)) {
                workdays++;
            }
            current = current.plusDays(1);
        }

        return workdays;
    }

    /**
     * Get the start of the week (Monday).
     *
     * Replaces: fnc_GetWeekStart
     */
    public static LocalDate getWeekStart(LocalDate date) {
        return date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Get the end of the week (Sunday).
     */
    public static LocalDate getWeekEnd(LocalDate date) {
        return date.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    }

    /**
     * Get the last day of the month.
     *
     * Replaces: fnc_GetMonthEnd
     */
    public static LocalDate getMonthEnd(LocalDate date) {
        return date.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
    }

    /**
     * Get the first day of the month.
     */
    public static LocalDate getMonthStart(LocalDate date) {
        return date.with(java.time.temporal.TemporalAdjusters.firstDayOfMonth());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Conversion Utilities
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Convert java.util.Date to LocalDate.
     */
    public static LocalDate toLocalDate(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /**
     * Convert java.util.Date to LocalDateTime.
     */
    public static LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /**
     * Convert LocalDate to java.util.Date.
     */
    public static Date toDate(LocalDate localDate) {
        if (localDate == null) return null;
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * Convert LocalDateTime to java.util.Date.
     */
    public static Date toDate(LocalDateTime localDateTime) {
        if (localDateTime == null) return null;
        return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Validation Utilities
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if a date is within a range (inclusive).
     */
    public static boolean isWithinRange(LocalDate date, LocalDate start, LocalDate end) {
        if (date == null) return false;
        return !date.isBefore(start) && !date.isAfter(end);
    }

    /**
     * Check if date is in the past.
     */
    public static boolean isPast(LocalDate date) {
        return date != null && date.isBefore(LocalDate.now());
    }

    /**
     * Check if date is in the future.
     */
    public static boolean isFuture(LocalDate date) {
        return date != null && date.isAfter(LocalDate.now());
    }

    /**
     * Check if date is today.
     */
    public static boolean isToday(LocalDate date) {
        return date != null && date.equals(LocalDate.now());
    }

    /**
     * Get days until a future date.
     */
    public static long daysUntil(LocalDate futureDate) {
        return ChronoUnit.DAYS.between(LocalDate.now(), futureDate);
    }

    /**
     * Get days since a past date.
     */
    public static long daysSince(LocalDate pastDate) {
        return ChronoUnit.DAYS.between(pastDate, LocalDate.now());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Formatting Utilities
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Format date as string.
     */
    public static String format(LocalDate date) {
        return date != null ? date.format(DATE_FORMAT) : null;
    }

    /**
     * Format datetime as string.
     */
    public static String format(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_FORMAT) : null;
    }

    /**
     * Parse date from string.
     */
    public static LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return null;
        return LocalDate.parse(dateStr, DATE_FORMAT);
    }

    /**
     * Parse datetime from string.
     */
    public static LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) return null;
        return LocalDateTime.parse(dateTimeStr, DATETIME_FORMAT);
    }

    /**
     * Get age in years from birthdate.
     */
    public static int getAge(LocalDate birthDate) {
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    /**
     * Get shelf life remaining in days.
     */
    public static long getShelfLifeRemaining(LocalDate expiryDate) {
        if (expiryDate == null) return Long.MAX_VALUE;
        return daysUntil(expiryDate);
    }

    /**
     * Check if shelf life is sufficient.
     */
    public static boolean hasMinimumShelfLife(LocalDate expiryDate, int minDays) {
        return getShelfLifeRemaining(expiryDate) >= minDays;
    }
}
