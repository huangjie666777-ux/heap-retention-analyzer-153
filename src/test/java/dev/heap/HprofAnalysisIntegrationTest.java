package dev.heap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** End-to-end: real HotSpot dump -> graph -> dominators -> ranking + path. */
class HprofAnalysisIntegrationTest {

    @TempDir
    Path dir;

    @Test
    void analyzesRealHeapDump() throws Exception {
        Path dump = dir.resolve("test.hprof");
        dumpInChildJvm(dump);
        assertTrue(Files.size(dump) > 0);

        Analysis analysis = new Analysis(new HprofGraphBuilder().build(dump.toFile()));
        Map<String, Object> summary = analysis.summary();
        assertTrue((int) summary.get("objects") > 1000);
        assertTrue((int) summary.get("edges") > 1000);
        assertTrue((int) summary.get("roots") > 0);

        List<Map<String, Object>> top = analysis.retainedRanking(20);
        assertFalse(top.isEmpty());
        long prev = Long.MAX_VALUE;
        for (Map<String, Object> row : top) {
            long retained = (long) row.get("retainedSize");
            assertTrue(retained <= prev, "ranking must be descending");
            prev = retained;
            assertNotNull(row.get("id"));
            assertNotNull(row.get("className"));
            assertNotNull(row.get("immediateDominator"));
            assertTrue((long) row.get("shallowSize") <= retained);
        }

        // The static CACHE list must be a root and retain its chain.
        Integer cacheIdx = findByClass(analysis, "java.util.ArrayList");
        assertNotNull(cacheIdx);
        Map<String, Object> view = analysis.objectView(cacheIdx);
        assertTrue((boolean) view.get("reachable"));

        // A CacheNode deep in the chain must have a real root path with labels.
        Integer nodeIdx = findByClass(analysis, "dev.heap.DumpGenerator$CacheNode");
        assertNotNull(nodeIdx);
        List<Map<String, Object>> path = analysis.rootPath(nodeIdx);
        assertNotNull(path);
        assertFalse(path.isEmpty());
        for (Map<String, Object> edge : path) {
            String ref = (String) edge.get("ref");
            assertTrue(ref.contains(".") || ref.startsWith("["), "edge label: " + ref);
        }
    }

    @Test
    void corruptFileIsRejected() throws Exception {
        Path bad = dir.resolve("bad.hprof");
        Files.write(bad, "not a heap dump at all".getBytes());
        AnalysisException e = org.junit.jupiter.api.Assertions.assertThrows(
                AnalysisException.class,
                () -> new HprofGraphBuilder().build(bad.toFile()));
        assertEquals(400, e.status());
    }

    /** Dumps from a tiny dedicated JVM so the 50k-object limit holds. */
    private void dumpInChildJvm(Path dump) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-Xmx24m", "-cp",
                System.getProperty("java.class.path"),
                "dev.heap.DumpGenerator", dump.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), "dump generator failed: " + output);
    }

    private Integer findByClass(Analysis analysis, String className) {
        Graph g = analysis.graph();
        for (int i = 0; i < g.nodeCount(); i++) {
            if (g.classNames[i].equals(className)) {
                return i;
            }
        }
        return null;
    }
}
