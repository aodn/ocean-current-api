package au.org.aodn.oceancurrent.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.monitoring-security")
@Configuration
public class MonitoringSecurityProperties {
    private List<String> authorisedInstanceIds;
    private String ec2IdentityCertPath;
    private long ec2IdentityTimestampToleranceSeconds;
}
