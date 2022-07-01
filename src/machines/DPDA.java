package machines;

import javafx.scene.paint.Color;
import machines.parser.MachineParser;
import machines.parser.MachineParserSettings;
import machines.parser.ParseVerdict;
import misc.Colors;

import java.util.*;
import java.util.stream.Collectors;

import static machines.convert.ImmutableFunctions.subArray;

public class DPDA extends Machine {

    private static final int DEFAULT_STACKS = 1;
    private static final String DEFAULT_START = "START";
    private static final String DEFAULT_REJECT = "no transition";
    private static final String DEFAULT_EPS = "eps";
    private static final String DEFAULT_BOTTOM = "Z";
    private static final String DEFAULT_STACK_SEP = ",";

    private MachineParser parser;

    private int stacks = DEFAULT_STACKS;
    private String startState = DEFAULT_START;
    private String eps = DEFAULT_EPS;
    private String bottom = DEFAULT_BOTTOM;
    private String stackSep = DEFAULT_STACK_SEP;
    private TreeSet<String> acceptStates = new TreeSet<>();
    private Transitions transitions = new Transitions();

    private String currentState;
    private ArrayList<String> input;
    private int inputPtr;
    private ArrayList<String>[] stack;

    private boolean executionFinished = false;

    public static DPDA with(int stacks, String startState, Set<String> acceptStates, String eps, String bottom, String stackSep, Transitions transitions) {
        DPDA dpda = new DPDA();
        dpda.stacks = stacks;
        dpda.startState = startState;
        dpda.acceptStates = new TreeSet<>(acceptStates);
        dpda.eps = eps;
        dpda.bottom = bottom;
        dpda.stackSep = stackSep;
        dpda.transitions = transitions;
        return dpda;
    }



    public DPDA() {
        parser = new MachineParser();
        parser.addSettings(new MachineParserSettings("stacks", this::parseStacks));
        parser.addSettings(new MachineParserSettings("start", (String[] args) -> startState = args[1]));
        parser.addSettings(new MachineParserSettings("eps", (String[] args) -> eps = args[1]));
        parser.addSettings(new MachineParserSettings("bottom", (String[] args) -> bottom = args[1]));
        parser.addSettings(new MachineParserSettings("stack-separator", (String[] args) -> stackSep = args[1]));
        parser.addSettingsNoReq(new MachineParserSettings("accept", (String[] args) -> acceptStates.addAll(Arrays.asList(args).subList(1, args.length))));
        parser.addSettingsChecker(() -> MachineParser.automatonEmptyAcceptSet(this::getAcceptStates));
        parser.setMain(this::parseTransition);
        parser.setFromStateExtractor(args -> args[0]);
        parser.setToStateExtractor(args -> args[stacks + 3]);
        parser.setTransitionExtractor(args -> new TransitionArgument(args[0], subArray(args, 1, stacks + 2)));
        parser.addPostChecker(this::checkStackSymbolsPopped);
    }

    public MachineParser getParser() {
        return parser;
    }

    private ParseVerdict parseStacks(String val) {
        int ln = parser.getLine();
        try {
            stacks = Integer.parseInt(val);
            if (stacks < 0)
                return ParseVerdict.error(String.format("Number of stacks must be a non-negative integer (got %d)", stacks), ln);
            if (stacks == 0)
                return ParseVerdict.warning(String.format("Line %s: DPDA with no stacks. Probably a DFA should be used instead", ln));
        } catch (NumberFormatException e) {
            return ParseVerdict.error(String.format("Unable to parse number of stacks: %s", val), ln);
        }
        return ParseVerdict.OK;
    }

