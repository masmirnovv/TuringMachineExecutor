package machines.convert;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class NamespaceTree {

    static Function<String, String> addMore(String sym) {
        return s -> s + sym;
    }

    private static final Function<String, String> ADD_MORE_APOSTROPHES = addMore("'");



    private ChildType childType;
    private NamespaceTree parent;
    private HashMap<String, NamespaceTree> stringChildren;
    private NamespaceTree[] intChildren;
    private HashMap<ArrayList<String>, NamespaceTree> listChildren;

    private String value;
    private HashSet<String> valuesNamespace;

    private boolean isSelected = false;

    NamespaceTree() {
        childType = ChildType.NONE;
        valuesNamespace = new HashSet<>();
    }

    private NamespaceTree(NamespaceTree parent) {
        this.childType = ChildType.NONE;
        this.parent = parent;
        this.valuesNamespace = parent.valuesNamespace;
    }

    private NamespaceTree(NamespaceTree parent, String value, Function<String, String> nextValueGen) {
        this.childType = ChildType.LEAF;
        this.parent = parent;
        this.valuesNamespace = parent.valuesNamespace;

        String v = value;
        while (valuesNamespace.contains(v))
            v = nextValueGen.apply(v);
        this.value = v;
        valuesNamespace.add(v);
    }



    String get(String... path) {
        NamespaceTree cur = this;
        for (String p : path)
            cur = cur.to(p);
        return cur.value;
    }

    String get(int last, String... path) {
        NamespaceTree cur = this;
        for (String p : path)
            cur = cur.to(p);
        return cur.to(last).value;
    }

    String get(ArrayList<String> last, String... path) {
        NamespaceTree cur = this;
        for (String p : path)
            cur = cur.to(p);
        return cur.to(last).value;
    }

    String get(Collection<String> last, String... path) {
        return get(new ArrayList<>(last), path);
    }



    NamespaceTree to(String key) {
        if (childType == ChildType.STRING)
            return stringChildren.get(key);
        else
            throw new IllegalStateException();
    }

    NamespaceTree to(int key) {
        if (childType == ChildType.INT)
            return intChildren[key];
        else
            throw new IllegalStateException();
    }

    NamespaceTree to(ArrayList<String> key) {
        if (childType == ChildType.LIST)
            return listChildren.get(key);
        else
            throw new IllegalStateException();
    }

    NamespaceTree to(Collection<String> key) {
        if (childType == ChildType.LIST)
            return listChildren.get(new ArrayList<>(key));
        else
            throw new IllegalStateException();
    }

    NamespaceTree also() {
        if (parent == null)
            throw new IllegalStateException();
        return parent;
    }



    NamespaceTree add(String child, String childValue, Function<String, String> nextValueGen) {
        switch (childType) {
            case NONE:
                childType = ChildType.STRING;
                stringChildren = new HashMap<>();
                add(child, childValue, nextValueGen);
                break;
            case STRING:
                if (stringChildren.containsKey(child)) throw new IllegalStateException();
                stringChildren.put(child, new NamespaceTree(this, childValue, nextValueGen));
                break;
            default:
                throw new AssertionError();
        }
        return this;
    }

    NamespaceTree add(String child, String childValue) {
        return add(child, childValue, ADD_MORE_APOSTROPHES);
    }

    NamespaceTree add(String child) {
        return add(child, child);
    }

    NamespaceTree add(ArrayList<String> child, String childValue, Function<String, String> nextValueGen) {
        switch (childType) {
            case NONE:
                childType = ChildType.LIST;
                listChildren = new HashMap<>();
                add(child, childValue, nextValueGen);
                break;
            case LIST:
                if (listChildren.containsKey(child)) throw new IllegalStateException();
                listChildren.put(child, new NamespaceTree(this, childValue, nextValueGen));
                break;
            default:
                throw new AssertionError();
        }
        return this;
    }

    NamespaceTree add(Collection<String> child, String childValue, Function<String, String> nextValueGen) {
        return add(new ArrayList<>(child), childValue, nextValueGen);
    }

    NamespaceTree add(ArrayList<String> child, String childValue) {
        return add(child, childValue, ADD_MORE_APOSTROPHES);
    }

    NamespaceTree add(Collection<String> child, String childValue) {
        return add(child, childValue, ADD_MORE_APOSTROPHES);
    }

    NamespaceTree addAll(String child, String... moreChildren) {
        switch (childType) {
            case NONE:
                childType = ChildType.STRING;
                stringChildren = new HashMap<>();
                addAll(child, moreChildren);
                break;
            case STRING:
                if (stringChildren.containsKey(child)) throw new IllegalStateException();
                stringChildren.put(child, new NamespaceTree(this));
                for (String moreChild : moreChildren) {
                    if (stringChildren.containsKey(moreChild)) throw new IllegalStateException();
                    stringChildren.put(moreChild, new NamespaceTree(this));
                }
                break;
            default:
                throw new IllegalStateException();
        }
        return this;
    }

    NamespaceTree addAll(Collection<String> args, Function<String, String> argToValue, Function<String, String> nextValueGen) {
        for (String arg : args)
            add(arg, argToValue.apply(arg), nextValueGen);
        return this;
    }

    NamespaceTree addAll(Collection<String> args, Function<String, String> argToValue) {
        for (String arg : args)
            add(arg, argToValue.apply(arg));
        return this;
    }

    NamespaceTree addAll(Collection<String> args) {
        for (String child : args)
            add(child);
        return this;
    }

    NamespaceTree addAll(int sz, IntFunction<String> intToValue, Function<String, String> nextValueGen) {
        if (childType == ChildType.NONE) {
            childType = ChildType.INT;
            intChildren = new NamespaceTree[sz];
            for (int i = 0; i < sz; i++)
                intChildren[i] = new NamespaceTree(this, intToValue.apply(i), nextValueGen);
            return this;
        } else
            throw new AssertionError();
    }

    NamespaceTree addAll(int sz, IntFunction<String> intToValue) {
        return addAll(sz, intToValue, ADD_MORE_APOSTROPHES);
    }

    NamespaceTree addAllLists(Collection<ArrayList<String>> args, Function<ArrayList<String>, String> argToValue, Function<String, String> nextValueGen) {
        switch (childType) {
            case NONE:
                childType = ChildType.LIST;
                listChildren = new HashMap<>();
                addAllLists(args, argToValue, nextValueGen);
                break;
            case LIST:
                for (ArrayList<String> arg : args) {
                    if (listChildren.containsKey(arg)) throw new IllegalStateException();
                    listChildren.put(arg, new NamespaceTree(this, argToValue.apply(arg), nextValueGen));
                }
                break;
            default:
                throw new IllegalStateException();
        }
        return this;
    }

    NamespaceTree addAllLists(Collection<ArrayList<String>> args, Function<ArrayList<String>, String> argToValue) {
        return addAllLists(args, argToValue, ADD_MORE_APOSTROPHES);
    }



    NamespaceTree selectThis() {
        isSelected = true;
        return this;
    }

    NamespaceTree selectAll() {
        switch (childType) {
            case LEAF:
            case NONE:
                throw new IllegalStateException();
            case STRING:
                for (Map.Entry<String, NamespaceTree> childEntry : stringChildren.entrySet())
                    childEntry.getValue().isSelected = true;
                break;
            case INT:
                for (NamespaceTree child : intChildren)
                    child.isSelected = true;
                break;
            case LIST:
                for (Map.Entry<ArrayList<String>, NamespaceTree> childEntry : listChildren.entrySet())
                    childEntry.getValue().isSelected = true;
                break;
        }
        return this;
    }

    NamespaceTree selectOnly(String... keys) {
        if (childType == ChildType.STRING) {
            for (String key : keys)
                stringChildren.get(key).isSelected = true;
        } else
            throw new IllegalStateException();
        return this;
    }

    NamespaceTree selectOnly(Collection<String> keys) {
        if (childType == ChildType.STRING) {
            for (String key : keys)
                stringChildren.get(key).isSelected = true;
        } else
            throw new IllegalStateException();
        return this;
    }

    NamespaceTree unselect(String... keys) {
        if (childType == ChildType.STRING) {
            for (String key : keys)
                stringChildren.get(key).isSelected = false;
        } else
            throw new IllegalStateException();
        return this;
    }

    NamespaceTree selectOnly(int... keys) {
        if (childType == ChildType.INT) {
            for (int key : keys)
                intChildren[key].isSelected = true;
        } else
            throw new IllegalStateException();
        return this;
    }

    NamespaceTree selectWithout(String... keys) {
        if (childType == ChildType.STRING) {
            HashSet<String> ks = new HashSet<>(stringChildren.keySet());
            ks.removeAll(Arrays.asList(keys));
            for (String key : ks)
                stringChildren.get(key).isSelected = true;
        } else
            throw new IllegalStateException();
        return this;
    }

    NamespaceTree selectWithout(int... keys) {
        if (childType == ChildType.INT) {
            HashSet<Integer> ks = IntStream.range(0, intChildren.length).boxed().collect(Collectors.toCollection(HashSet::new));
            ks.removeAll(Arrays.stream(keys).boxed().collect(Collectors.toList()));
            for (int key : ks)
                intChildren[key].isSelected = true;
        } else
            throw new IllegalStateException();
        return this;
    }

    NamespaceTree selectByPredicate(IntPredicate p) {
        if (childType == ChildType.INT) {
            for (int key = 0; key < intChildren.length; key++) {
                if (p.test(key))
                    intChildren[key].isSelected = true;
            }
        } else
            throw new IllegalStateException();
        return this;
    }

    NamespaceTree selectByPredicate(Predicate<ArrayList<String>> p) {
        if (childType == ChildType.LIST) {
            for (ArrayList<String> key : listChildren.keySet()) {
                if (p.test(key))
                    listChildren.get(key).isSelected = true;
            }
        } else
            throw new IllegalStateException();
        return this;
    }



    Collection<String> collectSelected() {
        Collection<NamespaceTree> selected = getSelected();
        return selected.stream().map(sel -> sel.value).collect(Collectors.toList());
    }

    @SafeVarargs
    final void forEachSelected(Consumer<String> action, Consumer<String>... moreActions) {
        Collection<NamespaceTree> selected = getSelected();
        for (NamespaceTree sel : selected)
            action.accept(sel.value);
        for (Consumer<String> act : moreActions) {
            for (NamespaceTree sel : selected)
                act.accept(sel.value);
        }
    }

    @SafeVarargs
    final void forAll(Consumer<String> action, Consumer<String>... moreActions) {
        getRoot().assertNoSelected();
        switch (childType) {
            case STRING:
            case INT:
            case LIST:
                selectThis().forEachSelected(action, moreActions);
                break;
            default:
                throw new IllegalStateException();
        }
    }

    @SafeVarargs
    final void forThis(Consumer<String> action, Consumer<String>... moreActions) {
        getRoot().assertNoSelected();
        if (childType == ChildType.LEAF)
            selectThis().forEachSelected(action, moreActions);
        else
            throw new IllegalStateException();
    }

    @SafeVarargs
    final void forAllStringKeys(Consumer<String> action, Consumer<String>... moreActions) {
        getRoot().assertNoSelected();
        if (childType == ChildType.STRING) {
            for (String key : stringChildren.keySet())
                action.accept(key);
            for (Consumer<String> act : moreActions) {
                for (String key : stringChildren.keySet())
                    act.accept(key);
            }
        } else
            throw new IllegalStateException();
    }

    @SafeVarargs
    final void forAllIntKeys(Consumer<Integer> action, Consumer<Integer>... moreActions) {
        getRoot().assertNoSelected();
        if (childType == ChildType.INT) {
            for (int i = 0; i < intChildren.length; i++)
                action.accept(i);
            for (Consumer<Integer> act : moreActions) {
                for (int i = 0; i < intChildren.length; i++)
                    act.accept(i);
            }
        } else
            throw new IllegalStateException();
    }

    @SafeVarargs
    final void forAllListKeys(Consumer<ArrayList<String>> action, Consumer<ArrayList<String>>... moreActions) {
        getRoot().assertNoSelected();
        if (childType == ChildType.LIST) {
            for (ArrayList<String> key : listChildren.keySet())
                action.accept(key);
            for (Consumer<ArrayList<String>> act : moreActions) {
                for (ArrayList<String> key : listChildren.keySet())
                    act.accept(key);
            }
        } else
            throw new IllegalStateException();
    }



    private NamespaceTree getRoot() {
        if (parent == null)
            return this;
        return parent.getRoot();
    }

    private Collection<NamespaceTree> getSelected() {
        return getRoot().getSelected(false);
    }

    private Collection<NamespaceTree> getSelected(boolean all) {
        List<NamespaceTree> list = new ArrayList<>();
        switch (childType) {
            case NONE:
                break;
            case LEAF:
                if (all || isSelected)
                    list.add(this);
                break;
            case STRING:
                for (NamespaceTree child : stringChildren.values())
                    list.addAll(child.getSelected(all || isSelected));
                break;
            case INT:
                for (NamespaceTree child : intChildren)
                    list.addAll(child.getSelected(all || isSelected));
                break;
            case LIST:
                for (NamespaceTree child : listChildren.values())
                    list.addAll(child.getSelected(all || isSelected));
                break;
            default:
                throw new IllegalStateException();
        }
        isSelected = false;
        return list;
    }

    private void assertNoSelected() {
        if (isSelected)
            throw new IllegalStateException();
        switch (childType) {
            case NONE:
            case LEAF:
                break;
            case STRING:
                for (NamespaceTree child : stringChildren.values())
                    child.assertNoSelected();
                break;
            case INT:
                for (NamespaceTree child : intChildren)
                    child.assertNoSelected();
                break;
            case LIST:
                for (NamespaceTree child : listChildren.values())
                    child.assertNoSelected();
                break;
            default:
                throw new IllegalStateException();
        }
    }



    @Override
    public String toString() {
        switch (childType) {
            case NONE:
                return "NONE";
            case LEAF:
                return "LEAF: " + value;
            case STRING:
                return stringChildren.toString();
            case INT:
                return IntStream.range(0, intChildren.length).mapToObj(i -> i + ": " + intChildren[i]).collect(Collectors.joining(", ", "[", "]"));
            case LIST:
                return listChildren.toString();
            default:
                throw new IllegalStateException();
        }
    }

    private enum ChildType {
        NONE, STRING, INT, LIST, LEAF
    }

}