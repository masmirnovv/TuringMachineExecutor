package machines;

import javafx.scene.paint.Color;
import machines.parser.MachineParser;
import machines.parser.MachineParserSettings;
import machines.parser.ParseVerdict;
import misc.Colors;

import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

public class OneTM extends Machine {

    private static final String DEFAULT_START = "START";
    private static final String DEFAULT_ACCEPT = "ACCEPT";
    private static final String DEFAULT_REJECT = "REJECT";
    private static final String DEFAULT_BLANK = "_";
    private static final String DEFAULT_BOUND = "BD";

    private MachineParser parser;

    private String startState = DEFAULT_START;
    private String acceptState = DEFAULT_ACCEPT;
    private String rejectState = DEFAULT_REJECT;
    private String blankSymbol = DEFAULT_BLANK;
    private String leftBound = DEFAULT_BOUND;
    private Transitions transitions = new Transitions();

    private String currentState;
    private LinkedList<String> currentBefore;
    private LinkedList<String> currentAfter;

    public static OneTM with(
            String startState, String acceptState, String rejectState, String blankSymbol, String leftBound,
            Transitions transitions
    ) {
        OneTM m = new OneTM();
        m.startState = startState;
        m.acceptState = acceptState;
        m.rejectState = rejectState;
        m.blankSymbol = blankSymbol;
        m.leftBound = leftBound;
        m.transitions = transitions;
        return m;
    }



    public OneTM() {
        parser = new MachineParser();
        parser.addSettings(new MachineParserSettings("start", (String[] args) -> startState = args[1]));
        parser.addSettings(new MachineParserSettings("accept", (String[] args) -> acceptState = args[1]));
        parser.addSettings(new MachineParserSettings("reject", (String[] args) -> rejectState = args[1]));
        parser.addSettings(new MachineParserSettings("blank", (String[] args) -> blankSymbol = args[1]));
        parser.addSettings(new MachineParserSettings("left-bound", (String[] args) -> leftBound = args[1]));
        parser.addSettingsChecker(() -> MachineParser.assertSettingsEquals(this::getAcceptState, "accept state", this::getRejectState, "reject state"));
        parser.addSettingsChecker(() -> MachineParser.assertSettingsEquals(this::getStartState, "start state", this::getAcceptState, "accept state"));
        parser.addSettingsChecker(() -> MachineParser.assertSettingsEquals(this::getStartState, "start state", this::getRejectState, "reject state"));
        parser.addSettingsChecker(() -> MachineParser.assertSettingsEquals(this::getBlank, "blank symbol", this::getBound, "left bound symbol"));
        parser.setMain(this::parseTransition);
        parser.setFromStateExtractor(args -> args[0]);
        parser.setToStateExtractor(args -> args[3]);
        parser.setTransitionExtractor(args -> new TransitionArgument(args[0], args[1]));
        parser.addPostChecker(() -> parser.checkBasicReachability(startState, acceptState, rejectState));
    }

    MachineParser getParser() {
        return parser;
    }

    private ParseVerdict parseTransition(String[] args) {
        int ln = parser.getLine();

        ParseVerdict verdict = new ParseVerdict();
        if (verdict.merge(parser.assertArgsCnt(6).apply(args)))
            return verdict;
        if (verdict.merge(parser.assertArgEquals(2, "->").apply(args)))
            return verdict;

        String fromState = args[0];
        String fromSymbol = args[1];
        String toState = args[3];
        String toSymbol = args[4];
        try {
            TransitionDirection dir = TransitionDirection.parse(args[5], ln);
            if (!fromSymbol.equals(leftBound) && toSymbol.equals(leftBound))
                throw new ParseException(String.format(
                        "Line %d: Forbidden transition. In one-way Turing machines, " +
                        "you can't place a new bound symbol", ln), ln);
            if (fromSymbol.equals(leftBound) && !toSymbol.equals(leftBound))
                throw new ParseException(String.format(
                        "Line %d: Forbidden transition. In one-way Turing machines, " +
                        "you can't remove an existing bound symbol", ln), ln);
            if (fromSymbol.equals(leftBound) && dir.equals(TransitionDirection.LEFT))
                throw new ParseException(String.format(
                        "Line %d: Forbidden transition. In one-way Turing machines, " +
                        "you can't go left while on bound", ln), ln);
            transitions.set(fromState, fromSymbol, toState, toSymbol, dir);
        } catch (ParseException ex) {
            return verdict.putError(ex);
        }
        if (fromState.equals(acceptState))
            verdict.putWarning(String.format("Line %s: transition from accept state", ln));
        else if (fromState.equals(rejectState))
            verdict.putWarning(String.format("Line %s: transition from reject state", ln));
        return verdict;
    }



    public String getCurrentState() {
        return currentState;
    }

    public String getStartState() {
        return startState;
    }

