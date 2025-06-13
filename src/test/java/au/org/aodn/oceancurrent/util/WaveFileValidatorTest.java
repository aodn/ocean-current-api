package au.org.aodn.oceancurrent.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WaveFileValidator Tests")
class WaveFileValidatorTest {

    @Nested
    @DisplayName("isNumeric Tests")
    class IsNumericTests {

        @Test
        @DisplayName("Should return true for valid numeric strings")
        void testIsNumeric_Valid() {
            assertTrue(WaveFileValidator.isNumeric("123"));
            assertTrue(WaveFileValidator.isNumeric("0"));
            assertTrue(WaveFileValidator.isNumeric("9999"));
            assertTrue(WaveFileValidator.isNumeric("2021"));
            assertTrue(WaveFileValidator.isNumeric("00"));
            assertTrue(WaveFileValidator.isNumeric("1234567890"));
        }

        @Test
        @DisplayName("Should return false for invalid numeric strings")
        void testIsNumeric_Invalid() {
            assertFalse(WaveFileValidator.isNumeric(null));
            assertFalse(WaveFileValidator.isNumeric(""));
            assertFalse(WaveFileValidator.isNumeric("12a"));
            assertFalse(WaveFileValidator.isNumeric("a123"));
            assertFalse(WaveFileValidator.isNumeric("12.3"));
            assertFalse(WaveFileValidator.isNumeric("12-3"));
            assertFalse(WaveFileValidator.isNumeric(" 123"));
            assertFalse(WaveFileValidator.isNumeric("123 "));
            assertFalse(WaveFileValidator.isNumeric("12 3"));
        }
    }

    @Nested
    @DisplayName("isValidYearPart Tests")
    class IsValidYearPartTests {

        @Test
        @DisplayName("Should return true for valid year parts")
        void testIsValidYearPart_Valid() {
            assertTrue(WaveFileValidator.isValidYearPart("y2021"));
            assertTrue(WaveFileValidator.isValidYearPart("y1999"));
            assertTrue(WaveFileValidator.isValidYearPart("y0000"));
            assertTrue(WaveFileValidator.isValidYearPart("y9999"));
            assertTrue(WaveFileValidator.isValidYearPart("y2025"));
        }

        @Test
        @DisplayName("Should return false for invalid year parts")
        void testIsValidYearPart_Invalid() {
            assertFalse(WaveFileValidator.isValidYearPart(null));
            assertFalse(WaveFileValidator.isValidYearPart(""));
            assertFalse(WaveFileValidator.isValidYearPart("y21")); // too short
            assertFalse(WaveFileValidator.isValidYearPart("y202")); // too short
            assertFalse(WaveFileValidator.isValidYearPart("y20211")); // too long
            assertFalse(WaveFileValidator.isValidYearPart("x2021")); // wrong prefix
            assertFalse(WaveFileValidator.isValidYearPart("2021")); // missing prefix
            assertFalse(WaveFileValidator.isValidYearPart("y202a")); // non-numeric
            assertFalse(WaveFileValidator.isValidYearPart("Y2021")); // wrong case
        }
    }

    @Nested
    @DisplayName("isValidMonthPart Tests")
    class IsValidMonthPartTests {

        @Test
        @DisplayName("Should return true for valid month parts")
        void testIsValidMonthPart_Valid() {
            assertTrue(WaveFileValidator.isValidMonthPart("m01"));
            assertTrue(WaveFileValidator.isValidMonthPart("m12"));
            assertTrue(WaveFileValidator.isValidMonthPart("m00"));
            assertTrue(WaveFileValidator.isValidMonthPart("m99"));
            assertTrue(WaveFileValidator.isValidMonthPart("m06"));
        }

        @Test
        @DisplayName("Should return false for invalid month parts")
        void testIsValidMonthPart_Invalid() {
            assertFalse(WaveFileValidator.isValidMonthPart(null));
            assertFalse(WaveFileValidator.isValidMonthPart(""));
            assertFalse(WaveFileValidator.isValidMonthPart("m1")); // too short
            assertFalse(WaveFileValidator.isValidMonthPart("m123")); // too long
            assertFalse(WaveFileValidator.isValidMonthPart("x01")); // wrong prefix
            assertFalse(WaveFileValidator.isValidMonthPart("01")); // missing prefix
            assertFalse(WaveFileValidator.isValidMonthPart("m1a")); // non-numeric
            assertFalse(WaveFileValidator.isValidMonthPart("M01")); // wrong case
        }
    }

    @Nested
    @DisplayName("isValidFilenamePart Tests")
    class IsValidFilenamePartTests {

        @Test
        @DisplayName("Should return true for valid filename parts")
        void testIsValidFilenamePart_Valid() {
            assertTrue(WaveFileValidator.isValidFilenamePart("2021010100.gif"));
            assertTrue(WaveFileValidator.isValidFilenamePart("1999123123.gif"));
            assertTrue(WaveFileValidator.isValidFilenamePart("0000000000.gif"));
            assertTrue(WaveFileValidator.isValidFilenamePart("9999999999.gif"));
            assertTrue(WaveFileValidator.isValidFilenamePart("2025061015.gif"));
        }

