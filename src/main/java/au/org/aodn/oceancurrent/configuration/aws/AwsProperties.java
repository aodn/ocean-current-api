package au.org.aodn.oceancurrent.configuration.aws;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {
    private String region;
    private String accessKeyId;
    private String secretAccessKey;
    private S3 s3 = new S3();

    @Data
    public static class S3 {
        private String bucketName;
        private String wavesPrefix = "WAVES/";
    }
}
