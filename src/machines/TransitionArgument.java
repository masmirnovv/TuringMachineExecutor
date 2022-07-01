package machines;

import java.util.Arrays;

public class TransitionArgument implements Comparable<TransitionArgument> {

    private String state;
    private String[] symbols;

    public TransitionArgument(String state, String symbol) {
        this.state = state;
        this.symbols = new String[] {symbol};
    }

    public TransitionArgument(String state, String[] symbols) {
        this.state = state;
        this.symbols = symbols;
    }

    public TransitionArgument copy() {
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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TransitionArgument))
            return false;
        TransitionArgument that = (TransitionArgument) o;
        return this.state.equals(that.state) && Arrays.deepEquals(this.symbols, that.symbols);
    }

    @Override
    public int hashCode() {
        int hash = state.hashCode();
        for (String symbol : symbols)
            hash = 12439409 * hash + symbol.hashCode();
        return hash;
    }


    @Override
    public int compareTo(TransitionArgument that) {
        int cmp = this.state.compareTo(that.state);
        if (cmp != 0)
            return cmp;
        for (int i = 0; i < symbols.length; i++) {
            cmp = this.symbols[i].compareTo(that.symbols[i]);
            if (cmp != 0)
                return cmp;
        }
        return 0;
    }

}
