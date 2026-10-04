package dev.heap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Shortest strong-reference path from any root to a given object, computed by
 * multi-source BFS over the real graph edges (never the dominator tree).
 */
public final class PathFinder {

    public record Step(long fromId, String fromClass, String ref, long toId, String toClass) {}

    private PathFinder() {
    }

    /** Returns edges root -> ... -> target, or null when target is unreachable. */
    public static List<Step> shortestPath(Graph graph, int target) {
        int n = graph.nodeCount();
        int[] parentNode = new int[n];
        int[] parentEdge = new int[n];
        Arrays.fill(parentNode, -1);
        Deque<Integer> queue = new ArrayDeque<>();
        for (int i = 0; i < n; i++) {
            if (graph.roots[i]) {
                parentNode[i] = i;   // visited marker for roots
                queue.add(i);
            }
        }
        while (!queue.isEmpty()) {
            int node = queue.poll();
            if (node == target) {
                break;
            }
            for (int e = graph.edgeOffsets[node]; e < graph.edgeOffsets[node + 1]; e++) {
                int next = graph.edgeTargets[e];
                if (parentNode[next] == -1) {
                    parentNode[next] = node;
                    parentEdge[next] = e;
                    queue.add(next);
                }
            }
        }
        if (parentNode[target] == -1) {
            return null;
        }
        List<Step> steps = new ArrayList<>();
        int node = target;
        while (!(graph.roots[node] && parentNode[node] == node)) {
            int parent = parentNode[node];
            int edge = parentEdge[node];
            steps.add(new Step(graph.ids[parent], graph.classNames[parent],
                    graph.edgeLabels[edge], graph.ids[node], graph.classNames[node]));
            node = parent;
        }
        Collections.reverse(steps);
        return steps;
    }
}
