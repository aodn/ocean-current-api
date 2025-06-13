package au.org.aodn.oceancurrent.configuration.aws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
@RequiredArgsConstructor
public class AwsConfig {

    private final AwsProperties awsProperties;

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder clientBuilder = S3Client.builder()
                .region(Region.of(awsProperties.getRegion()));

        // Use provided credentials if available, otherwise fall back to default provider chain
        if (awsProperties.getAccessKeyId() != null && !awsProperties.getAccessKeyId().isEmpty() &&
            awsProperties.getSecretAccessKey() != null && !awsProperties.getSecretAccessKey().isEmpty()) {

            AwsCredentials credentials = AwsBasicCredentials.create(
                    awsProperties.getAccessKeyId(),
                    awsProperties.getSecretAccessKey());

            clientBuilder.credentialsProvider(StaticCredentialsProvider.create(credentials));
        } else {
            // Use default credentials provider chain (IAM roles, environment variables, etc.)
            clientBuilder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return clientBuilder.build();
    }
}
