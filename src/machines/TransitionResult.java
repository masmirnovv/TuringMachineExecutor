package machines;

import java.util.Arrays;

public class TransitionResult {

    private String state;
    private String[] symbols;
    private TransitionDirection[] directions;

    public TransitionResult(String state) {
        this.state = state;
        this.symbols = new String[0];
        this.directions = new TransitionDirection[0];
    }

    public TransitionResult(String state, String symbol, TransitionDirection direction) {
        this.state = state;
        this.symbols = new String[] {symbol};
        this.directions = new TransitionDirection[] {direction};
    }

    public TransitionResult(String state, String[] symbols, TransitionDirection[] directions) {
        this.state = state;
        this.symbols = symbols;
        this.directions = directions;
    }

    public TransitionResult(String state, String[] symbols) {
        this(state, symbols, null);
    }

    public TransitionResult copy() {
        return new TransitionResult(state, symbols, directions);
    }

    public TransitionArgument asArgument() {
        return new TransitionArgument(state, symbols);
    }

    public String getState() {
        return state;
    }

    public String getSymbol() {
        return symbols[0];
    }

    public String[] getSymbols() {
        return symbols;
    }

    public TransitionDirection getDirection() {
        return directions[0];
    }

    public TransitionDirection[] getDirections() {
        return directions;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TransitionResult))
            return false;
        TransitionResult that = (TransitionResult) o;
        return this.state.equals(that.state) && Arrays.deepEquals(this.symbols, that.symbols) && Arrays.deepEquals(this.directions, that.directions);
    }

    @Override
    public int hashCode() {
        int hash = state.hashCode();
        for (String symbol : symbols)
            hash = 12439409 * hash + symbol.hashCode();
        if (directions != null) {
            for (TransitionDirection dir : directions)
                hash = 12439409 * hash + (dir == null? 0 : dir.hashCode());
        }
        return hash;
    }

}
