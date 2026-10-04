package dev.heap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Synthetic-graph dominator semantics: cycles, sharing, multiple roots. */
class DominatorAnalysisTest {

    private Graph.Builder builder() {
        return new Graph.Builder();
    }

    private int add(Graph.Builder b, long id, long shallow) {
        return b.addNode(id, "test.Node", shallow);
    }

    private void edge(Graph.Builder b, int from, int to) {
        b.addEdge(from, to, b.labelOf("test.Node.f" + from + "_" + to));
    }

    @Test
    void chainDominatesTransitively() {
        Graph.Builder b = builder();
        int a = add(b, 1, 10);
        int c = add(b, 2, 20);
        int d = add(b, 3, 40);
        b.markRoot(a);
        edge(b, a, c);
        edge(b, c, d);
        DominatorAnalysis dom = DominatorAnalysis.compute(b.build());
        assertEquals(-1, dom.immediateDominator(a));
        assertEquals(a, dom.immediateDominator(c));
        assertEquals(c, dom.immediateDominator(d));
        assertEquals(70, dom.retainedSize(a));
        assertEquals(60, dom.retainedSize(c));
        assertEquals(40, dom.retainedSize(d));
    }

    @Test
    void sharedSubgraphIsNotDoubleCounted() {
        // root -> x, root -> y, x -> s, y -> s: s dominated by root only.
        Graph.Builder b = builder();
        int r = add(b, 1, 1);
        int x = add(b, 2, 10);
        int y = add(b, 3, 10);
        int s = add(b, 4, 100);
        b.markRoot(r);
        edge(b, r, x);
        edge(b, r, y);
        edge(b, x, s);
        edge(b, y, s);
        DominatorAnalysis dom = DominatorAnalysis.compute(b.build());
        assertEquals(r, dom.immediateDominator(s));
        assertEquals(10, dom.retainedSize(x));
        assertEquals(10, dom.retainedSize(y));
        assertEquals(121, dom.retainedSize(r));
    }

    @Test
    void cyclesTerminateAndRetain() {
        // root -> a <-> b, b -> c, c -> b (cycle between b and c).
        Graph.Builder b = builder();
        int r = add(b, 1, 1);
        int a = add(b, 2, 2);
        int c1 = add(b, 3, 4);
        int c2 = add(b, 4, 8);
        b.markRoot(r);
        edge(b, r, a);
        edge(b, a, c1);
        edge(b, c1, c2);
        edge(b, c2, c1);
        DominatorAnalysis dom = DominatorAnalysis.compute(b.build());
        assertEquals(a, dom.immediateDominator(c1));
        assertEquals(c1, dom.immediateDominator(c2));
        assertEquals(15, dom.retainedSize(r));
        assertEquals(12, dom.retainedSize(c1));
    }

    @Test
    void multipleRootsShareVirtualRoot() {
        // two roots both point at s: s's idom is the virtual root.
        Graph.Builder b = builder();
        int r1 = add(b, 1, 1);
        int r2 = add(b, 2, 1);
        int s = add(b, 3, 50);
        b.markRoot(r1);
        b.markRoot(r2);
        edge(b, r1, s);
        edge(b, r2, s);
        DominatorAnalysis dom = DominatorAnalysis.compute(b.build());
        assertEquals(-1, dom.immediateDominator(s));
        assertEquals(1, dom.retainedSize(r1));
        assertEquals(1, dom.retainedSize(r2));
        assertEquals(50, dom.retainedSize(s));
    }

    @Test
    void unreachableMarkedAndExcludedFromRanking() {
        Graph.Builder b = builder();
        int r = add(b, 1, 5);
        int u = add(b, 2, 999);
        b.markRoot(r);
        DominatorAnalysis dom = DominatorAnalysis.compute(b.build());
        assertTrue(dom.isReachable(r));
        assertFalse(dom.isReachable(u));
        List<Integer> ranking = dom.reachableNodesByRetainedDesc();
        assertEquals(List.of(r), ranking);
    }
}