    private ParseVerdict parseTransition(String[] args) {
        ParseVerdict verdict = new ParseVerdict();
        if (stacks == 0 && verdict.merge(parser.assertArgsCnt(4).apply(args)))
            return verdict;
        if (stacks > 0 && verdict.merge(parser.assertArgsAtLeast(stacks + 4).apply(args)))
            return verdict;
        if (verdict.merge(parser.assertArgEquals(stacks + 2, "->").apply(args)))
            return verdict;

        String fromState = args[0];
        String toState = args[stacks + 3];
        String[] fromSymbol = subArray(args, 1, stacks + 2);
        String[] toSymbol = subArray(args, stacks + 4, args.length);
        if (stacks > 0 && toSymbol.length == 0) {
            toSymbol = new String[stacks - 1];
            Arrays.fill(toSymbol, stackSep);
        }
        String[][] pushSyms = getStackPushSymbols(toSymbol);

        if (stacks > 0) {
            int sepCnt = 0;
            for (String s : toSymbol) {
                if (s.equals(stackSep))
                    sepCnt++;
            }
            if (sepCnt != stacks - 1)
                return verdict.putError(String.format(
                        "Line %d: expected %d stack changes after '->' (got %d)", parser.getLine(), stacks, sepCnt + 1)
                );
        }
        for (int i = 1; i < fromSymbol.length; i++) {
            if (fromSymbol[i].equals(eps))
                return verdict.putError(String.format("Line %d, stack %d: eps-symbol is forbidden here", parser.getLine(), i));
            if (fromSymbol[i].equals(stackSep))
                verdict.putWarning(String.format("Line %d, stack %d: symbol '%s' is a stack separator", parser.getLine(), i, stackSep));
        }
        for (int i = 0; i < stacks; i++) {
            int from = 0;
            if (fromSymbol[i + 1].equals(bottom)) {
                from = 1;
                if (pushSyms[i].length == 0 || !pushSyms[i][0].equals(bottom))
                    return verdict.putError(String.format(
                            "Line %d, stack %d: forbidden stack bottom behavior (stack bottom removed)",
                            parser.getLine(), i + 1
                    ));
            }
            for (int j = from; j < pushSyms[i].length; j++) {
                if (pushSyms[i][j].equals(bottom))
                    return verdict.putError(String.format(
                            "Line %d, stack %d: forbidden stack bottom behavior (new stack bottom pushed)",
                            parser.getLine(), i + 1
                    ));
            }
        }
        if (fromSymbol[0].length() != 1 && !fromSymbol[0].equals(eps)) {
            verdict.putWarning(String.format(
                    "Line %d: '%s' is multi-character but input parser at Execute tab splits input into one-character entities",
                    parser.getLine(), fromSymbol[0])
            );
        }

        transitions.set(fromState, fromSymbol, toState, toSymbol);
        return verdict;
    }

    private String[][] getStackPushSymbols(String[] toSymbols) {
        int stacks = 1 + (int) Arrays.stream(toSymbols).filter(sym -> !sym.equals(stackSep)).count();
        String[][] push = new String[stacks][];
        int i = 0, j = 0;
        for (String sym : toSymbols) {
            if (sym.equals(stackSep)) {
                push[i++] = new String[j];
                j = 0;
            } else
                j++;
        }
        push[i] = new String[j];
        i = 0; j = 0;
        for (String sym : toSymbols) {
            if (sym.equals(stackSep)) {
                j = 0;
                i++;
            } else
                push[i][j++] = sym;
        }
        return push;
    }

