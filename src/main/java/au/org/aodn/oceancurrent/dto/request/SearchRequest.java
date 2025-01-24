package au.org.aodn.oceancurrent.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SearchRequest {
    @NotBlank(message = "Product is required")
    private String product;

    @NotBlank(message = "Sub-product is required")
    private String subProduct;

    @NotBlank(message = "Region is required")
    private String region;

    @NotNull(message = "Date is required")
    @Digits(message = "Date must be a number", integer = 10, fraction = 0)
    private Integer date;

    @Min(value = 1, message = "Size must be at least 1")
    private Integer size = 200;
}
