package dev.heap;

/** Fatal rejection of an upload or query: corrupt dump, limit exceeded, bad input. */
public class AnalysisException extends Exception {
    private final int status;

    public AnalysisException(int status, String message) {
        super(message);
        this.status = status;
    }

    public AnalysisException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
