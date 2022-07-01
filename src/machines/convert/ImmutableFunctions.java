package machines.convert;

import machines.TransitionArgument;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

abstract public class ImmutableFunctions {

    @SafeVarargs
    static Collection<ArrayList<String>> multiply(Collection<String>... args) {
        if (args.length == 0)
            return List.of(new ArrayList<>());
        Collection<String>[] subArgs = new Collection[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, args.length - 1);
        Collection<ArrayList<String>> subRes = multiply(subArgs);
        Collection<ArrayList<String>> res = new ArrayList<>();
        for (String s : args[0]) {
            for (ArrayList<String> rest : subRes) {
                ArrayList<String> newElem = new ArrayList<>();
                newElem.add(s);
                newElem.addAll(rest);
                res.add(newElem);
            }
        }
        return res;
    }

    @SafeVarargs
    static Collection<ArrayList<String>> multCols(Collection<ArrayList<String>>... cols) {
        if (cols.length == 0)
            return List.of(new ArrayList<>());
        Collection<ArrayList<String>>[] subArgs = new Collection[cols.length - 1];
        System.arraycopy(cols, 1, subArgs, 0, cols.length - 1);
        Collection<ArrayList<String>> subRes = multCols(subArgs);
        Collection<ArrayList<String>> res = new ArrayList<>();
        for (ArrayList<String> s : cols[0]) {
            for (ArrayList<String> rest : subRes) {
                ArrayList<String> newElem = new ArrayList<>();
                newElem.addAll(s);
                newElem.addAll(rest);
                res.add(newElem);
            }
        }
        return res;
    }

    static Collection<ArrayList<String>> power(Collection<String> col, int n) {
        if (n == 0) {
            return List.of(new ArrayList<>());
        } else {
            ArrayList<ArrayList<String>> pow = new ArrayList<>();
            Collection<ArrayList<String>> powPrev = power(col, n - 1);
            for (ArrayList<String> ls : powPrev) {
                for (String s : col) {
                    ArrayList<String> newList = new ArrayList<>(ls);
                    newList.add(s);
                    pow.add(newList);
                }
            }
            return pow;
        }
    }

    static Collection<ArrayList<String>> subsets(Collection<String> col) {
        ArrayList<String> ls = toList(col);
        ArrayList<ArrayList<String>> subsets = new ArrayList<>();
        for (int mask = 0; mask < (1 << ls.size()); mask++) {
            TreeSet<String> subset = new TreeSet<>();
            for (int bit = 0; bit < ls.size(); bit++) {
                if ((mask >> bit) % 2 == 1)
                    subset.add(ls.get(bit));
            }
            subsets.add(toList(subset));
        }
        return subsets;
    }

    static Collection<ArrayList<String>> tails(Collection<ArrayList<String>> lists) {
        HashSet<ArrayList<String>> tails = new HashSet<>();
        for (ArrayList<String> ls : lists) {
            for (int i = 0; i <= ls.size(); i++)
                tails.add(new ArrayList<>(ls.subList(i, ls.size())));
        }
        return tails;
    }

    static Collection<ArrayList<String>> mtmArgs(Collection<TransitionArgument> args, String marker, int tapes) {
        HashSet<ArrayList<String>> mtmArgs = new HashSet<>();
        for (TransitionArgument arg : args) {
            for (int mask = 0; mask < (1 << tapes); mask++) {
                ArrayList<String> mtmArg = new ArrayList<>();
                mtmArg.add(arg.getState());
                for (int bit = 0; bit < tapes; bit++)
                    mtmArg.add((mask >> bit) % 2 == 1 ? arg.getSymbols()[bit] : marker);
                for (int i = 0; i < tapes; i++)
                    mtmArgs.add(arrayList(toStr(i), mtmArg));
            }
        }
        return mtmArgs;
    }

    static Collection<ArrayList<String>> dpdaTails(Collection<TransitionArgument> args) {

    }

    static Collection<ArrayList<String>> dcaTails(Collection<TransitionArgument> args, int offset, int counters) {
        HashSet<ArrayList<String>> argTails = new HashSet<>();
        for (TransitionArgument arg : args) {
            for (int i = offset; i <= counters + offset; i++)
                argTails.add(arrayList(arg.getState(), toList(arg.getSymbols()).subList(i, counters + offset)));
        }
        return argTails;
    }

    static String[] array(String... args) {
        return args;
    }

    public static String[] subArray(String[] array, int from, int to) {
        String[] subArray = new String[to - from];
        System.arraycopy(array, from, subArray, 0, to - from);
        return subArray;
    }

