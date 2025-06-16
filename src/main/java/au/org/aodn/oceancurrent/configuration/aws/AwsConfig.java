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

import java.util.Optional;

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
        if (isValidCredential(awsProperties.getAccessKeyId()) && isValidCredential(awsProperties.getSecretAccessKey())) {

            log.info("Using static AWS credentials from configuration");
            log.debug("Static credentials configured - credential source: application properties");

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

    private boolean isValidCredential(String credential) {
        return credential != null &&
               !credential.isEmpty() &&
               !credential.startsWith("${") &&
               !credential.endsWith("}");
    }

    private void detectAndLogCredentialSource(AwsCredentialsProvider credentialsProvider) {
        try {
            AwsCredentials credentials = credentialsProvider.resolveCredentials();
            String accessKeyId = credentials.accessKeyId();

            log.info("Successfully resolved AWS credentials from DefaultCredentialsProvider");

            // Try to determine the source by checking environment and system properties
            String credentialSource = determineCredentialSource(accessKeyId);
            log.info("Detected credential source: {}", credentialSource);

        } catch (SdkClientException e) {
            log.error("Failed to resolve AWS credentials from DefaultCredentialsProvider:", e);
            throw e;
        }
    }

    private String determineCredentialSource(String accessKeyId) {
        // Check credential sources in AWS SDK default chain order
        return checkSystemProperties(accessKeyId)
                .orElse(checkEnvironmentVariables(accessKeyId)
                .orElse(checkWebIdentityToken()
                .orElse(checkSharedCredentialsFiles()
                .orElse(checkEcsContainerCredentials()
                .orElse(checkEc2InstanceProfile(accessKeyId)
                .orElse(determineUnknownSource(accessKeyId)))))));
    }

    private Optional<String> checkSystemProperties(String accessKeyId) {
        String sysPropAccessKey = System.getProperty("aws.accessKeyId");
        if (sysPropAccessKey != null && sysPropAccessKey.equals(accessKeyId)) {
            log.debug("Credentials match Java system property aws.accessKeyId");
            return Optional.of("Java System Properties (aws.accessKeyId, aws.secretAccessKey, aws.sessionToken)");
        }
        return Optional.empty();
    }

    private Optional<String> checkEnvironmentVariables(String accessKeyId) {
        String envAccessKey = System.getenv("AWS_ACCESS_KEY_ID");
        if (envAccessKey != null && envAccessKey.equals(accessKeyId)) {
            log.debug("Credentials match environment variable AWS_ACCESS_KEY_ID");
            return Optional.of("Environment Variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN)");
        }
        return Optional.empty();
    }

    private Optional<String> checkWebIdentityToken() {
        String webIdentityTokenFile = System.getenv("AWS_WEB_IDENTITY_TOKEN_FILE");
        String roleArn = System.getenv("AWS_ROLE_ARN");
        if (webIdentityTokenFile != null && roleArn != null) {
            log.debug("Web Identity Token file and role ARN detected: tokenFile={}, roleArn={}",
                     webIdentityTokenFile, roleArn);
            return Optional.of("Web Identity Token from AWS STS (WebIdentityTokenFileCredentialsProvider)");
        }
        return Optional.empty();
    }

    private Optional<String> checkSharedCredentialsFiles() {
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
            return Optional.of("Shared credentials and config files (ProfileCredentialsProvider) - Profile: " + profile);
        }
        return Optional.empty();
    }

    private Optional<String> checkEcsContainerCredentials() {
        String ecsCredentialsUri = System.getenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI");
        String ecsCredentialsFullUri = System.getenv("AWS_CONTAINER_CREDENTIALS_FULL_URI");
        String ecsAuthToken = System.getenv("AWS_CONTAINER_AUTHORIZATION_TOKEN");
        String ecsAuthTokenFile = System.getenv("AWS_CONTAINER_AUTHORIZATION_TOKEN_FILE");

        if (ecsCredentialsUri != null || ecsCredentialsFullUri != null) {
            String relativeUriStatus = ecsCredentialsUri != null ? "present" : "null";
            String fullUriStatus = ecsCredentialsFullUri != null ? "present" : "null";
            String authTokenStatus = ecsAuthToken != null ? "present" : "null";
            String authTokenFileStatus = ecsAuthTokenFile != null ? "present" : "null";

            log.debug("ECS container credentials detected - RelativeURI: {}, FullURI: {}, AuthToken: {}, AuthTokenFile: {}",
                     relativeUriStatus, fullUriStatus, authTokenStatus, authTokenFileStatus);
            return Optional.of("Amazon ECS container credentials (ContainerCredentialsProvider)");
        }
        return Optional.empty();
    }

    private Optional<String> checkEc2InstanceProfile(String accessKeyId) {
        if (accessKeyId.startsWith("ASIA")) {
            log.debug("Access key starts with ASIA, indicating temporary credentials from EC2 instance profile or STS");
            return Optional.of("Amazon EC2 instance IAM role credentials (InstanceProfileCredentialsProvider)");
        }
        return Optional.empty();
    }

    private String determineUnknownSource(String accessKeyId) {
        if (accessKeyId.startsWith("AKIA")) {
            log.debug("Access key starts with AKIA (long-term credentials) but source couldn't be determined precisely");
            return "Unknown source - likely from shared credentials file or other configuration";
        }
        return "Unknown credential source - credentials resolved but source could not be determined";
    }
}
