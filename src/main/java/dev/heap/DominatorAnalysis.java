package dev.heap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Immediate-dominator analysis over the object graph plus a virtual root
 * connected to every GC root / static-reference target. A node dominates
 * another when every path from the virtual root passes through it. Retained
 * bytes are the shallow-size sum of the dominated subtree, never a reachable
 * sum. Unreachable nodes are flagged and excluded from retention ranking.
 *
 * Uses the iterative Cooper-Harvey-Kennedy algorithm, which handles cycles,
 * shared subgraphs and multiple roots correctly.
 */
public final class DominatorAnalysis {
    private final Graph graph;
    private final int[] idom;          // per node; -2 = unreachable, -1 = virtual root
    private final long[] retained;     // shallow sum of dominated subtree
    private final boolean[] reachable;

    private DominatorAnalysis(Graph graph, int[] idom, long[] retained, boolean[] reachable) {
        this.graph = graph;
        this.idom = idom;
        this.retained = retained;
        this.reachable = reachable;
    }

    public static DominatorAnalysis compute(Graph graph) {
        int n = graph.nodeCount();

        // Predecessor index (CSR) for the CHK intersection step.
        int[] inDegree = new int[n];
        for (int e = 0; e < graph.edgeCount(); e++) {
            inDegree[graph.edgeTargets[e]]++;
        }
        int[] predOffsets = new int[n + 1];
        for (int i = 0; i < n; i++) {
            predOffsets[i + 1] = predOffsets[i] + inDegree[i];
        }
        int[] preds = new int[graph.edgeCount()];
        int[] cursor = Arrays.copyOf(predOffsets, n);
        for (int from = 0; from < n; from++) {
            for (int e = graph.edgeOffsets[from]; e < graph.edgeOffsets[from + 1]; e++) {
                preds[cursor[graph.edgeTargets[e]]++] = from;
            }
        }

        // Iterative postorder DFS from the virtual root (all roots are starts).
        int[] postOrder = new int[n];
        int[] postOf = new int[n];
        Arrays.fill(postOf, -1);
        int postCount = 0;
        int[] edgeCursor = new int[n];
        Deque<Integer> stack = new ArrayDeque<>();
        boolean[] onStack = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (!graph.roots[i] || postOf[i] >= 0) {
                continue;
            }
            stack.push(i);
            onStack[i] = true;
            while (!stack.isEmpty()) {
                int node = stack.peek();
                int end = graph.edgeOffsets[node + 1];
                boolean pushed = false;
                for (int e = edgeCursor[node]; e < end; e++) {
                    int target = graph.edgeTargets[e];
                    if (postOf[target] < 0 && !onStack[target]) {
                        edgeCursor[node] = e + 1;
                        stack.push(target);
                        onStack[target] = true;
                        pushed = true;
                        break;
                    }
                }
                if (!pushed) {
                    edgeCursor[node] = end;
                    stack.pop();
                    onStack[node] = false;
                    postOf[node] = postCount;
                    postOrder[postCount++] = node;
                }
            }
        }

        // CHK iteration. idom -1 denotes the virtual root; -2 is "undefined".
        int[] idom = new int[n];
        Arrays.fill(idom, -2);
        for (int i = 0; i < n; i++) {
            if (graph.roots[i] && postOf[i] >= 0) {
                idom[i] = -1;
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int p = postCount - 1; p >= 0; p--) {   // reverse postorder
                int node = postOrder[p];
                if (idom[node] == -1) {
                    continue;   // roots keep the virtual root as idom
                }
                int newIdom = -2;
                for (int q = predOffsets[node]; q < predOffsets[node + 1]; q++) {
                    int pred = preds[q];
                    if (idom[pred] == -2) {
                        continue;   // predecessor not yet processed
                    }
                    newIdom = (newIdom == -2) ? pred : intersect(idom, postOf, newIdom, pred);
                }
                if (newIdom != -2 && newIdom != idom[node]) {
                    idom[node] = newIdom;
                    changed = true;
                }
            }
        }

        // Retained sizes: postorder accumulation, children before parents.
        long[] retained = new long[n];
        boolean[] reachable = new boolean[n];
        for (int i = 0; i < n; i++) {
            retained[i] = graph.shallowSizes[i];
            reachable[i] = postOf[i] >= 0;
        }
        for (int p = 0; p < postCount; p++) {
            int node = postOrder[p];
            int parent = idom[node];
            if (parent >= 0) {
                retained[parent] += retained[node];
            }
        }
        return new DominatorAnalysis(graph, idom, retained, reachable);
    }

    private static int intersect(int[] idom, int[] postOf, int a, int b) {
        while (a != b) {
            if (a == -1 || b == -1) {
                return -1;   // both reached the virtual root
            }
            if (postOf[a] < postOf[b]) {
                a = idom[a];
            } else {
                b = idom[b];
            }
        }
        return a;
    }

    public int immediateDominator(int node) {
        return idom[node];
    }

    public long retainedSize(int node) {
        return retained[node];
    }

    public boolean isReachable(int node) {
        return reachable[node];
    }

    public List<Integer> reachableNodesByRetainedDesc() {
        List<Integer> nodes = new ArrayList<>();
        for (int i = 0; i < graph.nodeCount(); i++) {
            if (reachable[i]) {
                nodes.add(i);
            }
        }
        nodes.sort((a, b) -> Long.compare(retained[b], retained[a]));
        return nodes;
    }
}