    private ParseVerdict checkStackSymbolsPopped() {
        ParseVerdict verdict = new ParseVerdict();
        for (int i = 0; i < stacks; i++) {
            HashSet<String> poppedSym = new HashSet<>(), pushedSym = new HashSet<>();
            for (Map.Entry<TransitionArgument, TransitionResult> entry : transitions.flatEntries()) {
                poppedSym.add(entry.getKey().getSymbols()[i + 1]);
                pushedSym.addAll(Arrays.asList(getStackPushSymbols(entry.getValue().getSymbols())[i]));
            }
            for (String sym : poppedSym) {
                if (!sym.equals(bottom) && !pushedSym.contains(sym))
                    verdict.putWarning(String.format("Stack %d: symbol '%s' is popped but never pushed", i + 1, sym));
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

    public Set<String> getAcceptStates() {
        return acceptStates;
    }

    public String getRejectState() {
        return DEFAULT_REJECT;
    }

    public String getBound() {
        return bottom;
    }

    public String getEps() {
        return eps;
    }

    public String getStackSep() {
        return stackSep;
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
        return getSymbolsSet(0);
    }

    public Set<String> getSymbolsSet(int i) {
        if (i == 0) {
            HashSet<String> s = new HashSet<>(Set.of(eps));
            for (Map.Entry<TransitionArgument, TransitionResult> tr : transitions.flatEntries())
                s.add(tr.getKey().getSymbols()[0]);
            return s;
        } else {
            HashSet<String> s = new HashSet<>(Set.of(bottom));
            for (Map.Entry<TransitionArgument, TransitionResult> tr : transitions.flatEntries()) {
                s.add(tr.getKey().getSymbols()[i]);
                String[][] newSyms = getStackPushSymbols(tr.getValue().getSymbols());
                s.addAll(Arrays.asList(newSyms[i - 1]));
            }
            s.remove(eps);
            return s;
        }
    }



    public void init(String input) {
        currentState = startState;
        this.input = input.chars()
                .mapToObj(n -> Character.toString((char) n))
                .collect(Collectors.toCollection(ArrayList::new));
        inputPtr = 0;
        stack = new ArrayList[stacks];
        for (int i = 0; i < stacks; i++) {
            stack[i] = new ArrayList<>();
            stack[i].add(bottom);
        }
        executionFinished = false;
    }

    private TransitionResult step(TransitionArgument arg) {
        TransitionResult res = transitions.get(arg);
        if (res != null) {
            if (!arg.getSymbols()[0].equals(eps))
                inputPtr++;
            return res;
        }

        String[] epsSyms = new String[stacks + 1];
        epsSyms[0] = eps;
        System.arraycopy(arg.getSymbols(), 1, epsSyms, 1, stacks);
        res = transitions.get(arg.getState(), epsSyms);
        if (res != null) return res;

        executionFinished = true;
        return inputPtr == input.size()?
                defaultResult(arg.getState(), arg.getSymbols()) :
                defaultRejectResult(arg.getSymbols());
    }

    public void makeStep() {
        if (executionFinished)
            return;
        TransitionArgument arg = new TransitionArgument(currentState, popAllSymbols());
        TransitionResult res = step(arg);
        currentState = res.getState();
        String[][] symbolsToPush = getStackPushSymbols(res.getSymbols());
        for (int i = 0; i < stacks; i++) {
            for (String symToPush : symbolsToPush[i]) {
                if (!symToPush.equals(eps))
                    stack[i].add(symToPush);
            }
        }
    }



    private String[] popAllSymbols() {
        String[] popped = new String[stacks + 1];
        popped[0] = inputPtr == input.size()? eps : input.get(inputPtr);
        for (int i = 0; i < stacks; i++)
            popped[i + 1] = stack[i].remove(stack[i].size() - 1);
        return popped;
    }

    private TransitionResult defaultResult(String state, String[] symbols) {
        if (stacks == 0)
            return new TransitionResult(state, new String[0]);
        String[] resSym = new String[2 * stacks - 1];
        for (int i = 0; i < stacks; i++) {
            if (i != 0)
                resSym[2 * i - 1] = stackSep;
            resSym[2 * i] = symbols[i + 1];
        }
        return new TransitionResult(state, resSym);
    }

    private TransitionResult defaultRejectResult(String[] symbols) {
        return defaultResult(DEFAULT_REJECT, symbols);
    }



    public int tapes() {
        return stacks + 1;
    }

    public int getTapeSize(int tape) {
        return tape == 0? input.size() + 1 : stack[tape - 1].size() + 1;
    }

    public String getTapeContent(int tape, int i) {
        if (tape == 0) {
            return i < input.size()? input.get(i) : "";
        } else {
            if (i < stack[tape - 1].size())
                return stack[tape - 1].get(i).equals(bottom)? "" : stack[tape - 1].get(i);
            else
                return "<";
        }
    }

    public Color getTapeContentColor(int tape, int i) {
        if (tape == 0) {
            if (input.isEmpty())
                return Colors.EXE_BLANK;
            else
                return i == input.size()? Colors.EXE_NONE : Colors.EXE_DEFAULT;
        } else {
            if (i == 0)
                return Colors.EXE_RED;
            else if (i == stack[tape - 1].size())
                return Colors.EXE_NONE;
            else
                return Colors.EXE_DEFAULT;
        }
    }

    public boolean getTapeContentPointer(int tape, int i) {
        return tape == 0 && i == inputPtr;
    }



    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (stacks != DEFAULT_STACKS) sb.append("stacks: ").append(stacks).append("\n");
        if (!startState.equals(DEFAULT_START)) sb.append("start: ").append(startState).append("\n");
        if (!acceptStates.isEmpty()) sb.append("accept: ").append(String.join(" ", acceptStates)).append("\n");
        if (!eps.equals(DEFAULT_EPS)) sb.append("eps: ").append(eps).append("\n");
        if (!bottom.equals(DEFAULT_BOTTOM)) sb.append("bottom: ").append(bottom).append("\n");
        if (!stackSep.equals(DEFAULT_STACK_SEP)) sb.append("stack-separator: ").append(stackSep).append("\n");

        ArrayList<TransitionArgument> args = new ArrayList<>(transitions.args());
        Collections.sort(args);
        String lastState = null;
        for (TransitionArgument arg : args) {
            if (!arg.getState().equals(lastState))
                sb.append("\n");
            lastState = arg.getState();
            TransitionResult res = transitions.get(arg);
            sb.append(arg.getState()).append(' ').append(String.join(" ", arg.getSymbols())).append(" -> ")
                    .append(res.getState());
            if (stacks != 0 && res.getSymbols().length != stacks - 1) {
                sb.append(' ');
                String[][] pushSyms = getStackPushSymbols(res.getSymbols());
                for (int i = 0; i < stacks; i++) {
                    if (i != 0)
                        sb.append(' ').append(stackSep).append(' ');
                    if (pushSyms[i].length == 0)
                        sb.append(eps);
                    else
                        sb.append(String.join(" ", pushSyms[i]));
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

}