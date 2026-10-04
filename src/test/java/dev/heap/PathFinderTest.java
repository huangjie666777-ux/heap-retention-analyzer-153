package dev.heap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class PathFinderTest {

    @Test
    void shortestPathUsesRealEdgesWithLabels() {
        Graph.Builder b = new Graph.Builder();
        int r = b.addNode(0x10, "app.Root", 16);
        int mid = b.addNode(0x20, "app.Mid", 16);
        int leaf = b.addNode(0x30, "app.Leaf", 16);
        b.markRoot(r);
        b.addEdge(r, mid, b.labelOf("app.Root.child"));
        b.addEdge(mid, leaf, b.labelOf("[3]"));
        Graph g = b.build();
        List<PathFinder.Step> path = PathFinder.shortestPath(g, g.indexOf(0x30));
        assertEquals(2, path.size());
        assertEquals("app.Root.child", path.get(0).ref());
        assertEquals("[3]", path.get(1).ref());
        assertEquals(0x20, path.get(1).fromId());
    }

    @Test
    void unreachableHasNoPath() {
        Graph.Builder b = new Graph.Builder();
        int r = b.addNode(1, "app.Root", 16);
        int u = b.addNode(2, "app.Lost", 16);
        b.markRoot(r);
        Graph g = b.build();
        assertNull(PathFinder.shortestPath(g, g.indexOf(2)));
    }

    @Test
    void rootHasEmptyPath() {
        Graph.Builder b = new Graph.Builder();
        int r = b.addNode(1, "app.Root", 16);
        b.markRoot(r);
        Graph g = b.build();
        assertEquals(0, PathFinder.shortestPath(g, g.indexOf(1)).size());
    }
}
