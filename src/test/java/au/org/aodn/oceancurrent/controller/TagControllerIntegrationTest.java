package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.service.TagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
// import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import au.org.aodn.oceancurrent.dto.WaveTagResponse;

import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TagController.class)
class TagControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TagService tagService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetTagsByTagFile_Success() throws Exception {
        // Given
        String tagFile = "test_tagfile";
        WaveTagResponse.TagData tagData1 = new WaveTagResponse.TagData(150, 200, 12, "Title 1", "http://url1.com");
        WaveTagResponse.TagData tagData2 = new WaveTagResponse.TagData(160, 210, 15, "Title 2", "http://url2.com");
        WaveTagResponse response = new WaveTagResponse(tagFile, Arrays.asList(tagData1, tagData2));

        when(tagService.isDatabaseAvailable()).thenReturn(true);
        when(tagService.tagFileExists(tagFile)).thenReturn(true);
        when(tagService.getTagsByTagFile(tagFile)).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/by-tagfile")
                .param("tagfile", tagFile)
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
    void testGetTagsByTagFile_DatabaseNotAvailable() throws Exception {
        // Given
        String tagFile = "test_tagfile";
        when(tagService.isDatabaseAvailable()).thenReturn(false);
        when(tagService.downloadSqliteDatabase()).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/by-tagfile")
                .param("tagfile", tagFile)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()); // Should auto-download and return data
    }

    @Test
    void testGetTagsByTagFile_TagFileNotFound() throws Exception {
        // Given
        String tagFile = "nonexistent_tagfile";
        when(tagService.isDatabaseAvailable()).thenReturn(true);
        when(tagService.tagFileExists(tagFile)).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/by-tagfile")
                .param("tagfile", tagFile)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Tagfile 'nonexistent_tagfile' not found in database"));
    }

    @Test
    void testGetAllTagFiles_Success() throws Exception {
        // Given
        List<String> tagFiles = Arrays.asList("tagfile1", "tagfile2", "tagfile3");
        when(tagService.isDatabaseAvailable()).thenReturn(true);
        when(tagService.getAllTagFiles()).thenReturn(tagFiles);

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/tagfiles")
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
    void testTriggerDownload_Success() throws Exception {
        // Given
        when(tagService.downloadSqliteDatabase()).thenReturn(true);

        // When & Then
        mockMvc.perform(post("/tags/surface-waves/download")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("SQLite database downloaded successfully"))
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void testTriggerDownload_Failed() throws Exception {
        // Given
        when(tagService.downloadSqliteDatabase()).thenReturn(false);

        // When & Then
        mockMvc.perform(post("/tags/surface-waves/download")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Failed to download SQLite database"));
    }

    @Test
    void testGetDatabaseStatus_Available() throws Exception {
        // Given
        when(tagService.isDatabaseAvailable()).thenReturn(true);

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/status")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseAvailable").value(true))
                .andExpect(jsonPath("$.message").value("Database is available"));
    }

    @Test
    void testGetDatabaseStatus_NotAvailable() throws Exception {
        // Given
        when(tagService.isDatabaseAvailable()).thenReturn(false);

        // When & Then
        mockMvc.perform(get("/tags/surface-waves/status")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseAvailable").value(false))
                .andExpect(jsonPath("$.message").value("Database is not available"));
    }
}
