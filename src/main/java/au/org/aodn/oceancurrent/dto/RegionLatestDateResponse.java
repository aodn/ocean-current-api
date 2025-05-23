package au.org.aodn.oceancurrent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegionLatestDateResponse {
    private String productId;
    private List<RegionLatestDate> regionLatestDates;
}
