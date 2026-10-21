package org.example.ride_hailing_logistics.common.util;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Chuan hoa dinh danh truoc khi kiem tra duy nhat (FR-IAM-002, DR-VAL-003). */
public final class Normalizers {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    // So di dong VN sau khi bo dau 0 / 84: 9 chu so, bat dau 3,5,7,8,9
    private static final Pattern VN_MOBILE = Pattern.compile("^[35789]\\d{8}$");
    // Bien so VN: 2 so tinh + 1-2 chu/so seri + 4-5 so, vi du 51F12345, 59X112345
    private static final Pattern PLATE = Pattern.compile("^\\d{2}[A-Z][A-Z0-9]?\\d{4,5}$");

    private Normalizers() {
    }

    public static Optional<String> email(String raw) {
        if (raw == null) return Optional.empty();
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return EMAIL.matcher(value).matches() && value.length() <= 254 ? Optional.of(value) : Optional.empty();
    }

    /** Dua ve dang E.164 +84xxxxxxxxx; "0901 234 567", "+84 901234567", "84901234567" la cung mot so. */
    public static Optional<String> phone(String raw) {
        if (raw == null) return Optional.empty();
        String digits = raw.replaceAll("[\\s.()-]", "");
        if (digits.startsWith("+84")) {
            digits = digits.substring(3);
        } else if (digits.startsWith("84") && digits.length() == 11) {
            digits = digits.substring(2);
        } else if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return VN_MOBILE.matcher(digits).matches() ? Optional.of("+84" + digits) : Optional.empty();
    }

    public static Optional<String> plate(String raw) {
        if (raw == null) return Optional.empty();
        String value = raw.toUpperCase(Locale.ROOT).replaceAll("[\\s.-]", "");
        return PLATE.matcher(value).matches() ? Optional.of(value) : Optional.empty();
    }

    /** Che PII khi khong can day du (DR-PRV-004): +84901234567 -> +84901***567. */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, phone.length() - 6) + "***" + phone.substring(phone.length() - 3);
    }
}
