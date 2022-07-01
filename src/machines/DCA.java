package machines;

import javafx.scene.paint.Color;
import machines.parser.MachineParser;
import machines.parser.MachineParserSettings;
import machines.parser.ParseVerdict;
import misc.Colors;

import java.util.*;
import java.util.stream.Collectors;

import static machines.convert.ImmutableFunctions.subArray;

public class DCA extends Machine {

    private static final int DEFAULT_COUNTERS = 1;
    private static final String DEFAULT_START = "START";
    private static final String DEFAULT_REJECT = "no transition";
    private static final String DEFAULT_EPS = "eps";

    private static final ArrayList<String> ARGS = new ArrayList<>(List.of("=", ">"));
    private static final ArrayList<String> RES = new ArrayList<>(List.of("+1", "0", "-1"));

    private MachineParser parser;

    private int counters = DEFAULT_COUNTERS;
    private String startState = DEFAULT_START;
    private String eps = DEFAULT_EPS;
    private TreeSet<String> acceptStates = new TreeSet<>();
    private Transitions transitions = new Transitions();

    private String currentState;
    private ArrayList<String> input;
    private int inputPtr;
    private long[] counter;

    private boolean executionFinished = false;

    public static DCA with(int counters, String startState, Set<String> acceptStates, String eps, Transitions transitions) {
        DCA dca = new DCA();
        dca.counters = counters;
        dca.startState = startState;
        dca.acceptStates = new TreeSet<>(acceptStates);
        dca.eps = eps;
        dca.transitions = transitions;
        return dca;
    }



    public DCA() {
        parser = new MachineParser();
        parser.addSettings(new MachineParserSettings("counters", this::parseCounters));
        parser.addSettings(new MachineParserSettings("start", (String[] args) -> startState = args[1]));
        parser.addSettings(new MachineParserSettings("eps", (String[] args) -> eps = args[1]));
        parser.addSettingsNoReq(new MachineParserSettings("accept", (String[] args) -> acceptStates.addAll(Arrays.asList(args).subList(1, args.length))));
        parser.addSettingsChecker(() -> MachineParser.automatonEmptyAcceptSet(this::getAcceptStates));
        parser.setMain(this::parseTransition);
        parser.setFromStateExtractor(args -> args[0]);
        parser.setToStateExtractor(args -> args[counters + 3]);
        parser.setTransitionExtractor(args -> new TransitionArgument(args[0], subArray(args, 1, counters + 2)));
    }

    public MachineParser getParser() {
        return parser;
    }

    private ParseVerdict parseCounters(String val) {
        int ln = parser.getLine();
        try {
            counters = Integer.parseInt(val);
            if (counters < 0)
                return ParseVerdict.error(String.format("Number of counters must be a non-negative integer (got %d)", counters), ln);
            if (counters == 0)
                return ParseVerdict.warning(String.format("Line %s: DCA with no counters. Probably a DFA should be used instead", ln));
        } catch (NumberFormatException e) {
            return ParseVerdict.error(String.format("Unable to parse number of counters: %s", val), ln);
        }
        return ParseVerdict.OK;
    }

