package machines.convert;

import machines.*;
import misc.Graph;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static machines.TransitionDirection.*;
import static machines.convert.ImmutableFunctions.*;
import static machines.convert.NamespaceTree.addMore;

public class Convert {

    public static Machine init(String code) {
        if (code.startsWith("tm-"))
            return new TM();
        if (code.startsWith("1tm-"))
            return new OneTM();
        if (code.startsWith("mtm-"))
            return new MTM();
        if (code.startsWith("dfa-"))
            return new DFA();
        if (code.startsWith("nfa-"))
            return new NFA();
        if (code.startsWith("dca-"))
            return new DCA();
        if (code.startsWith("dpda-"))
            return new DPDA();
        throw new AssertionError();
    }

    public static Machine convert(Machine m, String code) {
        switch (code) {
            case "tm-nostay":
            case "1tm-nostay":
                return noStayConvert(m);

            case "1tm-tm":
                return onetmTmConvert((OneTM) m);
            case "tm-1tm":
                return tmOnetmConvert((TM) m);
            case "tm-1tm-2":
                return tmOnetmConvert2((TM) m);
            case "tm-mtm":
                return tmMtmConvert((TM) m);
            case "mtm-tm":
                return mtmTmConvert((MTM) m);
            case "mtm-tm-2":
                return mtmTmConvert2((MTM) m);

            case "nfa-noeps":
                return nfaNoEpsConvert((NFA) m);

            case "dfa-nfa":
                return dfaNfaConvert((DFA) m);
            case "nfa-dfa":
                return nfaDfaConvert((NFA) m);

            case "dfa-tm":
                return dfaTmConvert((DFA) m);

            case "dfa-dca":
                return dfaDcaConvert((DFA) m);
            case "dca-dpda":
                return dcaDpdaConvert((DCA) m);

            case "dca-2counters":
                return dca2CountersConvert((DCA) m);

            case "tm-dpda-2stacks":
                return tmDpda2StacksConvert((TM) m);
            case "dpda-tm":
                return dpdaTmConvert((DPDA) m);

            default:
                throw new AssertionError();
        }
    }



    private static Machine noStayConvert(Machine m) {
        String start = m.getStartState(), accept = m.getAcceptState(), reject = m.getRejectState();
        String blank = m.getBlank(), bd = m.getBound();
        Set<String> stSet = m.getStatesSet(), symSet = m.getSymbolsSet();
        Transitions newTransitions = new Transitions();

        NamespaceTree states = new NamespaceTree();
        states.addAll("src", "back", "back-2")
                .to("src").addAll(stSet).also()
                .to("back").addAll(List.of(accept, reject), st -> "back-to-" + st).also()
                .to("back-2").addAll(List.of(accept, reject), st -> "back-to-" + st + "-2").also()
                .add("ill", "inf-loop-left").add("ilr", "inf-loop-right");

        NamespaceTree syms = new NamespaceTree();
        syms.addAll("src").to("src").addAll(symSet);

        (bd == null? syms.to("src").selectAll() : syms.to("src").selectWithout(bd)).forEachSelected(
                sym -> newTransitions.set(states.get("ilr"), sym, states.get("ill"), sym, LEFT),
                sym -> List.of(accept, reject).forEach(
                        term -> newTransitions.set(states.get("back-2", term), sym, term, sym, LEFT)
                )
        );
        syms.to("src").forAll(
                sym -> newTransitions.set(states.get("ill"), sym, states.get("ilr"), sym, RIGHT),
                sym -> List.of(accept, reject).forEach(
                        term -> newTransitions.set(states.get("back", term), sym, term, sym, RIGHT)
                )
        );

        newTransitions.setAll(m.getTransitions().selectByResult(res -> res.getDirection() != STAY));

        HashSet<TransitionArgument> visited = new HashSet<>();
        Graph<TransitionArgument> stayClosure = new Graph<>();
        m.getTransitions().select(arg -> !arg.getState().equals(accept) && !arg.getState().equals(reject))
                .selectByResult(res -> res.getDirection() == STAY).forEach((arg, res) -> stayClosure.addEdge(arg, res.asArgument()));

        for (TransitionArgument arg : stayClosure.getVertices()) {
            if (!visited.contains(arg)) {
                TransitionArgument curArg = arg.copy();
                HashSet<TransitionArgument> curVisited = new HashSet<>(List.of(curArg));
                TransitionArgument lastArg = stayClosure.edgeFrom(curArg);
                while (true) {
                    if (lastArg == null || visited.contains(curArg)) {
                        TransitionResult res = newTransitions.get(curArg);
                        if (res != null) {
                            for (TransitionArgument vis : curVisited)
                                newTransitions.set(vis, res);
                        }
                        break;
                    } else if (lastArg.getState().equals(accept) || lastArg.getState().equals(reject)) {
                        TransitionResult res = m.getTransitions().get(curArg);
                        if (res.getDirection() == STAY) {
                            for (TransitionArgument vis : curVisited) {
                                if (vis.getSymbol().equals(bd))
                                    newTransitions.set(vis, states.get("back-2", res.getState()), vis.getSymbol(), RIGHT);
                                else
                                    newTransitions.set(vis, states.get("back", res.getState()), vis.getSymbol(), LEFT);
                            }
                        } else {
                            for (TransitionArgument vis : curVisited)
                                newTransitions.set(vis, res);
                        }
                        break;
                    } else if (curVisited.contains(lastArg)) {
                        for (TransitionArgument vis : curVisited) {
                            if (vis.getSymbol().equals(bd))
                                newTransitions.set(vis, states.get("ilr"), vis.getSymbol(), RIGHT);
                            else
                                newTransitions.set(vis, states.get("ill"), vis.getSymbol(), LEFT);
                        }
                        break;
                    } else {
                        curArg = lastArg.copy();
                        curVisited.add(curArg);
                        lastArg = stayClosure.edgeFrom(curArg);
                    }
                }
                visited.addAll(curVisited);
            }
        }


        if (m instanceof OneTM)
            return removeUnreachableStates(OneTM.with(start, accept, reject, blank, bd, newTransitions));
        if (m instanceof TM)
            return removeUnreachableStates(TM.with(start, accept, reject, blank, newTransitions));
        throw new AssertionError();
    }


    private static TM onetmTmConvert(OneTM m) {
        String start = m.getStartState(), accept = m.getAcceptState(), reject = m.getRejectState();
        String blank = m.getBlank(), bd = m.getBound();
        Set<String> stSet = m.getStatesSet(), symSet = m.getSymbolsSet();
        Transitions newTransitions = new Transitions();

        NamespaceTree states = new NamespaceTree();
        states.addAll("src")
                .to("src").addAll(stSet).also()
                .add("st", start).add("ac", accept).add("rj", reject).add("set-bd").add("remove-bd").add("final");

        NamespaceTree syms = new NamespaceTree();
        syms.addAll("src", "mark", "mark-rj")
                .to("src").addAll(symSet).also()
                .to("mark").addAll(symSet).also()
                .to("mark-rj").addAll(symSet, s -> s, s -> '\'' + s);


        syms.to("src").selectWithout(bd).forEachSelected(
                sym -> newTransitions.set(states.get("st"), sym, states.get("set-bd"), sym, LEFT)
        );
        newTransitions.set(states.get("set-bd"), blank, states.get("src", start), bd, RIGHT);

        m.getTransitions().forEach(newTransitions::set);

        syms.to("src").selectWithout(bd).forEachSelected(
                sym -> newTransitions.set(accept, sym, states.get("remove-bd"), syms.get("mark", sym), LEFT),
                sym -> newTransitions.set(reject, sym, states.get("remove-bd"), syms.get("mark-rj", sym), LEFT),
                newTransitions.goLeft(states.get("remove-bd")),
                newTransitions.goRight(states.get("final")),
                sym -> newTransitions.set(states.get("final"), syms.get("mark", sym), states.get("ac"), sym, STAY),
                sym -> newTransitions.set(states.get("final"), syms.get("mark-rj", sym), states.get("rj"), sym, STAY)
        );
        newTransitions.set(accept, bd, states.get("ac"), blank, STAY);
        newTransitions.set(reject, bd, states.get("rj"), blank, STAY);
        newTransitions.set(states.get("remove-bd"), bd, states.get("final"), blank, RIGHT);

        return removeUnreachableStates(TM.with(states.get("st"), states.get("ac"), states.get("rj"), blank, newTransitions));
    }

    private static OneTM tmOnetmConvert(TM m) {
        String start = m.getStartState(), accept = m.getAcceptState(), reject = m.getRejectState();
        String blank = m.getBlank();
        Set<String> stSet = m.getStatesSet(), symSet = m.getSymbolsSet();
        Transitions newTransitions = new Transitions();

        NamespaceTree states = new NamespaceTree();
        states.addAll("src", "sh-sym", "sh-st", "f-carry-sym")
                .to("src").addAll(stSet).also()
                .add("st", start).add("ac", accept).add("rj", reject).add("set-rbd")
                .add("start-back").add("start-back-2").add("sh-r-1", "shift-rbd").add("sh-r-2", "shift-rbd-back")
                .add("sh-r-3", "shift-all-rbd").add("sh-r-4", "shift-all-back")
                .add("f-to-left", "final-to-left").add("f-set-m", "final-set-marker").add("f-carry-more", "final-carry-more")
                .add("f-carry-this", "final-carry-this").add("f-to-rbd", "final-goto-rbd").add("f", "final")
                .to("sh-sym").addAll(symSet, sym -> "shift-" + sym);

        NamespaceTree syms = new NamespaceTree();
        syms.addAll("src", "st", "mark-ac", "mark-rj")
                .to("src").addAll(symSet).also()
                .to("mark-ac").addAll(symSet).also()
                .to("mark-rj").addAll(symSet, s -> s, s -> '\'' + s).also()
                .add("bd", "BD").add("rbd", "RBD").add("marker", "*", addMore("*"));
        syms.selectOnly("src", "mark-ac", "mark-rj").forEachSelected(
                sym -> states.to("f-carry-sym").add(sym, "final-carry-" + sym)
        );


        syms.to("src").selectWithout(blank).forEachSelected(
                sym -> newTransitions.set(states.get("st"), sym, states.get("set-rbd"), sym, RIGHT),
                newTransitions.goRight(states.get("set-rbd"))
        );
        newTransitions.set(states.get("set-rbd"), blank, states.get("start-back"), syms.get("rbd"), LEFT);
        syms.to("src").selectWithout(blank).forEachSelected(newTransitions.goLeft(states.get("start-back")));
        newTransitions.set(states.get("start-back"), syms.get("bd"), states.get("src", start), syms.get("bd"), RIGHT);

        m.getTransitions().forEach(newTransitions::set);

        AtomicInteger i = new AtomicInteger(0);
        m.getTransitions().select(arg -> arg.getSymbol().equals(blank)).forEach(
                (arg, res) -> syms.to("st").add(arg.getState(), "ST-" + i.incrementAndGet()),
                (arg, res) -> states.to("sh-st").add(arg.getState(), "shift-" + arg.getState())
        );

        syms.to("st").forAllStringKeys(
                state -> newTransitions.set(state, syms.get("rbd"), states.get("sh-r-1"), syms.get("st", state), RIGHT),
                state -> newTransitions.set(states.get("sh-r-2"), syms.get("st", state), m.getTransitions().get(state, blank))
        );
        newTransitions.set(states.get("sh-r-1"), blank, states.get("sh-r-2"), syms.get("rbd"), LEFT);

        syms.to("st").forAllStringKeys(
                state -> newTransitions.set(state, syms.get("bd"), states.get("sh-st", state), syms.get("bd"), RIGHT),
                state -> syms.to("src").forAll(
                        sym -> newTransitions.set(states.get("sh-st", state), sym, states.get("sh-sym", sym), syms.get("st", state), RIGHT)
                ),
                state -> newTransitions.set(states.get("sh-st", state), syms.get("rbd"), states.get("sh-r-3"), syms.get("st", state), RIGHT)
        );
        syms.to("src").forAll(
                sym -> syms.to("src").forAll(
                        sym2 -> newTransitions.set(states.get("sh-sym", sym), sym2, states.get("sh-sym", sym2), sym, RIGHT)
                ),
                sym -> newTransitions.set(states.get("sh-sym", sym), syms.get("rbd"), states.get("sh-r-3"), sym, RIGHT),
                newTransitions.goLeft(states.get("sh-r-4"))
        );
        newTransitions.set(states.get("sh-r-3"), blank, states.get("sh-r-4"), syms.get("rbd"), LEFT);
        syms.to("st").forAllStringKeys(
                state -> newTransitions.set(states.get("sh-r-4"), syms.get("st", state), m.getTransitions().get(state, blank))
        );

        syms.to("src").forAll(
                sym -> newTransitions.set(accept, sym, states.get("f-to-left"), syms.get("mark-ac", sym), LEFT),
                newTransitions.goLeft(states.get("f-to-left"))
        );
        newTransitions.set(states.get("f-to-left"), syms.get("bd"), states.get("f-set-m"), syms.get("bd"), RIGHT);
        newTransitions.set(states.get("f-set-m"), blank, states.get("f-carry-more"), syms.get("marker"), RIGHT);
        newTransitions.set(states.get("f-set-m"), syms.get("marker"), states.get("f-carry-this"), syms.get("marker"), RIGHT);
        newTransitions.set(states.get("f-carry-more"), blank, states.get("f-carry-more"), blank, RIGHT);
        newTransitions.set(states.get("f-carry-more"), syms.get("marker"), states.get("f-carry-this"), blank, RIGHT);
        syms.selectOnly("mark-ac", "mark-rj").to("src").selectWithout(blank).forEachSelected(
                sym -> newTransitions.set(states.get("f-set-m"), sym, states.get("f-to-rbd"), sym, RIGHT),
                sym -> newTransitions.set(states.get("f-carry-more"), sym, states.get("f-carry-sym", sym), syms.get("marker"), LEFT)
        );
        syms.selectOnly("src", "mark-ac", "mark-rj").forEachSelected(
                newTransitions.goRight(states.get("f-to-rbd")),
                sym -> newTransitions.set(states.get("f-carry-this"), sym, states.get("f-carry-sym", sym), syms.get("marker"), LEFT),
                sym -> newTransitions.set(states.get("f-carry-sym", sym), blank, states.get("f-carry-sym", sym), blank, LEFT),
                sym -> newTransitions.set(states.get("f-carry-sym", sym), syms.get("marker"), states.get("f-set-m"), sym, RIGHT)
        );
        newTransitions.set(states.get("f-carry-this"), syms.get("rbd"), states.get("f"), blank, LEFT);
        newTransitions.set(states.get("f-to-rbd"), syms.get("rbd"), states.get("f"), blank, LEFT);

        newTransitions.set(states.get("f"), syms.get("marker"), states.get("f"), blank, LEFT);
        syms.to("src").forAll(
                newTransitions.goLeft(states.get("f")),
                sym -> newTransitions.set(states.get("f"), syms.get("mark-ac", sym), states.get("ac"), sym, STAY),
                sym -> newTransitions.set(states.get("f"), syms.get("mark-rj", sym), states.get("rj"), sym, STAY)
        );

        return removeUnreachableStates(OneTM.with(states.get("st"), states.get("ac"), states.get("rj"), blank, syms.get("bd"), newTransitions));
    }

