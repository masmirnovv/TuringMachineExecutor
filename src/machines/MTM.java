package machines;

import javafx.scene.paint.Color;
import machines.parser.MachineParser;
import machines.parser.MachineParserSettings;
import machines.parser.ParseVerdict;
import misc.Colors;

import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

import static machines.convert.ImmutableFunctions.subArray;

public class MTM extends Machine {

    private static final int DEFAULT_TAPES = 2;
    private static final String DEFAULT_START = "START";
    private static final String DEFAULT_ACCEPT = "ACCEPT";
    private static final String DEFAULT_REJECT = "REJECT";
    private static final String DEFAULT_BLANK = "_";

    private MachineParser parser;

    private int tapes = DEFAULT_TAPES;
    private String startState = DEFAULT_START;
    private String acceptState = DEFAULT_ACCEPT;
    private String rejectState = DEFAULT_REJECT;
    private String blankSymbol = DEFAULT_BLANK;
    private Transitions transitions = new Transitions();

    private String currentState;
    private LinkedList<String>[] currentBefore;
    private LinkedList<String>[] currentAfter;

    public static MTM with(
            int tapes, String startState, String acceptState, String rejectState, String blankSymbol,
            Transitions transitions) {
        MTM m = new MTM();
        m.tapes = tapes;
        m.startState = startState;
        m.acceptState = acceptState;
        m.rejectState = rejectState;
        m.blankSymbol = blankSymbol;
        m.transitions = transitions;
        return m;
    }



    public MTM() {
        parser = new MachineParser();
        parser.addSettings(new MachineParserSettings("tapes", this::parseTapes));
        parser.addSettings(new MachineParserSettings("start", (String[] args) -> startState = args[1]));
        parser.addSettings(new MachineParserSettings("accept", (String[] args) -> acceptState = args[1]));
        parser.addSettings(new MachineParserSettings("reject", (String[] args) -> rejectState = args[1]));
        parser.addSettings(new MachineParserSettings("blank", (String[] args) -> blankSymbol = args[1]));
        parser.addSettingsChecker(() -> MachineParser.assertSettingsEquals(this::getAcceptState, "accept state", this::getRejectState, "reject state"));
        parser.addSettingsChecker(() -> MachineParser.assertSettingsEquals(this::getStartState, "start state", this::getAcceptState, "accept state"));
        parser.addSettingsChecker(() -> MachineParser.assertSettingsEquals(this::getStartState, "start state", this::getRejectState, "reject state"));
        parser.setMain(args -> parseTransition(parser, args));
        parser.setFromStateExtractor(args -> args[0]);
        parser.setToStateExtractor(args -> args[tapes + 2]);
        parser.setTransitionExtractor(args -> new TransitionArgument(args[0], subArray(args, 1, tapes + 1)));
        parser.addPostChecker(() -> parser.checkBasicReachability(startState, acceptState, rejectState));
        parser.addPostChecker(this::checkMinorTapesSymbols);
    }

    MachineParser getParser() {
        return parser;
    }

    private ParseVerdict parseTapes(String val) {
        int ln = parser.getLine();
        try {
            tapes = Integer.parseInt(val);
            if (tapes < 1)
                return ParseVerdict.error(String.format("Number of tapes must be a positive integer (got %d)", tapes), ln);
            if (tapes == 1)
                return ParseVerdict.warning(String.format("Line %s: multitape TM with one tape. Probably should be interpreted as standard Turing machine", ln));
        } catch (NumberFormatException e) {
            return ParseVerdict.error(String.format("Unable to parse number of tapes: %s", val), ln);
        }
        return ParseVerdict.OK;
    }

    private ParseVerdict parseTransition(MachineParser parser, String[] args) {
        int argsCnt = 3 + 3 * tapes;

        ParseVerdict verdict = new ParseVerdict();
        if (verdict.merge(parser.assertArgsCnt(argsCnt).apply(args)))
            return verdict;
        if (verdict.merge(parser.assertArgEquals(tapes + 1, "->").apply(args)))
            return verdict;

        String fromState = args[0];
        String toState = args[tapes + 2];
        String[] fromSymbols = new String[tapes];
        String[] toSymbols = new String[tapes];
        TransitionDirection[] dirs = new TransitionDirection[tapes];
        for (int i = 0; i < tapes; i++) {
            fromSymbols[i] = args[1 + i];
            toSymbols[i] = args[tapes + 3 + 2 * i];
            try {
                dirs[i] = TransitionDirection.parse(args[tapes + 4 + 2 * i], parser.getLine());
            } catch (ParseException ex) {
                return verdict.putError(ex);
            }
        }
        if (fromState.equals(acceptState))
            verdict.putWarning(String.format("Line %s: transition from accept state", parser.getLine()));
        else if (fromState.equals(rejectState))
            verdict.putWarning(String.format("Line %s: transition from reject state", parser.getLine()));
        transitions.set(fromState, fromSymbols, toState, toSymbols, dirs);
        return verdict;
    }

    private ParseVerdict checkMinorTapesSymbols() {
        ParseVerdict verdict = new ParseVerdict();
        for (int tape = 1; tape < tapes; tape++) {
            HashSet<String> readSym = new HashSet<>(), writtenSym = new HashSet<>();
            for (Map.Entry<TransitionArgument, TransitionResult> entry : transitions.flatEntries()) {
                readSym.add(entry.getKey().getSymbols()[tape]);
                writtenSym.add(entry.getValue().getSymbols()[tape]);
            }
            for (String sym : readSym) {
                if (!sym.equals(blankSymbol) && !writtenSym.contains(sym))
                    verdict.putWarning(String.format("Tape %d: symbol '%s' is read but never written", tape + 1, sym));
            }
        }
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
        HashSet<String> s = new HashSet<>(Set.of(blankSymbol));
        for (Map.Entry<TransitionArgument, TransitionResult> tr : transitions.flatEntries()) {
            s.addAll(Arrays.asList(tr.getKey().getSymbols()));
            s.addAll(Arrays.asList(tr.getValue().getSymbols()));
        }
        return s;
    }

