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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final AwsProperties awsProperties;

    private static final String WAVES_REGION = "Au";
    private static final Pattern WAVES_FILE_PATTERN = Pattern.compile("y\\d{4}/m\\d{2}/\\d{10}\\.gif");

    public List<ImageMetadataEntry> listAndConvertSurfaceWavesFiles(String prefix) {
        log.info("Listing S3 objects with prefix: {}", prefix);

        List<ImageMetadataEntry> entries = new ArrayList<>();
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                    .bucket(awsProperties.getS3().getBucketName())
                    .maxKeys(1000);

            if (prefix != null && !prefix.isEmpty()) {
                requestBuilder.prefix(prefix);
            }

            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());

            for (S3Object s3Object : response.contents()) {
                String key = s3Object.key();

                if (WAVES_FILE_PATTERN.matcher(key).find()) {
                    ImageMetadataEntry entry = createMetadataEntry(key);
                    entries.add(entry);
                }
            }

            continuationToken = response.nextContinuationToken();

        } while (continuationToken != null);

        log.info("Found {} S3 objects in bucket '{}' with prefix '{}'",
                entries.size(), awsProperties.getS3().getBucketName(), prefix);

        return entries;
    }

    public List<ImageMetadataEntry> listAllSurfaceWaves() {
        return listAndConvertSurfaceWavesFiles(awsProperties.getS3().getWavesPrefix());
    }

    private ImageMetadataEntry createMetadataEntry(String key) {
        ImageMetadataEntry entry = new ImageMetadataEntry();
        entry.setProductId("surfaceWaves");
        entry.setPath(awsProperties.getS3().getWavesPrefix());
        entry.setRegion(WAVES_REGION);

        // Extract filename from the path WAVES/y2026/m06/2025060422.gif
        String fileName = key.substring(key.lastIndexOf('/') + 1);
        entry.setFileName(fileName);

        return entry;
    }
}