    private static OneTM tmOnetmConvert2(TM m) {
        String start = m.getStartState(), accept = m.getAcceptState(), reject = m.getRejectState();
        String blank = m.getBlank();
        Set<String> stSet = m.getStatesSet(), symSet = m.getSymbolsSet();
        Transitions newTransitions = new Transitions(), mainTransitions = new Transitions();

        NamespaceTree states = new NamespaceTree();
        states.addAll("src", "src-bd", "1-s", "2", "3-more", "3-carry", "3-shift")
                .to("src").addAll(stSet).also()
                .to("src-bd").addAllLists(multiply(List.of("lbd", "rbd"), stSet), ls -> "set-" + String.join("-", ls)).also()
                .add("st", start).add("ac", accept).add("rj", reject)

                .to("1-s").addAllLists(multiply(range(3), symSet), ls -> "start-" + switchInt(ls, 0, "set-after-0-", "set-after-1-", "carry-") + ls.get(1)).also()
                .add("1-more", "start-fill-more").add("1-this", "start-mark-this").add("1-fill", "start-fill")
                .add("1-s-more", "start-stretch-more").add("1-s-mark", "start-stretch-mark-this")
                .add("1-set-lbd-1", "start-set-lbd-after-1").add("1-set-lbd", "start-set-lbd")

                .to("2").addAllLists(multiply(range(2), range(3), states.selectOnly("src", "src-bd").collectSelected()), ls -> switchInt(ls, 0, "r", "l") + "-" + switchInt(ls, 1, "#", ">", "<") + "-" + ls.get(2)).also()

                .to("3-more").addAllLists(multiply(range(2), range(2)), ls -> "shift-more-" + switchInt(ls, 0, "r", "l") + "-" + switchInt(ls, 1, "#", "X")).also()
                .add("3-s-more", "zip-more").add("3-s-this-1", "zip-this-after-1").add("3-s-this", "zip-this")
                .add("3-mark", "zip-mark-this").add("3-final", "final");

        NamespaceTree syms = new NamespaceTree();
        syms.addAll("src", "mark", "mark-rj")
                .to("src").addAll(symSet).also()
                .to("mark").addAll(symSet).also()
                .to("mark-rj").addAll(symSet, s -> s, s -> '\'' + s).also()
                .add("bd", "BD").add("lbd", "LBD").add("rbd", "RBD").add("new-blank", "_", addMore("_"));

        states.to("3-carry").addAllLists(multiply(range(2), range(2), syms.selectWithout("bd", "new-blank").collectSelected()), ls -> "shift-carry-" + ls.get(2) + "-" + switchInt(ls, 0, "r", "l") + "-" + switchInt(ls, 1, "#", "X")).also()
                .to("3-shift").addAll(syms.selectOnly("src", "mark", "mark-rj").collectSelected(), sym -> "zip-" + sym);

        // 0. make mainTransitions set

        m.getTransitions().forEach(mainTransitions::set);
        List.of("lbd", "rbd").forEach(
                dir -> m.getTransitions().select(arg -> arg.getSymbol().equals(blank)).forEach(
                        (arg, res) -> mainTransitions.set(arg.getState(), syms.get(dir), states.get(List.of(dir, arg.getState()), "src-bd"), blank, dir.equals("lbd")? LEFT : RIGHT),
                        (arg, res) -> mainTransitions.set(states.get(List.of(dir, arg.getState()), "src-bd"), blank, arg.getState(), syms.get(dir), dir.equals("lbd")? RIGHT : LEFT)
                )
        );

        // 1. 2x stretch the input

        syms.to("src").selectWithout(blank).forEachSelected(newTransitions.goRight(states.get("st")));
        newTransitions.set(states.get("st"), blank, states.get("1-more"), blank, LEFT);
        syms.selectOnly("new-blank").to("src").selectWithout(blank).forEachSelected(newTransitions.goLeft(states.get("1-more")));
        syms.selectOnly("bd").to("mark").selectWithout(blank).forEachSelected(
                sym -> newTransitions.set(states.get("1-more"), sym, states.get("1-this"), sym, RIGHT)
        );
        syms.to("src").selectWithout(blank).forEachSelected(
                sym -> newTransitions.set(states.get("1-this"), sym, states.get("1-fill"), syms.get("mark", sym), RIGHT)
        );
        syms.selectOnly("new-blank").to("src").selectWithout(blank).forEachSelected(newTransitions.goRight(states.get("1-fill")));
        newTransitions.set(states.get("1-fill"), blank, states.get("1-more"), syms.get("new-blank"), LEFT);
        newTransitions.set(states.get("1-this"), syms.get("new-blank"), states.get("1-s-more"), syms.get("new-blank"), LEFT);
        newTransitions.set(states.get("1-this"), blank, states.get("1-set-lbd"), syms.get("rbd"), RIGHT);

        syms.selectOnly("new-blank").to("src").selectWithout(blank).forEachSelected(newTransitions.goLeft(states.get("1-s-more")));
        syms.to("src").selectWithout(blank).forEachSelected(
                sym -> newTransitions.set(states.get("1-s-more"), syms.get("mark", sym), states.get(List.of("2", sym), "1-s"), syms.get("new-blank"), RIGHT),
                sym -> newTransitions.set(states.get(List.of("2", sym), "1-s"), syms.get("new-blank"), states.get(List.of("2", sym), "1-s"), syms.get("new-blank"), RIGHT),
                sym -> newTransitions.set(states.get(List.of("2", sym), "1-s"), blank, states.get(List.of("1", sym), "1-s"), syms.get("rbd"), LEFT),
                sym -> newTransitions.set(states.get(List.of("2", sym), "1-s"), syms.get("rbd"), states.get(List.of("0", sym), "1-s"), blank, LEFT),
                sym -> newTransitions.set(states.get(List.of("1", sym), "1-s"), syms.get("new-blank"), states.get(List.of("0", sym), "1-s"), blank, LEFT),
                sym -> newTransitions.set(states.get(List.of("0", sym), "1-s"), syms.get("new-blank"), states.get("1-s-mark"), sym, LEFT)
        );
        newTransitions.set(states.get("1-s-mark"), syms.get("new-blank"), states.get("1-s-more"), syms.get("rbd"), LEFT);
        newTransitions.set(states.get("1-s-mark"), syms.get("bd"), states.get("1-set-lbd-1"), syms.get("bd"), RIGHT);
        syms.to("src").selectWithout(blank).forEachSelected(
                sym -> newTransitions.set(states.get("1-set-lbd-1"), sym, states.get("1-set-lbd"), sym, RIGHT)
        );
        newTransitions.set(states.get("1-set-lbd"), blank, states.get(List.of("0", "0", start), "2"), syms.get("lbd"), LEFT);

        // 2. main steps

        range(2).forEach(half -> mainTransitions.selectByResult(res -> res.getDirection() == STAY).forEach((arg, res) ->
                newTransitions.set(states.get(List.of(half, "0", arg.getState()), "2"), arg.getSymbol(), states.get(List.of(half, "0", res.getState()), "2"), res.getSymbol(), STAY))
        );
        range(2).forEach(half -> mainTransitions.selectByResult(res -> res.getDirection() != STAY).forEach((arg, res) -> {
            String fromState = arg.getState(), fromSym = arg.getSymbol(), toState = res.getState(), toSym = res.getSymbol();
            TransitionDirection dir = switchInt(half, res.getDirection(), res.getDirection().reverse());
            String sdir = dir == RIGHT? switchInt(half, "1", "2") : switchInt(half, "2", "1");

            newTransitions.set(states.get(List.of(half, "0", fromState), "2"), fromSym, states.get(List.of(half, sdir, toState), "2"), toSym, dir);
            syms.selectOnly("lbd", "rbd").to("src").selectAll().forEachSelected(
                    sym -> newTransitions.set(states.get(List.of(half, sdir, toState), "2"), sym, states.get(List.of(half, "0", toState), "2"), sym, dir)
            );
            newTransitions.set(states.get(List.of("0", "2", toState), "2"), syms.get("bd"), states.get(List.of("1", "2", toState), "2"), syms.get("bd"), RIGHT);
            newTransitions.set(states.get(List.of("1", "0", toState), "2"), syms.get("bd"), states.get(List.of("0", "0", toState), "2"), syms.get("bd"), RIGHT);
        }));

        range(2).forEach(half -> syms.to("src").forAll(
                sym -> newTransitions.set(states.get(List.of(half, "0", accept), "2"), sym, states.get(List.of(half, "1"), "3-more"), syms.get("mark", sym), switchInt(half, LEFT, RIGHT))
        ));

        // 3. zip result

        range(2).forEach(half -> range(2).forEach(isAct -> syms.selectWithout("bd", "new-blank").forEachSelected(sym ->
                newTransitions.set(states.get(List.of(half, isAct), "3-more"), sym, states.get(List.of(half, switchInt(isAct, "1", "0")), "3-more"), sym, switchInt(half, LEFT, RIGHT))
        )));
        newTransitions.set(states.get(List.of("0", "1"), "3-more"), syms.get("bd"), states.get(List.of("1", "1"), "3-more"), syms.get("bd"), RIGHT);
        newTransitions.set(states.get(List.of("1", "0"), "3-more"), syms.get("lbd"), states.get(List.of("1", "1", syms.get("lbd")), "3-carry"), blank, LEFT);
        newTransitions.set(states.get(List.of("0", "0"), "3-more"), syms.get("lbd"), states.get("3-s-more"), syms.get("lbd"), RIGHT);
        range(2).forEach(half -> syms.selectWithout("bd", "new-blank").forEachSelected(sym ->
                syms.selectWithout("bd", "new-blank").forEachSelected(
                        sym2 -> newTransitions.set(states.get(List.of(half, "0", sym), "3-carry"), sym2, states.get(List.of(half, "1", sym2), "3-carry"), sym, switchInt(half, RIGHT, LEFT)),
                        sym2 -> newTransitions.set(states.get(List.of(half, "1", sym), "3-carry"), sym2, states.get(List.of(half, "0", sym), "3-carry"), sym2, switchInt(half, RIGHT, LEFT))
                ),
                sym -> newTransitions.set(states.get(List.of("1", "0", sym), "3-carry"), syms.get("bd"), states.get(List.of("0", "0", sym), "3-carry"), syms.get("bd"), RIGHT)
        ));
        newTransitions.set(states.get(List.of("0", "0", syms.get("rbd")), "3-carry"), blank, states.get(List.of("0", "1"), "3-more"), syms.get("rbd"), LEFT);

        // optimization (no extra LBD & RBD carries)
        newTransitions.set(states.get(List.of("1", "0", syms.get("lbd")), "3-carry"), blank, states.get(List.of("1", "1", syms.get("lbd")), "3-carry"), blank, LEFT);
        newTransitions.set(states.get(List.of("0", "0", blank), "3-carry"), syms.get("rbd"), states.get(List.of("0", "1"), "3-more"), syms.get("rbd"), LEFT);

        newTransitions.set(states.get("3-s-more"), blank, states.get("3-s-more"), blank, RIGHT);
        newTransitions.set(states.get("3-s-more"), syms.get("lbd"), states.get("3-s-this-1"), blank, RIGHT);
        newTransitions.set(states.get("3-s-this-1"), blank, states.get("3-s-this"), blank, RIGHT);
        newTransitions.set(states.get("3-s-this"), syms.get("rbd"), states.get("3-final"), blank, LEFT);
        syms.selectOnly("mark", "mark-rj").to("src").selectWithout(blank).forEachSelected(
                sym -> newTransitions.set(states.get("3-s-more"), sym, states.get("3-shift", sym), syms.get("lbd"), LEFT)
        );
        syms.selectOnly("src", "mark", "mark-rj").forEachSelected(
                sym -> newTransitions.set(states.get("3-s-this"), sym, states.get("3-shift", sym), syms.get("lbd"), LEFT),
                sym -> newTransitions.set(states.get("3-shift", sym), blank, states.get("3-shift", sym), blank, LEFT),
                sym -> newTransitions.set(states.get("3-shift", sym), syms.get("lbd"), states.get("3-mark"), sym, RIGHT)
        );
        newTransitions.set(states.get("3-mark"), blank, states.get("3-s-more"), syms.get("lbd"), RIGHT);

        syms.to("src").forAll(
                newTransitions.goLeft(states.get("3-final")),
                sym -> newTransitions.set(states.get("3-final"), syms.get("mark", sym), states.get("ac"), sym, STAY),
                sym -> newTransitions.set(states.get("3-final"), syms.get("mark-rj", sym), states.get("rj"), sym, STAY)
        );
        newTransitions.set(states.get("3-final"), syms.get("lbd"), states.get("3-final"), blank, LEFT);

        return removeUnreachableStates(OneTM.with(states.get("st"), states.get("ac"), states.get("rj"), blank, syms.get("bd"), newTransitions));
    }

