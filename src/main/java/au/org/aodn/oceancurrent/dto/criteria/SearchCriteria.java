package au.org.aodn.oceancurrent.dto.criteria;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchCriteria {
    private String product;
    private String subProduct;
    private String region;
    private String date;
    private Integer size;
}
