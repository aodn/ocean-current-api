package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.service.tags.TagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import au.org.aodn.oceancurrent.dto.WaveTagResponse;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TagController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TagService tagService;

    @BeforeEach
    void setUp() {
        // Default mock for ensureDataAvailability to avoid 503 errors
        when(tagService.ensureDataAvailability("surface-waves")).thenReturn(true);

        // Add common mocks that are needed by most tests
        when(tagService.getSupportedProductTypes()).thenReturn(Arrays.asList("surface-waves", "argo"));
    }

    @Test
    void testGetWaveTagsByDate_Success() throws Exception {
        // Given
        String dateTime = "2021010100";
        String expectedTagFile = "y2021/m01/2021010100_Buoy.txt";
        WaveTagResponse.TagData tagData1 = new WaveTagResponse.TagData(150, 200, 12, "Title 1", "http://url1.com");
        WaveTagResponse.TagData tagData2 = new WaveTagResponse.TagData(160, 210, 15, "Title 2", "http://url2.com");
        WaveTagResponse response = new WaveTagResponse(expectedTagFile, Arrays.asList(tagData1, tagData2));

        // Mock ensureDataAvailable() to return true (data is available)
        when(tagService.isDataAvailable("surface-waves")).thenReturn(true);
        when(tagService.getAllTagFiles("surface-waves")).thenReturn(List.of(expectedTagFile));
        when(tagService.constructTagFilePath("surface-waves", dateTime)).thenReturn(expectedTagFile);
        when(tagService.tagFileExists("surface-waves", expectedTagFile)).thenReturn(true);
        when(tagService.getTagsByTagFile("surface-waves", expectedTagFile)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/by-date/{dateTime}", dateTime)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagFile").value(expectedTagFile))
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.tags[0].x").value(150))
                .andExpect(jsonPath("$.tags[0].y").value(200))
                .andExpect(jsonPath("$.tags[0].sz").value(12))
                .andExpect(jsonPath("$.tags[0].title").value("Title 1"))
                .andExpect(jsonPath("$.tags[0].url").value("http://url1.com"));
    }

        @Test
    void testGetWaveTagsByDate_DateNotFound() throws Exception {
        // Given
        String dateTime = "2021010100";
        String expectedTagFile = "y2021/m01/2021010100_Buoy.txt";
        List<String> availableFiles = Arrays.asList("y2021/m01/2021010101_Buoy.txt", "y2021/m01/2021010102_Buoy.txt");

        // Mock ensureDataAvailable() to return true (data is available)
        when(tagService.isDataAvailable("surface-waves")).thenReturn(true);
        when(tagService.getAllTagFiles("surface-waves")).thenReturn(availableFiles);

        when(tagService.constructTagFilePath("surface-waves", dateTime)).thenReturn(expectedTagFile);
        when(tagService.tagFileExists("surface-waves", expectedTagFile)).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/by-date/{dateTime}", dateTime)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No data found for date '" + dateTime + "'. Expected tag file: " + expectedTagFile));
    }

    @Test
    void testGetWaveTagsByDate_DatabaseUnavailableDownloadFails() throws Exception {
        // Given
        String dateTime = "2021010100";

        // Override the default mock to simulate download failure
        when(tagService.ensureDataAvailability("surface-waves")).thenReturn(false);

        // Mock ensureDataAvailable() flow where download fails
        when(tagService.isDataAvailable("surface-waves")).thenReturn(false);
        when(tagService.getAllTagFiles("surface-waves")).thenReturn(List.of());
        when(tagService.downloadData("surface-waves")).thenReturn(false); // Download fails

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/by-date/{dateTime}", dateTime)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Surface wave buoy data is temporarily unavailable. Please try again in a moment."));
    }

    @Test
    void testGetWaveTagsByTagFile_Success() throws Exception {
        // Given
        String tagFile = "test_tagfile";
        WaveTagResponse.TagData tagData1 = new WaveTagResponse.TagData(150, 200, 12, "Title 1", "http://url1.com");
        WaveTagResponse.TagData tagData2 = new WaveTagResponse.TagData(160, 210, 15, "Title 2", "http://url2.com");
        WaveTagResponse response = new WaveTagResponse(tagFile, Arrays.asList(tagData1, tagData2));

        // Mock ensureDataAvailable() to return true (data is available)
        when(tagService.isDataAvailable("surface-waves")).thenReturn(true);
        when(tagService.getAllTagFiles("surface-waves")).thenReturn(List.of(tagFile));
        when(tagService.tagFileExists("surface-waves", tagFile)).thenReturn(true);
        when(tagService.getTagsByTagFile("surface-waves", tagFile)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/by-tag-file")
                .param("tagFile", tagFile)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagFile").value(tagFile))
                .andExpect(jsonPath("$.tags").isArray())
                .andExpect(jsonPath("$.tags.length()").value(2))
                .andExpect(jsonPath("$.tags[0].x").value(150))
                .andExpect(jsonPath("$.tags[0].y").value(200))
                .andExpect(jsonPath("$.tags[0].sz").value(12))
                .andExpect(jsonPath("$.tags[0].title").value("Title 1"))
                .andExpect(jsonPath("$.tags[0].url").value("http://url1.com"));
    }

    @Test
    void testGetWaveTagsByTagFile_DatabaseNotAvailable_AutoDownload() throws Exception {
        // Given
        String tagFile = "test_tagfile";
        WaveTagResponse.TagData tagData1 = new WaveTagResponse.TagData(150, 200, 12, "Title 1", "http://url1.com");
        WaveTagResponse response = new WaveTagResponse(tagFile, List.of(tagData1));

        // Mock the ensureDataAvailable() flow:
        // First check: data not available or no tag files
        // After download: data available with tag files
        when(tagService.isDataAvailable("surface-waves"))
                .thenReturn(false)  // first check in ensureDataAvailable
                .thenReturn(true);  // after download
        when(tagService.getAllTagFiles("surface-waves"))
                .thenReturn(List.of())  // first check - empty
                .thenReturn(List.of(tagFile));  // after download
        when(tagService.downloadData("surface-waves")).thenReturn(true);
        when(tagService.tagFileExists("surface-waves", tagFile)).thenReturn(true);
        when(tagService.getTagsByTagFile("surface-waves", tagFile)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/by-tag-file")
                .param("tagFile", tagFile)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagFile").value(tagFile));
    }

    @Test
    void testGetWaveTagsByTagFile_TagFileNotFound() throws Exception {
        // Given
        String tagFile = "nonexistent_tagfile";
        // Mock ensureDataAvailable() to return true (data is available)
        when(tagService.isDataAvailable("surface-waves")).thenReturn(true);
        when(tagService.getAllTagFiles("surface-waves")).thenReturn(List.of("other_file.txt"));
        when(tagService.tagFileExists("surface-waves", tagFile)).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/by-tag-file")
                .param("tagFile", tagFile)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Tag file '" + tagFile + "' not found in database"));
    }

    @Test
    void testGetWaveTagsByTagFile_DatabaseUnavailableDownloadFails() throws Exception {
        // Given
        String tagFile = "test_tagfile";

        // Override the default mock to simulate download failure
        when(tagService.ensureDataAvailability("surface-waves")).thenReturn(false);

        // Mock ensureDataAvailable() flow where download fails
        when(tagService.isDataAvailable("surface-waves")).thenReturn(false);
        when(tagService.getAllTagFiles("surface-waves")).thenReturn(List.of());
        when(tagService.downloadData("surface-waves")).thenReturn(false); // Download fails

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/by-tag-file")
                .param("tagFile", tagFile)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Surface wave data is temporarily unavailable. Please try again in a moment."));
    }

    @Test
    void testGetAllTagFiles_Success() throws Exception {
        // Given
        List<String> tagFiles = Arrays.asList("tagfile1", "tagfile2", "tagfile3");
        // Mock ensureDataAvailable() to return true (data is available)
        when(tagService.isDataAvailable("surface-waves")).thenReturn(true);
        when(tagService.getAllTagFiles("surface-waves")).thenReturn(tagFiles);

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/tag-files")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagfiles").isArray())
                .andExpect(jsonPath("$.tagfiles.length()").value(3))
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.tagfiles[0]").value("tagfile1"))
                .andExpect(jsonPath("$.tagfiles[1]").value("tagfile2"))
                .andExpect(jsonPath("$.tagfiles[2]").value("tagfile3"));
    }

    @Test
    void testGetAllTagFiles_DatabaseUnavailableDownloadFails() throws Exception {
        // Given
        // Override the default mock to simulate download failure
        when(tagService.ensureDataAvailability("surface-waves")).thenReturn(false);

        // Mock ensureDataAvailable() flow where download fails
        when(tagService.isDataAvailable("surface-waves")).thenReturn(false);
        when(tagService.getAllTagFiles("surface-waves")).thenReturn(List.of());
        when(tagService.downloadData("surface-waves")).thenReturn(false); // Download fails

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/tag-files")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Surface wave data is temporarily unavailable. Please try again in a moment."));
    }

    @Test
    void testTriggerDownload_Success() throws Exception {
        // Given
        List<String> tagFiles = Arrays.asList("file1", "file2");
        when(tagService.downloadData("surface-waves")).thenReturn(true);
        when(tagService.getAllTagFiles("surface-waves")).thenReturn(tagFiles);

        // When & Then
        mockMvc.perform(post("/tags/surface-waves/download")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Surface wave data downloaded successfully"))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.tagFileCount").value(2));
    }

    @Test
    void testTriggerDownload_Failed() throws Exception {
        // Given
        when(tagService.downloadData("surface-waves")).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/tags/surface-waves/download")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Failed to download surface wave data. Please try again later."));
    }

    @Test
    void testGetDatabaseStatus_Available() throws Exception {
        // Given
        List<String> tagFiles = Arrays.asList("file1", "file2", "file3");
        when(tagService.isDataAvailable("surface-waves")).thenReturn(true);
        when(tagService.getAllTagFiles("surface-waves")).thenReturn(tagFiles);

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/status")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseAvailable").value(true))
                .andExpect(jsonPath("$.tagFileCount").value(3))
                .andExpect(jsonPath("$.lastUpdate").value("recently"))
                .andExpect(jsonPath("$.autoDownload").value("enabled"))
                .andExpect(jsonPath("$.message").value("Surface wave data is available (3 tag files)"));
    }

    @Test
    void testGetDatabaseStatus_NotAvailable() throws Exception {
        // Given
        when(tagService.isDataAvailable("surface-waves")).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/status")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseAvailable").value(false))
                .andExpect(jsonPath("$.tagFileCount").value(0))
                .andExpect(jsonPath("$.lastUpdate").value("unknown"))
                .andExpect(jsonPath("$.autoDownload").value("enabled"))
                .andExpect(jsonPath("$.message").value("Surface wave data is not available"));
    }

    @Test
    void testGetSupportedProductTypes_Success() throws Exception {
        // Given
        List<String> productTypes = Arrays.asList("surface-waves", "argo");
        when(tagService.getSupportedProductTypes()).thenReturn(productTypes);

        // When & Then
        mockMvc.perform(get("/tags/products")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productTypes").isArray())
                .andExpect(jsonPath("$.productTypes.length()").value(2))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.productTypes[0]").value("surface-waves"))
                .andExpect(jsonPath("$.productTypes[1]").value("argo"));
    }

    @Test
    void testGetGenericTagsByDate_Success() throws Exception {
        // Given - Use a different product type to hit the generic endpoint
        String productType = "argo";
        String dateTime = "20210101";
        String expectedTagFile = "20210101.txt";
        WaveTagResponse.TagData tagData = new WaveTagResponse.TagData(150, 200, 12, "Title 1", "http://url1.com");
        WaveTagResponse response = new WaveTagResponse(expectedTagFile, List.of(tagData));

        // Mock for ensureDataAvailability for this product type
        when(tagService.ensureDataAvailability(productType)).thenReturn(true);

        when(tagService.getSupportedProductTypes()).thenReturn(List.of(productType));
        when(tagService.isValidDateFormat(productType, dateTime)).thenReturn(true);
        when(tagService.isDataAvailable(productType)).thenReturn(true);
        when(tagService.hasData(productType)).thenReturn(true);
        when(tagService.constructTagFilePath(productType, dateTime)).thenReturn(expectedTagFile);
        when(tagService.tagFileExists(productType, expectedTagFile)).thenReturn(true);
        when(tagService.getTagsByTagFile(productType, expectedTagFile)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/tags/{productType}/by-date/{dateTime}", productType, dateTime)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagFile").value(expectedTagFile));
    }

    @Test
    void testGetGenericTagsByDate_UnsupportedProductType() throws Exception {
        // Given
        String productType = "unsupported-product";
        String dateTime = "2021010100";
        when(tagService.getSupportedProductTypes()).thenReturn(Arrays.asList("surface-waves", "argo"));

        // When & Then
        mockMvc.perform(get("/tags/{productType}/by-date/{dateTime}", productType, dateTime)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported product type: " + productType));
    }

    @Test
    void testGetGenericTagsByDate_DataUnavailable() throws Exception {
        // Given - Use a different product type to hit the generic endpoint
        String productType = "argo";
        String dateTime = "20210101";

        // Mock for ensureDataAvailability for this product type to simulate failure
        when(tagService.ensureDataAvailability(productType)).thenReturn(false);

        when(tagService.getSupportedProductTypes()).thenReturn(List.of(productType));
        when(tagService.isValidDateFormat(productType, dateTime)).thenReturn(true);
        when(tagService.isDataAvailable(productType)).thenReturn(false);
        when(tagService.hasData(productType)).thenReturn(false);
        when(tagService.downloadData(productType)).thenReturn(false); // Download fails

        // When & Then
        mockMvc.perform(get("/tags/{productType}/by-date/{dateTime}", productType, dateTime)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Data for product " + productType + " is temporarily unavailable. Please try again in a moment."));
    }

    @Test
    void testGetGenericTagsByDate_InvalidDateFormat() throws Exception {
        // Given - Use a different product type to hit the generic endpoint
        String productType = "argo";
        String dateTime = "invalid-date";
        when(tagService.getSupportedProductTypes()).thenReturn(List.of(productType));
        when(tagService.isValidDateFormat(productType, dateTime)).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/tags/{productType}/by-date/{dateTime}", productType, dateTime)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid date format for product type " + productType + ": " + dateTime));
    }

    @Test
    void testGetGenericTagFiles_Success() throws Exception {
        // Given - Use a different product type to hit the generic endpoint
        String productType = "argo";
        List<String> tagFiles = Arrays.asList("file1", "file2");

        // Mock for ensureDataAvailability for this product type
        when(tagService.ensureDataAvailability(productType)).thenReturn(true);

        when(tagService.getSupportedProductTypes()).thenReturn(List.of(productType));
        when(tagService.isDataAvailable(productType)).thenReturn(true);
        when(tagService.hasData(productType)).thenReturn(true);
        when(tagService.getAllTagFiles(productType)).thenReturn(tagFiles);

        // When & Then
        mockMvc.perform(get("/tags/{productType}/tag-files", productType)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productType").value(productType))
                .andExpect(jsonPath("$.tagFiles").isArray())
                .andExpect(jsonPath("$.tagFiles.length()").value(2))
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    void testGetGenericTagFiles_UnsupportedProductType() throws Exception {
        // Given
        String productType = "unsupported-product";
        when(tagService.getSupportedProductTypes()).thenReturn(Arrays.asList("surface-waves", "argo"));

        // When & Then
        mockMvc.perform(get("/tags/{productType}/tag-files", productType)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported product type: " + productType));
    }

    @Test
    void testGetGenericTagFiles_DataUnavailable() throws Exception {
        // Given - Use a different product type to hit the generic endpoint
        String productType = "argo";

        // Mock for ensureDataAvailability for this product type to simulate failure
        when(tagService.ensureDataAvailability(productType)).thenReturn(false);

        when(tagService.getSupportedProductTypes()).thenReturn(List.of(productType));
        when(tagService.isDataAvailable(productType)).thenReturn(false);
        when(tagService.hasData(productType)).thenReturn(false);
        when(tagService.downloadData(productType)).thenReturn(false); // Download fails

        // When & Then
        mockMvc.perform(get("/tags/{productType}/tag-files", productType)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Data for product " + productType + " is temporarily unavailable. Please try again in a moment."));
    }

    @Test
    void testGenericTriggerDownload_Success() throws Exception {
        // Given - Use a different product type to hit the generic endpoint
        String productType = "argo";
        List<String> tagFiles = Arrays.asList("file1", "file2");
        when(tagService.getSupportedProductTypes()).thenReturn(List.of(productType));
        when(tagService.downloadData(productType)).thenReturn(true);
        when(tagService.getAllTagFiles(productType)).thenReturn(tagFiles);

        // When & Then
        mockMvc.perform(post("/tags/{productType}/download", productType)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Data downloaded successfully for product " + productType))
                .andExpect(jsonPath("$.productType").value(productType))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.tagFileCount").value(2));
    }

    @Test
    void testGenericTriggerDownload_UnsupportedProductType() throws Exception {
        // Given
        String productType = "unsupported-product";
        when(tagService.getSupportedProductTypes()).thenReturn(Arrays.asList("surface-waves", "argo"));

        // When & Then
        mockMvc.perform(post("/tags/{productType}/download", productType)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported product type: " + productType));
    }

    @Test
    void testGenericTriggerDownload_Failed() throws Exception {
        // Given - Use a different product type to hit the generic endpoint
        String productType = "argo";
        when(tagService.getSupportedProductTypes()).thenReturn(List.of(productType));
        when(tagService.downloadData(productType)).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/tags/{productType}/download", productType)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Failed to download data for product " + productType + ". Please try again later."));
    }
}