    private static MTM tmMtmConvert(TM m) {
        return MTM.with(1, m.getStartState(), m.getAcceptState(), m.getRejectState(), m.getBlank(), m.getTransitions());
    }

    private static TM mtmTmConvert(MTM m) {
        int n = m.tapes();
        if (n == 1)
            return TM.with(m.getStartState(), m.getAcceptState(), m.getRejectState(), m.getBlank(), m.getTransitions());

        String start = m.getStartState(), accept = m.getAcceptState(), reject = m.getRejectState();
        String blank = m.getBlank();
        Set<String> stSet = m.getStatesSet(), symSet = m.getSymbolsSet();
        Set<String>[] symSetOf = new Set[n];
        for (int i = 0; i < n; i++)
            symSetOf[i] = m.getSymbolsSet(i);
        Transitions newTransitions = new Transitions();

        NamespaceTree states = new NamespaceTree();
        states.addAll("src", "1-init", "1-tp-mark", "1-tp-sep", "21", "21-no-mark", "21-no-sep", "21-ext", "21-ok", "22")
                .addAll("23", "23-mark", "3-clear", "3-back")
                .to("src").addAll(stSet).also()
                .add("st", start).add("ac", accept).add("rj", reject)

                .to("1-init").addAll(4, i -> "init-" + (i + 1)).also()
                .to("1-tp-mark").addAll(n - 1, i -> "init-tape" + (i + 2) + "-mark").also()
                .to("1-tp-sep").addAll(n - 1, i -> "init-tape" + (i + 2) + "-sep").also()

                .to("21").addAll(n, i -> "check-" + (i + 1)).also()
                .to("21-no-mark").addAll(n, i -> "check-" + (i + 1) + "-no-marked").also()
                .to("21-no-sep").addAll(n, i -> "check-" + (i + 1) + "-no-sep").also()
                .to("21-ok").addAll(n, i -> "chek-ok-" + (i + 1)).also()
                .add("21-check-state", "check-state").add("21-ext-final", "check-extend-final").add("23-final", "execute-to-right")

                .to("3-clear").addAll(n, i -> "final-clear-" + (i + 1)).also()
                .to("3-back").addAll(3, i -> "final-back-" + (i + 1)).also()
                .add("3-init", "final").add("3-ac", "final-accept").add("3-rj", "final-reject");

        NamespaceTree syms = new NamespaceTree();
        syms.addAll("src", "mark", "st")
                .to("src").addAll(symSet).also()
                .to("mark").addAll(symSet).also()
                .add("sep", "/", addMore("/"));

        AtomicInteger it = new AtomicInteger(0);
        states.to("src").forAll(st -> syms.to("st").add(st, "ST-" + it.incrementAndGet()));

        intRange(n).forEach(i -> {
            symSetOf[i].forEach(sym -> {
                states.to("21-ext").add(List.of(toStr(i), sym), "check-extend-" + (i + 1) + "-carry-" + sym);
                states.to("21-ext").add(List.of(toStr(i), syms.get("mark", sym)), "check-extend-" + (i + 1) + "-carry-" + syms.get("mark", sym));
            });
            states.to("21-ext").add(List.of(toStr(i), syms.get("sep")), "check-extend-" + (i + 1) + "-carry-sep");
        });

        Collection<ArrayList<String>> argTails = tails(m.getTransitions().args().stream()
                .map(TransitionArgument::getSymbols).map(Arrays::asList).map(ArrayList::new)
                .collect(Collectors.toList()));
        argTails.forEach(
                tail -> states.to("22").add(tail, "det" + (tail.isEmpty()? "" : "-" + String.join("/", tail)))
        );

        ArrayList<Map.Entry<TransitionArgument, TransitionResult>> trEnum = new ArrayList<>(m.getTransitions().flatEntries());
        intRange(trEnum.size()).forEach(trI -> intRange(n).forEach(sz -> {
            states.to("23").add(List.of(toStr(trI), toStr(sz)), "exe-tr" + (trI + 1) + "-" + (sz + 1));
            states.to("23-mark").add(List.of(toStr(trI), toStr(sz)), "exe-tr" + (trI + 1) + "-" + sz + "-mark");
        }));

        // 1. init start state

        syms.to("src").forAll(
                sym -> newTransitions.set(states.get("st"), sym, states.get(0, "1-init"), sym, LEFT)
        );
        newTransitions.set(states.get(0, "1-init"), blank, states.get(1, "1-init"), syms.get("sep"), LEFT);
        newTransitions.set(states.get(1, "1-init"), blank, states.get(2, "1-init"), syms.get("st", start), RIGHT);
        newTransitions.set(states.get(2, "1-init"), syms.get("sep"), states.get(2, "1-init"), syms.get("sep"), RIGHT);
        syms.to("src").forAll(
                sym -> newTransitions.set(states.get(2, "1-init"), sym, states.get(3, "1-init"), syms.get("mark", sym), RIGHT)
        );
        syms.to("src").selectWithout(blank).forEachSelected(newTransitions.goRight(states.get(3, "1-init")));
        newTransitions.set(states.get(3, "1-init"), blank, states.get(0, "1-tp-mark"), syms.get("sep"), RIGHT);

        intRange(n - 1).forEach(
                i -> newTransitions.set(states.get(i, "1-tp-mark"), blank, states.get(i, "1-tp-sep"), syms.get("mark", blank), RIGHT)
        );
        intRange(n - 2).forEach(
                i -> newTransitions.set(states.get(i, "1-tp-sep"), blank, states.get(i + 1, "1-tp-mark"), syms.get("sep"), RIGHT)
        );
        newTransitions.set(states.get(n - 2, "1-tp-sep"), blank, states.get(n - 1, "21-no-mark"), syms.get("sep"), LEFT);

        // 2.1. check for extra space exists; also check for terminal state

        intRange(n).forEach(i -> {
            symSetOf[i].forEach(sym -> {
                newTransitions.set(states.get(i, "21"), sym, states.get(i, "21"), sym, LEFT);
                newTransitions.set(states.get(i, "21"), syms.get("mark", sym), states.get(i, "21-no-sep"), syms.get("mark", sym), LEFT);
                newTransitions.set(states.get(i, "21-no-mark"), sym, states.get(i, "21"), sym, LEFT);
                newTransitions.set(states.get(i, "21-no-mark"), syms.get("mark", sym), states.get(List.of(toStr(i), blank), "21-ext"), syms.get("mark", sym), RIGHT);
                newTransitions.set(states.get(i, "21-no-sep"), sym, states.get(i, "21"), sym, LEFT);
            });
            newTransitions.set(states.get(i, "21"), syms.get("sep"), i == 0? states.get("21-check-state") : states.get(i - 1, "21-no-mark"), syms.get("sep"), LEFT);
            newTransitions.set(states.get(i, "21-no-sep"), syms.get("sep"), states.get(List.of(toStr(i), blank), "21-ext"), syms.get("sep"), RIGHT);

            syms.to("src").selectOnly(symSetOf[i]).forEachSelected(
                    sym -> syms.to("src").selectOnly(symSetOf[i]).forEachSelected(
                            sym2 -> newTransitions.set(states.get(List.of(toStr(i), sym), "21-ext"), sym2, states.get(List.of(toStr(i), sym2), "21-ext"), sym, RIGHT),
                            sym2 -> newTransitions.set(states.get(List.of(toStr(i), syms.get("mark", sym)), "21-ext"), sym2, states.get(List.of(toStr(i), sym2), "21-ext"), syms.get("mark", sym), RIGHT),
                            sym2 -> newTransitions.set(states.get(List.of(toStr(i), sym), "21-ext"), syms.get("mark", sym2), states.get(List.of(toStr(i), syms.get("mark", sym2)), "21-ext"), sym, RIGHT)
                    )
            );
            syms.to("src").selectOnly(symSetOf[i]).also().to("mark").selectOnly(symSetOf[i]).forEachSelected(
                    sym -> newTransitions.set(states.get(List.of(toStr(i), sym), "21-ext"), syms.get("sep"), i == n - 1? states.get("21-ext-final") : states.get(List.of(toStr(i + 1), syms.get("sep")), "21-ext"), sym, RIGHT)
            );
            newTransitions.set(states.get(List.of(toStr(i), syms.get("sep")), "21-ext"), blank, states.get(List.of(toStr(i), blank), "21-ext"), syms.get("sep"), RIGHT);
        });
        newTransitions.set(states.get("21-ext-final"), blank, states.get(n - 1, "21-no-mark"), syms.get("sep"), LEFT);

        states.to("src").selectWithout(accept, reject).forEachSelected(
                state -> newTransitions.set(states.get("21-check-state"), syms.get("st", state), states.get("21-check-state"), syms.get("st", state), RIGHT)
        );
        states.to("src").selectOnly(accept, reject).forEachSelected(
                state -> newTransitions.set(states.get("21-check-state"), syms.get("st", state), states.get("3-init"), syms.get("st", state), RIGHT)
        );
        newTransitions.set(states.get("21-check-state"), syms.get("sep"), states.get(0, "21-ok"), syms.get("sep"), RIGHT);

        intRange(n).forEach(i -> {
            syms.to("src").selectOnly(symSetOf[i]).also().to("mark").selectOnly(symSetOf[i]).forEachSelected(
                    sym -> newTransitions.set(states.get(i, "21-ok"), sym, states.get(i, "21-ok"), sym, RIGHT)
            );
            if (i != n - 1) newTransitions.set(states.get(i, "21-ok"), syms.get("sep"), states.get(i + 1, "21-ok"), syms.get("sep"), RIGHT);
            else            newTransitions.set(states.get(i, "21-ok"), syms.get("sep"), states.get(List.of(), "22"), syms.get("sep"), LEFT);
        });

        // 2.2. determine argument

        intRange(n + 1).forEach(i -> argTails.stream().filter(ls -> ls.size() == i).forEach(ls -> {
            syms.selectOnly("sep").to("src").selectOnly(i == 0? List.of() : symSetOf[n - i]).selectOnly(i == n? List.of() : symSetOf[n - i - 1]).forEachSelected(
                    newTransitions.goLeft(states.get(ls, "22"))
            );
            if (i != n) syms.to("src").selectOnly(symSetOf[n - 1 - i]).forEachSelected(sym -> {
                if (argTails.contains(arrayList(sym, ls)))
                    newTransitions.set(states.get(ls, "22"), syms.get("mark", sym), states.get(arrayList(sym, ls), "22"), syms.get("mark", sym), LEFT);
            });
        }));

        intRange(trEnum.size()).forEach(trI ->
                newTransitions.set(states.get(Arrays.asList(trEnum.get(trI).getKey().getSymbols()), "22"), syms.get("st", trEnum.get(trI).getKey().getState()),
                        states.get(List.of(toStr(trI), toStr(n - 1)), "23"), syms.get("st", trEnum.get(trI).getValue().getState()), RIGHT)
        );

        // 2.3. execute step

        intRange(trEnum.size()).forEach(trI -> intRange(n).forEach(sz -> {
            syms.selectOnly("sep").to("src").selectOnly(sz == n - 1? List.of() : symSetOf[n - 2 - sz]).selectOnly(symSetOf[n - 1 - sz]).forEachSelected(
                    newTransitions.goRight(states.get(List.of(toStr(trI), toStr(sz)), "23"))
            );
            newTransitions.set(states.get(List.of(toStr(trI), toStr(sz)), "23"), syms.get("mark", trEnum.get(trI).getKey().getSymbols()[n - 1 - sz]),
                    states.get(List.of(toStr(trI), toStr(sz)), "23-mark"), trEnum.get(trI).getValue().getSymbols()[n - 1 - sz], trEnum.get(trI).getValue().getDirections()[n - 1 - sz]);
            symSetOf[n - 1 - sz].forEach(sym ->
                    newTransitions.set(states.get(List.of(toStr(trI), toStr(sz)), "23-mark"), sym, sz == 0? states.get("23-final") : states.get(List.of(toStr(trI), toStr(sz - 1)), "23"), syms.get("mark", sym), RIGHT)
            );
        }));

        symSetOf[n - 1].forEach(newTransitions.goRight(states.get("23-final")));
        newTransitions.set(states.get("23-final"), syms.get("sep"), states.get(n - 1, "21-no-mark"), syms.get("sep"), LEFT);

        // 3. final steps

        newTransitions.set(states.get("3-init"), syms.get("sep"), states.get(0, "3-clear"), syms.get("sep"), RIGHT);
        intRange(n).forEach(i -> syms.to("src").selectOnly(symSetOf[i]).also().to("mark").selectOnly(symSetOf[i]).forEachSelected(
                sym -> newTransitions.set(states.get(i, "3-clear"), sym, states.get(i, "3-clear"), i == 0? sym : blank, RIGHT),
                sym -> newTransitions.set(states.get(i, "3-clear"), syms.get("sep"), i == n - 1? states.get(0, "3-back") : states.get(i + 1, "3-clear"), i == 0? syms.get("sep") : blank, i == n - 1? LEFT : RIGHT)
        ));
        newTransitions.set(states.get(0, "3-back"), blank, states.get(0, "3-back"), blank, LEFT);
        newTransitions.set(states.get(0, "3-back"), syms.get("sep"), states.get(1, "3-back"), blank, LEFT);
        syms.to("src").selectOnly(symSetOf[0]).also().to("mark").selectOnly(symSetOf[0]).forEachSelected(newTransitions.goLeft(states.get(1, "3-back")));
        newTransitions.set(states.get(1, "3-back"), syms.get("sep"), states.get(2, "3-back"), blank, LEFT);

        newTransitions.set(states.get(2, "3-back"), syms.get("st", accept), states.get("3-ac"), blank, RIGHT);
        newTransitions.set(states.get(2, "3-back"), syms.get("st", reject), states.get("3-rj"), blank, RIGHT);
        symSetOf[0].forEach(sym -> {
            newTransitions.set(states.get("3-ac"), sym, states.get("3-ac"), sym, RIGHT);
            newTransitions.set(states.get("3-rj"), sym, states.get("3-rj"), sym, RIGHT);
            newTransitions.set(states.get("3-ac"), syms.get("mark", sym), states.get("ac"), sym, STAY);
            newTransitions.set(states.get("3-rj"), syms.get("mark", sym), states.get("rj"), sym, STAY);
        });

        return removeUnreachableStates(TM.with(states.get("st"), states.get("ac"), states.get("rj"), blank, newTransitions));
    }

