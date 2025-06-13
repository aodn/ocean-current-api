package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.aws.AwsProperties;
import au.org.aodn.oceancurrent.exception.S3ServiceException;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3Service Tests")
class S3ServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private AwsProperties awsProperties;

    @Mock
    private AwsProperties.S3 s3Properties;

    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new S3Service(s3Client, awsProperties);
        when(awsProperties.getS3()).thenReturn(s3Properties);
    }

    @Nested
    @DisplayName("listAndConvertSurfaceWavesFiles Tests")
    class ListAndConvertSurfaceWavesFilesTests {

        @Test
        @DisplayName("Should successfully list and convert valid wave files")
        void testListAndConvertSurfaceWavesFiles_Success() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);
            when(s3Properties.getWavesPrefix()).thenReturn("WAVES/");

            S3Object validWave1 = S3Object.builder()
                    .key("WAVES/y2021/m01/2021010100.gif")
                    .build();

            S3Object validWave2 = S3Object.builder()
                    .key("WAVES/y2021/m02/2021020100.gif")
                    .build();

            S3Object invalidFile = S3Object.builder()
                    .key("invalid/path/structure.gif")
                    .build();

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(Arrays.asList(validWave1, validWave2, invalidFile))
                    .nextContinuationToken(null)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");

            // Assert
            assertEquals(2, result.size());

            ImageMetadataEntry entry1 = result.get(0);
            assertEquals("surfaceWaves", entry1.getProductId());
            assertEquals("WAVES/", entry1.getPath());
            assertEquals("2021010100.gif", entry1.getFileName());
            assertEquals("Au", entry1.getRegion());
            assertNull(entry1.getDepth());

            ImageMetadataEntry entry2 = result.get(1);
            assertEquals("surfaceWaves", entry2.getProductId());
            assertEquals("WAVES/", entry2.getPath());
            assertEquals("2021020100.gif", entry2.getFileName());
            assertEquals("Au", entry2.getRegion());
            assertNull(entry2.getDepth());

            verify(s3Client, times(1)).listObjectsV2(any(ListObjectsV2Request.class));
        }

        @Test
        @DisplayName("Should handle pagination with continuation token")
        void testListAndConvertSurfaceWavesFiles_WithPagination() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            S3Object wave1 = S3Object.builder()
                    .key("WAVES/y2021/m01/2021010100.gif")
                    .build();

            S3Object wave2 = S3Object.builder()
                    .key("WAVES/y2021/m02/2021020100.gif")
                    .build();

            ListObjectsV2Response firstResponse = ListObjectsV2Response.builder()
                    .contents(Collections.singletonList(wave1))
                    .nextContinuationToken("token123")
                    .build();

            ListObjectsV2Response secondResponse = ListObjectsV2Response.builder()
                    .contents(Collections.singletonList(wave2))
                    .nextContinuationToken(null)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(firstResponse, secondResponse);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");

            // Assert
            assertEquals(2, result.size());
            verify(s3Client, times(2)).listObjectsV2(any(ListObjectsV2Request.class));
        }

        @Test
        @DisplayName("Should return empty list when no files match pattern")
        void testListAndConvertSurfaceWavesFiles_NoMatchingFiles() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            S3Object nonMatchingFile = S3Object.builder()
                    .key("OTHER/random/file.txt")
                    .build();

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(Collections.singletonList(nonMatchingFile))
                    .nextContinuationToken(null)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("OTHER/");

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should throw S3ServiceException when S3Exception occurs")
        void testListAndConvertSurfaceWavesFiles_S3Exception() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            S3Exception s3Exception = (S3Exception) S3Exception.builder()
                    .message("Access denied")
                    .statusCode(403)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenThrow(s3Exception);

            // Act & Assert
            S3ServiceException exception = assertThrows(S3ServiceException.class,
                    () -> s3Service.listAndConvertSurfaceWavesFiles("WAVES/"));

            assertTrue(exception.getMessage().contains("Failed to list S3 objects"));
            assertTrue(exception.getMessage().contains("Access denied"));
            assertEquals(s3Exception, exception.getCause());
        }

        @Test
        @DisplayName("Should throw S3ServiceException when SdkException occurs")
        void testListAndConvertSurfaceWavesFiles_SdkException() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            SdkException sdkException = SdkException.builder()
                    .message("Network timeout")
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenThrow(sdkException);

            // Act & Assert
            S3ServiceException exception = assertThrows(S3ServiceException.class,
                    () -> s3Service.listAndConvertSurfaceWavesFiles("WAVES/"));

            assertTrue(exception.getMessage().contains("AWS SDK error"));
            assertTrue(exception.getMessage().contains("Network timeout"));
            assertEquals(sdkException, exception.getCause());
        }

        @Test
        @DisplayName("Should throw S3ServiceException when unexpected exception occurs")
        void testListAndConvertSurfaceWavesFiles_UnexpectedException() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            RuntimeException runtimeException = new RuntimeException("Unexpected error");

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenThrow(runtimeException);

            // Act & Assert
            S3ServiceException exception = assertThrows(S3ServiceException.class,
                    () -> s3Service.listAndConvertSurfaceWavesFiles("WAVES/"));

            assertTrue(exception.getMessage().contains("Unexpected error"));
            assertEquals(runtimeException, exception.getCause());
        }
    }

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("Should throw S3ServiceException when bucket name is null")
        void testValidation_NullBucketName() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn(null);

            // Act & Assert
            S3ServiceException exception = assertThrows(S3ServiceException.class,
                    () -> s3Service.listAndConvertSurfaceWavesFiles("WAVES/"));

            assertTrue(exception.getMessage().contains("S3 bucket name is not configured"));
        }

        @Test
        @DisplayName("Should throw S3ServiceException when bucket name is empty")
        void testValidation_EmptyBucketName() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("");

            // Act & Assert
            S3ServiceException exception = assertThrows(S3ServiceException.class,
                    () -> s3Service.listAndConvertSurfaceWavesFiles("WAVES/"));

            assertTrue(exception.getMessage().contains("S3 bucket name is not configured"));
        }

        @Test
        @DisplayName("Should throw S3ServiceException when prefix contains directory traversal")
        void testValidation_InvalidPrefix() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");

            // Act & Assert
            S3ServiceException exception = assertThrows(S3ServiceException.class,
                    () -> s3Service.listAndConvertSurfaceWavesFiles("../malicious/path"));

            assertTrue(exception.getMessage().contains("Invalid prefix: contains directory traversal patterns"));
        }

        @Test
        @DisplayName("Should accept null prefix")
        void testValidation_NullPrefix() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(Collections.emptyList())
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act & Assert
            assertDoesNotThrow(() -> s3Service.listAndConvertSurfaceWavesFiles(null));
        }
    }

    @Nested
    @DisplayName("listAllSurfaceWaves Tests")
    class ListAllSurfaceWavesTests {

        @Test
        @DisplayName("Should call listAndConvertSurfaceWavesFiles with waves prefix")
        void testListAllSurfaceWaves() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getWavesPrefix()).thenReturn("WAVES/");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            S3Object s3Object = S3Object.builder()
                    .key("WAVES/y2021/m01/2021010100.gif")
                    .build();

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(Collections.singletonList(s3Object))
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAllSurfaceWaves();

            // Assert
            assertEquals(1, result.size());
            assertEquals("surfaceWaves", result.get(0).getProductId());
            verify(s3Client).listObjectsV2(any(ListObjectsV2Request.class));
        }
    }

    @Nested
    @DisplayName("isBucketAccessible Tests")
    class IsBucketAccessibleTests {

        @Test
        @DisplayName("Should return true when bucket is accessible")
        void testIsBucketAccessible_Success() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");

            HeadBucketResponse response = HeadBucketResponse.builder().build();
            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenReturn(response);

            // Act
            boolean result = s3Service.isBucketAccessible();

            // Assert
            assertTrue(result);
            verify(s3Client).headBucket(any(HeadBucketRequest.class));
        }

        @Test
        @DisplayName("Should return false when bucket does not exist")
        void testIsBucketAccessible_NoSuchBucket() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("non-existent-bucket");

            NoSuchBucketException exception = NoSuchBucketException.builder()
                    .message("Bucket does not exist")
                    .build();

            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenThrow(exception);

            // Act
            boolean result = s3Service.isBucketAccessible();

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when access is denied")
        void testIsBucketAccessible_AccessDenied() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");

            S3Exception exception = (S3Exception) S3Exception.builder()
                    .message("Access denied")
                    .statusCode(403)
                    .build();

            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenThrow(exception);

            // Act
            boolean result = s3Service.isBucketAccessible();

            // Assert
            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when unexpected exception occurs")
        void testIsBucketAccessible_UnexpectedException() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");

            RuntimeException exception = new RuntimeException("Unexpected error");

            when(s3Client.headBucket(any(HeadBucketRequest.class)))
                    .thenThrow(exception);

            // Act
            boolean result = s3Service.isBucketAccessible();

            // Assert
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Wave Pattern Matching Tests")
    class WavePatternMatchingTests {

        @Test
        @DisplayName("Should match valid wave file patterns")
        void testWavePatternMatching_ValidPatterns() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);
            when(s3Properties.getWavesPrefix()).thenReturn("WAVES/");

            List<S3Object> validWaveFiles = Arrays.asList(
                    S3Object.builder().key("WAVES/y2021/m01/2021010100.gif").build(),
                    S3Object.builder().key("WAVES/y2023/m12/2023123123.gif").build(),
                    S3Object.builder().key("WAVES/y1999/m06/1999061515.gif").build()
            );

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(validWaveFiles)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");

            // Assert
            assertEquals(3, result.size());
            assertEquals("2021010100.gif", result.get(0).getFileName());
            assertEquals("2023123123.gif", result.get(1).getFileName());
            assertEquals("1999061515.gif", result.get(2).getFileName());
        }

        @Test
        @DisplayName("Should not match invalid wave file patterns")
        void testWavePatternMatching_InvalidPatterns() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            List<S3Object> invalidFiles = Arrays.asList(
                    S3Object.builder().key("WAVES/y21/m01/2021010100.gif").build(), // wrong year format
                    S3Object.builder().key("WAVES/y2021/m1/2021010100.gif").build(), // wrong month format
                    S3Object.builder().key("WAVES/y2021/m01/20210101.gif").build(), // wrong filename format (8 digits)
                    S3Object.builder().key("WAVES/y2021/m01/2021010100.png").build(), // wrong extension
                    S3Object.builder().key("OTHER/y2021/m01/2021010100.gif").build() // wrong path
            );

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(invalidFiles)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");

            // Assert
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("String-based Validation Tests")
    class StringValidationTests {

        @Test
        @DisplayName("Should validate correct wave file format")
        void testValidWaveFileFormat_Valid() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            List<S3Object> validFiles = Arrays.asList(
                    S3Object.builder().key("WAVES/y2021/m01/2021010100.gif").build(),
                    S3Object.builder().key("WAVES/y1999/m12/1999120523.gif").build(),
                    S3Object.builder().key("WAVES/y2025/m06/2025061015.gif").build()
            );

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(validFiles)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");

            // Assert
            assertEquals(3, result.size());
        }

        @Test
        @DisplayName("Should reject files with wrong extension")
        void testValidWaveFileFormat_WrongExtension() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            List<S3Object> invalidFiles = Arrays.asList(
                    S3Object.builder().key("WAVES/y2021/m01/2021010100.png").build(),
                    S3Object.builder().key("WAVES/y2021/m01/2021010100.jpg").build(),
                    S3Object.builder().key("WAVES/y2021/m01/2021010100.txt").build(),
                    S3Object.builder().key("WAVES/y2021/m01/2021010100.mp4").build(),
                    S3Object.builder().key("WAVES/y2021/m01/2021010100").build() // no extension
            );

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(invalidFiles)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should reject files with invalid year format")
        void testValidWaveFileFormat_InvalidYear() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            List<S3Object> invalidFiles = Arrays.asList(
                    S3Object.builder().key("WAVES/y21/m01/2021010100.gif").build(), // 2 digits
                    S3Object.builder().key("WAVES/y202/m01/2021010100.gif").build(), // 3 digits
                    S3Object.builder().key("WAVES/y20211/m01/2021010100.gif").build(), // 5 digits
                    S3Object.builder().key("WAVES/x2021/m01/2021010100.gif").build(), // wrong prefix
                    S3Object.builder().key("WAVES/y202a/m01/2021010100.gif").build() // non-numeric
            );

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(invalidFiles)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should reject files with invalid month format")
        void testValidWaveFileFormat_InvalidMonth() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            List<S3Object> invalidFiles = Arrays.asList(
                    S3Object.builder().key("WAVES/y2021/m1/2021010100.gif").build(), // 1 digit
                    S3Object.builder().key("WAVES/y2021/m123/2021010100.gif").build(), // 3 digits
                    S3Object.builder().key("WAVES/y2021/x01/2021010100.gif").build(), // wrong prefix
                    S3Object.builder().key("WAVES/y2021/m1a/2021010100.gif").build() // non-numeric
            );

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(invalidFiles)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should reject files with invalid timestamp format")
        void testValidWaveFileFormat_InvalidTimestamp() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            List<S3Object> invalidFiles = Arrays.asList(
                    S3Object.builder().key("WAVES/y2021/m01/202101010.gif").build(), // 9 digits
                    S3Object.builder().key("WAVES/y2021/m01/20210101.gif").build(), // 8 digits
                    S3Object.builder().key("WAVES/y2021/m01/20210101001.gif").build(), // 11 digits
                    S3Object.builder().key("WAVES/y2021/m01/202101010a.gif").build(), // non-numeric
                    S3Object.builder().key("WAVES/y2021/m01/2021-01-01.gif").build() // wrong format with dashes
            );

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(invalidFiles)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should reject files with invalid path structure")
        void testValidWaveFileFormat_InvalidPathStructure() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            List<S3Object> invalidFiles = Arrays.asList(
                    S3Object.builder().key("OTHER/y2021/m01/2021010100.gif").build(), // wrong prefix
                    S3Object.builder().key("WAVES/y2021/2021010100.gif").build(), // missing month part
                    S3Object.builder().key("WAVES/y2021/m01/extra/2021010100.gif").build(), // extra path segment
                    S3Object.builder().key("y2021/m01/2021010100.gif").build(), // missing WAVES prefix
                    S3Object.builder().key("WAVES\\y2021\\m01\\2021010100.gif").build() // wrong separators
            );

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(invalidFiles)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should reject files that are too short")
        void testValidWaveFileFormat_TooShort() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            List<S3Object> invalidFiles = Arrays.asList(
                    S3Object.builder().key("WAVES/y21/m1/2021.gif").build(),
                    S3Object.builder().key("WAVES/short.gif").build(),
                    S3Object.builder().key("WAVES/.gif").build(),
                    S3Object.builder().key("").build()
            );

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(invalidFiles)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");

            // Assert
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should handle null and empty keys gracefully")
        void testValidWaveFileFormat_NullAndEmpty() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            // Note: S3Object.builder() won't accept null keys, so this tests the validation logic
            // by ensuring the service handles edge cases gracefully
            List<S3Object> edgeCaseFiles = Arrays.asList(
                    S3Object.builder().key("").build(),
                    S3Object.builder().key(" ").build(),
                    S3Object.builder().key("   ").build()
            );

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(edgeCaseFiles)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act & Assert - should not throw exceptions
            assertDoesNotThrow(() -> {
                List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");
                assertTrue(result.isEmpty());
            });
        }

        @Test
        @DisplayName("Should validate boundary year values")
        void testValidWaveFileFormat_BoundaryYears() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            List<S3Object> boundaryFiles = Arrays.asList(
                    S3Object.builder().key("WAVES/y0000/m01/0000010100.gif").build(), // year 0000
                    S3Object.builder().key("WAVES/y9999/m01/9999010100.gif").build(), // year 9999
                    S3Object.builder().key("WAVES/y1900/m01/1900010100.gif").build(), // old year
                    S3Object.builder().key("WAVES/y2100/m01/2100010100.gif").build()  // future year
            );

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(boundaryFiles)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");

            // Assert - all should be valid as they follow the correct format
            assertEquals(4, result.size());
        }

        @Test
        @DisplayName("Should validate boundary month values")
        void testValidWaveFileFormat_BoundaryMonths() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            List<S3Object> validMonths = Arrays.asList(
                    S3Object.builder().key("WAVES/y2021/m01/2021010100.gif").build(), // January
                    S3Object.builder().key("WAVES/y2021/m12/2021010100.gif").build(), // December
                    S3Object.builder().key("WAVES/y2021/m00/2021010100.gif").build(), // month 00 (format valid)
                    S3Object.builder().key("WAVES/y2021/m99/2021010100.gif").build()  // month 99 (format valid)
            );

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(validMonths)
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");

            // Assert - all should be valid as they follow the correct format structure
            assertEquals(4, result.size());
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle empty S3 response")
        void testEmptyS3Response() {
            // Arrange
            when(s3Properties.getBucketName()).thenReturn("test-bucket");
            when(s3Properties.getMaxKeysPerRequest()).thenReturn(1000);

            ListObjectsV2Response response = ListObjectsV2Response.builder()
                    .contents(Collections.emptyList())
                    .build();

            when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                    .thenReturn(response);

            // Act
            List<ImageMetadataEntry> result = s3Service.listAndConvertSurfaceWavesFiles("WAVES/");

            // Assert
            assertTrue(result.isEmpty());
        }
    }
}
