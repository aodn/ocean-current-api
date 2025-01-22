package au.org.aodn.oceancurrent.util.elasticsearch;

public interface IndexingCallback {
    void onProgress(String message);
    void onError(String message);
    void onComplete(String message);
}
