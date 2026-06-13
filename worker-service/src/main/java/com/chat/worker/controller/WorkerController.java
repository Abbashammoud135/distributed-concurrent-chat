package com.chat.worker.controller;

import com.chat.worker.service.AnalysisService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class WorkerController {
    private final AnalysisService analysisService;

    public WorkerController(AnalysisService analysisService) { this.analysisService = analysisService; }

    @PostMapping("/process")
    public String process(@RequestBody String message) {
        // Artificial delay injection for testing timeouts
        if (message.contains("sleep")) {
            try { Thread.sleep(4000); } catch (InterruptedException ignored) {}
        }
        return analysisService.analyzeMessage(message);
    }

    @GetMapping("/benchmark")
    public Map<String, Long> benchmark() {
        return analysisService.runCpuBenchmark();
    }
}