package au.org.aodn.oceancurrent.constant;

import java.util.Map;

public class ProductConstants {
    private ProductConstants() {
        throw new AssertionError("Utility class - do not instantiate");
    }

    public static final Map<String, String> PRODUCT_ID_MAPPINGS = Map.of(
            "oceanColour-chlA-year", "oceanColour-chlA",
            "adjustedSeaLevelAnomaly-sst-year", "adjustedSeaLevelAnomaly-sst"
    );
}
