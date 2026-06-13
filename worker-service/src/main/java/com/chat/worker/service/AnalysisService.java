package com.chat.worker.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@Service
public class AnalysisService {

    // Simulates an ML/Processing stage (e.g., text sanitization, sentiment check)
    public String analyzeMessage(String payload) {
        // Simulating processing delay
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        
        // Simple logic to show it passed through the worker
        if (payload.toLowerCase().contains("error")) {
            throw new RuntimeException("Simulated processing error");
        }
        return payload + " [Processed by Worker]";
    }

    // CPU-Bound operation: Sequential vs Parallel Benchmark (Requirement for PDF)
    public Map<String, Long> runCpuBenchmark() {
        List<Double> data = new ArrayList<>();
        for (int i = 0; i < 5000000; i++) data.add(Math.random());

        long startSeq = System.currentTimeMillis();
        double seqSum = data.stream().map(Math::sqrt).map(Math::sin).mapToDouble(Double::doubleValue).sum();
        long seqTime = System.currentTimeMillis() - startSeq;

        long startPar = System.currentTimeMillis();
        double parSum = data.parallelStream().map(Math::sqrt).map(Math::sin).mapToDouble(Double::doubleValue).sum();
        long parTime = System.currentTimeMillis() - startPar;

        // Print sums to prevent potential dead-code elimination by JVM compiler
        System.out.println("Benchmark sums computed: seq=" + seqSum + ", par=" + parSum);

        return Map.of(
            "sequentialTimeMs", seqTime,
            "parallelTimeMs", parTime,
            "dataSize", 5000000L
        );
    }
}