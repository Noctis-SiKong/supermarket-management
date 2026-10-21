package nz.ac.aut.pdc.supermarket.cui;

import java.util.Objects;

/** Uniform menu feedback: outcome and presentation rendered in the current language.
  */
public record OperationFeedback(boolean success, String message, nz.ac.aut.pdc.supermarket.i18n.Texts.Display display) {

    public OperationFeedback(boolean success, String message) { this(success, message, null); }

    public String render(nz.ac.aut.pdc.supermarket.i18n.Texts texts) {
        if (display != null) return display.render(texts);
        return success ? texts.text("feedback.success") : texts.text("feedback.rejected", texts.inputError(message));
    }

    public static OperationFeedback shown(boolean success, nz.ac.aut.pdc.supermarket.i18n.Texts.Display display) {
        return new OperationFeedback(success, display.render(new nz.ac.aut.pdc.supermarket.i18n.Texts("en")), display);
    }

    public OperationFeedback {
        Objects.requireNonNull(message, "message");
    }

    public static OperationFeedback ok(String message) {
        return new OperationFeedback(true, message);
    }

    public static OperationFeedback fail(String message) {
        return new OperationFeedback(false, message);
    }
}