    private static TM mtmTmConvert2(MTM m) {
        int n = m.tapes();
        if (n == 1)
            return TM.with(m.getStartState(), m.getAcceptState(), m.getRejectState(), m.getBlank(), m.getTransitions());

        String start = m.getStartState(), accept = m.getAcceptState(), reject = m.getRejectState();
        String blank = m.getBlank();
        Set<String> stSet = m.getStatesSet(), symSet = m.getSymbolsSet();
        Set<String>[] symSetOf = new Set[n];
        for (int i = 0; i < n; i++)
            symSetOf[i] = m.getSymbolsSet(i);
        Transitions newTransitions = new Transitions(), mainTransitions = new Transitions();

        NamespaceTree states = new NamespaceTree();
        states.addAll("src", "1-ext", "1-str", "1-mark", "21", "22", "22-l", "22-r", "22-r-back", "22-fin")
                .addAll("3-clear", "3-cleared", "3-get", "3-carry", "3-set", "3-carry-more", "3-fin")
                .to("src").addAll(stSet).also()
                .add("st", start).add("ac", accept).add("rj", reject)

                .to("1-ext").addAll(n - 1, i -> "start-extend-" + (i + 1)).also()
                .add("1-more", "start-extend-more").add("1-ext-fin", "start-extended").add("1-str-more", "start-stretch-more")
                .to("1-str").addAllLists(multiply(range(n + 1), symSet), ls -> "start-set-" + ls.get(1) + "-" + (getInt(ls, 0) + 1)).also()
                .to("1-mark").addAll(n, i -> "start-mark-" + (i + 1)).also()

                .to("3-clear").addAllLists(multiply(range(n), List.of(accept, reject)), ls -> "final-tp" + (getInt(ls, 0) + 1) + "-" + ls.get(1) + "-clear").also()
                .to("3-cleared").addAll(List.of(accept, reject), term -> "final-cleared-" + term).also()
                .to("3-carry-more").addAll(List.of(accept, reject), term -> "final-carry-more-" + term).also()
                .to("3-get").addAllLists(multiply(List.of(accept, reject), range(n)), ls -> "final-get-" + (getInt(ls, 1) + 1) + "-" + ls.get(1)).also()
                .to("3-fin").addAllLists(multiply(List.of(accept, reject), range(3)), ls -> "final-" + ls.get(0) + "-" + (getInt(ls, 1) + 1));

        NamespaceTree syms = new NamespaceTree();
        syms.addAll("src", "mark")
                .to("src").addAll(symSet).also()
                .to("mark").addAll(symSet).also()
                .add("new-blank", "_", addMore("_")).add("marker", "*", addMore("*"));

        List.of(accept, reject).forEach(term ->
                syms.selectOnly("new-blank").to("src").selectOnly(symSetOf[0]).unselect(blank).also().to("mark").selectOnly(symSetOf[0]).forEachSelected(
                        sym -> states.to("3-carry").add(List.of(term, sym), "final-carry-" + sym + "-" + term),
                        sym -> states.to("3-set").add(List.of(term, sym), "final-set-" + sym + "-" + term)
                )
        );

        Collection<ArrayList<String>> args = mtmArgs(m.getTransitions().args(), syms.get("marker"), n);
        range(n).forEach(tape -> List.of(accept, reject).forEach(termSt ->
                args.add(arrayList(tape, termSt, repeat(syms.get("marker"), n)))
        ));
        args.forEach(
                arg -> states.to("21").add(arg, "det-t" + (getInt(arg, 0) + 1) + "-" + String.join("/", arg.subList(2, arg.size())) + "-" + arg.get(1))
        );

        ArrayList<Map.Entry<TransitionArgument, TransitionResult>> trEnum = new ArrayList<>(m.getTransitions().flatEntries());
        states.to("22").addAllLists(multCols(multiply(range(n), range(trEnum.size())), power(List.of("Y", "N"), n)),
                ls -> "exe-tp" + (getInt(ls, 0) + 1) + "-tr" + (getInt(ls, 1) + 1) + "-" + String.join("", ls.subList(2, 2 + n)));
        states.to("22-l").addAllLists(multCols(multiply(range(n), range(n), range(trEnum.size())), power(List.of("Y", "N"), n)),
                ls -> "exe-tp" + (getInt(ls, 0) + 1) + "-<-" + (getInt(ls, 1) + 1) + "-tr" + (getInt(ls, 2) + 1) + "-" + String.join("", ls.subList(3, 3 + n)));
        states.to("22-r").addAllLists(multCols(multiply(range(n), range(n), range(trEnum.size())), power(List.of("Y", "N"), n)),
                ls -> "exe-tp" + (getInt(ls, 0) + 1) + "->-" + (getInt(ls, 1) + 1) + "-tr" + (getInt(ls, 2) + 1) + "-" + String.join("", ls.subList(3, 3 + n)));
        states.to("22-r-back").addAllLists(multCols(multiply(range(n), range(n), range(trEnum.size())), power(List.of("Y", "N"), n)),
                ls -> "exe-tp" + (getInt(ls, 0) + 1) + "->-back-" + (getInt(ls, 1) + 1) + "-tr" + (getInt(ls, 2) + 1) + "-" + String.join("", ls.subList(3, 3 + n)));
        states.to("22-fin").addAllLists(multiply(range(n), range(2 * n + 1), stSet),
                ls -> "exe-tp" + (getInt(ls, 0) + 1) + "-final-" + (getInt(ls, 1) + 1) + "-" + ls.get(2));

        // 1. init

        syms.to("src").selectWithout(blank).forEachSelected(newTransitions.goRight(states.get("st")));
        newTransitions.set(states.get("st"), blank, states.get("1-more"), blank, LEFT);
        syms.selectOnly("new-blank").to("mark").selectWithout(blank).forEachSelected(newTransitions.goLeft(states.get("1-more")));
        syms.to("src").selectWithout(blank).forEachSelected(
                sym -> newTransitions.set(states.get("1-more"), sym, states.get(0, "1-ext"), syms.get("mark", sym), RIGHT)
        );
        syms.selectOnly("new-blank").to("mark").selectWithout(blank).forEachSelected(newTransitions.goRight(states.get(0, "1-ext")));
        intRange(n - 2).forEach(
                i -> newTransitions.set(states.get(i, "1-ext"), blank, states.get(i + 1, "1-ext"), syms.get("new-blank"), RIGHT)
        );
        newTransitions.set(states.get(n - 2, "1-ext"), blank, states.get("1-more"), syms.get("new-blank"), LEFT);
        newTransitions.set(states.get("1-more"), blank, states.get("1-ext-fin"), blank, RIGHT);
        syms.to("mark").selectWithout(blank).forEachSelected(newTransitions.goRight(states.get("1-ext-fin")));
        newTransitions.set(states.get("1-ext-fin"), syms.get("new-blank"), states.get("1-str-more"), syms.get("new-blank"), LEFT);
        newTransitions.set(states.get("1-ext-fin"), blank, states.get(0, "1-mark"), blank, RIGHT);

        syms.selectOnly("new-blank").to("src").selectWithout(blank).forEachSelected(newTransitions.goLeft(states.get("1-str-more")));
        syms.to("src").selectWithout(blank).forEachSelected(
                sym -> newTransitions.set(states.get("1-str-more"), syms.get("mark", sym), states.get(List.of("0", sym), "1-str"), syms.get("new-blank"), RIGHT),
                sym -> newTransitions.set(states.get(List.of("0", sym), "1-str"), syms.get("new-blank"), states.get(List.of("0", sym), "1-str"), syms.get("new-blank"), RIGHT),
                sym -> syms.to("src").forAll(
                        sym2 -> newTransitions.set(states.get(List.of("0", sym), "1-str"), sym2, states.get(List.of("1", sym), "1-str"), sym2, LEFT)
                ),
                sym -> intRange(n - 1).forEach(
                        i -> newTransitions.set(states.get(List.of(toStr(i + 1), sym), "1-str"), syms.get("new-blank"), states.get(List.of(toStr(i + 2), sym), "1-str"), syms.get("new-blank"), LEFT)
                ),
                sym -> newTransitions.set(states.get(List.of(toStr(n), sym), "1-str"), syms.get("new-blank"), states.get("1-str-more"), sym, LEFT)
        );
        newTransitions.set(states.get("1-str-more"), blank, states.get(0, "1-mark"), blank, RIGHT);

        symSetOf[0].forEach(
                sym -> newTransitions.set(states.get(0, "1-mark"), sym, states.get(1, "1-mark"), syms.get("mark", sym), RIGHT)
        );
        intRange(n - 1).forEach(i -> syms.selectOnly("new-blank").to("src").selectOnly(blank).forEachSelected(
                blnk -> newTransitions.set(states.get(i + 1, "1-mark"), blnk,
                        i == n - 2? states.get(arrayList("0", start, repeat(syms.get("marker"), n)), "21") : states.get(i + 2, "1-mark"), syms.get("mark", blank), RIGHT)
        ));

        // 2.1. determine argument

        args.forEach(arg -> symSetOf[getInt(arg, 0)].forEach(sym -> {
            int markersCnt = eqCnt(arg.subList(2, arg.size()), syms.get("marker"));
            if (markersCnt > 0)
                mainTransitions.set(states.get(arg, "21"), sym, states.get(toPrevTape(arg, n), "21"), sym, LEFT);
            if (markersCnt > 1 && eq(arg, 2 + getInt(arg, 0), syms.get("marker"))) {
                ArrayList<String> newArg = toPrevTape(set(arg, 2 + getInt(arg, 0), sym), n);
                if (args.contains(newArg))
                    mainTransitions.set(states.get(arg, "21"), syms.get("mark", sym), states.get(newArg, "21"), syms.get("mark", sym), LEFT);
            }
        }));

        intRange(trEnum.size()).forEach(trI -> intRange(n).forEach(tape -> {
            String argSt = trEnum.get(trI).getKey().getState();
            ArrayList<String> argSym = toList(trEnum.get(trI).getKey().getSymbols());
            mainTransitions.set(states.get(arrayList(toStr(tape), argSt, set(argSym, tape, syms.get("marker"))), "21"), syms.get("mark", argSym.get(tape)),
                    states.get(arrayList(toStr(prevMod(tape, n)), toStr(trI), repeat("N", n)), "22"), syms.get("mark", argSym.get(tape)), LEFT);
        }));

        // 2.2. execute step

        states.to("22").forAllListKeys(ls -> {
            int tape = getInt(ls, 0), tr = getInt(ls, 1), eqNCnt = eqCnt(ls.subList(2, ls.size()), "N");
            if (eqNCnt == 0) return;
            String reqArg = trEnum.get(tr).getKey().getSymbols()[tape], reqRes = trEnum.get(tr).getValue().getSymbols()[tape];
            String resSt = trEnum.get(tr).getValue().getState();
            symSetOf[tape].forEach(sym ->
                    mainTransitions.set(states.get(ls, "22"), sym, states.get(toNextTape(ls, n), "22"), sym, RIGHT)
            );
            if (eq(ls, 2 + tape, "N")) {
                switch (trEnum.get(tr).getValue().getDirections()[tape]) {
                    case STAY: mainTransitions.set(states.get(ls, "22"), syms.get("mark", reqArg),
                            eqNCnt == 1? states.get(List.of(toStr(nextMod(tape, n)), toStr(n - 1), resSt), "22-fin") : states.get(set(toNextTape(ls, n), 2 + tape, "Y"), "22"), syms.get("mark", reqRes), RIGHT); break;
                    case LEFT: mainTransitions.set(states.get(ls, "22"), syms.get("mark", reqArg), states.get(toPrevTape(insert(ls, 1, toStr(n - 1)), n), "22-l"), reqRes, LEFT); break;
                    case RIGHT: mainTransitions.set(states.get(ls, "22"), syms.get("mark", reqArg), states.get(toNextTape(insert(ls, 1, toStr(n - 1)), n), "22-r"), reqRes, RIGHT); break;
                }
            } else {
                syms.to("mark").selectOnly(symSetOf[tape]).forEachSelected(sym ->
                        mainTransitions.set(states.get(ls, "22"), sym, states.get(toNextTape(ls, n), "22"), sym, RIGHT)
                );
            }
        });

        states.to("22-l").forAllListKeys(ls -> {
            int tape = getInt(ls, 0), stLeft = getInt(ls, 1), tr = getInt(ls, 2), eqNCnt = eqCnt(ls.subList(2, ls.size()), "N");
            if (eqNCnt == 0) return;
            String reqRes = trEnum.get(tr).getValue().getSymbols()[tape], resSt = trEnum.get(tr).getValue().getState();
            symSetOf[tape].forEach(sym -> {
                if (stLeft == 0) {
                    if (eqNCnt == 1)
                        mainTransitions.set(states.get(ls, "22-l"), sym, states.get(List.of(toStr(nextMod(tape, n)), toStr(2 * n - 1), resSt), "22-fin"), syms.get("mark", sym), RIGHT);
                    else
                        mainTransitions.set(states.get(ls, "22-l"), sym, states.get(arrayList(toStr(nextMod(tape, n)), toStr(tr), set(ls.subList(3, 3 + n), tape, "Y")), "22"), syms.get("mark", sym), RIGHT);
                } else
                    mainTransitions.set(states.get(ls, "22-l"), sym, states.get(toPrevTape(dec(ls, 1), n), "22-l"), sym, LEFT);
            });
            if (stLeft != 0 && eq(ls, 3 + tape, "Y"))
                mainTransitions.set(states.get(ls, "22-l"), syms.get("mark", reqRes), states.get(toPrevTape(dec(ls, 1), n), "22-l"), syms.get("mark", reqRes), LEFT);
        });

        states.to("22-r").forAllListKeys(ls -> {
            int tape = getInt(ls, 0), stLeft = getInt(ls, 1), tr = getInt(ls, 2), eqNCnt = eqCnt(ls.subList(2, ls.size()), "N");
            if (eqNCnt == 0) return;
            String resSt = trEnum.get(tr).getValue().getState();
            symSetOf[tape].forEach(sym -> {
                if (stLeft == 0) {
                    if (eqNCnt == 1)
                        mainTransitions.set(states.get(ls, "22-r"), sym, states.get(arrayList(toStr(nextMod(tape, n)), resSt, repeat(syms.get("marker"), n)), "21"), syms.get("mark", sym), RIGHT);
                    else
                        mainTransitions.set(states.get(ls, "22-r"), sym, states.get(toPrevTape(set(ls, 1, toStr(n - 1)), n), "22-r-back"), syms.get("mark", sym), LEFT);
                } else
                    mainTransitions.set(states.get(ls, "22-r"), sym, states.get(toNextTape(dec(ls, 1), n), "22-r"), sym, RIGHT);
            });
            if (stLeft != 0) {
                syms.to("mark").selectOnly(symSetOf[tape]).forEachSelected(
                        sym -> mainTransitions.set(states.get(ls, "22-r"), sym, states.get(toNextTape(dec(ls, 1), n), "22-r"), sym, RIGHT)
                );
            }
        });

        states.to("22-r-back").forAllListKeys(ls -> {
            int tape = getInt(ls, 0), stLeft = getInt(ls, 1), tr = getInt(ls, 2), eqNCnt = eqCnt(ls.subList(2, ls.size()), "N");
            if (eqNCnt == 0) return;
            symSetOf[tape].forEach(sym -> {
                if (stLeft == 0) {
                    if (eqNCnt != 1)
                        mainTransitions.set(states.get(ls, "22-r-back"), sym, states.get(arrayList(toStr(nextMod(tape, n)), toStr(tr), set(ls.subList(3, 3 + n), tape, "Y")), "22"), sym, RIGHT);
                } else
                    mainTransitions.set(states.get(ls, "22-r-back"), sym, states.get(toPrevTape(dec(ls, 1), n), "22-r-back"), sym, LEFT);
            });
            if (stLeft != 0) {
                syms.to("mark").selectOnly(symSetOf[tape]).forEachSelected(
                        sym -> mainTransitions.set(states.get(ls, "22-r-back"), sym, states.get(toPrevTape(dec(ls, 1), n), "22-r-back"), sym, LEFT)
                );
            }
        });

        states.to("22-fin").forAllListKeys(ls -> symSetOf[getInt(ls, 0)].forEach(sym -> {
            if (getInt(ls, 1) == 0)
                mainTransitions.set(states.get(ls, "22-fin"), sym, states.get(arrayList(toStr(nextMod(getInt(ls, 0), n)), ls.get(2), repeat(syms.get("marker"), n)), "21"), sym, RIGHT);
            else {
                mainTransitions.set(states.get(ls, "22-fin"), sym, states.get(toNextTape(dec(ls, 1), n), "22-fin"), sym, RIGHT);
                mainTransitions.set(states.get(ls, "22-fin"), syms.get("mark", sym), states.get(toNextTape(dec(ls, 1), n), "22-fin"), syms.get("mark", sym), RIGHT);
            }
        }));

        newTransitions.setAll(mainTransitions.replaceBlanks(blank, syms.get("new-blank")));

        // 3. final steps

        Collection<String> markers = repeat(syms.get("marker"), n);
        intRange(n).forEach(tape -> syms.selectOnly("new-blank").to("src").selectOnly(symSetOf[tape]).unselect(blank).also().to("mark").selectOnly(symSetOf[tape]).forEachSelected(
                sym -> List.of(accept, reject).forEach(
                        term -> newTransitions.set(states.get(arrayList(toStr(tape), term, markers), "21"), sym, states.get(arrayList(toStr(nextMod(tape, n)), term, markers), "21"), sym, RIGHT)
                )
        ));
        intRange(n).forEach(tape -> List.of(accept, reject).forEach(term -> {
            newTransitions.set(states.get(arrayList(toStr(tape), term, markers), "21"), blank, states.get(List.of(toStr(prevMod(tape, n)), term), "3-clear"), blank, LEFT);
            syms.selectOnly("new-blank").to("src").selectOnly(symSetOf[tape]).unselect(blank).also().to("mark").selectOnly(symSetOf[tape]).forEachSelected(
                    sym -> newTransitions.set(states.get(List.of(toStr(tape), term), "3-clear"), sym, states.get(List.of(toStr(prevMod(tape, n)), term), "3-clear"), tape == 0 ? sym : blank, LEFT)
            );
            newTransitions.set(states.get(List.of(toStr(tape), term), "3-clear"), blank, states.get("3-cleared", term), blank, RIGHT);
        }));

        List.of(accept, reject).forEach(term -> {
            newTransitions.set(states.get("3-cleared", term), blank, states.get("3-cleared", term), blank, RIGHT);
            syms.selectOnly("new-blank").to("src").selectOnly(symSetOf[0]).unselect(blank).also().to("mark").selectOnly(symSetOf[0]).forEachSelected(
                    sym -> newTransitions.set(states.get("3-cleared", term), sym, states.get(List.of(term, toStr(n - 1)), "3-get"), sym, RIGHT)
            );
            intRange(n - 1).forEach(
                    i -> newTransitions.set(states.get(List.of(term, toStr(i + 1)), "3-get"), blank, states.get(List.of(term, toStr(i)), "3-get"), blank, RIGHT)
            );
            syms.selectOnly("new-blank").to("src").selectOnly(symSetOf[0]).unselect(blank).also().to("mark").selectOnly(symSetOf[0]).forEachSelected(
                    sym -> newTransitions.set(states.get(List.of(term, "0"), "3-get"), sym, states.get(List.of(term, sym), "3-carry"), syms.get("marker"), LEFT),
                    sym -> newTransitions.set(states.get(List.of(term, sym), "3-carry"), blank, states.get(List.of(term, sym), "3-carry"), blank, LEFT),
                    sym -> syms.selectOnly("new-blank").to("src").selectOnly(symSetOf[0]).unselect(blank).also().to("mark").selectOnly(symSetOf[0]).forEachSelected(
                            sym2 -> newTransitions.set(states.get(List.of(term, sym), "3-carry"), sym2, states.get(List.of(term, sym), "3-set"), sym2, RIGHT)
                    ),
                    sym -> newTransitions.set(states.get(List.of(term, sym), "3-set"), blank, states.get("3-carry-more", term), sym, RIGHT)
            );
            newTransitions.set(states.get("3-carry-more", term), blank, states.get("3-carry-more", term), blank, RIGHT);
            newTransitions.set(states.get("3-carry-more", term), syms.get("marker"), states.get(List.of(term, toStr(n - 1)), "3-get"), blank, RIGHT);
            newTransitions.set(states.get(List.of(term, "0"), "3-get"), blank, states.get(List.of(term, "0"), "3-fin"), blank, LEFT);

            newTransitions.set(states.get(List.of(term, "0"), "3-fin"), blank, states.get(List.of(term, "0"), "3-fin"), blank, LEFT);
            newTransitions.set(states.get(List.of(term, "0"), "3-fin"), syms.get("new-blank"), states.get(List.of(term, "1"), "3-fin"), blank, LEFT);
            syms.to("src").selectOnly(symSetOf[0]).unselect(blank).also().to("mark").selectOnly(symSetOf[0]).forEachSelected(
                    sym -> newTransitions.set(states.get(List.of(term, "0"), "3-fin"), sym, states.get(List.of(term, "1"), "3-fin"), sym, LEFT),
                    newTransitions.goLeft(states.get(List.of(term, "1"), "3-fin"))
            );
            newTransitions.set(states.get(List.of(term, "1"), "3-fin"), syms.get("new-blank"), states.get(List.of(term, "1"), "3-fin"), blank, LEFT);
            newTransitions.set(states.get(List.of(term, "1"), "3-fin"), blank, states.get(List.of(term, "2"), "3-fin"), blank, RIGHT);
            syms.to("src").selectOnly(symSetOf[0]).forEachSelected(
                    newTransitions.goRight(states.get(List.of(term, "2"), "3-fin")),
                    sym -> newTransitions.set(states.get(List.of(term, "2"), "3-fin"), syms.get("mark", sym), states.get(term.equals(accept)? "ac" : "rj"), sym, STAY)
            );
        });

        return removeUnreachableStates(TM.with(states.get("st"), states.get("ac"), states.get("rj"), blank, newTransitions));
    }


