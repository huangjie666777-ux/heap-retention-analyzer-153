package dev.heap;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import org.netbeans.lib.profiler.heap.Field;
import org.netbeans.lib.profiler.heap.GCRoot;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.ObjectArrayInstance;
import org.netbeans.lib.profiler.heap.ObjectFieldValue;

/**
 * Reads a HotSpot HPROF file (4- or 8-byte IDs, segmented heap dumps) through
 * the NetBeans profiler library, extracting only object identity, class and
 * shallow size. The reference graph is built here from instance fields and
 * array elements; no retained-size figures from the library are used.
 */
public final class HprofGraphBuilder {
    public static final long MAX_FILE_BYTES = 100L * 1024 * 1024;      // 100 MiB
    public static final int MAX_OBJECTS = 50_000;
    public static final int MAX_EDGES = 200_000;

    private static final String REFERENCE_CLASS = "java.lang.ref.Reference";
    private static final String CLASS_CLASS = "java.lang.Class";

    public Graph build(File hprofFile) throws AnalysisException {
        if (hprofFile == null || !hprofFile.isFile()) {
            throw new AnalysisException(400, "upload is not a readable file");
        }
        if (hprofFile.length() > MAX_FILE_BYTES) {
            throw new AnalysisException(413, "hprof exceeds 100 MiB limit");
        }
        Heap heap;
        try {
            heap = HeapFactory.createHeap(hprofFile);
        } catch (IOException | RuntimeException e) {
            throw new AnalysisException(400, "corrupt or unsupported hprof: " + e.getMessage(), e);
        }
        Graph.Builder builder = new Graph.Builder();
        try {
            collectNodes(heap, builder);
            markRoots(heap, builder);
            collectEdges(heap, builder);
        } catch (AnalysisException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AnalysisException(400, "corrupt or unsupported hprof: " + e.getMessage(), e);
        }
        return builder.build();
    }

    private void collectNodes(Heap heap, Graph.Builder builder) throws AnalysisException {
        Iterator<?> it = heap.getAllInstancesIterator();
        while (it.hasNext()) {
            Instance inst = (Instance) it.next();
            JavaClass jc = inst.getJavaClass();
            if (jc == null || CLASS_CLASS.equals(jc.getName())) {
                continue;   // nodes are plain instances and arrays, not java.lang.Class
            }
            if (builder.nodeCount() >= MAX_OBJECTS) {
                throw new AnalysisException(413, "object count exceeds " + MAX_OBJECTS + " limit");
            }
            builder.addNode(inst.getInstanceId(), jc.getName(), inst.getSize());
        }
    }

    private void markRoots(Heap heap, Graph.Builder builder) {
        for (Object obj : heap.getGCRoots()) {
            Instance inst = ((GCRoot) obj).getInstance();
            if (inst != null) {
                Integer idx = builder.indexOf(inst.getInstanceId());
                if (idx != null) {
                    builder.markRoot(idx);
                }
            }
        }
        // Targets of class static references are roots too.
        for (Object obj : heap.getAllClasses()) {
            JavaClass jc = (JavaClass) obj;
            for (Object fvObj : jc.getStaticFieldValues()) {
                if (fvObj instanceof ObjectFieldValue ofv) {
                    Instance target = ofv.getInstance();
                    if (target != null) {
                        Integer idx = builder.indexOf(target.getInstanceId());
                        if (idx != null) {
                            builder.markRoot(idx);
                        }
                    }
                }
            }
        }
    }

    private void collectEdges(Heap heap, Graph.Builder builder) throws AnalysisException {
        Iterator<?> it = heap.getAllInstancesIterator();
        while (it.hasNext()) {
            Instance inst = (Instance) it.next();
            Integer from = builder.indexOf(inst.getInstanceId());
            if (from == null) {
                continue;
            }
            if (inst instanceof ObjectArrayInstance array) {
                List<?> values = array.getValues();
                for (int i = 0; i < values.size(); i++) {
                    Instance element = (Instance) values.get(i);
                    if (element != null) {
                        addEdge(builder, from, element.getInstanceId(), "[" + i + "]");
                    }
                }
            } else {
                for (Object fvObj : inst.getFieldValues()) {
                    if (!(fvObj instanceof ObjectFieldValue ofv)) {
                        continue;
                    }
                    Field field = ofv.getField();
                    if (field.isStatic() || isReferenceReferent(field)) {
                        continue;   // soft/weak/phantom referents are not strong edges
                    }
                    Instance target = ofv.getInstance();
                    if (target == null) {
                        continue;   // null references are ignored
                    }
                    String label = field.getDeclaringClass().getName() + "." + field.getName();
                    addEdge(builder, from, target.getInstanceId(), label);
                }
            }
        }
    }

    private boolean isReferenceReferent(Field field) {
        JavaClass declaring = field.getDeclaringClass();
        return declaring != null && REFERENCE_CLASS.equals(declaring.getName());
    }

    private void addEdge(Graph.Builder builder, int from, long targetId, String label)
            throws AnalysisException {
        Integer to = builder.indexOf(targetId);
        if (to == null) {
            return;   // target outside node set (e.g. java.lang.Class); no class-loader simulation
        }
        if (builder.edgeCount() >= MAX_EDGES) {
            throw new AnalysisException(413, "edge count exceeds " + MAX_EDGES + " limit");
        }
        builder.addEdge(from, to, builder.labelOf(label));
    }
}
