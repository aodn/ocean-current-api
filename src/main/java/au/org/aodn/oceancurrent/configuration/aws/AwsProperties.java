package au.org.aodn.oceancurrent.configuration.aws;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {
    private String region;
    private String accessKey;
    private String secretKey;
    private S3 s3 = new S3();

    @Data
    public static class S3 {
        private String bucketName;
        private String wavesPrefix = "WAVES/";
    }

    @PostConstruct
    public void logConfig() {
        System.out.println("AWS Properties loaded:");
        System.out.println("Region: " + region);
        System.out.println("Access Key: " + (accessKey != null ? accessKey.substring(0, 4) + "..." : "null"));
        System.out.println("Secret Key length: " + (secretKey != null ? secretKey.length() : 0));
        System.out.println("Bucket Name: " + s3.getBucketName());
        System.out.println("Waves Prefix: " + s3.getWavesPrefix());
    }
}
