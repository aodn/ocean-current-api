package au.org.aodn.oceancurrent.configuration.aws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class AwsConfig {

    private final AwsProperties awsProperties;

    @Bean
    public S3Client s3Client() {
        log.info("Configuring AWS S3 Client for region: {}", awsProperties.getRegion());

        S3ClientBuilder clientBuilder = S3Client.builder()
                .region(Region.of(awsProperties.getRegion()));

        // Use provided credentials if available, otherwise fall back to default provider chain
        if (awsProperties.getAccessKeyId() != null && !awsProperties.getAccessKeyId().isEmpty() &&
            awsProperties.getSecretAccessKey() != null && !awsProperties.getSecretAccessKey().isEmpty()) {

            log.info("Using static AWS credentials - Access Key ID: {}***",
                    awsProperties.getAccessKeyId().substring(0, Math.min(4, awsProperties.getAccessKeyId().length())));
            log.debug("Static credentials configured with access key starting with: {}",
                    awsProperties.getAccessKeyId().substring(0, Math.min(8, awsProperties.getAccessKeyId().length())));

            AwsCredentials credentials = AwsBasicCredentials.create(
                    awsProperties.getAccessKeyId(),
                    awsProperties.getSecretAccessKey());

            clientBuilder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        } else {
            log.info("No static AWS credentials configured, using DefaultCredentialsProvider chain");
            log.debug("DefaultCredentialsProvider will attempt to resolve credentials from: " +
                    "1) Java system properties (aws.accessKeyId, aws.secretAccessKey, aws.sessionToken), " +
                    "2) Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN), " +
                    "3) Web Identity Token from AWS STS (WebIdentityTokenFileCredentialsProvider), " +
                    "4) Shared credentials and config files (ProfileCredentialsProvider), " +
                    "5) Amazon ECS container credentials (ContainerCredentialsProvider), " +
                    "6) Amazon EC2 instance IAM role credentials (InstanceProfileCredentialsProvider)");

            // Create default credentials provider and test which one is being used
            AwsCredentialsProvider defaultProvider = DefaultCredentialsProvider.create();
            detectAndLogCredentialSource(defaultProvider);

            clientBuilder.credentialsProvider(defaultProvider);
        }

        log.info("AWS S3 Client configuration completed successfully");
        return clientBuilder.build();
    }

    private void detectAndLogCredentialSource(AwsCredentialsProvider credentialsProvider) {
        try {
            AwsCredentials credentials = credentialsProvider.resolveCredentials();
            String accessKeyId = credentials.accessKeyId();

            log.info("Successfully resolved AWS credentials - Access Key ID: {}***",
                    accessKeyId.substring(0, Math.min(4, accessKeyId.length())));

            // Try to determine the source by checking environment and system properties
            String credentialSource = determineCredentialSource(accessKeyId);
            log.info("Detected credential source: {}", credentialSource);

        } catch (SdkClientException e) {
            log.error("Failed to resolve AWS credentials from DefaultCredentialsProvider: {}", e.getMessage());
            throw e;
        }
    }

    private String determineCredentialSource(String accessKeyId) {
        // 1. Check Java system properties (first in chain)
        String sysPropAccessKey = System.getProperty("aws.accessKeyId");
        if (sysPropAccessKey != null && sysPropAccessKey.equals(accessKeyId)) {
            log.debug("Credentials match Java system property aws.accessKeyId");
            return "Java System Properties (aws.accessKeyId, aws.secretAccessKey, aws.sessionToken)";
        }

        // 2. Check environment variables (second in chain)
        String envAccessKey = System.getenv("AWS_ACCESS_KEY_ID");
        if (envAccessKey != null && envAccessKey.equals(accessKeyId)) {
            log.debug("Credentials match environment variable AWS_ACCESS_KEY_ID");
            return "Environment Variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN)";
        }

        // 3. Check for Web Identity Token (third in chain)
        String webIdentityTokenFile = System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE");
        String roleArn = System.getenv("AWS_ROLE_ARN");
        if (webIdentityTokenFile != null && roleArn != null) {
            log.debug("Web Identity Token file and role ARN detected: tokenFile={}, roleArn={}",
                     webIdentityTokenFile, roleArn);
            return "Web Identity Token from AWS STS (WebIdentityTokenFileCredentialsProvider)";
        }

        // 4. Check for shared credentials and config files (fourth in chain)
        String awsCredentialsFile = System.getenv("AWS_SHARED_CREDENTIALS_FILE");
        String awsConfigFile = System.getenv("AWS_CONFIG_FILE");
        String homeDir = System.getProperty("user.home");
        String profileName = System.getenv("AWS_PROFILE");

        boolean hasCredentialsFile = awsCredentialsFile != null ||
                                   (homeDir != null && java.nio.file.Files.exists(java.nio.file.Paths.get(homeDir, ".aws", "credentials")));
        boolean hasConfigFile = awsConfigFile != null ||
                              (homeDir != null && java.nio.file.Files.exists(java.nio.file.Paths.get(homeDir, ".aws", "config")));

        if (hasCredentialsFile || hasConfigFile) {
            String profile = profileName != null ? profileName : "default";
            log.debug("AWS shared credentials/config files detected, using profile: {}", profile);
            return "Shared credentials and config files (ProfileCredentialsProvider) - Profile: " + profile;
        }

        // 5. Check for ECS container credentials (fifth in chain)
        String ecsCredentialsUri = System.getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI");
        String ecsCredentialsFullUri = System.getenv("AWS_CONTAINER_CREDENTIALS_FULL_URI");
        String ecsAuthToken = System.getenv("AWS_CONTAINER_AUTHORIZATION_TOKEN");
        String ecsAuthTokenFile = System.getenv("AWS_CONTAINER_AUTHORIZATION_TOKEN_FILE");

        if (ecsCredentialsUri != null || ecsCredentialsFullUri != null) {
            log.debug("ECS container credentials detected - RelativeURI: {}, FullURI: {}, AuthToken: {}, AuthTokenFile: {}",
                     ecsCredentialsUri != null ? "present" : "null",
                     ecsCredentialsFullUri != null ? "present" : "null",
                     ecsAuthToken != null ? "present" : "null",
                     ecsAuthTokenFile != null ? "present" : "null");
            return "Amazon ECS container credentials (ContainerCredentialsProvider)";
        }

        // 6. EC2 instance profile is last in chain - if we get here and have credentials, it's likely EC2
        // Temporary credentials from STS/EC2 typically start with "ASIA"
        if (accessKeyId.startsWith("ASIA")) {
            log.debug("Access key starts with ASIA, indicating temporary credentials from EC2 instance profile or STS");
            return "Amazon EC2 instance IAM role credentials (InstanceProfileCredentialsProvider)";
        }

        // If we have long-term credentials (starting with "AKIA") but couldn't match them to env vars or system props,
        // they might be from credentials file that we couldn't detect properly
        if (accessKeyId.startsWith("AKIA")) {
            log.debug("Access key starts with AKIA (long-term credentials) but source couldn't be determined precisely");
            return "Unknown source - likely from shared credentials file or other configuration";
        }

        return "Unknown credential source - credentials resolved but source could not be determined";
    }
}
