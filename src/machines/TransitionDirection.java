package machines;

import java.text.ParseException;

public enum TransitionDirection {

    RIGHT,
    LEFT,
    STAY;

    static TransitionDirection parse(String s, int lineN) throws ParseException {
        switch (s) {
            case ">":
                return RIGHT;
            case "<":
                return LEFT;
            case "^":
                return STAY;
            default:
                throw new ParseException(String.format("Line %d: Invalid transition direction '%s' (expected '>', '<' or '^')", lineN, s), lineN);
        }
    }

    public TransitionDirection reverse() {
        switch (this) {
            case RIGHT:
                return LEFT;
            case LEFT:
                return RIGHT;
            case STAY:
                return STAY;
            default:
                throw new AssertionError();
        }
    }

    @Override
    public String toString() {
        switch (this) {
            case RIGHT:
                return ">";
            case LEFT:
                return "<";
            case STAY:
                return "^";
            default:
                throw new AssertionError();
        }
    }

}