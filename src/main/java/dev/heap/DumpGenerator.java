package dev.heap;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a known object graph and writes a real HotSpot HPROF via the
 * platform MXBean. Used by tests and by the README demo.
 */
public final class DumpGenerator {

    static final class CacheNode {
        CacheNode next;
        byte[] payload;
        String[] tags;

        CacheNode(int payloadSize, int tagCount) {
            this.payload = new byte[payloadSize];
            this.tags = new String[tagCount];
            for (int i = 0; i < tagCount; i++) {
                this.tags[i] = "tag-" + i;
            }
        }
    }

    /** Static root: target of a class static reference. */
    static final List<CacheNode> CACHE = new ArrayList<>();

    static {
        CacheNode head = new CacheNode(64 * 1024, 4);
        CACHE.add(head);
        CacheNode cur = head;
        for (int i = 0; i < 5; i++) {
            cur.next = new CacheNode(32 * 1024, 2);
            cur = cur.next;
        }
    }

    private DumpGenerator() {
    }

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "demo.hprof";
        dump(out);
        System.out.println("wrote " + out);
    }

    public static void dump(String path) throws Exception {
        HotSpotDiagnosticMXBean mx = ManagementFactory.getPlatformMXBean(
                HotSpotDiagnosticMXBean.class);
        mx.dumpHeap(path, true);
    }
}
