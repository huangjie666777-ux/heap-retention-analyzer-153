package dev.heap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable object graph index. Nodes are plain instances and arrays only.
 * Edges are strong references from instance fields (declaring class + field
 * name kept as label) and object array elements (index kept as label).
 */
public final class Graph {
    final long[] ids;
    final String[] classNames;
    final long[] shallowSizes;
    final boolean[] roots;
    final int[] edgeOffsets;   // length nodeCount + 1, CSR layout
    final int[] edgeTargets;
    final String[] edgeLabels;
    final Map<Long, Integer> indexById;

    private Graph(long[] ids, String[] classNames, long[] shallowSizes, boolean[] roots,
                  int[] edgeOffsets, int[] edgeTargets, String[] edgeLabels,
                  Map<Long, Integer> indexById) {
        this.ids = ids;
        this.classNames = classNames;
        this.shallowSizes = shallowSizes;
        this.roots = roots;
        this.edgeOffsets = edgeOffsets;
        this.edgeTargets = edgeTargets;
        this.edgeLabels = edgeLabels;
        this.indexById = indexById;
    }

    public int nodeCount() {
        return ids.length;
    }

    public int edgeCount() {
        return edgeTargets.length;
    }

    public int rootCount() {
        int n = 0;
        for (boolean r : roots) {
            if (r) n++;
        }
        return n;
    }

    public Integer indexOf(long id) {
        return indexById.get(id);
    }

    public static final class Builder {
        private final List<Long> ids = new ArrayList<>();
        private final List<String> classNames = new ArrayList<>();
        private final List<Long> shallowSizes = new ArrayList<>();
        private final List<Boolean> roots = new ArrayList<>();
        private final Map<Long, Integer> indexById = new HashMap<>();
        private final List<List<int[]>> outEdges = new ArrayList<>();
        private final List<String> labels = new ArrayList<>();
        private final Map<String, Integer> labelIndex = new HashMap<>();
        private int edgeCount;

        public int addNode(long id, String className, long shallowSize) {
            int idx = ids.size();
            ids.add(id);
            classNames.add(className);
            shallowSizes.add(shallowSize);
            roots.add(Boolean.FALSE);
            indexById.put(id, idx);
            outEdges.add(new ArrayList<>());
            return idx;
        }

        public Integer indexOf(long id) {
            return indexById.get(id);
        }

        public void markRoot(int node) {
            roots.set(node, Boolean.TRUE);
        }

        public int labelOf(String label) {
            Integer existing = labelIndex.get(label);
            if (existing != null) {
                return existing;
            }
            labels.add(label);
            labelIndex.put(label, labels.size() - 1);
            return labels.size() - 1;
        }

        public void addEdge(int from, int to, int label) {
            outEdges.get(from).add(new int[]{to, label});
            edgeCount++;
        }

        public int nodeCount() {
            return ids.size();
        }

        public int edgeCount() {
            return edgeCount;
        }

        public Graph build() {
            int n = ids.size();
            long[] idArr = new long[n];
            String[] cn = new String[n];
            long[] ss = new long[n];
            boolean[] rt = new boolean[n];
            int[] offsets = new int[n + 1];
            for (int i = 0; i < n; i++) {
                idArr[i] = ids.get(i);
                cn[i] = classNames.get(i);
                ss[i] = shallowSizes.get(i);
                rt[i] = roots.get(i);
                offsets[i + 1] = offsets[i] + outEdges.get(i).size();
            }
            int[] targets = new int[edgeCount];
            String[] edgeLbl = new String[edgeCount];
            int pos = 0;
            for (int i = 0; i < n; i++) {
                for (int[] e : outEdges.get(i)) {
                    targets[pos] = e[0];
                    edgeLbl[pos] = labels.get(e[1]);
                    pos++;
                }
            }
            return new Graph(idArr, cn, ss, rt, offsets, targets, edgeLbl, indexById);
        }
    }
}
