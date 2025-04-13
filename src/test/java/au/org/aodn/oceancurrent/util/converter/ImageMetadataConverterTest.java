package au.org.aodn.oceancurrent.util.converter;

import au.org.aodn.oceancurrent.dto.CurrentMetersPlotResponse;
import au.org.aodn.oceancurrent.model.FileMetadata;
import au.org.aodn.oceancurrent.model.ImageMetadataEntry;
import au.org.aodn.oceancurrent.model.ImageMetadataGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class ImageMetadataConverterTest {

    /**
     * Helper method to create an ImageMetadataEntry instance
     */
    private ImageMetadataEntry createEntry(String path, String productId, String region, String depth, String fileName) {
        ImageMetadataEntry entry = new ImageMetadataEntry();
        entry.setPath(path);
        entry.setProductId(productId);
        entry.setRegion(region);
        entry.setDepth(depth);
        entry.setFileName(fileName);
        return entry;
    }

    /**
     * Helper method to find a DepthData object by depth value
     */
    private CurrentMetersPlotResponse.DepthData findDepthDataByDepth(
            List<CurrentMetersPlotResponse.DepthData> depthDataList, String depth) {

        return depthDataList.stream()
                .filter(data -> {
                    if (depth == null) {
                        return data.getDepth() == null;
                    }
                    return depth.equals(data.getDepth());
                })
                .findFirst()
                .orElse(null);
    }

    @Nested
    @DisplayName("toMetadataGroup method tests")
    class ToMetadataGroupTests {

        @Test
        @DisplayName("Should create metadata group with valid entries")
        public void testWithValidEntries() {
            // Arrange
            String path = "/data/images";
            String productId = "sst";
            String region = "TAS";

            ImageMetadataEntry entry1 = createEntry(path, productId, region, null, "image1.png");
            ImageMetadataEntry entry2 = createEntry(path, productId, region, null, "image2.png");
            List<ImageMetadataEntry> entries = Arrays.asList(entry1, entry2);

            // Act
            ImageMetadataGroup result = ImageMetadataConverter.toMetadataGroup(entries, productId, region);

            // Assert
            assertNotNull(result);
            assertEquals(productId, result.getProductId());
            assertEquals(region, result.getRegion());
            assertEquals(path, result.getPath());
            assertEquals(2, result.getFiles().size());
            assertEquals("image1.png", result.getFiles().get(0).getName());
            assertEquals("image2.png", result.getFiles().get(1).getName());
        }

        @Test
        @DisplayName("Should handle empty entries list")
        public void testWithEmptyEntries() {
            // Arrange
            String productId = "sst";
            String region = "TAS";
            List<ImageMetadataEntry> entries = Collections.emptyList();

            // Act
            ImageMetadataGroup result = ImageMetadataConverter.toMetadataGroup(entries, productId, region);

            // Assert
            assertNotNull(result);
            assertEquals(productId, result.getProductId());
            assertEquals(region, result.getRegion());
            assertNull(result.getPath());
            assertTrue(result.getFiles().isEmpty());
        }

        @Test
        @DisplayName("Should handle null entries")
        public void testWithNullEntries() {
            // Arrange
            String productId = "sst";
            String region = "TAS";

            // Act
            ImageMetadataGroup result = ImageMetadataConverter.toMetadataGroup(null, productId, region);

            // Assert
            assertNotNull(result);
            assertEquals(productId, result.getProductId());
            assertEquals(region, result.getRegion());
            assertNull(result.getPath());
            assertTrue(result.getFiles().isEmpty());
        }
    }

    @Nested
    @DisplayName("createMetadataGroups method tests")
    class CreateMetadataGroupsTests {

        @Test
        @DisplayName("Should handle empty entries list")
        public void testWithEmptyEntries() {
            // Arrange
            List<ImageMetadataEntry> entries = Collections.emptyList();

            // Act
            List<ImageMetadataGroup> result = ImageMetadataConverter.createMetadataGroups(entries);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should handle null depth values")
        public void testWithNullDepthValues() {
            // Arrange
            ImageMetadataEntry entry1 = createEntry("/path1", "sst", "TAS", null, "image1.png");
            ImageMetadataEntry entry2 = createEntry("/path1", "sst", "TAS", null, "image2.png");
            List<ImageMetadataEntry> entries = Arrays.asList(entry1, entry2);

            // Act
            List<ImageMetadataGroup> result = ImageMetadataConverter.createMetadataGroups(entries);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());

            ImageMetadataGroup group = result.get(0);
            assertNull(group.getDepth());
            assertEquals("/path1", group.getPath());
            assertEquals("sst", group.getProductId());
            assertEquals("TAS", group.getRegion());
            assertEquals(2, group.getFiles().size());
        }

        @Test
        @DisplayName("Should handle multiple depth values")
        public void testWithMultipleDepthValues() {
            // Arrange
            ImageMetadataEntry entry1 = createEntry("/path1", "sst", "TAS", "10m", "image1.png");
            ImageMetadataEntry entry2 = createEntry("/path1", "sst", "TAS", "10m", "image2.png");
            ImageMetadataEntry entry3 = createEntry("/path1", "sst", "TAS", "20m", "image3.png");
            List<ImageMetadataEntry> entries = Arrays.asList(entry1, entry2, entry3);

            // Act
            List<ImageMetadataGroup> result = ImageMetadataConverter.createMetadataGroups(entries);

            // Assert
            assertNotNull(result);
            assertEquals(2, result.size());

            // Find groups by depth
            ImageMetadataGroup group10m = result.stream()
                    .filter(g -> "10m".equals(g.getDepth()))
                    .findFirst()
                    .orElse(null);

            ImageMetadataGroup group20m = result.stream()
                    .filter(g -> "20m".equals(g.getDepth()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(group10m);
            assertEquals(2, group10m.getFiles().size());

            assertNotNull(group20m);
            assertEquals(1, group20m.getFiles().size());
        }
    }

    @Nested
    @DisplayName("createMetadataGroupsByProductRegionDepth method tests")
    class CreateMetadataGroupsByProductRegionDepthTests {

        @Test
        @DisplayName("Should handle empty entries list")
        public void testWithEmptyEntries() {
            // Arrange
            List<ImageMetadataEntry> entries = Collections.emptyList();

            // Act
            List<ImageMetadataGroup> result = ImageMetadataConverter.createMetadataGroupsByProductRegionDepth(entries);

            // Assert
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should handle varied entries")
        public void testWithVariedEntries() {
            // Arrange
            // Different product
            ImageMetadataEntry entry1 = createEntry("/path1", "sst", "TAS", "10m", "image1.png");
            ImageMetadataEntry entry2 = createEntry("/path2", "temp", "TAS", "10m", "image2.png");

            // Different region
            ImageMetadataEntry entry3 = createEntry("/path3", "sst", "NSW", "10m", "image3.png");

            // Different depth
            ImageMetadataEntry entry4 = createEntry("/path1", "sst", "TAS", "20m", "image4.png");

            // Same group as entry1
            ImageMetadataEntry entry5 = createEntry("/path1", "sst", "TAS", "10m", "image5.png");

            List<ImageMetadataEntry> entries = Arrays.asList(entry1, entry2, entry3, entry4, entry5);

            // Act
            List<ImageMetadataGroup> result = ImageMetadataConverter.createMetadataGroupsByProductRegionDepth(entries);

            // Assert
            assertNotNull(result);
            assertEquals(4, result.size());

            // Check each unique combination has its own group
            boolean foundSstTas10m = false;
            boolean foundTempTas10m = false;
            boolean foundSstNsw10m = false;
            boolean foundSstTas20m = false;

            for (ImageMetadataGroup group : result) {
                if ("sst".equals(group.getProductId()) && "TAS".equals(group.getRegion()) && "10m".equals(group.getDepth())) {
                    foundSstTas10m = true;
                    assertEquals(2, group.getFiles().size()); // entry1 and entry5
                }

                if ("temp".equals(group.getProductId()) && "TAS".equals(group.getRegion()) && "10m".equals(group.getDepth())) {
                    foundTempTas10m = true;
                    assertEquals(1, group.getFiles().size()); // entry2
                }

                if ("sst".equals(group.getProductId()) && "NSW".equals(group.getRegion()) && "10m".equals(group.getDepth())) {
                    foundSstNsw10m = true;
                    assertEquals(1, group.getFiles().size()); // entry3
                }

                if ("sst".equals(group.getProductId()) && "TAS".equals(group.getRegion()) && "20m".equals(group.getDepth())) {
                    foundSstTas20m = true;
                    assertEquals(1, group.getFiles().size()); // entry4
                }
            }

            assertTrue(foundSstTas10m);
            assertTrue(foundTempTas10m);
            assertTrue(foundSstNsw10m);
            assertTrue(foundSstTas20m);
        }

        @Test
        @DisplayName("Should handle null depth")
        public void testWithNullDepth() {
            // Arrange
            ImageMetadataEntry entry1 = createEntry("/path1", "sst", "TAS", null, "image1.png");
            ImageMetadataEntry entry2 = createEntry("/path1", "sst", "TAS", null, "image2.png");
            List<ImageMetadataEntry> entries = Arrays.asList(entry1, entry2);

            // Act
            List<ImageMetadataGroup> result = ImageMetadataConverter.createMetadataGroupsByProductRegionDepth(entries);

            // Assert
            assertNotNull(result);
            assertEquals(1, result.size());

            ImageMetadataGroup group = result.get(0);
            assertNull(group.getDepth());
            assertEquals("/path1", group.getPath());
            assertEquals("sst", group.getProductId());
            assertEquals("TAS", group.getRegion());
            assertEquals(2, group.getFiles().size());
        }
    }

    @Nested
    @DisplayName("toFileMetadata method tests")
    class ToFileMetadataTests {

        @Test
        @DisplayName("Should convert ImageMetadataEntry to FileMetadata")
        public void testToFileMetadata() {
            // Arrange
            ImageMetadataEntry entry = createEntry("/path", "sst", "TAS", "10m", "test-image.png");

            // Act
            FileMetadata result = ImageMetadataConverter.toFileMetadata(entry);

            // Assert
            assertNotNull(result);
            assertEquals("test-image.png", result.getName());
        }
    }

    @Nested
    @DisplayName("createCurrentMetersPlotResponse method tests")
    class CreateCurrentMetersPlotResponseTests {

        @Test
        @DisplayName("Should handle null entries")
        @SuppressWarnings("ConstantConditions")
        public void testWithNullEntries() {
            // Arrange
            List<ImageMetadataEntry> entries = null;

            // Act
            CurrentMetersPlotResponse result = ImageMetadataConverter.createCurrentMetersPlotResponse(entries);

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("Should handle empty entries")
        public void testWithEmptyEntries() {
            // Arrange
            List<ImageMetadataEntry> entries = Collections.emptyList();

            // Act
            CurrentMetersPlotResponse result = ImageMetadataConverter.createCurrentMetersPlotResponse(entries);

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("Should handle single entry")
        public void testWithSingleEntry() {
            // Arrange
            ImageMetadataEntry entry = createEntry("/path1", "currentMeters-48", "BMP120", "xyz", "image1.png");
            List<ImageMetadataEntry> entries = Collections.singletonList(entry);

            // Act
            CurrentMetersPlotResponse result = ImageMetadataConverter.createCurrentMetersPlotResponse(entries);

            // Assert
            assertNotNull(result);
            assertEquals("currentMeters-48", result.getProductId());
            assertEquals("BMP120", result.getRegion());

            List<CurrentMetersPlotResponse.DepthData> depthDataList = result.getDepthData();
            assertNotNull(depthDataList);
            assertEquals(1, depthDataList.size());

            CurrentMetersPlotResponse.DepthData depthData = depthDataList.get(0);
            assertEquals("xyz", depthData.getDepth());
            assertEquals("/path1", depthData.getPath());
            assertEquals(1, depthData.getFiles().size());
            assertEquals("image1.png", depthData.getFiles().get(0));
        }

        @Test
        @DisplayName("Should handle multiple entries with same depth")
        public void testWithMultipleEntriesSameDepth() {
            // Arrange
            ImageMetadataEntry entry1 = createEntry("/path1", "currentMetersPlot-48", "BMP120", "xyz", "image1.png");
            ImageMetadataEntry entry2 = createEntry("/path1", "currentMetersPlot-48", "BMP120", "xyz", "image2.png");
            ImageMetadataEntry entry3 = createEntry("/path1", "currentMetersPlot-48", "BMP120", "xyz", "image3.png");
            List<ImageMetadataEntry> entries = Arrays.asList(entry1, entry2, entry3);

            // Act
            CurrentMetersPlotResponse result = ImageMetadataConverter.createCurrentMetersPlotResponse(entries);

            // Assert
            assertNotNull(result);
            assertEquals("currentMetersPlot-48", result.getProductId());
            assertEquals("BMP120", result.getRegion());

            List<CurrentMetersPlotResponse.DepthData> depthDataList = result.getDepthData();
            assertNotNull(depthDataList);
            assertEquals(1, depthDataList.size());

            CurrentMetersPlotResponse.DepthData depthData = depthDataList.get(0);
            assertEquals("xyz", depthData.getDepth());
            assertEquals("/path1", depthData.getPath());
            assertEquals(3, depthData.getFiles().size());

            // Check files are sorted alphabetically
            assertEquals("image1.png", depthData.getFiles().get(0));
            assertEquals("image2.png", depthData.getFiles().get(1));
            assertEquals("image3.png", depthData.getFiles().get(2));
        }

        @Test
        @DisplayName("Should handle multiple depths")
        public void testWithMultipleDepths() {
            // Arrange
            ImageMetadataEntry entry1 = createEntry("/path1", "currentMetersPlot-48", "BMP120", "xyz", "image1.png");
            ImageMetadataEntry entry2 = createEntry("/path1", "currentMetersPlot-48", "BMP120", "zt", "image2.png");
            List<ImageMetadataEntry> entries = Arrays.asList(entry1, entry2);

            // Act
            CurrentMetersPlotResponse result = ImageMetadataConverter.createCurrentMetersPlotResponse(entries);

            // Assert
            assertNotNull(result);
            assertEquals("currentMetersPlot-48", result.getProductId());
            assertEquals("BMP120", result.getRegion());

            List<CurrentMetersPlotResponse.DepthData> depthDataList = result.getDepthData();
            assertNotNull(depthDataList);
            assertEquals(2, depthDataList.size());

            // Find and check each depth data
            CurrentMetersPlotResponse.DepthData depthXyz = findDepthDataByDepth(depthDataList, "xyz");
            assertNotNull(depthXyz);
            assertEquals("/path1", depthXyz.getPath());
            assertEquals(1, depthXyz.getFiles().size());
            assertEquals("image1.png", depthXyz.getFiles().get(0));

            CurrentMetersPlotResponse.DepthData depthZt = findDepthDataByDepth(depthDataList, "zt");
            assertNotNull(depthZt);
            assertEquals("/path1", depthZt.getPath());
            assertEquals(1, depthZt.getFiles().size());
            assertEquals("image2.png", depthZt.getFiles().get(0));
        }

        @Test
        @DisplayName("Should handle null depth")
        public void testWithNullDepth() {
            // Arrange
            ImageMetadataEntry entry1 = createEntry("/path1", "currentMetersPlot-48", "BMP120", null, "image1.png");
            ImageMetadataEntry entry2 = createEntry("/path1", "currentMetersPlot-48", "BMP120", null, "image2.png");
            List<ImageMetadataEntry> entries = Arrays.asList(entry1, entry2);

            // Act
            CurrentMetersPlotResponse result = ImageMetadataConverter.createCurrentMetersPlotResponse(entries);

            // Assert
            assertNotNull(result);
            assertEquals("currentMetersPlot-48", result.getProductId());
            assertEquals("BMP120", result.getRegion());

            List<CurrentMetersPlotResponse.DepthData> depthDataList = result.getDepthData();
            assertNotNull(depthDataList);
            assertEquals(1, depthDataList.size());

            CurrentMetersPlotResponse.DepthData depthData = depthDataList.get(0);
            assertNull(depthData.getDepth());
            assertEquals("/path1", depthData.getPath());
            assertEquals(2, depthData.getFiles().size());

            // Check files are sorted alphabetically
            assertEquals("image1.png", depthData.getFiles().get(0));
            assertEquals("image2.png", depthData.getFiles().get(1));
        }

        @Test
        @DisplayName("Should handle mixed depths")
        public void testWithMixedDepths() {
            // Arrange
            ImageMetadataEntry entry1 = createEntry("/path1", "currentMetersPlot-48", "BMP120", "xyz", "image1.png");
            ImageMetadataEntry entry2 = createEntry("/path1", "currentMetersPlot-48", "BMP120", "xyz", "image2.png");
            ImageMetadataEntry entry3 = createEntry("/path1", "currentMetersPlot-48", "BMP120", "zt", "image3.png");
            ImageMetadataEntry entry4 = createEntry("/path1", "currentMetersPlot-48", "BMP120", "zt", "image4.png");
            ImageMetadataEntry entry5 = createEntry("/path1", "currentMetersPlot-48", "BMP120", null, "image5.png");
            List<ImageMetadataEntry> entries = Arrays.asList(entry1, entry2, entry3, entry4, entry5);

            // Act
            CurrentMetersPlotResponse result = ImageMetadataConverter.createCurrentMetersPlotResponse(entries);

            // Assert
            assertNotNull(result);
            assertEquals("currentMetersPlot-48", result.getProductId());
            assertEquals("BMP120", result.getRegion());

            List<CurrentMetersPlotResponse.DepthData> depthDataList = result.getDepthData();
            assertNotNull(depthDataList);
            assertEquals(3, depthDataList.size());

            // Find and check each depth data
            CurrentMetersPlotResponse.DepthData depthXyz = findDepthDataByDepth(depthDataList, "xyz");
            assertNotNull(depthXyz);
            assertEquals("/path1", depthXyz.getPath());
            assertEquals(2, depthXyz.getFiles().size());
            assertEquals("image1.png", depthXyz.getFiles().get(0));
            assertEquals("image2.png", depthXyz.getFiles().get(1));

            CurrentMetersPlotResponse.DepthData depthZt = findDepthDataByDepth(depthDataList, "zt");
            assertNotNull(depthZt);
            assertEquals("/path1", depthZt.getPath());
            assertEquals(2, depthZt.getFiles().size());
            assertEquals("image3.png", depthZt.getFiles().get(0));
            assertEquals("image4.png", depthZt.getFiles().get(1));

            CurrentMetersPlotResponse.DepthData depthNull = findDepthDataByDepth(depthDataList, null);
            assertNotNull(depthNull);
            assertEquals("/path1", depthNull.getPath());
            assertEquals(1, depthNull.getFiles().size());
            assertEquals("image5.png", depthNull.getFiles().get(0));
        }

        @Test
        @DisplayName("Should sort filenames alphabetically")
        public void testWithUnsortedFilenames() {
            // Arrange - deliberately using filenames that would be sorted differently
            ImageMetadataEntry entry1 = createEntry("/path1", "currentMetersPlot-48", "BMP120", "xyz", "cimage.png");
            ImageMetadataEntry entry2 = createEntry("/path1", "currentMetersPlot-48", "BMP120", "xyz", "aimage.png");
            ImageMetadataEntry entry3 = createEntry("/path1", "currentMetersPlot-48", "BMP120", "xyz", "bimage.png");
            List<ImageMetadataEntry> entries = Arrays.asList(entry1, entry2, entry3);

            // Act
            CurrentMetersPlotResponse result = ImageMetadataConverter.createCurrentMetersPlotResponse(entries);

            // Assert
            assertNotNull(result);

            List<CurrentMetersPlotResponse.DepthData> depthDataList = result.getDepthData();
            assertEquals(1, depthDataList.size());

            CurrentMetersPlotResponse.DepthData depthData = depthDataList.get(0);
            assertEquals(3, depthData.getFiles().size());

            // Check files are sorted alphabetically, not in the order they were added
            assertEquals("aimage.png", depthData.getFiles().get(0));
            assertEquals("bimage.png", depthData.getFiles().get(1));
            assertEquals("cimage.png", depthData.getFiles().get(2));
        }
    }
}
