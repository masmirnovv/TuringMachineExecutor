package machines;

import javafx.scene.paint.Color;
import machines.parser.MachineParser;
import machines.parser.MachineParserSettings;
import machines.parser.ParseVerdict;
import misc.Colors;
import misc.Graph;

import java.util.*;
import java.util.stream.Collectors;

public class NFA extends Machine {

    private static final String DEFAULT_START = "START";
    private static final String DEFAULT_EPS = "eps";

    private MachineParser parser;

    private String startState = DEFAULT_START;
    private String eps = DEFAULT_EPS;
    private TreeSet<String> acceptStates = new TreeSet<>();
    private Transitions transitions = new Transitions();

    private TreeSet<String> currentStates;
    private Graph<String> epsGraph;
    private ArrayList<String> input;
    private int ptr;

    public static NFA with(String startState, String eps, Set<String> acceptStates, Transitions transitions) {
        NFA m = new NFA();
        m.startState = startState;
        m.eps = eps;
        m.acceptStates = new TreeSet<>(acceptStates);
        m.transitions = transitions;
        return m;
    }



    public NFA() {
        parser = new MachineParser();
        parser.setNondeterministic();
        parser.addSettings(new MachineParserSettings("start", (String[] args) -> startState = args[1]));
        parser.addSettings(new MachineParserSettings("eps", (String[] args) -> eps = args[1]));
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
        if (fromState.equals(toState) && fromSymbol.equals(eps))
            verdict.putWarning(String.format("Line %d: cyclic eps-transition", parser.getLine()));
        if (fromSymbol.length() != 1 && !fromSymbol.equals(eps))
            verdict.putWarning(String.format(
                    "Line %d: '%s' is multi-character but input parser at Execute tab splits input into one-character entities",
                    parser.getLine(), fromSymbol)
            );
        transitions.add(fromState, fromSymbol, toState);
        return verdict;
    }



    public String getCurrentState() {
        return String.join(", ", currentStates);
    }

    public String getStartState() {
        return startState;
    }

    public Set<String> getAcceptStates() {
        return acceptStates;
    }

    public String getEps() {
        return eps;
    }

    public Transitions getTransitions() {
        return transitions;
    }



    public boolean isInStartState() {
        return ptr == 0;
    }

    public boolean isInAcceptState() {
        return ptr == input.size() && acceptAndCurrentIntersects();
    }

    public boolean isInRejectState() {
        return ptr == input.size() && !acceptAndCurrentIntersects();
    }

    public boolean isInTerminalState() {
        return ptr == input.size() || currentStates.isEmpty();
    }

    private boolean acceptAndCurrentIntersects() {
        for (String ac : acceptStates) {
            if (currentStates.contains(ac))
                return true;
        }
        return false;
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
        HashSet<String> s = new HashSet<>(Set.of(eps));
        for (Map.Entry<TransitionArgument, TransitionResult> tr : transitions.flatEntries())
            s.add(tr.getKey().getSymbol());
        return s;
    }



    public void init(String input) {
        buildEpsGraph();
        currentStates = new TreeSet<>();
        currentStates.addAll(epsGraph.bfs(startState));
        this.input = new ArrayList<>();
        for (char c : input.toCharArray())
            this.input.add(Character.toString(c));
        ptr = 0;
    }

    public Graph<String> buildEpsGraph() {
        epsGraph = new Graph<>();
        epsGraph.addVertices(startState);
        for (Map.Entry<TransitionArgument, TransitionResult> tr : transitions.flatEntries()) {
            epsGraph.addVertices(tr.getKey().getState(), tr.getValue().getState());
            if (tr.getKey().getSymbol().equals(eps))
                epsGraph.addEdge(tr.getKey().getState(), tr.getValue().getState());
        }
        return epsGraph;
    }

    public void makeStep() {
        if (isInTerminalState())
            return;
        TreeSet<String> curStates = new TreeSet<>(currentStates);
        currentStates = new TreeSet<>();
        String curSymbol = input.get(ptr++);
        for (String curState : curStates) {
            List<String> newStates = transitions.getAll(curState, curSymbol).stream()
                    .map(TransitionResult::getState).collect(Collectors.toList());
            currentStates.addAll(epsGraph.bfs(newStates));
        }
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
        if (!eps.equals(DEFAULT_EPS)) sb.append("eps: ").append(eps).append("\n");

        ArrayList<TransitionArgument> args = new ArrayList<>(transitions.args());
        Collections.sort(args);
        String lastState = null;
        for (TransitionArgument arg : args) {
            if (!arg.getState().equals(lastState))
                sb.append("\n");
            lastState = arg.getState();
            List<TransitionResult> results = transitions.getAll(arg);
            for (TransitionResult res : results)
                sb.append(arg.getState()).append(' ').append(arg.getSymbol()).append(" -> ").append(res.getState()).append('\n');
        }
        return sb.toString();
    }

}