    private static NFA nfaNoEpsConvert(NFA m) {
        String eps = m.getEps();
        Graph<String> epsClosureInv = m.buildEpsGraph().closure().inverse();

        Set<String> newAccept = new TreeSet<>();
        Transitions newTransitions = new Transitions();

        for (String ac : m.getAcceptStates()) {
            newAccept.add(ac);
            newAccept.addAll(epsClosureInv.edgesFrom(ac));
        }
        for (Map.Entry<TransitionArgument, TransitionResult> tr : m.getTransitions().flatEntries()) {
            if (!tr.getKey().getSymbol().equals(eps)) {
                newTransitions.add(tr);
                for (String from : epsClosureInv.edgesFrom(tr.getKey().getState()))
                    newTransitions.add(from, tr.getKey().getSymbol(), tr.getValue().getState());
            }
        }

        return removeUnreachableStates(NFA.with(m.getStartState(), "eps", newAccept, newTransitions));
    }


    private static NFA dfaNfaConvert(DFA m) {
        return NFA.with(m.getStartState(), "eps", m.getAcceptStates(), m.getTransitions());
    }

    private static DFA nfaDfaConvert(NFA m) {
        NFA noEps = nfaNoEpsConvert(m);
        Transitions newTransitions = new Transitions();
        Set<String> newAccept = new TreeSet<>();

        String start = noEps.getStartState(), eps = noEps.getEps();
        Set<String> stSet = noEps.getStatesSet(), symSet = noEps.getSymbolsSet();

        NamespaceTree states = new NamespaceTree();
        states.addAll("src", "set")
                .to("src").addAll(stSet).also()
                .to("set").addAllLists(subsets(stSet), ls -> ls.isEmpty()? "none" : String.join("/", ls));

        NamespaceTree syms = new NamespaceTree();
        syms.addAll("src").to("src").addAll(symSet);

        LinkedList<TreeSet<String>> q = new LinkedList<>(List.of(new TreeSet<>(List.of(start))));
        HashSet<TreeSet<String>> visited = new HashSet<>();
        while (!q.isEmpty()) {
            TreeSet<String> curState = q.removeFirst();
            visited.add(curState);
            String curStateSt = states.get(curState, "set");
            if (curState.stream().anyMatch(curSt -> noEps.getAcceptStates().contains(curSt)))
                newAccept.add(curStateSt);
            syms.to("src").selectWithout(eps).forEachSelected(sym -> {
                TreeSet<String> toState = curState.stream().map(curSt -> noEps.getTransitions().getAll(curSt, sym))
                        .flatMap(List::stream).map(TransitionResult::getState).collect(Collectors.toCollection(TreeSet::new));
                if (!toState.isEmpty()) {
                    newTransitions.set(states.get(curState, "set"), sym, states.get(toState, "set"));
                    if (!visited.contains(toState))
                        q.addLast(toState);
                }
            });
        }

        return DFA.with(states.get(List.of(start), "set"), newAccept, newTransitions);
    }


