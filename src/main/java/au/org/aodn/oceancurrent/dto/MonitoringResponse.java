package au.org.aodn.oceancurrent.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringResponse {
    private String status;
    private String timestamp;
    private String message;

    public MonitoringResponse(String status, ZonedDateTime timestamp, String message) {
        this.status = status;
        this.timestamp = timestamp.toString();
        this.message = message;
    }
}
