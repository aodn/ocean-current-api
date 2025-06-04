package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.aws.AwsProperties;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {
    private final S3Client s3Client;
    private final AwsProperties awsProperties;
    private static final Pattern WAVES_FILE_PATTERN = Pattern.compile("y\\d{4}/m\\d{2}/\\d{10}\\.gif");
    private static final String WAVES_REGION = "Au";

    public List<ImageMetadataEntry> listWavesFiles() {
        List<ImageMetadataEntry> entries = new ArrayList<>();
        String prefix = awsProperties.getS3().getWavesPrefix();
        String bucketName = awsProperties.getS3().getBucketName();

        log.info("Listing waves files from S3 bucket: {} with prefix: {}", bucketName, prefix);

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        try {
            ListObjectsV2Response response = s3Client.listObjectsV2(request);
            for (S3Object s3Object : response.contents()) {
                String key = s3Object.key();
                if (WAVES_FILE_PATTERN.matcher(key).find()) {
                    ImageMetadataEntry entry = createMetadataEntry(key);
                    entries.add(entry);
                }
            }
        } catch (S3Exception e) {
            log.error("S3 Error: {}", e.getMessage());
            log.error("Error Code: {}", e.awsErrorDetails().errorCode());
            log.error("Error Message: {}", e.awsErrorDetails().errorMessage());
            log.error("Request ID: {}", e.requestId());
            log.error("Service Name: {}", e.awsErrorDetails().serviceName());
            throw new RuntimeException("Failed to list S3 objects: " + e.awsErrorDetails().errorMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error listing S3 objects", e);
            throw new RuntimeException("Failed to list S3 objects", e);
        }

        log.info("Found {} waves files in S3", entries.size());
        return entries;
    }

    private ImageMetadataEntry createMetadataEntry(String key) {
        ImageMetadataEntry entry = new ImageMetadataEntry();
        entry.setProductId("surfaceWaves");
        entry.setPath(key);
        entry.setRegion(WAVES_REGION);

        // Extract filename from the path
        String fileName = key.substring(key.lastIndexOf('/') + 1);
        entry.setFileName(fileName);

        return entry;
    }
}
