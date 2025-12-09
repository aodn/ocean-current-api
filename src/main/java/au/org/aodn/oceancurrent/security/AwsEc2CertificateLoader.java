package au.org.aodn.oceancurrent.security;


import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class AwsEc2CertificateLoader {

    private static final Logger logger = LoggerFactory.getLogger(AwsEc2CertificateLoader.class);

    // AWS EC2 Instance Identity Document certificate URLs per region
    // Source: https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/verify-signature.html
    private static final Map<String, String> REGION_CERT_URLS = new HashMap<>();

    static {
        // Standard regions
        REGION_CERT_URLS.put("us-east-1", "https://ec2.us-east-1.amazonaws.com/instance-identity/document/signature");
        REGION_CERT_URLS.put("us-east-2", "https://ec2.us-east-2.amazonaws.com/instance-identity/document/signature");
        REGION_CERT_URLS.put("us-west-1", "https://ec2.us-west-1.amazonaws.com/instance-identity/document/signature");
        REGION_CERT_URLS.put("us-west-2", "https://ec2.us-west-2.amazonaws.com/instance-identity/document/signature");
        REGION_CERT_URLS.put("eu-west-1", "https://ec2.eu-west-1.amazonaws.com/instance-identity/document/signature");
        REGION_CERT_URLS.put("eu-west-2", "https://ec2.eu-west-2.amazonaws.com/instance-identity/document/signature");
        REGION_CERT_URLS.put("eu-west-3", "https://ec2.eu-west-3.amazonaws.com/instance-identity/document/signature");
        REGION_CERT_URLS.put("eu-central-1", "https://ec2.eu-central-1.amazonaws.com/instance-identity/document/signature");
        REGION_CERT_URLS.put("ap-northeast-1", "https://ec2.ap-northeast-1.amazonaws.com/instance-identity/document/signature");
        REGION_CERT_URLS.put("ap-northeast-2", "https://ec2.ap-northeast-2.amazonaws.com/instance-identity/document/signature");
        REGION_CERT_URLS.put("ap-southeast-1", "https://ec2.ap-southeast-1.amazonaws.com/instance-identity/document/signature");
        REGION_CERT_URLS.put("ap-southeast-2", "https://ec2.ap-southeast-2.amazonaws.com/instance-identity/document/signature");
        REGION_CERT_URLS.put("ap-south-1", "https://ec2.ap-south-1.amazonaws.com/instance-identity/document/signature");
        REGION_CERT_URLS.put("sa-east-1", "https://ec2.sa-east-1.amazonaws.com/instance-identity/document/signature");
        REGION_CERT_URLS.put("ca-central-1", "https://ec2.ca-central-1.amazonaws.com/instance-identity/document/signature");
        // Add more regions as needed
    }

    // Actual certificate download URLs (these are the public keys)
    private static final Map<String, String> REGION_PUBLIC_KEY_URLS = new HashMap<>();

    static {
        // These are the actual public certificate URLs for EC2 instance identity
        REGION_PUBLIC_KEY_URLS.put("us-east-1", "https://s3.amazonaws.com/ec2metadata-signature-verification/us-east-1/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("us-east-2", "https://s3.amazonaws.com/ec2metadata-signature-verification/us-east-2/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("us-west-1", "https://s3.amazonaws.com/ec2metadata-signature-verification/us-west-1/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("us-west-2", "https://s3.amazonaws.com/ec2metadata-signature-verification/us-west-2/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("eu-west-1", "https://s3.amazonaws.com/ec2metadata-signature-verification/eu-west-1/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("eu-west-2", "https://s3.amazonaws.com/ec2metadata-signature-verification/eu-west-2/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("eu-west-3", "https://s3.amazonaws.com/ec2metadata-signature-verification/eu-west-3/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("eu-central-1", "https://s3.amazonaws.com/ec2metadata-signature-verification/eu-central-1/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("eu-north-1", "https://s3.amazonaws.com/ec2metadata-signature-verification/eu-north-1/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("ap-northeast-1", "https://s3.amazonaws.com/ec2metadata-signature-verification/ap-northeast-1/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("ap-northeast-2", "https://s3.amazonaws.com/ec2metadata-signature-verification/ap-northeast-2/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("ap-northeast-3", "https://s3.amazonaws.com/ec2metadata-signature-verification/ap-northeast-3/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("ap-southeast-1", "https://s3.amazonaws.com/ec2metadata-signature-verification/ap-southeast-1/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("ap-southeast-2", "https://s3.amazonaws.com/ec2metadata-signature-verification/ap-southeast-2/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("ap-south-1", "https://s3.amazonaws.com/ec2metadata-signature-verification/ap-south-1/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("ca-central-1", "https://s3.amazonaws.com/ec2metadata-signature-verification/ca-central-1/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("sa-east-1", "https://s3.amazonaws.com/ec2metadata-signature-verification/sa-east-1/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("us-gov-west-1", "https://s3.amazonaws.com/ec2metadata-signature-verification/us-gov-west-1/certificate.pem");
        REGION_PUBLIC_KEY_URLS.put("us-gov-east-1", "https://s3.amazonaws.com/ec2metadata-signature-verification/us-gov-east-1/certificate.pem");
    }

    private final HttpClient httpClient;
    private final Map<String, X509Certificate> certificateCache;

    public AwsEc2CertificateLoader() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.certificateCache = new HashMap<>();
    }

    @PostConstruct
    public void init() {
        logger.info("EC2 Certificate Loader initialized");
    }

    /**
     * Get the EC2 instance identity certificate for a specific region
     */
    public X509Certificate getCertificateForRegion(String region) throws Exception {
        // Check cache first
        if (certificateCache.containsKey(region)) {
            return certificateCache.get(region);
        }

        String certUrl = REGION_PUBLIC_KEY_URLS.get(region);
        if (certUrl == null) {
            throw new IllegalArgumentException("Unsupported region: " + region);
        }

        logger.info("Loading EC2 certificate for region: {}", region);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(certUrl))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<byte[]> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download certificate for region " + region +
                    ": HTTP " + response.statusCode());
        }

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate certificate = (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(response.body()));

        // Cache it
        certificateCache.put(region, certificate);

        logger.info("Successfully loaded EC2 certificate for region {}: {}",
                region, certificate.getSubjectX500Principal());

        return certificate;
    }

    /**
     * Preload certificates for specific regions
     */
    public void preloadCertificates(String... regions) {
        for (String region : regions) {
            try {
                getCertificateForRegion(region);
            } catch (Exception e) {
                logger.error("Failed to preload certificate for region {}: {}",
                        region, e.getMessage());
            }
        }
    }

    public Map<String, String> getSupportedRegions() {
        return new HashMap<>(REGION_PUBLIC_KEY_URLS);
    }
}