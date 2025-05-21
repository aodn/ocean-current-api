package au.org.aodn.oceancurrent.constant;

public final class CacheNames {
    public static final String IMAGE_LIST = "imageList";
    public static final String CURRENT_METERS_PLOT_LIST = "currentMetersPlotList";
    public static final String LATEST_FILES = "latestFiles";

    private CacheNames() {
        throw new AssertionError("Utility class - do not instantiate");
    }
}
