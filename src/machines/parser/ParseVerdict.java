package machines.parser;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class ParseVerdict {

    private List<ParseException> errors = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public static final ParseVerdict OK = new ParseVerdict();

    public ParseVerdict putError(ParseException error) {
        if (error != null)
            errors.add(error);
        return this;
    }

    public ParseVerdict putError(String error) {
        return putError(new ParseException(error, -1));
    }

    public ParseVerdict putWarning(String warning) {
        if (warning != null)
            warnings.add(warning);
        return this;
    }

    public static ParseVerdict error(String errorMsg, int line) {
        ParseVerdict verdict = new ParseVerdict();
        verdict.putError(new ParseException(errorMsg, line));
        return verdict;
    }

    public static ParseVerdict warning(String warning) {
        ParseVerdict verdict = new ParseVerdict();
        verdict.putWarning(warning);
        return verdict;
    }

    public boolean merge(ParseVerdict another) {
        this.errors.addAll(another.errors);
        this.warnings.addAll(another.warnings);
        return another.hasErrors();
    }

    public List<ParseException> getErrors() {
        return errors;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public void throwFirstError() throws ParseException {
        if (hasErrors())
            throw errors.get(0);
    }
}
