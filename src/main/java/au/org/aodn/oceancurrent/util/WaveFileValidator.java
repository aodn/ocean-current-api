package au.org.aodn.oceancurrent.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for validating wave file formats
 * Expected format: WAVES/y{year}/m{month}/{timestamp}.gif
 * Example: WAVES/y2021/m01/2021010100.gif
 */
@Slf4j
@UtilityClass
public class WaveFileValidator {

    private static final String EXPECTED_EXTENSION = ".gif";
    private static final int EXPECTED_FILENAME_LENGTH = 14; // "2021010100.gif"
    private static final int EXPECTED_TOTAL_LENGTH = 30; // "WAVES/y2021/m01/2021010100.gif"
    private static final int EXPECTED_PARTS_COUNT = 4; // ["WAVES", "y2021", "m01", "2021010100.gif"]
    private static final int EXPECTED_YEAR_PART_LENGTH = 5; // "y2021"
    private static final int EXPECTED_MONTH_PART_LENGTH = 3; // "m01"
    private static final int EXPECTED_TIMESTAMP_LENGTH = 10; // "2021010100"

    public static boolean isValidWaveFile(String key) {
        if (key == null || !key.endsWith(EXPECTED_EXTENSION)) {
            return false;
        }

        if (key.length() != EXPECTED_TOTAL_LENGTH) {
            return false;
        }

        if (!key.startsWith("WAVES/y")) {
            return false;
        }

        try {
            String[] parts = key.split("/");
            if (parts.length != EXPECTED_PARTS_COUNT) {
                return false;
            }

            String yearPart = parts[1];
            if (!isValidYearPart(yearPart)) {
                return false;
            }

            String monthPart = parts[2];
            if (!isValidMonthPart(monthPart)) {
                return false;
            }

            String filename = parts[3];
            return isValidFilenamePart(filename);

        } catch (Exception e) {
            log.debug("Error parsing wave file key '{}': {}", key, e.getMessage());
            return false;
        }
    }

    public static boolean isValidYearPart(String yearPart) {
        if (yearPart == null || yearPart.length() != EXPECTED_YEAR_PART_LENGTH) {
            return false;
        }

        if (!yearPart.startsWith("y")) {
            return false;
        }

        return isNumeric(yearPart.substring(1));
    }

    public static boolean isValidMonthPart(String monthPart) {
        if (monthPart == null || monthPart.length() != EXPECTED_MONTH_PART_LENGTH) {
            return false;
        }

        if (!monthPart.startsWith("m")) {
            return false;
        }

        return isNumeric(monthPart.substring(1));
    }

    public static boolean isValidFilenamePart(String filename) {
        if (filename == null || filename.length() != EXPECTED_FILENAME_LENGTH) {
            return false;
        }

        if (!filename.endsWith(EXPECTED_EXTENSION)) {
            return false;
        }

        String timestampPart = filename.substring(0, EXPECTED_TIMESTAMP_LENGTH);
        return isNumeric(timestampPart);
    }

    public static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) {
                return false;
            }
        }

        return true;
    }
}