    private ParseVerdict parseTransition(String[] args) {
        ParseVerdict verdict = new ParseVerdict();
        if (verdict.merge(parser.assertArgsCnt(2 * counters + 4).apply(args)))
            return verdict;
        if (verdict.merge(parser.assertArgEquals(counters + 2, "->").apply(args)))
            return verdict;

        String fromState = args[0];
        String toState = args[counters + 3];
        String[] fromSymbols = subArray(args, 1, counters + 2);
        String[] toSymbols = subArray(args, counters + 4, 2 * counters + 4);
        for (int i = 0; i < counters; i++) {
            if (parseArg(fromSymbols[i + 1]) == Arg.NULL)
                return verdict.putError(String.format("Line %d, counter %d: invalid argument '%s', expected %s",
                        parser.getLine(), i + 1, fromSymbols[i + 1],
                        ARGS.stream().map(s -> "'" + s + "'").collect(Collectors.joining(" or "))));
            if (parseResult(toSymbols[i]) == Result.NULL)
                return verdict.putError(String.format("Line %d, counter %d: invalid result action '%s', expected %s",
                        parser.getLine(), i + 1, toSymbols[i],
                        RES.stream().map(s -> "'" + s + "'").collect(Collectors.joining(" or "))));
        }

        for (int i = 0; i < counters; i++) {
            Arg arg = parseArg(fromSymbols[i + 1]);
            Result res = parseResult(toSymbols[i]);
            if (arg == Arg.ZERO && res == Result.DEC)
                return verdict.putError(String.format("line %d, counter %d: forbidden behavior, cannot decrement a zero counter",
                        parser.getLine(), i + 1));
        }
        if (fromSymbols[0].length() != 1 && !fromSymbols[0].equals(eps)) {
            verdict.putWarning(String.format(
                    "Line %d: '%s' is multi-character but input parser at Execute tab splits input into one-character entities",
                    parser.getLine(), fromSymbols[0])
            );
        }

        transitions.set(fromState, fromSymbols, toState, toSymbols);
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

    public String getEps() {
        return eps;
    }

    public Transitions getTransitions() {
        return transitions;
    }



    public boolean isInStartState() {
        return currentState.equals(startState) && inputPtr == 0;
    }

    public boolean isInAcceptState() {
        return executionFinished && acceptStates.contains(currentState) && inputPtr == input.size();
    }

    public boolean isInRejectState() {
        return executionFinished && !isInAcceptState();
    }

    public boolean isInTerminalState() {
        return executionFinished;
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
            s.add(tr.getKey().getSymbols()[0]);
        return s;
    }



    public void init(String input) {
        currentState = startState;
        this.input = input.chars()
                .mapToObj(n -> Character.toString((char) n))
                .collect(Collectors.toCollection(ArrayList::new));
        inputPtr = 0;
        counter = new long[counters];
        executionFinished = false;
    }

    private TransitionResult step(TransitionArgument arg) {
        TransitionResult res = transitions.get(arg);
        if (res != null) {
            if (!arg.getSymbols()[0].equals(eps))
                inputPtr++;
            return res;
        }

        String[] epsSyms = new String[counters + 1];
        epsSyms[0] = eps;
        System.arraycopy(arg.getSymbols(), 1, epsSyms, 1, counters);
        res = transitions.get(arg.getState(), epsSyms);
        if (res != null) return res;

        executionFinished = true;
        return inputPtr == input.size()? defaultResult(arg.getState()) : defaultRejectResult();
    }

    public void makeStep() {
        if (executionFinished)
            return;
        TransitionArgument arg = new TransitionArgument(currentState, getCounterArgs());
        TransitionResult res = step(arg);
        currentState = res.getState();
        for (int i = 0; i < counters; i++) {
            switch (parseResult(res.getSymbols()[i])) {
                case INC:
                    counter[i]++;
                    break;
                case ZERO:
                    break;
                case DEC:
                    counter[i]--;
                    break;
                default:
                    throw new IllegalStateException();
            }
        }
    }



    private String[] getCounterArgs() {
        String[] args = new String[counters + 1];
        args[0] = inputPtr == input.size()? eps : input.get(inputPtr);
        for (int i = 0; i < counters; i++)
            args[i + 1] = ARGS.get(counter[i] == 0? 0 : 1);
        return args;
    }

    private TransitionResult defaultResult(String state) {
        String[] zeros = new String[counters];
        Arrays.fill(zeros, RES.get(1));
        return new TransitionResult(state, zeros);
    }

    private TransitionResult defaultRejectResult() {
        return defaultResult(DEFAULT_REJECT);
    }



    public int tapes() {
        return counters + 1;
    }

    public int getTapeSize(int tape) {
        return tape == 0? input.size() + 1 : Long.toString(counter[tape - 1]).length();
    }

    public String getTapeContent(int tape, int i) {
        if (tape == 0) {
            return i < input.size()? input.get(i) : "";
        } else {
            return Character.toString(Long.toString(counter[tape - 1]).charAt(i));
        }
    }

    public Color getTapeContentColor(int tape, int i) {
        if (tape == 0) {
            if (input.isEmpty())
                return Colors.EXE_BLANK;
            else
                return i == input.size()? Colors.EXE_NONE : Colors.EXE_DEFAULT;
        } else {
            return counter[tape - 1] == 0? Colors.EXE_LIME : Colors.EXE_LIGHTER_LIME;
        }
    }

    public boolean getTapeContentPointer(int tape, int i) {
        return tape == 0 && i == inputPtr;
    }



    private enum Arg {
        ZERO, POS, NULL
    }

    private enum Result {
        INC, ZERO, DEC, NULL
    }

    private Arg parseArg(String arg) {
        switch (ARGS.indexOf(arg)) {
            case 0:
                return Arg.ZERO;
            case 1:
                return Arg.POS;
            default:
                return Arg.NULL;
        }
    }

    private Result parseResult(String res) {
        switch (RES.indexOf(res)) {
            case 0:
                return Result.INC;
            case 1:
                return Result.ZERO;
            case 2:
                return Result.DEC;
            default:
                return Result.NULL;
        }
    }



    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (counters != DEFAULT_COUNTERS) sb.append("counters: ").append(counters).append("\n");
        if (!startState.equals(DEFAULT_START)) sb.append("start: ").append(startState).append("\n");
        if (!acceptStates.isEmpty()) sb.append("accept: ").append(String.join(" ", acceptStates)).append("\n");
        if (!eps.equals(DEFAULT_EPS)) sb.append("eps: ").append(eps).append("\n");;

        ArrayList<TransitionArgument> args = new ArrayList<>(transitions.args());
        Collections.sort(args);
        String lastState = null;
        for (TransitionArgument arg : args) {
            if (!arg.getState().equals(lastState))
                sb.append("\n");
            lastState = arg.getState();
            TransitionResult res = transitions.get(arg);
            sb.append(arg.getState()).append(' ').append(String.join(" ", arg.getSymbols())).append(" -> ")
                    .append(res.getState()).append(res.getSymbols().length == 0? "" : " ")
                    .append(String.join(" ", res.getSymbols())).append('\n');
        }
        return sb.toString();
    }

}