    private static TM dfaTmConvert(DFA m) {
        String start = m.getStartState();
        Set<String> accept = m.getAcceptStates(), stSet = m.getStatesSet(), symSet = m.getSymbolsSet();
        Transitions newTransitions = new Transitions();

        NamespaceTree states = new NamespaceTree();
        states.addAll("src")
                .to("src").addAll(stSet).also()
                .add("ac", "accept").add("rj", "reject");

        NamespaceTree syms = new NamespaceTree();
        syms.addAll("src")
                .to("src").addAll(symSet).also()
                .add("blank", "_", addMore("_"));

        m.getTransitions().forEach((arg, res) -> newTransitions.set(arg, res.getState(), arg.getSymbol(), RIGHT));
        stSet.forEach(st -> newTransitions.set(st, syms.get("blank"), states.get(accept.contains(st)? "ac" : "rj"), syms.get("blank"), STAY));

        return TM.with(start, states.get("ac"), states.get("rj"), syms.get("blank"), newTransitions);
    }


    private static DCA dfaDcaConvert(DFA m) {
        NamespaceTree syms = new NamespaceTree().addAll("src").to("src").addAll(m.getSymbolsSet()).also().add("eps");
        return DCA.with(0, m.getStartState(), m.getAcceptStates(), syms.get("eps"), m.getTransitions());
    }

    private static DPDA dcaDpdaConvert(DCA m) {
        int stacks = m.tapes() - 1;
        Transitions newTransitions = new Transitions();
        String bottom = "Z", sep = ",", unit = "1", eps = m.getEps();

        m.getTransitions().forEach((arg, res) -> {
            ArrayList<String> argSym = new ArrayList<>(List.of(arg.getSymbols()[0])), resSym = new ArrayList<>();
            for (int i = 0; i < stacks; i++) {
                String topSym = arg.getSymbols()[i + 1].equals("=")? bottom : unit;
                argSym.add(topSym);
                switch (res.getSymbols()[i]) {
                    case "+1":
                        resSym.addAll(List.of(topSym, unit));
                        break;
                    case "0":
                        resSym.addAll(List.of(topSym));
                        break;
                    case "-1":
                        resSym.add(eps);
                        break;
                    default:
                        throw new IllegalStateException();
                }
                if (i != stacks - 1)
                    resSym.add(sep);
            }
            newTransitions.set(arg.getState(), toArray(argSym), res.getState(), toArray(resSym));
        });

        return DPDA.with(stacks, m.getStartState(), m.getAcceptStates(), eps, bottom, sep, newTransitions);
    }


    private static DPDA tmDpda2StacksConvert(TM m) {
        String start = m.getStartState(), accept = m.getAcceptState(), reject = m.getRejectState();
        String blank = m.getBlank();
        Set<String> stSet = m.getStatesSet(), symSet = m.getSymbolsSet();
        Transitions newTransitions = new Transitions();

        NamespaceTree states = new NamespaceTree();
        states.addAll("src", "fin")
                .to("src").addAll(stSet).also()
                .add("st", start).add("ac", accept).add("rj", reject)
                .add("1", start + "-2")
                .to("fin").addAllLists(multiply(range(3), List.of(accept, reject)), ls -> "final-" + (getInt(ls, 0) + 1) + "-" + ls.get(1));

        NamespaceTree syms = new NamespaceTree();
        syms.addAll("src")
                .to("src").addAll(symSet).also()
                .add("eps").add("bottom", "Z", addMore("*")).add("sep", ",", s -> s.equals(",")? "/" : s + "/");
        String eps = syms.get("eps"), bottom = syms.get("bottom"), sep = syms.get("sep");

        // 1. init

        syms.selectOnly("bottom").to("src").selectWithout(blank).forEachSelected(
                sym -> syms.to("src").selectWithout(blank).forEachSelected(
                        sym2 -> newTransitions.set(states.get("st"), array(sym2, sym, bottom), states.get("st"), array(sym, sym2, sep, bottom)),
                        sym2 -> newTransitions.set(states.get("1"), array(eps, sym2, sym), states.get("1"), array(eps, sep, sym, sym2))
                ),
                sym -> newTransitions.set(states.get("st"), array(eps, sym, bottom), states.get("1"), array(sym, sep, bottom)),
                sym -> newTransitions.set(states.get("1"), array(eps, bottom, sym), states.get("src", start), array(bottom, sep, sym))
        );

        // 2. main transitions

        m.getTransitions().select(arg -> !(arg.getState().equals(accept) || arg.getState().equals(reject))).forEach((arg, res) ->
                syms.selectOnly("bottom").to("src").selectAll().forEachSelected(sym -> {
                    String argSt = states.get("src", arg.getState()), resSt = states.get("src", res.getState());
                    String argSym = syms.get("src", arg.getSymbol()), resSym = syms.get("src", res.getSymbol());
                    switch (res.getDirection()) {
                        case STAY: newTransitions.set(argSt, array(eps, sym, argSym), resSt, array(sym, sep, resSym)); break;
                        case RIGHT: newTransitions.set(argSt, array(eps, sym, argSym), resSt, array(sym, resSym, sep, eps)); break;
                        case LEFT: newTransitions.set(argSt, array(eps, sym, argSym), resSt, array(sym.equals(bottom)? bottom : eps, sep, resSym, sym.equals(bottom)? blank : sym)); break;
                    }
                })
        );
        m.getTransitions().select(arg -> arg.getSymbol().equals(blank)).forEach((arg, res) ->
                syms.selectOnly("bottom").to("src").selectAll().forEachSelected(sym -> {
                    String argSt = states.get("src", arg.getState()), resSt = states.get("src", res.getState());
                    String resSym = syms.get("src", res.getSymbol());
                    switch (res.getDirection()) {
                        case STAY: newTransitions.set(argSt, array(eps, sym, bottom), resSt, array(sym, sep, bottom, resSym)); break;
                        case RIGHT: newTransitions.set(argSt, array(eps, sym, bottom), resSt, array(sym, resSym, sep, bottom)); break;
                        case LEFT: newTransitions.set(argSt, array(eps, sym, bottom), resSt, array(sym.equals(bottom)? bottom : eps, sep, bottom, resSym, sym.equals(bottom)? blank : sym)); break;
                    }
                })
        );
        List.of(accept, reject).forEach(term -> syms.selectOnly("bottom").to("src").selectAll().forEachSelected(sym ->
                syms.selectOnly("bottom").to("src").selectAll().forEachSelected(
                        sym2 -> newTransitions.set(term, array(eps, sym, sym2), states.get(List.of("0", term), "fin"), array(sym, sep, sym2))
                )
        ));

        // 3. final steps

        List.of(accept, reject).forEach(term -> {
            ArrayList<String> fin = IntStream.range(0, 4)
                    .mapToObj(i -> i == 3? (states.get(term.equals(accept)? "ac" : "rj")) : states.get(List.of(toStr(i), term), "fin"))
                    .collect(Collectors.toCollection(ArrayList::new));
            syms.selectOnly("bottom").to("src").selectAll().forEachSelected(
                    sym -> symSet.forEach(sym2 -> {
                        newTransitions.set(fin.get(0), array(eps, sym2, sym), fin.get(0), array(eps, sep, sym, sym2));
                        newTransitions.set(fin.get(2), array(eps, sym, sym2), fin.get(2), array(sym, sym2, sep, eps));
                    }),
                    sym -> newTransitions.set(fin.get(0), array(eps, bottom, sym), fin.get(1), array(bottom, sep, sym)),
                    sym -> newTransitions.set(fin.get(2), array(eps, sym, bottom), fin.get(3), array(sym, sep, bottom))
            );

            newTransitions.set(fin.get(1), array(eps, bottom, blank), fin.get(1), array(bottom, sep, eps));
            syms.selectOnly("bottom").to("src").selectWithout(blank).forEachSelected(
                    sym -> newTransitions.set(fin.get(1), array(eps, bottom, sym), fin.get(2), array(bottom, sep, sym))
            );
            newTransitions.set(fin.get(3), array(eps, blank, bottom), fin.get(3), array(eps, sep, bottom));
        });

        return DPDA.with(2, states.get("st"), Set.of(states.get("ac")), syms.get("eps"), bottom, sep, newTransitions);
    }