    public Set<String> getSymbolsSet(int tape) {
        HashSet<String> s = new HashSet<>(Set.of(blankSymbol));
        for (Map.Entry<TransitionArgument, TransitionResult> tr : transitions.flatEntries()) {
            s.add(tr.getKey().getSymbols()[tape]);
            s.add(tr.getValue().getSymbols()[tape]);
        }
        return s;
    }



    public void init(String input) {
        currentState = startState;
        currentBefore = new LinkedList[tapes];
        currentAfter = new LinkedList[tapes];
        for (int i = 0; i < tapes; i++) {
            currentBefore[i] = new LinkedList<>();
            currentBefore[i].addFirst(blankSymbol);
            if (i == 0) {
                currentAfter[i] = input.chars()
                        .mapToObj(n -> Character.toString((char) n))
                        .collect(Collectors.toCollection(LinkedList::new));
                if (currentAfter[i].isEmpty() || !currentAfter[i].getLast().equals(blankSymbol))
                    currentAfter[i].addLast(blankSymbol);
            } else {
                currentAfter[i] = new LinkedList<>();
                currentAfter[i].addLast(blankSymbol);
            }
        }
    }

    private TransitionResult step(TransitionArgument arg) {
        TransitionResult result = transitions.get(arg);
        return Objects.requireNonNullElseGet(result, () -> defaultRejectResult(arg.getSymbols()));
    }

    public void makeStep() {
        if (isInTerminalState())
            return;
        TransitionArgument arg = new TransitionArgument(currentState, currentSymbols());
        TransitionResult result = step(arg);
        currentState = result.getState();
        for (int i = 0; i < tapes; i++) {
            currentAfter[i].removeFirst();
            currentAfter[i].addFirst(result.getSymbols()[i]);
            switch (result.getDirections()[i]) {
                case RIGHT:
                    currentBefore[i].addLast(currentAfter[i].removeFirst());
                    if (currentAfter[i].isEmpty())
                        currentAfter[i].add(blankSymbol);
                    else if (currentBefore[i].size() > 1 &&
                            currentBefore[i].getFirst().equals(blankSymbol) &&
                            currentBefore[i].get(1).equals(blankSymbol))
                        currentBefore[i].removeFirst();
                    break;
                case LEFT:
                    currentAfter[i].addFirst(currentBefore[i].removeLast());
                    if (currentBefore[i].isEmpty())
                        currentBefore[i].add(blankSymbol);
                    /* falls */
                case STAY:
                    if (currentAfter[i].size() > 1 &&
                            currentAfter[i].getLast().equals(blankSymbol) &&
                            currentAfter[i].get(currentAfter[i].size() - 2).equals(blankSymbol))
                        currentAfter[i].removeLast();
                    else if (!currentAfter[i].getLast().equals(blankSymbol))
                        currentAfter[i].addLast(blankSymbol);
                    break;
            }
        }
    }



    public int tapes() {
        return tapes;
    }

    public int getTapeSize(int tape) {
        return currentBefore[tape].size() + currentAfter[tape].size();
    }

    public String getTapeContent(int tape, int i) {
        String content = getTapeContent0(tape, i);
        return content.equals(blankSymbol)? "" : content;
    }

    public Color getTapeContentColor(int tape, int i) {
        return getTapeContent0(tape, i).equals(blankSymbol)? Colors.EXE_BLANK : Colors.EXE_DEFAULT;
    }

    public boolean getTapeContentPointer(int tape, int i) {
        return i == currentBefore[tape].size();
    }

    private String getTapeContent0(int tape, int i) {
        return i < currentBefore[tape].size()? currentBefore[tape].get(i) : currentAfter[tape].get(i - currentBefore[tape].size());
    }


    private String[] currentSymbols() {
        String[] syms = new String[tapes];
        for (int i = 0; i < tapes; i++)
            syms[i] = currentAfter[i].getFirst();
        return syms;
    }

    private TransitionResult defaultRejectResult(String[] symbols) {
        TransitionDirection[] stay = new TransitionDirection[tapes];
        Arrays.fill(stay, TransitionDirection.STAY);
        return new TransitionResult(rejectState, symbols, stay);
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (tapes != DEFAULT_TAPES) sb.append("tapes: ").append(tapes).append("\n");
        if (!startState.equals(DEFAULT_START)) sb.append("start: ").append(startState).append("\n");
        if (!acceptState.equals(DEFAULT_ACCEPT)) sb.append("accept: ").append(acceptState).append("\n");
        if (!rejectState.equals(DEFAULT_REJECT)) sb.append("reject: ").append(rejectState).append("\n");
        if (!blankSymbol.equals(DEFAULT_BLANK)) sb.append("blank: ").append(blankSymbol).append("\n");

        ArrayList<TransitionArgument> args = new ArrayList<>(transitions.args());
        Collections.sort(args);
        String lastState = null;
        for (TransitionArgument arg : args) {
            if (!arg.getState().equals(lastState))
                sb.append("\n");
            lastState = arg.getState();
            TransitionResult res = transitions.get(arg);

            sb.append(arg.getState());
            for (String fromSym : arg.getSymbols())
                sb.append(' ').append(fromSym);
            sb.append(" -> ").append(res.getState());
            for (int i = 0; i < tapes; i++)
                sb.append(' ').append(res.getSymbols()[i]).append(' ').append(res.getDirections()[i]);
            sb.append('\n');
        }
        return sb.toString();
    }

}