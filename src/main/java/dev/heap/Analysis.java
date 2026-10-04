package dev.heap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One fully built, immutable analysis. Published only when complete. */
public final class Analysis {
    private final Graph graph;
    private final DominatorAnalysis dominators;

    public Analysis(Graph graph) {
        this.graph = graph;
        this.dominators = DominatorAnalysis.compute(graph);
    }

    public Graph graph() {
        return graph;
    }

    public Map<String, Object> summary() {
        int unreachable = 0;
        for (int i = 0; i < graph.nodeCount(); i++) {
            if (!dominators.isReachable(i)) {
                unreachable++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("objects", graph.nodeCount());
        out.put("edges", graph.edgeCount());
        out.put("roots", graph.rootCount());
        out.put("unreachable", unreachable);
        return out;
    }

    /** Top retained-bytes ranking, reachable objects only, descending. */
    public List<Map<String, Object>> retainedRanking(int limit) {
        List<Integer> nodes = dominators.reachableNodesByRetainedDesc();
        List<Map<String, Object>> out = new ArrayList<>(Math.min(limit, nodes.size()));
        for (int i = 0; i < nodes.size() && i < limit; i++) {
            out.add(objectView(nodes.get(i)));
        }
        return out;
    }

    public Map<String, Object> objectView(int node) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", HexIds.toHex(graph.ids[node]));
        out.put("className", graph.classNames[node]);
        out.put("shallowSize", graph.shallowSizes[node]);
        out.put("reachable", dominators.isReachable(node));
        if (dominators.isReachable(node)) {
            out.put("retainedSize", dominators.retainedSize(node));
            int idom = dominators.immediateDominator(node);
            out.put("immediateDominator", idom >= 0 ? HexIds.toHex(graph.ids[idom]) : "ROOT");
        } else {
            out.put("retainedSize", null);
            out.put("immediateDominator", null);
        }
        out.put("gcRoot", graph.roots[node]);
        return out;
    }

    /** Shortest strong-reference path from any root; null when unreachable. */
    public List<Map<String, Object>> rootPath(int node) {
        List<PathFinder.Step> steps = PathFinder.shortestPath(graph, node);
        if (steps == null) {
            return null;
        }
        List<Map<String, Object>> out = new ArrayList<>(steps.size());
        for (PathFinder.Step s : steps) {
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("from", HexIds.toHex(s.fromId()));
            edge.put("fromClass", s.fromClass());
            edge.put("ref", s.ref());
            edge.put("to", HexIds.toHex(s.toId()));
            edge.put("toClass", s.toClass());
            out.add(edge);
        }
        return out;
    }
}
