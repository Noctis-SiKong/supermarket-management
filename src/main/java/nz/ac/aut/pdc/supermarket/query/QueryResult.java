package nz.ac.aut.pdc.supermarket.query;

import java.util.Objects;

/** Read-only query outcomes: VALUE, REJECTED or FAILED.
  */
public sealed interface QueryResult<T>
        permits QueryResult.Value, QueryResult.Rejected, QueryResult.Failed {

    record Value<T>(T value) implements QueryResult<T> {
        public Value {
            Objects.requireNonNull(value, "value");
        }
    }

    record Rejected<T>(String code, String message) implements QueryResult<T> {
        public Rejected {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }

    record Failed<T>(String code, String message) implements QueryResult<T> {
        public Failed {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
        }
    }
}
