package dev.heap;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** HTTP layer: upload, retention ranking, root path, deletion. */
public final class Main {
    private static final long MAX_UPLOAD_BYTES = HprofGraphBuilder.MAX_FILE_BYTES + 1024 * 1024;

    private final AnalysisService service = new AnalysisService();

    public static void main(String[] args) {
        int port = Integer.parseInt(System.getProperty("port", "7070"));
        new Main().start(port);
    }

    public Javalin start(int port) {
        Javalin app = Javalin.create(cfg -> {
            cfg.http.maxRequestSize = MAX_UPLOAD_BYTES;
            cfg.showJavalinBanner = false;
        });
        app.post("/api/analyses", this::upload);
        app.get("/api/analyses/{id}", this::summary);
        app.get("/api/analyses/{id}/retained", this::retained);
        app.get("/api/analyses/{id}/objects/{hexId}", this::object);
        app.get("/api/analyses/{id}/objects/{hexId}/path", this::path);
        app.delete("/api/analyses/{id}", this::delete);
        app.exception(AnalysisException.class, (e, ctx) ->
                ctx.status(e.status()).json(Map.of("error", e.getMessage())));
        app.exception(NotFound.class, (e, ctx) ->
                ctx.status(404).json(Map.of("error", e.getMessage())));
        app.start(port);
        return app;
    }

    private void upload(Context ctx) throws Exception {
        UploadedFile upload = ctx.uploadedFile("file");
        if (upload == null) {
            throw new AnalysisException(400, "multipart field 'file' is required");
        }
        if (upload.size() > HprofGraphBuilder.MAX_FILE_BYTES) {
            throw new AnalysisException(413, "hprof exceeds 100 MiB limit");
        }
        Path temp = Files.createTempFile("hprof-upload-", ".hprof");
        try (InputStream in = upload.content()) {
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            AnalysisService.Created created = service.analyze(temp.toFile());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("analysisId", created.id());
            body.putAll(created.analysis().summary());
            ctx.status(201).json(body);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void summary(Context ctx) {
        Analysis analysis = requireAnalysis(ctx);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("analysisId", ctx.pathParam("id"));
        body.putAll(analysis.summary());
        ctx.json(body);
    }

    private void retained(Context ctx) {
        Analysis analysis = requireAnalysis(ctx);
        int limit = Math.max(1, Math.min(1000, ctx.queryParamAsClass("limit", Integer.class)
                .getOrDefault(50)));
        ctx.json(Map.of("items", analysis.retainedRanking(limit)));
    }

    private void object(Context ctx) {
        Analysis analysis = requireAnalysis(ctx);
        int node = requireNode(ctx, analysis);
        ctx.json(analysis.objectView(node));
    }

    private void path(Context ctx) throws AnalysisException {
        Analysis analysis = requireAnalysis(ctx);
        int node = requireNode(ctx, analysis);
        List<Map<String, Object>> path = analysis.rootPath(node);
        if (path == null) {
            ctx.json(Map.of("reachable", false, "path", List.of()));
            return;
        }
        ctx.json(Map.of("reachable", true, "path", path));
    }

    private void delete(Context ctx) {
        if (!service.delete(ctx.pathParam("id"))) {
            ctx.status(404).json(Map.of("error", "unknown analysisId"));
            return;
        }
        ctx.status(204);
    }

    private Analysis requireAnalysis(Context ctx) {
        Analysis analysis = service.get(ctx.pathParam("id"));
        if (analysis == null) {
            throw new NotFound("unknown analysisId");
        }
        return analysis;
    }

    private int requireNode(Context ctx, Analysis analysis) {
        Long id = HexIds.parse(ctx.pathParam("hexId"));
        Integer node = id == null ? null : analysis.graph().indexOf(id);
        if (node == null) {
            throw new NotFound("unknown object id");
        }
        return node;
    }

    /** 404 marker handled by Javalin's exception mapping. */
    static final class NotFound extends RuntimeException {
        NotFound(String message) {
            super(message);
        }
    }
}
