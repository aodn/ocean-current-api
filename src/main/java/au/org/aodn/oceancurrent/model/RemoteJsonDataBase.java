package au.org.aodn.oceancurrent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemoteJsonDataBase {
    private String path;
    private String product;
    private String subProduct;
    private String region;
}
