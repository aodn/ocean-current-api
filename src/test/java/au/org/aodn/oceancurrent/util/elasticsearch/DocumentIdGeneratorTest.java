package au.org.aodn.oceancurrent.util.elasticsearch;

import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentIdGeneratorTest {

    private static ImageMetadataEntry entry(String productId, String region, String path, String fileName) {
        ImageMetadataEntry entry = new ImageMetadataEntry();
        entry.setProductId(productId);
        entry.setRegion(region);
        entry.setPath(path);
        entry.setFileName(fileName);
        return entry;
    }

    @Test
    void generateDocumentId_includesSanitisedPath() {
        String id = DocumentIdGenerator.generateDocumentId(
                entry("tidalCurrents-sl", "Bass", "/tides/Bass_hv/2026", "202612310000.gif"));

        assertThat(id).matches("tidalcurrents-sl_bass__tides_bass_hv_2026_202612310000_gif_[0-9a-f]{32}");
    }

    @Test
    void generateDocumentId_differsForPathsThatSanitiseToTheSameText() {
        String slash = DocumentIdGenerator.generateDocumentId(entry("p", "r", "/a/b", "x.gif"));
        String underscore = DocumentIdGenerator.generateDocumentId(entry("p", "r", "/a_b", "x.gif"));

        assertThat(slash).isNotEqualTo(underscore);
    }

    @Test
    void generateDocumentId_differsForComponentsDifferingOnlyByCase() {
        String lower = DocumentIdGenerator.generateDocumentId(entry("p", "r", "/tides/bass", "a.gif"));
        String upper = DocumentIdGenerator.generateDocumentId(entry("p", "r", "/tides/Bass", "a.gif"));
        String upperName = DocumentIdGenerator.generateDocumentId(entry("p", "r", "/tides/bass", "A.gif"));

        assertThat(lower).isNotEqualTo(upper).isNotEqualTo(upperName);
        assertThat(upper).isNotEqualTo(upperName);
    }

    @Test
    void generateDocumentId_differsWhenTextMovesBetweenComponents() {
        // Same characters overall, split differently between region and path
        String first = DocumentIdGenerator.generateDocumentId(entry("p", "a_b", "/c", "x.gif"));
        String second = DocumentIdGenerator.generateDocumentId(entry("p", "a", "b_/c", "x.gif"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void generateDocumentId_differsForSameFileNameInDifferentYearFolders() {
        String id2026 = DocumentIdGenerator.generateDocumentId(
                entry("tidalCurrents-sl", "Bass", "/tides/Bass_hv/2026", "202612310000.gif"));
        String id2027 = DocumentIdGenerator.generateDocumentId(
                entry("tidalCurrents-sl", "Bass", "/tides/Bass_hv/2027", "202612310000.gif"));

        assertThat(id2026).isNotEqualTo(id2027);
    }

    @Test
    void generateDocumentId_differsForSameFileNameInDifferentFoldersWithoutRegion() {
        String first = DocumentIdGenerator.generateDocumentId(
                entry("sealCtdTags-10days", null, "/AATAMS/SATTAGS/Q9900690/10days", "20141117.gif"));
        String second = DocumentIdGenerator.generateDocumentId(
                entry("sealCtdTags-10days", null, "/AATAMS/SATTAGS/Q9900694/10days", "20141117.gif"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void generateDocumentId_isStableForTheSameFile() {
        ImageMetadataEntry a = entry("fourHourSst-sst", "SA", "/SST_4hr/SST/SA", "2026110812.gif");
        ImageMetadataEntry b = entry("fourHourSst-sst", "SA", "/SST_4hr/SST/SA", "2026110812.gif");

        assertThat(DocumentIdGenerator.generateDocumentId(a)).isEqualTo(DocumentIdGenerator.generateDocumentId(b));
    }

    @Test
    void generateDocumentId_handlesNullPath() {
        String id = DocumentIdGenerator.generateDocumentId(entry("surfaceWaves-wave", "Au", null, "2026110812.gif"));

        assertThat(id).matches("surfacewaves-wave_au__2026110812_gif_[0-9a-f]{32}");
    }

    @Test
    void generateDocumentId_returnsNullWithoutProductIdOrFileName() {
        assertThat(DocumentIdGenerator.generateDocumentId(entry(null, "Bass", "/x", "a.gif"))).isNull();
        assertThat(DocumentIdGenerator.generateDocumentId(entry("p", "Bass", "/x", null))).isNull();
    }
}