    private static TM dpdaTmConvert(DPDA m) {
        int n = m.tapes() - 1;
        if (n == 0)
            return dpdaNoStacksTmConvert(m);

        String start = m.getStartState(), eps = m.getEps(), bottom = m.getBound(), sep = m.getStackSep();
        Set<String> accept = m.getAcceptStates(), stSet = m.getStatesSet(), mainSymSet = m.getSymbolsSet();
        Set<String> allSymSet = new HashSet<>(mainSymSet);
        Set<String>[] stackSymSet = new Set[n];
        for (int i = 0; i < n; i++) {
            stackSymSet[i] = m.getSymbolsSet(1 + i);
            allSymSet.addAll(stackSymSet[i]);
        }
        mainSymSet.remove(eps);
        Transitions newTransitions = new Transitions();

        NamespaceTree states = new NamespaceTree();
        states.addAll("src", "1", "21", "21-skip", "22-step", "22-stay", "22-here", "22-skip", "22")
                .addAll("22-carry", "22-carry-back", "32-clear", "32-back")
                .to("src").addAll(stSet).also()
                .add("ac", "accept").add("rj", "reject")

                .to("1").addAll(4 + n, i -> i == 0? start : i == 1? "init-set-state" : i == 2? "init-mark" :
                        i == 3? "init-right" : "init-stack" + (i - 3)).also()
                .add("31-sym", "check-sym").add("31-st", "check-state").add("31-st-rj", "check-state-reject")
                .to("32-clear").addAllLists(multiply(range(n + 1), List.of("ac", "rj")),
                        ls -> "final-clear-stack" + getInt(ls, 0) + "-" + states.get(ls.get(1))).also()
                .to("32-back").addAll(List.of("ac", "rj"), term -> "final-back-" + states.get(term));

        NamespaceTree syms = new NamespaceTree();
        syms.addAll("src", "mark", "st")
                .to("src").addAll(allSymSet).also()
                .add("blank", "_", addMore("_")).add("marker", "*", addMore("*"))
                .to("mark").addAll(mainSymSet).add(syms.get("blank"));
        String blank = syms.get("blank"), marker = syms.get("marker");

        AtomicInteger it = new AtomicInteger(0);
        syms.to("st").addAll(stSet, st -> "ST-" + it.incrementAndGet());

        HashSet<ArrayList<String>> rawArgSrc = new HashSet<>(m.getTransitions().map((arg, res) -> toList(arg.getSymbols())));
        Collection<ArrayList<String>> argSrc = rawArgSrc.stream().filter(ls -> !eq(ls, 0, eps)).collect(Collectors.toList());
        rawArgSrc.stream().filter(ls -> eq(ls, 0, eps)).forEach(
                ls -> arrayList(blank, mainSymSet).stream().filter(sym -> !sym.equals(eps) && !rawArgSrc.contains(set(ls, 0, sym))).forEach(
                        sym -> argSrc.add(set(ls, 0, sym))
                )
        );
        Collection<ArrayList<String>> argTails = tails(argSrc);
        states.to("21").addAllLists(argTails, ls -> "det" + (ls.isEmpty()? "" : "-" + String.join("/", ls))).also()
                .to("21-skip").addAllLists(argTails, ls -> "det-skip" + (ls.isEmpty()? "" : "-" + String.join("/", ls)));

        Collection<ArrayList<String>> resSrc = removeElems(m.getTransitions().map((arg, res) -> toList(res.getSymbols())), eps, bottom);
        Collection<ArrayList<String>> resTails = tails(resSrc);
        states.to("22-step").addAllLists(resSrc, ls -> "exe-step" + (ls.isEmpty()? "" : "-" + String.join("/", ls))).also()
                .to("22-stay").addAllLists(resSrc, ls -> "exe-stay" + (ls.isEmpty()? "" : "-" + String.join("/", ls))).also()
                .to("22-here").addAllLists(resSrc, ls -> "exe-set-here" + (ls.isEmpty()? "" : "-" + String.join("/", ls))).also()
                .to("22-skip").addAllLists(resSrc, ls -> "exe-skip" + (ls.isEmpty()? "" : "-" + String.join("/", ls))).also()
                .to("22").addAllLists(resTails, ls -> "exe" + (ls.isEmpty()? "" : "-" + String.join("/", ls)));
        resTails.stream().filter(ls -> !(ls.isEmpty() || ls.get(0).equals(sep))).forEach(ls -> {
            int stacks = eqCnt(ls, sep) + 1;
            intRange(stacks).forEach(stacksLeft ->
                    syms.selectOnly("blank").to("src").selectOnly(stackSymSet[n - 1 - stacksLeft]).forEachSelected(stackSym ->
                            states.to("22-carry").add(arrayList(toStr(stacksLeft), stackSym, ls),
                                    "exe" + (ls.isEmpty()? "" : "-" + String.join("/", ls)) + "-" + toStr(stacksLeft) + "-carry-" + stackSym)
                    )
            );
            states.to("22-carry-back").add(ls, "exe" + (ls.isEmpty()? "" : "-" + String.join("/", ls)) + "-carry-back");
        });

        // 1. init

        syms.selectOnly("blank").to("src").selectOnly(mainSymSet).forEachSelected(
                sym -> newTransitions.set(states.get(0, "1"), sym, states.get(1, "1"), sym, LEFT),
                sym -> newTransitions.set(states.get(2, "1"), sym, states.get(sym.equals(blank)? 4 : 3, "1"), syms.get("mark", sym), RIGHT),
                sym -> newTransitions.set(states.get(3, "1"), sym, states.get(sym.equals(blank)? 4 : 3, "1"), sym, RIGHT)
        );
        newTransitions.set(states.get(1, "1"), blank, states.get(2, "1"), syms.get("st", start), RIGHT);

        intRange(n).forEach(
                i -> newTransitions.set(states.get(4 + i, "1"), blank, i == n - 1? states.get(List.of(), "21") : states.get(5 + i, "1"), bottom, RIGHT)
        );

        // 2.1. determine transition

        intRange(n + 2).forEach(sz -> argTails.stream().filter(arg -> arg.size() == sz && (sz == 0 || !arg.get(0).equals(eps))).forEach(arg -> {
            if (sz == n + 1) {
                mainSymSet.forEach(newTransitions.goLeft(states.get(arg, "21-skip")));
            } else if (sz == n) {
                syms.selectOnly("blank").to("src").selectOnly(mainSymSet).forEachSelected(
                        newTransitions.goLeft(states.get(arg, "21")),
                        sym -> {
                            if (argTails.contains(arrayList(sym, arg)) || argTails.contains(arrayList(eps, arg)))
                                newTransitions.set(states.get(arg, "21"), syms.get("mark", sym), states.get(arrayList(sym, arg), "21-skip"), syms.get("mark", sym), LEFT);
                            else
                                newTransitions.set(states.get(arg, "21"), syms.get("mark", sym), states.get(sym.equals(blank)? "31-st" : "31-st-rj"), syms.get("mark", sym), LEFT);
                        }
                );
                stackSymSet[0].forEach(sym ->
                        newTransitions.set(states.get(arg, "21-skip"), sym, states.get(arg, sym.equals(bottom)? "21" : "21-skip"), sym, LEFT)
                );
            } else {
                newTransitions.set(states.get(arg, "21"), blank, states.get(arg, "21"), blank, LEFT);
                for (String sym : stackSymSet[n - 1 - sz]) {
                    if (argTails.contains(arrayList(sym, arg)))
                        newTransitions.set(states.get(arg, "21"), sym, states.get(arrayList(sym, arg), sym.equals(bottom)? "21" : "21-skip"), sym.equals(bottom)? sym : blank, LEFT);
                    else
                        newTransitions.set(states.get(arg, "21"), sym, states.get("31-sym"), sym, LEFT);
                }
                if (sz != 0)
                    stackSymSet[n - sz].forEach(sym ->
                            newTransitions.set(states.get(arg, "21-skip"), sym, states.get(arg, sym.equals(bottom)? "21" : "21-skip"), sym, LEFT)
                    );
            }
        }));

        states.to("src").forAll(st -> argSrc.forEach(arg -> {
            TransitionResult res = m.getTransitions().get(st, toArray(arg));
            if (res != null) {
                newTransitions.set(states.get(arg, "21-skip"), syms.get("st", st),
                        states.get(removeElems(toList(res.getSymbols()), eps, bottom), "22-step"), syms.get("st", res.getState()), RIGHT);
            } else {
                res = m.getTransitions().get(st, toArray(set(arg, 0, eps)));
                if (res != null) {
                    newTransitions.set(states.get(arg, "21-skip"), syms.get("st", st),
                            states.get(removeElems(toList(res.getSymbols()), eps, bottom), "22-stay"), syms.get("st", res.getState()), RIGHT);
                } else if (accept.contains(st) && arg.get(0).equals(blank)) {
                    newTransitions.set(states.get(arg, "21-skip"), syms.get("st", st), states.get(List.of("0", "ac"), "32-clear"), blank, RIGHT);
                } else {
                    newTransitions.set(states.get(arg, "21-skip"), syms.get("st", st), states.get(List.of("0", "rj"), "32-clear"), blank, RIGHT);
                }
            }
        }));

        // 2.2. execute step

        resSrc.forEach(res -> {
            mainSymSet.forEach(sym -> {
                newTransitions.goRight(states.get(res, "22-step")).accept(sym);
                newTransitions.set(states.get(res, "22-step"), syms.get("mark", sym), states.get(res, "22-here"), sym, RIGHT);
                newTransitions.goRight(states.get(res, "22-stay")).accept(sym);
            });
            syms.selectOnly("blank").to("src").selectOnly(mainSymSet).forEachSelected(sym -> {
                newTransitions.set(states.get(res, "22-stay"), syms.get("mark", sym), states.get(res, "22-skip"), syms.get("mark", sym), RIGHT);
                newTransitions.set(states.get(res, "22-here"), sym, states.get(res, "22-skip"), syms.get("mark", sym), RIGHT);
                newTransitions.set(states.get(res, "22-skip"), sym, states.get(res, "22-skip"), sym, RIGHT);
            });
            newTransitions.set(states.get(res, "22-skip"), bottom, states.get(res, "22"), bottom, RIGHT);
        });

        resTails.forEach(res -> {
            int stacks = eqCnt(res, sep) + 1;
            syms.to("src").selectOnly(stackSymSet[n - stacks]).unselect(bottom)
                    .forEachSelected(newTransitions.goRight(states.get(res, "22"))
            );
            if (res.isEmpty()) {
                newTransitions.set(states.get(res, "22"), blank, states.get(List.of(), "21"), blank, LEFT);
            } else if (res.get(0).equals(sep)) {
                newTransitions.set(states.get(res, "22"), blank, states.get(res, "22"), blank, RIGHT);
                newTransitions.set(states.get(res, "22"), bottom, states.get(res.subList(1, res.size()), "22"), bottom, RIGHT);
            } else {
                newTransitions.set(states.get(res, "22"), blank, states.get(res.subList(1, res.size()), "22"), res.get(0), RIGHT);
                if (stacks > 1)
                    newTransitions.set(states.get(res, "22"), bottom, states.get(arrayList(toStr(stacks - 2), bottom, res), "22-carry"), marker, RIGHT);
            }
        });

        resTails.stream().filter(ls -> !(ls.isEmpty() || ls.get(0).equals(sep))).forEach(ls -> {
            int stacks = eqCnt(ls, sep) + 1;
            intRange(stacks).forEach(stacksLeft ->
                    syms.selectOnly("blank").to("src").selectOnly(stackSymSet[n - 1 - stacksLeft]).forEachSelected(sym -> {
                        syms.selectOnly("blank").to("src").selectOnly(stackSymSet[n - 1 - stacksLeft]).unselect(bottom).forEachSelected(sym2 ->
                                newTransitions.set(states.get(arrayList(toStr(stacksLeft), sym, ls), "22-carry"), sym2,
                                        stacksLeft == 0 && sym2.equals(blank)? states.get(ls, "22-carry-back") : states.get(arrayList(toStr(stacksLeft), sym2, ls), "22-carry"), sym, RIGHT)
                        );
                        if (stacksLeft != 0)
                            newTransitions.set(states.get(arrayList(toStr(stacksLeft), sym, ls), "22-carry"), bottom,
                                    states.get(arrayList(toStr(stacksLeft - 1), bottom, ls), "22-carry"), sym, RIGHT);
                    })
            );
            syms.selectOnly("blank").to("src").selectOnly(allSymSet).forEachSelected(
                    newTransitions.goLeft(states.get(ls, "22-carry-back"))
            );
            newTransitions.set(states.get(ls, "22-carry-back"), marker, states.get(ls.subList(1, ls.size()), "22"), ls.get(0), RIGHT);
        });

        // 3.1. check for acceptance

        allSymSet.forEach(newTransitions.goLeft(states.get("31-sym")));
        syms.selectOnly("blank").to("src").selectOnly(mainSymSet).forEachSelected(
                sym -> newTransitions.set(states.get("31-sym"), syms.get("mark", sym),
                        states.get(sym.equals(blank)? "31-st" : "31-st-rj"), syms.get("mark", sym), LEFT)
        );
        List.of("31-st", "31-st-rj").forEach(stId -> mainSymSet.forEach(newTransitions.goLeft(states.get(stId))));
        states.to("src").forAll(st -> {
            newTransitions.set(states.get("31-st"), syms.get("st", st), states.get(List.of("0", accept.contains(st)? "ac" : "rj"), "32-clear"), blank, RIGHT);
            newTransitions.set(states.get("31-st-rj"), syms.get("st", st), states.get(List.of("0", "rj"), "32-clear"), blank, RIGHT);
        });

        // 3.2. final steps

        List.of("ac", "rj").forEach(term -> {
            syms.selectOnly("blank").to("src").selectOnly(mainSymSet).also().to("mark").selectOnly(mainSymSet).selectOnly(blank)
                    .forEachSelected(newTransitions.goRight(states.get(List.of("0", term), "32-clear")));
            newTransitions.set(states.get(List.of("0", term), "32-clear"), bottom, states.get(List.of("1", term), "32-clear"), blank, RIGHT);
            intRange(n).forEach(stack -> {
                String stackCur = toStr(stack + 1), stackNext = toStr(stack + 2);
                syms.selectOnly("blank").to("src").selectOnly(stackSymSet[stack]).forEachSelected(sym -> {
                    String newState = stack != n - 1 && sym.equals(bottom)? states.get(List.of(stackNext, term), "32-clear") :
                            stack == n - 1 && sym.equals(blank)? states.get("32-back", term) : states.get(List.of(stackCur, term), "32-clear");
                    newTransitions.set(states.get(List.of(stackCur, term), "32-clear"), sym, newState, blank, RIGHT);
                });
            });
            syms.selectOnly("blank").to("src").selectOnly(mainSymSet).forEachSelected(
                    newTransitions.goLeft(states.get("32-back", term)),
                    sym -> newTransitions.set(states.get("32-back", term), syms.get("mark", sym), states.get(term), sym, STAY)
            );
        });

        return removeUnreachableStates(TM.with(states.get(0, "1"), states.get("ac"), states.get("rj"), blank, newTransitions));
    }
    private static TM dpdaNoStacksTmConvert(DPDA m) {
        String start = m.getStartState(), eps = m.getEps();
        Set<String> accept = m.getAcceptStates(), stSet = m.getStatesSet(), symSet = m.getSymbolsSet();
        Transitions newTransitions = new Transitions();

        NamespaceTree states = new NamespaceTree();
        states.addAll("src")
                .to("src").addAll(stSet).also()
                .add("ac", "accept").add("rj", "reject");

        NamespaceTree syms = new NamespaceTree();
        syms.addAll("src")
                .to("src").addAll(symSet).also()
                .add("blank", "_", addMore("_"));
        String blank = syms.get("blank");

        m.getTransitions().forEach((arg, res) -> {
            if (arg.getSymbols()[0].equals(eps)) {
                HashSet<String> missingSyms = new HashSet<>(symSet);
                missingSyms.add(blank);
                missingSyms.removeAll(m.getTransitions().select(arg2 -> arg2.getState().equals(arg.getState())).map((arg3, res3) -> arg3.getSymbols()[0]));
                for (String sym : missingSyms)
                    newTransitions.set(arg.getState(), sym, res.getState(), sym, STAY);
            } else
                newTransitions.set(arg.getState(), arg.getSymbols()[0], res.getState(), arg.getSymbols()[0], RIGHT);
        });

        for (String st : stSet)
            newTransitions.set(st, blank, states.get(accept.contains(st)? "ac" : "rj"), blank, STAY);

        return TM.with(start, states.get("ac"), states.get("rj"), blank, newTransitions);
    }


