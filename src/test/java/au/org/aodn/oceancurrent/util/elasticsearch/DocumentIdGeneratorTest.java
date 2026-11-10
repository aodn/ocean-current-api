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

        assertThat(id).isEqualTo("tidalcurrents-sl_bass__tides_bass_hv_2026_202612310000_gif");
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

        assertThat(id).isEqualTo("surfacewaves-wave_au__2026110812_gif");
    }

    @Test
    void generateDocumentId_returnsNullWithoutProductIdOrFileName() {
        assertThat(DocumentIdGenerator.generateDocumentId(entry(null, "Bass", "/x", "a.gif"))).isNull();
        assertThat(DocumentIdGenerator.generateDocumentId(entry("p", "Bass", "/x", null))).isNull();
    }
}
