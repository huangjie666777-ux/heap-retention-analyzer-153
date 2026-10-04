package dev.heap;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the live analyses. Each upload builds a fully independent Analysis in
 * isolation and is published atomically; concurrent uploads and queries never
 * share mutable state. Deletion drops the reference so the GC reclaims it.
 */
public final class AnalysisService {
    private final HprofGraphBuilder builder = new HprofGraphBuilder();
    private final Map<String, Analysis> analyses = new ConcurrentHashMap<>();

    public record Created(String id, Analysis analysis) {}

    public Created analyze(File hprofFile) throws AnalysisException {
        Graph graph = builder.build(hprofFile);      // limits enforced here
        Analysis analysis = new Analysis(graph);     // dominators + retained
        String id = UUID.randomUUID().toString();
        analyses.put(id, analysis);                  // publish only when complete
        return new Created(id, analysis);
    }

    public Analysis get(String id) {
        return analyses.get(id);
    }

    public boolean delete(String id) {
        return analyses.remove(id) != null;
    }
}
