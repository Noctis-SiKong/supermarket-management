package nz.ac.aut.pdc.shared;

/** Contract v1.0.2. VALUE/PREPARED never mean a committed business success. */
public record Result<T>(Status status, T value, String code, String field, String recordId, String detail) {
    public enum Status { VALUE, PREPARED, SUCCESS, NOT_FOUND, REJECTED, FAILED }
    public static <T> Result<T> value(T value) { return new Result<>(Status.VALUE, value, null, null, null, null); }
    public static <T> Result<T> prepared(T value) { return new Result<>(Status.PREPARED, value, null, null, null, null); }
    public static <T> Result<T> rejected(String code, String detail) { return new Result<>(Status.REJECTED, null, code, null, null, detail); }
    public static <T> Result<T> failed(String code, String detail) { return new Result<>(Status.FAILED, null, code, null, null, detail); }
    public boolean hasValue() { return status == Status.VALUE || status == Status.PREPARED || status == Status.SUCCESS; }
    public <U> Result<U> failure() {
        if (hasValue()) throw new IllegalStateException("Cannot convert a successful result to failure");
        return new Result<>(status, null, code, field, recordId, detail);
    }
}
