package au.org.aodn.oceancurrent.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

/**
 * Represents an AWS EC2 Instance Identity Document.
 *
 * The instance identity document is a JSON document that contains information about the instance.
 * It is available from the EC2 metadata service and is signed by AWS.
 *
 * Example document:
 * {
 *   "instanceId" : "i-0123456789abcdef0",
 *   "region" : "ap-southeast-2",
 *   "accountId" : "123456789012",
 *   "pendingTime" : "2025-12-02T10:00:00Z",
 *   "imageId" : "ami-0c55b159cbfafe1f0",
 *   "instanceType" : "t3.micro",
 *   "architecture" : "x86_64",
 *   "availabilityZone" : "ap-southeast-2a",
 *   "privateIp" : "10.0.1.50"
 * }
 *
 * @see <a href="https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/instance-identity-documents.html">AWS Instance Identity Documents</a>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InstanceIdentityDocument {

    /**
     * The ID of the instance.
     */
    @JsonProperty("instanceId")
    private String instanceId;

    /**
     * The AWS region where the instance is running.
     */
    @JsonProperty("region")
    private String region;

    /**
     * The AWS account ID that owns the instance.
     */
    @JsonProperty("accountId")
    private String accountId;

    /**
     * The time when the instance was launched (pending time).
     * This field is critical for preventing replay attacks - it's part of the signed
     * document and cannot be modified without invalidating the signature.
     */
    @JsonProperty("pendingTime")
    private String pendingTime;

    /**
     * The AMI ID used to launch the instance.
     */
    @JsonProperty("imageId")
    private String imageId;

    /**
     * The instance type (e.g., t3.micro, m5.large).
     */
    @JsonProperty("instanceType")
    private String instanceType;

    /**
     * The architecture of the instance (e.g., x86_64, arm64).
     */
    @JsonProperty("architecture")
    private String architecture;

    /**
     * The availability zone where the instance is running.
     */
    @JsonProperty("availabilityZone")
    private String availabilityZone;

    /**
     * The private IP address of the instance.
     */
    @JsonProperty("privateIp")
    private String privateIp;

    /**
     * Parse the pendingTime string into an Instant.
     *
     * @return Instant representing the pending time, or null if parsing fails
     */
    public Instant getPendingTimeAsInstant() {
        if (pendingTime == null || pendingTime.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(pendingTime);
        } catch (Exception e) {
            return null;
        }
    }
}
