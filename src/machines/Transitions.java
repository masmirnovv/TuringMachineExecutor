package machines;

import misc.Graph;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

public class Transitions {

    public HashMap<TransitionArgument, List<TransitionResult>> map = new HashMap<>();



    public void set(TransitionArgument arg, TransitionResult res) {
        map.put(arg, List.of(res));
    }

    public void set(Map.Entry<TransitionArgument, TransitionResult> entry) {
        set(entry.getKey(), entry.getValue());
    }

    public void set(String fromState, String fromSymbol, TransitionResult res) {
        set(new TransitionArgument(fromState, fromSymbol), res);
    }

    public void set(TransitionArgument arg, String toState, String toSymbol, TransitionDirection dir) {
        set(arg, new TransitionResult(toState, toSymbol, dir));
    }

    public void set(String fromState, String fromSymbol, String toState, String toSymbol, TransitionDirection dir) {
        set(new TransitionArgument(fromState, fromSymbol), new TransitionResult(toState, toSymbol, dir));
    }

    public void set(String fromState, String fromSymbol, String toState) {
        set(new TransitionArgument(fromState, fromSymbol), new TransitionResult(toState));
    }

    void set(String fromState, String[] fromSymbols, String toState, String[] toSymbols, TransitionDirection[] dirs) {
        set(new TransitionArgument(fromState, fromSymbols), new TransitionResult(toState, toSymbols, dirs));
    }

    public void set(String fromState, String[] fromSymbols, String toState, String[] toSymbols) {
        set(fromState, fromSymbols, toState, toSymbols, null);
    }

    public void setAll(Transitions that) {
        for (Map.Entry<TransitionArgument, TransitionResult> entry : that.flatEntries())
            this.set(entry);
    }

    private void add(TransitionArgument arg, TransitionResult res) {
        map.putIfAbsent(arg, new ArrayList<>());
        map.get(arg).add(res);
    }

    private void add(String fromState, String fromSymbol, String toState, String toSymbol, TransitionDirection dir) {
        add(new TransitionArgument(fromState, fromSymbol), new TransitionResult(toState, toSymbol, dir));
    }

    public void add(Map.Entry<TransitionArgument, TransitionResult> entry) {
        add(entry.getKey(), entry.getValue());
    }

    public void add(String fromState, String fromSymbol, String toState) {
        add(new TransitionArgument(fromState, fromSymbol), new TransitionResult(toState));
    }

    public TransitionResult get(TransitionArgument arg) {
        return map.containsKey(arg)? map.get(arg).get(0) : null;
    }

    public TransitionResult get(String fromState, String fromSymbol) {
        return get(new TransitionArgument(fromState, fromSymbol));
    }

    public TransitionResult get(String fromState, String[] fromSymbols) {
        return get(new TransitionArgument(fromState, fromSymbols));
    }

    List<TransitionResult> getAll(TransitionArgument arg) {
        return arg == null? null : map.getOrDefault(arg, new ArrayList<>());
    }

    public List<TransitionResult> getAll(String fromState, String fromSymbol) {
        return getAll(new TransitionArgument(fromState, fromSymbol));
    }

    public int size() {
        return map.size();
    }



    public Collection<TransitionArgument> args() {
        return map.keySet();
    }

    public Collection<TransitionResult> results() {
        HashSet<TransitionResult> res = new HashSet<>();
        for (Map.Entry<TransitionArgument, List<TransitionResult>> entry : map.entrySet())
            res.addAll(entry.getValue());
        return res;
    }

    public Collection<Map.Entry<TransitionArgument, TransitionResult>> flatEntries() {
        ArrayList<Map.Entry<TransitionArgument, TransitionResult>> entries = new ArrayList<>();
        for (Map.Entry<TransitionArgument, List<TransitionResult>> entry : map.entrySet()) {
            TransitionArgument arg = entry.getKey();
            for (TransitionResult res : entry.getValue())
                entries.add(Map.entry(arg, res));
        }
        return entries;
    }



    public Transitions select(Predicate<TransitionArgument> filter) {
        Transitions newTransitions = new Transitions();
        for (Map.Entry<TransitionArgument, TransitionResult> tr : flatEntries()) {
            if (filter.test(tr.getKey()))
                newTransitions.add(tr.getKey(), tr.getValue());
        }
        return newTransitions;
    }

    public Transitions selectByResult(Predicate<TransitionResult> filter) {
        Transitions newTransitions = new Transitions();
        for (Map.Entry<TransitionArgument, TransitionResult> tr : flatEntries()) {
            if (filter.test(tr.getValue()))
                newTransitions.add(tr.getKey(), tr.getValue());
        }
        return newTransitions;
    }

    @SafeVarargs
    public final void forEach(BiConsumer<TransitionArgument, TransitionResult> action, BiConsumer<TransitionArgument, TransitionResult>... moreActions) {
        Collection<Map.Entry<TransitionArgument, TransitionResult>> flatEntries = flatEntries();
        for (Map.Entry<TransitionArgument, TransitionResult> tr : flatEntries)
            action.accept(tr.getKey(), tr.getValue());
        for (BiConsumer<TransitionArgument, TransitionResult> act : moreActions) {
            for (Map.Entry<TransitionArgument, TransitionResult> tr : flatEntries)
                act.accept(tr.getKey(), tr.getValue());
        }
    }

    public <U> List<U> map(BiFunction<TransitionArgument, TransitionResult, U> mapFunc) {
        return flatEntries().stream().map(tr -> mapFunc.apply(tr.getKey(), tr.getValue())).collect(Collectors.toList());
    }



    public Consumer<String> goLeft(String state) {
        return goDir(TransitionDirection.LEFT, state);
    }

    public Consumer<String> goRight(String state) {
        return goDir(TransitionDirection.RIGHT, state);
    }

    private Consumer<String> goDir(TransitionDirection dir, String state) {
        return sym -> add(state, sym, state, sym, dir);
    }



    public Transitions removeUnreachableStates(String startState, String acceptState, String rejectState) {
        Graph<String> stateGraph = new Graph<>();
        stateGraph.addVertices(startState);
        if (acceptState != null)
            stateGraph.addVertices(acceptState, rejectState);
        for (Map.Entry<TransitionArgument, TransitionResult> tr : flatEntries())
            stateGraph.addEdge(tr.getKey().getState(), tr.getValue().getState());

        Set<String> unreachable = new HashSet<>(stateGraph.getVertices());
        unreachable.removeAll(stateGraph.bfs(startState));
        if (acceptState != null)
            unreachable.remove(rejectState);

        Transitions newTransitions = new Transitions();
        for (Map.Entry<TransitionArgument, TransitionResult> tr : flatEntries()) {
            if (!unreachable.contains(tr.getKey().getState()))
                newTransitions.add(tr.getKey(), tr.getValue());
        }

        return newTransitions;
    }

    public Transitions replaceBlanks(String oldBlank, String newBlank) {
        Transitions newTrs = new Transitions();
        for (Map.Entry<TransitionArgument, TransitionResult> entry : flatEntries()) {
            TransitionArgument arg = entry.getKey();
            TransitionResult res = entry.getValue();
            TransitionResult replRes = new TransitionResult(res.getState(), res.getSymbol().equals(oldBlank)? newBlank : res.getSymbol(), res.getDirection());
            newTrs.set(arg, replRes);
            if (arg.getSymbol().equals(oldBlank))
                newTrs.set(arg.getState(), newBlank, replRes);
        }
        return newTrs;
    }

}
