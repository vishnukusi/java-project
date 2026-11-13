package com.sentinelcv.integration;

import com.sentinelcv.controller.DashboardController;
import com.sentinelcv.controller.PageController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LandingPageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PageController pageController;

    @Autowired
    private DashboardController dashboardController;

    @Test
    void testPageControllerUnitReturn() {
        assertEquals("index", pageController.landing());
    }

    @Test
    void testRootEndpointServesLandingPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(containsString("Check your vision AI before you trust it.")))
                .andExpect(content().string(containsString("href=\"/dashboard\"")))
                .andExpect(content().string(containsString("/static/css/tokens.css")))
                .andExpect(content().string(containsString("/static/css/main.css")))
                .andExpect(content().string(containsString("/static/css/landing.css")))
                .andExpect(content().string(containsString("/static/favicon.svg")))
                .andExpect(content().string(containsString("ACCEPT")))
                .andExpect(content().string(containsString("REVIEW")))
                .andExpect(content().string(containsString("QUARANTINE")))
                .andExpect(content().string(containsString("UNAVAILABLE")))
                .andExpect(content().string(containsString("NOT RUN")));
    }

    @Test
    void testDashboardEndpointServesDashboard() throws Exception {
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard"))
                .andExpect(model().attributeExists("totalDatasets"))
                .andExpect(model().attributeExists("totalModels"))
                .andExpect(model().attributeExists("totalInferences"))
                .andExpect(model().attributeExists("totalFindings"))
                .andExpect(model().attributeExists("criticalFindings"))
                .andExpect(model().attributeExists("highFindings"))
                .andExpect(model().attributeExists("assessment"))
                .andExpect(model().attributeExists("recentAudits"))
                .andExpect(content().string(containsString("/static/js/api.js")))
                .andExpect(content().string(containsString("/static/js/overview.js")))
                .andExpect(content().string(containsString("/static/css/dashboard.css")));
    }

    @Test
    void testScanEndpointServesNewScan() throws Exception {
        mockMvc.perform(get("/scan"))
                .andExpect(status().isOk())
                .andExpect(view().name("scan"))
                .andExpect(model().attribute("maxUploadSize", "500MB"))
                .andExpect(content().string(containsString("Dataset Scan")))
                .andExpect(content().string(containsString("Model Scan")))
                .andExpect(content().string(containsString("Inference Verification")))
                .andExpect(content().string(containsString("Distribution Analysis")))
                .andExpect(content().string(containsString("500MB")))
                .andExpect(content().string(containsString("/static/js/api.js")))
                .andExpect(content().string(containsString("/static/js/scan.js")));
    }

    @Test
    void testStaticCssAndJsResourcesAccessible() throws Exception {
        mockMvc.perform(get("/static/css/tokens.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--bg-canvas")));

        mockMvc.perform(get("/static/css/main.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".skip")));

        mockMvc.perform(get("/static/css/landing.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".verdicts")));

        mockMvc.perform(get("/static/css/dashboard.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".shell")));

        mockMvc.perform(get("/static/js/api.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("window.SCV")));

        mockMvc.perform(get("/static/js/overview.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Registered assets")));

        mockMvc.perform(get("/static/js/scan.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("wire(form)")));

        mockMvc.perform(get("/static/favicon.svg"))
                .andExpect(status().isOk());
    }

    @Test
    void testDataIslandEndpointsAccessible() throws Exception {
        mockMvc.perform(get("/datasets"))
                .andExpect(status().isOk())
                .andExpect(view().name("dataset/list"))
                .andExpect(content().string(containsString("id=\"__DATA__\"")))
                .andExpect(content().string(containsString("datasets")));

        mockMvc.perform(get("/models"))
                .andExpect(status().isOk())
                .andExpect(view().name("model/list"))
                .andExpect(content().string(containsString("id=\"__DATA__\"")))
                .andExpect(content().string(containsString("models")));

        mockMvc.perform(get("/inference"))
                .andExpect(status().isOk())
                .andExpect(view().name("inference/provenance"))
                .andExpect(content().string(containsString("id=\"__DATA__\"")))
                .andExpect(content().string(containsString("records")));

        mockMvc.perform(get("/drift"))
                .andExpect(status().isOk())
                .andExpect(view().name("drift/analysis"))
                .andExpect(content().string(containsString("id=\"__DATA__\"")))
                .andExpect(content().string(containsString("driftAudits")));

        mockMvc.perform(get("/history"))
                .andExpect(status().isOk())
                .andExpect(view().name("audit/history"))
                .andExpect(content().string(containsString("id=\"__DATA__\"")))
                .andExpect(content().string(containsString("historyList")));

        mockMvc.perform(get("/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/list"))
                .andExpect(content().string(containsString("id=\"__DATA__\"")))
                .andExpect(content().string(containsString("reports")));
    }
}
