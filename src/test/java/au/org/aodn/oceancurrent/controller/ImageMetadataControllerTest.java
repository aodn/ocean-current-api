package au.org.aodn.oceancurrent.controller;

import au.org.aodn.oceancurrent.dto.RegionLatestDate;
import au.org.aodn.oceancurrent.dto.RegionLatestDateResponse;
import au.org.aodn.oceancurrent.service.ProductService;
import au.org.aodn.oceancurrent.service.RemoteLatestDateService;
import au.org.aodn.oceancurrent.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ImageMetadataController.class)
@AutoConfigureMockMvc(addFilters = false)
class ImageMetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SearchService searchService;

    @MockBean
    private ProductService productService;

    @MockBean
    private RemoteLatestDateService remoteLatestDateService;


    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String TODAY = LocalDate.now().format(DATE_FORMATTER);

    @Test
    void getLatestArgoDate_Success() throws Exception {
        // Given
        RegionLatestDate latestDate = new RegionLatestDate("", TODAY, "");
        RegionLatestDateResponse expectedResponse = new RegionLatestDateResponse("argo", List.of(latestDate));
        
        when(remoteLatestDateService.getLatestDateByProductId("argo")).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(get("/metadata/latest-dates/argo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.productId").value("argo"))
                .andExpect(jsonPath("$.regionLatestDates").isArray())
                .andExpect(jsonPath("$.regionLatestDates[0].region").value(""))
                .andExpect(jsonPath("$.regionLatestDates[0].latestDate").value(TODAY))
                .andExpect(jsonPath("$.regionLatestDates[0].path").value(""));
    }

    @Test
    void getLatestArgoDate_NoDataFound() throws Exception {
        // Given
        RegionLatestDate latestDate = new RegionLatestDate("", null, "");
        RegionLatestDateResponse expectedResponse = new RegionLatestDateResponse("argo", List.of(latestDate));
        
        when(remoteLatestDateService.getLatestDateByProductId("argo")).thenReturn(expectedResponse);

        // When & Then
        mockMvc.perform(get("/metadata/latest-dates/argo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.productId").value("argo"))
                .andExpect(jsonPath("$.regionLatestDates").isArray())
                .andExpect(jsonPath("$.regionLatestDates[0].region").value(""))
                .andExpect(jsonPath("$.regionLatestDates[0].latestDate").isEmpty())
                .andExpect(jsonPath("$.regionLatestDates[0].path").value(""));
    }

    @Test
    void getLatestArgoDate_ServiceException_ReturnsInternalServerError() throws Exception {
        // Given
        when(remoteLatestDateService.getLatestDateByProductId("argo"))
                .thenThrow(new RuntimeException("Remote service unavailable"));

        // When & Then
        mockMvc.perform(get("/metadata/latest-dates/argo"))
                .andExpect(status().isInternalServerError());
    }
}