package au.org.aodn.oceancurrent.service;

import au.org.aodn.oceancurrent.configuration.aws.AwsProperties;
import au.org.aodn.oceancurrent.exception.S3ServiceException;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import au.org.aodn.oceancurrent.util.WaveFileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final AwsProperties awsProperties;

    private static final String WAVES_REGION = "Au";
    private static final String WAVES_PRODUCT_ID = "surfaceWaves-wave";

    /**
     * Lists and converts S3 surface waves files with the given prefix
     *
     * @param prefix S3 prefix to filter objects
     * @return List of ImageMetadataEntry objects
     * @throws S3ServiceException if S3 operations fail
     */
    public List<ImageMetadataEntry> listAndConvertSurfaceWavesFiles(String prefix) {
        validateInputs(prefix);

        log.info("Listing S3 objects with prefix: '{}' in bucket: '{}'", prefix, awsProperties.getS3().getBucketName());

        List<ImageMetadataEntry> entries = new ArrayList<>();
        String continuationToken = null;

        try {
            do {
                ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder()
                        .bucket(awsProperties.getS3().getBucketName())
                        .maxKeys(awsProperties.getS3().getMaxKeysPerRequest());

                if (StringUtils.hasText(prefix)) {
                    requestBuilder.prefix(prefix);
                }

                if (continuationToken != null) {
                    requestBuilder.continuationToken(continuationToken);
                }

                ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());

                for (S3Object s3Object : response.contents()) {
                    String key = s3Object.key();

                    if (WaveFileValidator.isValidWaveFile(key)) {
                        ImageMetadataEntry entry = createMetadataEntry(key);
                        entries.add(entry);
                    }
                }

                continuationToken = response.nextContinuationToken();

            } while (continuationToken != null);

            log.info("Found {} S3 objects in bucket '{}' with prefix '{}'",
                    entries.size(), awsProperties.getS3().getBucketName(), prefix);

        } catch (S3Exception e) {
            String errorMessage = String.format("Failed to list S3 objects with prefix '%s' in bucket '%s': %s",
                    prefix, awsProperties.getS3().getBucketName(), e.getMessage());
            log.error(errorMessage, e);
            throw new S3ServiceException(errorMessage, e);
        } catch (SdkException e) {
            String errorMessage = String.format("AWS SDK error while listing S3 objects: %s", e.getMessage());
            log.error(errorMessage, e);
            throw new S3ServiceException(errorMessage, e);
        } catch (Exception e) {
            String errorMessage = String.format("Unexpected error while processing S3 objects: %s", e.getMessage());
            log.error(errorMessage, e);
            throw new S3ServiceException(errorMessage, e);
        }

        return entries;
    }

    public List<ImageMetadataEntry> listAllSurfaceWaves() {
        return listAndConvertSurfaceWavesFiles(awsProperties.getS3().getWavesPrefix());
    }

    private void validateInputs(String prefix) {
        String bucketName = awsProperties.getS3().getBucketName();

        if (!StringUtils.hasText(bucketName)) {
            throw new S3ServiceException("S3 bucket name is not configured or is empty");
        }

        if (prefix != null && prefix.contains("..")) {
            throw new S3ServiceException("Invalid prefix: contains directory traversal patterns");
        }
    }

    private ImageMetadataEntry createMetadataEntry(String key) {
        ImageMetadataEntry entry = new ImageMetadataEntry();

        entry.setProductId(WAVES_PRODUCT_ID);
        entry.setPath(awsProperties.getS3().getWavesPrefix());
        entry.setRegion(WAVES_REGION);

        String fileName = key.substring(key.lastIndexOf('/') + 1);
        entry.setFileName(fileName);

        return entry;
    }

    public boolean isBucketAccessible() {
        try {
            String bucketName = awsProperties.getS3().getBucketName();

            HeadBucketRequest request = HeadBucketRequest.builder()
                    .bucket(bucketName)
                    .build();

            s3Client.headBucket(request);
            log.debug("Successfully verified access to bucket: {}", bucketName);
            return true;

        } catch (NoSuchBucketException e) {
            log.warn("S3 bucket '{}' does not exist", awsProperties.getS3().getBucketName());
            return false;
        } catch (S3Exception e) {
            log.warn("Cannot access S3 bucket '{}': {}", awsProperties.getS3().getBucketName(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Unexpected error checking bucket accessibility: {}", e.getMessage(), e);
            return false;
        }
    }
}
