package bullrun;

final class ApiError extends RuntimeException {
    private static final long serialVersionUID = 1L;
    final int code;

    ApiError(int code, String message) {
        super(message, null, false, false);
        this.code = code;
    }
}
