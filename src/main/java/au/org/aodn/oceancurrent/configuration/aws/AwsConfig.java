package au.org.aodn.oceancurrent.configuration.aws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AwsConfig {
    private final AwsProperties awsProperties;

    @Bean
    public S3Client s3Client() {
        log.info("Initializing AWS S3 client");
        log.info("AWS Region: {}", awsProperties.getRegion());
        log.info("AWS Access Key ID: {}", awsProperties.getAccessKey());
        log.info("AWS Access Key ID length: {}", awsProperties.getAccessKey() != null ? awsProperties.getAccessKey().length() : 0);
        log.info("AWS Secret Key length: {}", awsProperties.getSecretKey() != null ? awsProperties.getSecretKey().length() : 0);
        log.info("S3 Bucket Name: {}", awsProperties.getS3().getBucketName());
        log.info("S3 Waves Prefix: {}", awsProperties.getS3().getWavesPrefix());

        // Log the first few characters of the access key for verification
        if (awsProperties.getAccessKey() != null && awsProperties.getAccessKey().length() >= 4) {
            log.info("AWS Access Key ID starts with: {}", awsProperties.getAccessKey().substring(0, 4));
        }

        if (awsProperties.getAccessKey() == null || awsProperties.getAccessKey().trim().isEmpty()) {
            log.error("AWS Access Key ID is null or empty");
            throw new IllegalStateException("AWS Access Key ID is not configured");
        }

        if (awsProperties.getSecretKey() == null || awsProperties.getSecretKey().trim().isEmpty()) {
            log.error("AWS Secret Key is null or empty");
            throw new IllegalStateException("AWS Secret Key is not configured");
        }

        if (awsProperties.getS3().getBucketName() == null || awsProperties.getS3().getBucketName().trim().isEmpty()) {
            log.error("S3 Bucket Name is null or empty");
            throw new IllegalStateException("S3 Bucket Name is not configured");
        }

        try {
            return S3Client.builder()
                    .region(Region.of(awsProperties.getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    awsProperties.getAccessKey().trim(),
                                    awsProperties.getSecretKey().trim()
                            )
                    ))
                    .build();
        } catch (Exception e) {
            log.error("Failed to create S3 client: {}", e.getMessage());
            throw e;
        }
    }
}