        @Test
        @DisplayName("Should return false for invalid filename parts")
        void testIsValidFilenamePart_Invalid() {
            assertFalse(WaveFileValidator.isValidFilenamePart(null));
            assertFalse(WaveFileValidator.isValidFilenamePart(""));
            assertFalse(WaveFileValidator.isValidFilenamePart("20210101.gif")); // 8 digits
            assertFalse(WaveFileValidator.isValidFilenamePart("202101010.gif")); // 9 digits
            assertFalse(WaveFileValidator.isValidFilenamePart("20210101001.gif")); // 11 digits
            assertFalse(WaveFileValidator.isValidFilenamePart("2021010100.png")); // wrong extension
            assertFalse(WaveFileValidator.isValidFilenamePart("2021010100.jpg")); // wrong extension
            assertFalse(WaveFileValidator.isValidFilenamePart("2021010100")); // no extension
            assertFalse(WaveFileValidator.isValidFilenamePart("202101010a.gif")); // non-numeric
            assertFalse(WaveFileValidator.isValidFilenamePart("2021-01-01.gif")); // wrong format
            assertFalse(WaveFileValidator.isValidFilenamePart("2021010100.GIF")); // wrong case
        }
    }

    @Nested
    @DisplayName("isValidWaveFile Tests")
    class IsValidWaveFileTests {

        @Test
        @DisplayName("Should return true for valid wave files")
        void testIsValidWaveFile_Valid() {
            assertTrue(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/2021010100.gif"));
            assertTrue(WaveFileValidator.isValidWaveFile("WAVES/y1999/m12/1999123123.gif"));
            assertTrue(WaveFileValidator.isValidWaveFile("WAVES/y2025/m06/2025061015.gif"));
            assertTrue(WaveFileValidator.isValidWaveFile("WAVES/y0000/m00/0000000000.gif"));
            assertTrue(WaveFileValidator.isValidWaveFile("WAVES/y9999/m99/9999999999.gif"));
        }

        @Test
        @DisplayName("Should return false for files with wrong extension")
        void testIsValidWaveFile_WrongExtension() {
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/2021010100.png"));
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/2021010100.jpg"));
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/2021010100.txt"));
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/2021010100.mp4"));
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/2021010100"));
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/2021010100.GIF"));
        }

        @Test
        @DisplayName("Should return false for files with wrong year format")
        void testIsValidWaveFile_WrongYear() {
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y21/m01/2021010100.gif")); // too short
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y202/m01/2021010100.gif")); // too short
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y20211/m01/2021010100.gif")); // too long
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/x2021/m01/2021010100.gif")); // wrong prefix
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y202a/m01/2021010100.gif")); // non-numeric
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/2021/m01/2021010100.gif")); // missing prefix
        }

        @Test
        @DisplayName("Should return false for files with wrong month format")
        void testIsValidWaveFile_WrongMonth() {
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m1/2021010100.gif")); // too short
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m123/2021010100.gif")); // too long
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/x01/2021010100.gif")); // wrong prefix
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m1a/2021010100.gif")); // non-numeric
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/01/2021010100.gif")); // missing prefix
        }

        @Test
        @DisplayName("Should return false for files with wrong timestamp format")
        void testIsValidWaveFile_WrongTimestamp() {
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/20210101.gif")); // 8 digits
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/202101010.gif")); // 9 digits
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/20210101001.gif")); // 11 digits
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/202101010a.gif")); // non-numeric
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/2021-01-01.gif")); // wrong format
        }

        @Test
        @DisplayName("Should return false for files with wrong path structure")
        void testIsValidWaveFile_WrongPath() {
            assertFalse(WaveFileValidator.isValidWaveFile("OTHER/y2021/m01/2021010100.gif")); // wrong prefix
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/2021010100.gif")); // missing month
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/extra/2021010100.gif")); // extra segment
            assertFalse(WaveFileValidator.isValidWaveFile("y2021/m01/2021010100.gif")); // missing WAVES
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES\\y2021\\m01\\2021010100.gif")); // wrong separators
        }

        @Test
        @DisplayName("Should return false for files with wrong length")
        void testIsValidWaveFile_WrongLength() {
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y21/m1/2021.gif")); // too short
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/2021010100extra.gif")); // too long
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/short.gif")); // way too short
        }

        @Test
        @DisplayName("Should return false for edge cases")
        void testIsValidWaveFile_EdgeCases() {
            assertFalse(WaveFileValidator.isValidWaveFile(null));
            assertFalse(WaveFileValidator.isValidWaveFile(""));
            assertFalse(WaveFileValidator.isValidWaveFile("   "));
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/.gif"));
            assertFalse(WaveFileValidator.isValidWaveFile("random string"));
        }

        @Test
        @DisplayName("Should validate exact length requirement")
        void testIsValidWaveFile_ExactLength() {
            String validFile = "WAVES/y2021/m01/2021010100.gif";
            assertEquals(30, validFile.length(), "Test case should be exactly 30 characters");
            assertTrue(WaveFileValidator.isValidWaveFile(validFile));

            // Test one character shorter and longer
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/202101010.gif")); // 29 chars
            assertFalse(WaveFileValidator.isValidWaveFile("WAVES/y2021/m01/20210101001.gif")); // 31 chars
        }
    }
}
