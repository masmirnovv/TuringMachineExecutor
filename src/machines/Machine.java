package machines;

import javafx.scene.paint.Color;
import machines.parser.MachineParser;
import machines.parser.ParseVerdict;

import java.util.Set;

public abstract class Machine {


    abstract MachineParser getParser();

    public ParseVerdict parse(String content) {
        return getParser().parse(content);
    }


    public String getCurrentState() {
        return null;
    }

    public String getStartState() {
        return null;
    }

    public String getAcceptState() {
        return null;
    }

    public String getRejectState() {
        return null;
    }

    public String getBlank() {
        return null;
    }

    public String getBound() {
        return null;
    }

    abstract public Transitions getTransitions();


    public boolean isInStartState() {
        return getCurrentState().equals(getStartState());
    }

    public boolean isInAcceptState() {
        return getCurrentState().equals(getAcceptState());
    }

    public boolean isInRejectState() {
        return getCurrentState().equals(getRejectState());
    }

    public boolean isInTerminalState() {
        return isInAcceptState() || isInRejectState();
    }


    abstract public Set<String> getStatesSet();

    abstract public Set<String> getSymbolsSet();

    public Set<String> getSymbolsSet(int tape) {
        return getSymbolsSet();
    }


    abstract public void init(String input);

    abstract public void makeStep();


    public int branches() {
        return 1;
    }

    public int getCurrentBranch() {
        return 0;
    }

    public void nextBranch() { }

    public void prevBranch() { }


    public int tapes() {
        return 1;
    }

    abstract public int getTapeSize(int tape);

    abstract public String getTapeContent(int tape, int i);

    abstract public Color getTapeContentColor(int tape, int i);

    abstract public boolean getTapeContentPointer(int tape, int i);

}