    private static DCA dpdaDcaNPlus1CountersConvert(DPDA m) {
        int n = m.tapes() - 1;
        if (n == 0)
            return DCA.with(0, m.getStartState(), m.getAcceptStates(), m.getEps(), m.getTransitions());

        String start = m.getStartState(), eps = m.getEps(), bottom = m.getBound(), sep = m.getStackSep();
        Set<String> accept = m.getAcceptStates(), stSet = m.getStatesSet(), mainSymSet = m.getSymbolsSet();
        Set<String> allSymSet = new HashSet<>(mainSymSet);
        Set<String>[] stackSymSet = new Set[n];
        String[][] stackSymEnum = new String[n][];
        for (int i = 0; i < n; i++) {
            stackSymSet[i] = m.getSymbolsSet(1 + i);
            stackSymEnum[i] = new String[stackSymSet[i].size()];
            stackSymEnum[i][0] = bottom;
            int j = 0;
            for (String sym : stackSymSet[i]) {
                if (!sym.equals(bottom))
                    stackSymEnum[i][++j] = sym;
            }
            allSymSet.addAll(stackSymSet[i]);
        }
        mainSymSet.remove(eps);
        Transitions newTransitions = new Transitions();

        NamespaceTree states = new NamespaceTree();
        states.addAll("src", "det", "exe")
                .to("src").add(start).addAll(accept);

        // 1. init

        return DCA.with(n + 1, null, null, eps, newTransitions);
    }

    private static DCA dca2CountersConvert(DCA m) {
        int counters = m.tapes() - 1;
        if (counters <= 2)
            return m;

        int[] p = primes(counters);
        final String EQ = "=", POS = ">", INC = "+1", ZERO = "0", DEC = "-1";
        String start = m.getStartState(), eps = m.getEps();
        Set<String> accept = m.getAcceptStates(), stSet = m.getStatesSet(), symSet = m.getSymbolsSet();
        Transitions newTransitions = new Transitions();

        Collection<ArrayList<String>> argTails =
                dcaTails(m.getTransitions().args(), 1, counters);
        argTails.addAll(m.getStatesSet().stream().map(st -> new ArrayList<>(List.of(st))).collect(Collectors.toList()));
        Collection<ArrayList<String>> resTails =
                dcaTails(m.getTransitions().results().stream().map(TransitionResult::asArgument).collect(Collectors.toList()), 0, counters);

        NamespaceTree states = new NamespaceTree();
        states.addAll("src", "2", "2-restore", "3", "3-back")
                .to("src").addAll(stSet).also().add("st", start);
        argTails.forEach(arg -> {
            if (arg.size() != counters + 1) {
                for (int i = 0; i < p[counters - arg.size()]; i++)
                    states.to("2").add(arrayList(toStr(i), arg), "det-" + arg.get(0) +
                            (arg.size() == 1? "" : "-" + String.join("/", arg.subList(1, arg.size()))) + "-" + i);
            }
            if (arg.size() != 1) {
                for (int i = 0; i < p[counters + 1 - arg.size()]; i++)
                    states.to("2-restore").add(arrayList(toStr(i), arg), "det-" + arg.get(0) +
                            (arg.size() == 1? "" : "-" + String.join("/", arg.subList(1, arg.size()))) + "-" + i + "-restore");
            }
        });
        resTails.forEach(res -> {
            if (res.size() != 1) {
                for (int i = 0; i < p[counters + 1 - res.size()]; i++)
                    states.to("3").add(arrayList(toStr(i), res), "exe-" + res.get(0) +
                            (res.size() == 1? "" : "-" + String.join("/", res.subList(1, res.size()))) + "-" + i);
            }
            if (res.size() != counters + 1) {
                states.to("3-back").add(res, "exe-" + res.get(0) +
                        (res.size() == 1? "" : "-" + String.join("/", res.subList(1, res.size()))) + "-back");
            }
        });

        // 1. init

        newTransitions.set(states.get("st"), array(eps, EQ, EQ), states.get(List.of("0", start), "2"), array(INC, ZERO));

        // 2. determine transition

        argTails.forEach(arg -> {
            if (arg.size() != counters + 1) {
                int mod = p[counters - arg.size()];
                for (int i = 0; i < mod; i++) {
                    int fi = i;
                    List.of(EQ, POS).forEach(sgn -> {
                        newTransitions.set(states.get(arrayList(toStr(fi), arg), "2"), array(eps, POS, sgn),
                                states.get(arrayList(toStr(nextMod(fi, mod)), arg), "2"), array(DEC, fi == mod - 1? INC : ZERO));
                        if (argTails.contains(insert(arg, 1, fi == 0? POS : EQ))) {
                            newTransitions.set(states.get(arrayList(toStr(fi), arg), "2"), array(eps, EQ, sgn),
                                    states.get(arrayList(toStr(fi), insert(arg, 1, fi == 0? POS : EQ)), "2-restore"), array(ZERO, ZERO));
                        } else {
                            newTransitions.set(states.get(arrayList(toStr(fi), arg), "2"), array(eps, EQ, sgn),
                                    states.get("src", arg.get(0)), array(ZERO, ZERO));
                        }
                    });
                }
            }
            if (arg.size() != 1) {
                int mod = p[counters + 1 - arg.size()];
                for (int i = 0; i < mod; i++) {
                    int fi = i;
                    List.of(EQ, POS).forEach(sgn -> List.of(EQ, POS).forEach(sgn2 -> {
                        if (fi == 0 && sgn2.equals(EQ)) {
                            if (sgn.equals(POS)) {
                                if (arg.size() == counters + 1) {
                                    for (String sym : symSet) {
                                        String[] syms = toArray(arrayList(sym, arg.subList(1, arg.size())));
                                        TransitionResult res = m.getTransitions().get(arg.get(0), syms);
                                        if (res != null) {
                                            newTransitions.set(states.get(arrayList("0", arg), "2-restore"), array(sym, POS, EQ),
                                                    states.get(arrayList("0", res.getState(), toList(res.getSymbols())), "3"), array(ZERO, ZERO));
                                        } else if (sym.equals(eps)) {
                                            newTransitions.set(states.get(arrayList("0", arg), "2-restore"), array(sym, POS, EQ),
                                                    states.get("src", arg.get(0)), array(ZERO, ZERO));
                                        }
                                    }
                                } else {
                                    newTransitions.set(states.get(arrayList("0", arg), "2-restore"), array(eps, POS, EQ),
                                            states.get(arrayList("0", arg), "2"), array(ZERO, ZERO));
                                }
                            }
                        } else {
                            newTransitions.set(states.get(arrayList(toStr(fi), arg), "2-restore"), array(eps, sgn, sgn2),
                                    states.get(arrayList(toStr(prevMod(fi, mod)), arg), "2-restore"), array(INC, fi == 0 ? DEC : ZERO));
                        }
                    }));
                }
            }
        });

        m.getTransitions().forEach((arg, res) -> {
            String fromSt = arg.getState(), toSt = res.getState(), fromSym = arg.getSymbols()[0];
            String[] fromArg = subArray(arg.getSymbols(), 1, arg.getSymbols().length), toArg = res.getSymbols();

        });

        // 3. execute step

        resTails.forEach(res -> {
            if (res.size() != 1) {
                int mod = p[counters + 1 - res.size()];
                switch (res.get(1)) {
                    case "0":
                        if (res.size() == 2)
                            newTransitions.set(states.get(arrayList("0", res), "3"), array(eps, POS, EQ),
                                    states.get(List.of("0", res.get(0)), "2"), array(ZERO, ZERO));
                        else
                            newTransitions.set(states.get(arrayList("0", res), "3"), array(eps, POS, EQ),
                                    states.get(arrayList("0", remove(res, 1)), "3"), array(ZERO, ZERO));
                        break;
                    case "+1":
                        for (int i = 0; i < mod; i++) {
                            newTransitions.set(states.get(arrayList(toStr(i), res), "3"), array(eps, POS, POS),
                                    states.get(arrayList(toStr(nextMod(i, mod)), res), "3"), array(i == mod - 1? DEC : ZERO, INC));
                        }
                        newTransitions.set(states.get(arrayList("0", res), "3"), array(eps, POS, EQ),
                                states.get(arrayList("1", res), "3"), array(ZERO, INC));
                        newTransitions.set(states.get(arrayList("0", res), "3"), array(eps, EQ, POS),
                                states.get(remove(res, 1), "3-back"), array(ZERO, ZERO));
                        break;
                    case "-1":
                        List.of(EQ, POS).forEach(sgn -> {
                            for (int i = 0; i < mod; i++) {
                                newTransitions.set(states.get(arrayList(toStr(i), res), "3"), array(eps, POS, sgn),
                                        states.get(arrayList(toStr(nextMod(i, mod)), res), "3"), array(DEC, i == mod - 1? INC : ZERO));
                            }
                        });
                        newTransitions.set(states.get(arrayList("0", res), "3"), array(eps, EQ, POS),
                                states.get(remove(res, 1), "3-back"), array(ZERO, ZERO));
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
            if (res.size() != counters + 1) {
                List.of(EQ, POS).forEach(sgn ->
                        newTransitions.set(states.get(res, "3-back"), array(eps, sgn, POS), states.get(res, "3-back"), array(INC, DEC))
                );
                if (res.size() == 1)
                    newTransitions.set(states.get(res, "3-back"), array(eps, POS, EQ), states.get(List.of("0", res.get(0)), "2"), array(ZERO, ZERO));
                else
                    newTransitions.set(states.get(res, "3-back"), array(eps, POS, EQ), states.get(arrayList("0", res), "3"), array(ZERO, ZERO));
            }
        });

        return DCA.with(2, states.get("st"), accept, eps, newTransitions);
    }



    private static TM removeUnreachableStates(TM m) {
        return TM.with(
                m.getStartState(), m.getAcceptState(), m.getRejectState(), m.getBlank(),
                m.getTransitions().removeUnreachableStates(m.getStartState(), m.getAcceptState(), m.getRejectState())
        );
    }

    private static OneTM removeUnreachableStates(OneTM m) {
        return OneTM.with(
                m.getStartState(), m.getAcceptState(), m.getRejectState(), m.getBlank(), m.getBound(),
                m.getTransitions().removeUnreachableStates(m.getStartState(), m.getAcceptState(), m.getRejectState())
        );
    }

    private static NFA removeUnreachableStates(NFA m) {
        return NFA.with(
                m.getStartState(), m.getEps(), m.getAcceptStates(),
                m.getTransitions().removeUnreachableStates(m.getStartState(), null, null)
        );
    }

}