    static ArrayList<String> arrayList(String first, Collection<String> tail) {
        ArrayList<String> list = new ArrayList<>();
        list.add(first);
        list.addAll(tail);
        return list;
    }

    static ArrayList<String> arrayList(String first, String second, Collection<String> tail) {
        ArrayList<String> list = new ArrayList<>();
        list.addAll(List.of(first, second));
        list.addAll(tail);
        return list;
    }

    static ArrayList<String> toList(Collection<String> col) {
        return new ArrayList<>(col);
    }

    static Collection<String> repeat(String elem, int n) {
        return Collections.nCopies(n, elem);
    }

    static ArrayList<String> removeElems(ArrayList<String> list, String... elems) {
        ArrayList<String> newList = new ArrayList<>();
        HashSet<String> blackList = new HashSet<>(Arrays.asList(elems));
        for (String elem : list) {
            if (!blackList.contains(elem))
                newList.add(elem);
        }
        return newList;
    }

    static Collection<ArrayList<String>> removeElems(Collection<ArrayList<String>> lists, String... elems) {
        HashSet<ArrayList<String>> newLists = new HashSet<>();
        HashSet<String> blackList = new HashSet<>(Arrays.asList(elems));
        for (ArrayList<String> ls : lists) {
            ArrayList<String> newList = new ArrayList<>();
            for (String elem : ls) {
                if (!blackList.contains(elem))
                    newList.add(elem);
            }
            newLists.add(newList);
        }
        return newLists;
    }

    static List<Integer> intRange(int n) {
        return IntStream.range(0, n).boxed().collect(Collectors.toList());
    }

    static List<String> range(int n) {
        return IntStream.range(0, n).mapToObj(Integer::toString).collect(Collectors.toList());
    }

    static String toStr(int n) {
        return Integer.toString(n);
    }

    static int getInt(ArrayList<String> ls, int pos) {
        return Integer.parseInt(ls.get(pos));
    }

    @SafeVarargs
    static <T> T switchInt(String n, T... branches) {
        return branches[Integer.parseInt(n)];
    }

    @SafeVarargs
    static <T> T switchInt(ArrayList<String> ls, int pos, T... branches) {
        return branches[Integer.parseInt(ls.get(pos))];
    }

    static int nextMod(int i, int mod) {
        return i == mod - 1? 0 : i + 1;
    }

    static int prevMod(int i, int mod) {
        return i == 0? mod - 1 : i - 1;
    }

    static ArrayList<String> toNextTape(ArrayList<String> args, int tapes) {
        return set(args, 0, toStr(nextMod(getInt(args, 0), tapes)));
    }

    static ArrayList<String> toPrevTape(ArrayList<String> args, int tapes) {
        return set(args, 0, toStr(prevMod(getInt(args, 0), tapes)));
    }

    static ArrayList<String> set(ArrayList<String> ls, int pos, String val) {
        ArrayList<String> list = new ArrayList<>(ls);
        list.set(pos, val);
        return list;
    }

    static ArrayList<String> set(Collection<String> ls, int pos, String val) {
        return set(new ArrayList<>(ls), pos, val);
    }

    static ArrayList<String> insert(ArrayList<String> ls, int pos, String val) {
        ArrayList<String> list = new ArrayList<>(ls);
        list.add(pos, val);
        return list;
    }

    static ArrayList<String> remove(ArrayList<String> ls, int pos) {
        ArrayList<String> list = new ArrayList<>(ls);
        list.remove(pos);
        return list;
    }


    static ArrayList<String> dec(ArrayList<String> ls, int pos) {
        return set(ls, pos, toStr(getInt(ls, pos) - 1));
    }

    static boolean eq(ArrayList<String> ls, int pos, String elem) {
        return ls.get(pos).equals(elem);
    }

    static int eqCnt(Collection<String> col, String elem) {
        return (int) col.stream().filter(e -> e.equals(elem)).count();
    }

    static String[] toArray(Collection<String> col) {
        String[] arr = new String[col.size()];
        int i = 0;
        for (String s : col)
            arr[i++] = s;
        return arr;
    }

    static ArrayList<String> toList(String[] arr) {
        return new ArrayList<>(Arrays.asList(arr));
    }

    static int[] primes(int size) {
        int[] primes = new int[size];
        int curSize = 0;
        for (int p = 2; curSize < size; p++) {
            pCheck: {
                for (int i = 0; i < curSize; i++) {
                    if (p % primes[i] == 0)
                        break pCheck;
                }
                primes[curSize++] = p;
            }
        }
        return primes;
    }

}
