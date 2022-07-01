package machines;

import javafx.scene.paint.Color;
import machines.parser.MachineParser;
import machines.parser.MachineParserSettings;
import machines.parser.ParseVerdict;
import misc.Colors;

import java.util.*;

public class DFA extends Machine {

    private static final String DEFAULT_START = "START";
    private static final String DEFAULT_REJECT = "no transition";

    private MachineParser parser;

    private String startState = DEFAULT_START;
    private TreeSet<String> acceptStates = new TreeSet<>();
    private Transitions transitions = new Transitions();

    private String currentState;
    private ArrayList<String> input;
    private int ptr;

    public static DFA with(String startState, Set<String> acceptStates, Transitions transitions) {
        DFA m = new DFA();
        m.startState = startState;
        m.acceptStates = new TreeSet<>(acceptStates);
        m.transitions = transitions;
        return m;
    }



    public DFA() {
        parser = new MachineParser();
        parser.addSettings(new MachineParserSettings("start", (String[] args) -> startState = args[1]));
        parser.addSettingsNoReq(new MachineParserSettings("accept", (String[] args) -> acceptStates.addAll(Arrays.asList(args).subList(1, args.length))));
        parser.addSettingsChecker(() -> MachineParser.automatonEmptyAcceptSet(this::getAcceptStates));
        parser.setMain(this::parseTransition);
        parser.setFromStateExtractor(args -> args[0]);
        parser.setToStateExtractor(args -> args[3]);
        parser.setTransitionExtractor(args -> new TransitionArgument(args[0], args[1]));
    }

    MachineParser getParser() {
        return parser;
    }

    private ParseVerdict parseTransition(String[] args) {
        ParseVerdict verdict = new ParseVerdict();
        if (verdict.merge(parser.assertArgsCnt(4).apply(args)))
            return verdict;
        if (verdict.merge(parser.assertArgEquals(2, "->").apply(args)))
            return verdict;

        String fromState = args[0];
        String fromSymbol = args[1];
        String toState = args[3];
        if (fromSymbol.length() != 1)
            verdict.putWarning(String.format(
                    "Line %d: '%s' is multi-character but input parser at Execute tab splits input into one-character entities",
                    parser.getLine(), fromSymbol)
            );
        transitions.set(fromState, fromSymbol, toState);
        return verdict;
    }



    public String getCurrentState() {
        return currentState;
    }

    public String getStartState() {
        return startState;
    }

    public Set<String> getAcceptStates() {
        return acceptStates;
    }

    public String getRejectState() {
        return DEFAULT_REJECT;
    }

    public Transitions getTransitions() {
        return transitions;
    }



    public boolean isInStartState() {
        return ptr == 0;
    }

    public boolean isInAcceptState() {
        return ptr == input.size() && acceptStates.contains(currentState);
    }

    public boolean isInRejectState() {
        return currentState.equals(DEFAULT_REJECT) || (ptr == input.size() && !acceptStates.contains(currentState));
    }



    public Set<String> getStatesSet() {
        HashSet<String> s = new HashSet<>(Set.of(startState));
        s.addAll(acceptStates);
        for (Map.Entry<TransitionArgument, TransitionResult> tr : transitions.flatEntries()) {
            s.add(tr.getKey().getState());
            s.add(tr.getValue().getState());
        }
        return s;
    }

    public Set<String> getSymbolsSet() {
        HashSet<String> s = new HashSet<>();
        for (Map.Entry<TransitionArgument, TransitionResult> tr : transitions.flatEntries())
            s.add(tr.getKey().getSymbol());
        return s;
    }



    public void init(String input) {
        currentState = startState;
        this.input = new ArrayList<>();
        for (char c : input.toCharArray())
            this.input.add(Character.toString(c));
        ptr = 0;
    }

    private TransitionResult step(TransitionArgument arg) {
        TransitionResult result = transitions.get(arg);
        return Objects.requireNonNullElseGet(result, () -> new TransitionResult(DEFAULT_REJECT));
    }

    public void makeStep() {
        if (isInTerminalState())
            return;
        TransitionArgument arg = new TransitionArgument(currentState, input.get(ptr++));
        TransitionResult result = step(arg);
        currentState = result.getState();
    }



    public int getTapeSize(int tape) {
        return Math.max(1, input.size());
    }

    public String getTapeContent(int tape, int i) {
        return input.isEmpty()? "" : input.get(i);
    }

    public Color getTapeContentColor(int tape, int i) {
        return input.isEmpty()? Colors.EXE_BLANK : Colors.EXE_DEFAULT;
    }

    public boolean getTapeContentPointer(int tape, int i) {
        return i == ptr;
    }



    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (!startState.equals(DEFAULT_START)) sb.append("start: ").append(startState).append("\n");
        if (!acceptStates.isEmpty()) sb.append("accept: ").append(String.join(" ", acceptStates)).append("\n");

        ArrayList<TransitionArgument> args = new ArrayList<>(transitions.args());
        Collections.sort(args);
        String lastState = null;
        for (TransitionArgument arg : args) {
            if (!arg.getState().equals(lastState))
                sb.append("\n");
            lastState = arg.getState();
            TransitionResult res = transitions.get(arg);
            sb.append(arg.getState()).append(' ').append(arg.getSymbol()).append(" -> ").append(res.getState()).append('\n');
        }
        return sb.toString();
    }

}
