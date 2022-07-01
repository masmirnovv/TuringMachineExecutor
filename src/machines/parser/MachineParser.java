package machines.parser;

import misc.Graph;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MachineParser {

    private Map<String, Function<String[], ParseVerdict>> settings = new HashMap<>();
    private List<Supplier<ParseVerdict>> settingsCheckers = new ArrayList<>();

    private Function<String[], ParseVerdict> main;
    private Function<String[], Object> transitionExtractor;
    private boolean allowTransitionsCollision = false;

    private Function<String[], String> fromStateExtractor;
    private Function<String[], String> toStateExtractor;
    private Graph<String> stateGraph;

    private List<Supplier<ParseVerdict>> postCheckers = new ArrayList<>();

    private int line;
    private DuplicationChecker declaredSettingsChecker;
    private DuplicationChecker declaredTransitionsChecker;

    private ParseVerdict verdict;



    public void addSettings(MachineParserSettings s) {
        Function<String[], ParseVerdict> sAction = combine(assertArgsCnt(2), args -> s.getAction().apply(args));
        settings.put(s.getKey(), sAction);
    }

    public void addSettingsNoReq(MachineParserSettings s) {
        Function<String[], ParseVerdict> sAction = args -> s.getAction().apply(args);
        settings.put(s.getKey(), sAction);
    }

    public void addSettingsChecker(Supplier<ParseVerdict> checker) {
        settingsCheckers.add(checker);
    }

    public void setMain(Function<String[], ParseVerdict> main) {
        this.main = main;
    }

    public void addPostChecker(Supplier<ParseVerdict> checker) {
        postCheckers.add(checker);
    }

    public void setFromStateExtractor(Function<String[], String> extractor) {
        fromStateExtractor = extractor;
    }

    public void setToStateExtractor(Function<String[], String> extractor) {
        toStateExtractor = extractor;
    }

    public void setTransitionExtractor(Function<String[], Object> extractor) {
        transitionExtractor = extractor;
    }

    public int getLine() {
        return line;
    }

    public void setNondeterministic() {
        allowTransitionsCollision = true;
    }



    public ParseVerdict parse(String content) {
        Scanner scanner = new Scanner(content);
        boolean readingSettings = true;
        line = 0;
        declaredSettingsChecker = new DuplicationChecker();
        if (!allowTransitionsCollision)
            declaredTransitionsChecker = new DuplicationChecker();
        stateGraph = new Graph<>();
        verdict = new ParseVerdict();

        while (scanner.hasNextLine()) {
            line++;

            String line = scanner.nextLine();
            int commentInd = line.indexOf("//");
            if (commentInd == -1)
                commentInd = line.length();

            int stPos = 0;
            while (stPos < commentInd && Character.isWhitespace(line.charAt(stPos)))
                stPos++;
            String[] args = line.substring(stPos, commentInd).split("\\s+");

            if (args.length == 1 && args[0].isEmpty())
                args = new String[0];
            if (args.length == 0)
                continue;

            if (readingSettings) {
                String st = args[0].toLowerCase();
                if (settings.containsKey(st)) {
                    Function<String[], ParseVerdict> action = settings.get(st);
                    if (!verdict.merge(action.apply(args)))
                        declaredSettingsChecker.put(st, this.line);
                } else {
                    readingSettings = false;
                    postSettingsValidate();
                }
            }
            if (!readingSettings) {
                if (!verdict.merge(main.apply(args))) {
                    String from = fromStateExtractor.apply(args);
                    String to = toStateExtractor.apply(args);
                    stateGraph.addEdge(from, to);
                    if (!allowTransitionsCollision) {
                        Object tr = transitionExtractor.apply(args);
                        declaredTransitionsChecker.put(tr, this.line);
                    }
                }
            }
        }
        if (readingSettings) {
            postSettingsValidate();
        }
        postValidate();
        return verdict;
    }

    private void postSettingsValidate() {
        for (Supplier<ParseVerdict> settingsChecker : settingsCheckers)
            verdict.merge(settingsChecker.get());
        declaredSettingsChecker.validate("settings");
    }

    private void postValidate() {
        for (Supplier<ParseVerdict> postChecker : postCheckers)
            verdict.merge(postChecker.get());
        if (!allowTransitionsCollision)
            declaredTransitionsChecker.validate("transition arguments");
    }



    public Function<String[], ParseVerdict> assertArgsCnt(int argsCnt) {
        return args -> {
            if (args.length != argsCnt) {
                String fewOrMany = args.length < argsCnt ? "few" : "many";
                return ParseVerdict.error(String.format(
                        "Line %d: Too %s arguments (expected %d args, got %d args)",
                        line, fewOrMany, argsCnt, args.length), line
                );
            }
            return ParseVerdict.OK;
        };
    }

    public Function<String[], ParseVerdict> assertArgsAtLeast(int argsCnt) {
        return args -> {
            if (args.length < argsCnt) {
                return ParseVerdict.error(String.format(
                        "Line %d: Too few arguments (expected at least %d args, got %d args)",
                        line, argsCnt, args.length), line
                );
            }
            return ParseVerdict.OK;
        };
    }

    public Function<String[], ParseVerdict> assertArgEquals(int index, String pat) {
        return args -> {
            if (!args[index].equals(pat))
                return ParseVerdict.error(String.format(
                        "Line %d, argument %d: Expected '%s' instead of '%s'",
                        line, index, pat, args[index]), line
                );
            return ParseVerdict.OK;
        };
    }

    public static ParseVerdict assertSettingsEquals(Supplier<String> set1, String set1desc, Supplier<String> set2, String set2desc) {
        if (set1.get().equals(set2.get()))
            return ParseVerdict.error(String.format(
                    "Names of %s and %s are equal (%s)",
                    set1desc, set2desc, set1.get()
            ), 0);
        return ParseVerdict.OK;
    }

    public static ParseVerdict automatonEmptyAcceptSet(Supplier<Set<String>> acceptGetter) {
        if (acceptGetter.get().isEmpty())
            return ParseVerdict.warning("No accept states of the automaton defined. Every input will be rejected");
        else
            return ParseVerdict.OK;
    }

    public ParseVerdict checkBasicReachability(String start, String accept, String reject) {
        stateGraph.addVertices(start, accept, reject);
        stateGraph.joinWithAll(reject);
        ParseVerdict verdict = new ParseVerdict();

        Set<String> unreachable = new HashSet<>(stateGraph.getVertices());
        unreachable.removeAll(stateGraph.bfs(start));
        unreachable.remove(reject);
        if (!unreachable.isEmpty()) {
            if (unreachable.contains(accept))
                verdict.putWarning(String.format(
                        "Accept state '%s' is unreachable from the start state '%s'. Any input of " +
                        "this machine will be either rejected or entered to infinite loop",
                        accept, start
                ));
            else if (unreachable.size() == 1)
                verdict.putWarning(String.format(
                        "State %s is unreachable from start state and therefore is useless",
                        unreachable.iterator().next()
                ));
            else
                verdict.putWarning(String.format(
                        "States %s are unreachable from start state and therefore are useless",
                        String.join(", ", unreachable)
                ));
        }
        return verdict;
    }



    @SafeVarargs
    private static Function<String[], ParseVerdict> combine(Function<String[], ParseVerdict>... actions) {
        return args -> {
            ParseVerdict verdict = new ParseVerdict();
            for (Function<String[], ParseVerdict> action : actions) {
                if (verdict.merge(action.apply(args)))
                    return verdict;
            }
            return verdict;
        };
    }



    private class DuplicationChecker {

        private HashMap<Object, List<Integer>> map = new HashMap<>();

        private void put(Object key, Integer value) {
            if (!map.containsKey(key))
                map.put(key, new ArrayList<>());
            map.get(key).add(value);
        }

        private void validate(String type) {
            for (Map.Entry<Object, List<Integer>> entry : map.entrySet()) {
                List<Integer> lines = entry.getValue();
                if (lines.size() > 1) {
                    int lastLine = lines.get(lines.size() - 1);
                    String linesStr = lines.stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(", "));
                    verdict.putWarning(String.format(
                            "Lines %s: duplicate %s: only the last declaration (line %d) will matter",
                            linesStr, type, lastLine
                    ));
                }
            }
        }

    }

}