    public String getAcceptState() {
        return acceptState;
    }

    public String getRejectState() {
        return rejectState;
    }

    public String getBlank() {
        return blankSymbol;
    }

    public String getBound() {
        return leftBound;
    }

    public Transitions getTransitions() {
        return transitions;
    }



    public Set<String> getStatesSet() {
        HashSet<String> s = new HashSet<>(Set.of(startState, acceptState, rejectState));
        for (Map.Entry<TransitionArgument, TransitionResult> tr : transitions.flatEntries()) {
            s.add(tr.getKey().getState());
            s.add(tr.getValue().getState());
        }
        return s;
    }

    public Set<String> getSymbolsSet() {
        HashSet<String> s = new HashSet<>(Set.of(blankSymbol, leftBound));
        for (Map.Entry<TransitionArgument, TransitionResult> tr : transitions.flatEntries()) {
            s.add(tr.getKey().getSymbol());
            s.add(tr.getValue().getSymbol());
        }
        return s;
    }



    public void init(String input) {
        currentState = startState;
        currentBefore = new LinkedList<>();
        currentBefore.addFirst(leftBound);
        currentAfter = input.chars()
                .mapToObj(n -> Character.toString((char) n))
                .collect(Collectors.toCollection(LinkedList::new));
        if (currentAfter.isEmpty() || !currentAfter.getLast().equals(blankSymbol))
            currentAfter.addLast(blankSymbol);
    }

    public TransitionResult step(TransitionArgument arg) {
        TransitionResult result = transitions.get(arg);
        return Objects.requireNonNullElseGet(result, () -> defaultRejectResult(arg.getSymbol()));
    }

    public void makeStep() {
        if (isInTerminalState())
            return;
        TransitionArgument arg = new TransitionArgument(currentState, currentSymbol());
        TransitionResult result = step(arg);
        currentState = result.getState();
        currentAfter.removeFirst();
        currentAfter.addFirst(result.getSymbol());
        switch (result.getDirection()) {
            case RIGHT:
                currentBefore.addLast(currentAfter.removeFirst());
                if (currentAfter.isEmpty())
                    currentAfter.add(blankSymbol);
                break;
            case LEFT:
                currentAfter.addFirst(currentBefore.removeLast());
                /* falls */
            case STAY:
                if (currentAfter.size() > 1 &&
                        currentAfter.getLast().equals(blankSymbol) &&
                        currentAfter.get(currentAfter.size() - 2).equals(blankSymbol))
                    currentAfter.removeLast();
                else if (!currentAfter.getLast().equals(blankSymbol))
                    currentAfter.addLast(blankSymbol);
                break;
        }
    }



    public int getTapeSize(int tape) {
        return currentBefore.size() + currentAfter.size();
    }

    public String getTapeContent(int tape, int i) {
        String content = getTapeContent0(i);
        return content.equals(leftBound) || content.equals(blankSymbol)? "" : content;
    }

    public Color getTapeContentColor(int tape, int i) {
        String content = getTapeContent0(i);
        return content.equals(leftBound)? Colors.EXE_RED : content.equals(blankSymbol)? Colors.EXE_BLANK : Colors.EXE_DEFAULT;
    }

    public boolean getTapeContentPointer(int tape, int i) {
        return i == currentBefore.size();
    }

    private String getTapeContent0(int i) {
        return i < currentBefore.size()? currentBefore.get(i) : currentAfter.get(i - currentBefore.size());
    }



    private String currentSymbol() {
        return currentAfter.getFirst();
    }

    public TransitionResult defaultRejectResult(String symbol) {
        return new TransitionResult(rejectState, symbol, TransitionDirection.STAY);
    }



    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!startState.equals(DEFAULT_START)) sb.append("start: ").append(startState).append("\n");
        if (!acceptState.equals(DEFAULT_ACCEPT)) sb.append("accept: ").append(acceptState).append("\n");
        if (!rejectState.equals(DEFAULT_REJECT)) sb.append("reject: ").append(rejectState).append("\n");
        if (!blankSymbol.equals(DEFAULT_BLANK)) sb.append("blank: ").append(blankSymbol).append("\n");
        if (!leftBound.equals(DEFAULT_BOUND)) sb.append("left-bound: ").append(leftBound).append("\n");

        ArrayList<TransitionArgument> args = new ArrayList<>(transitions.args());
        Collections.sort(args);
        String lastState = null;
        for (TransitionArgument arg : args) {
            if (!arg.getState().equals(lastState))
                sb.append("\n");
            lastState = arg.getState();
            TransitionResult res = transitions.get(arg);
            sb.append(arg.getState()).append(' ').append(arg.getSymbol()).append(" -> ").append(res.getState())
                    .append(' ').append(res.getSymbol()).append(' ').append(res.getDirection()).append('\n');
        }
        return sb.toString();
    }

}
