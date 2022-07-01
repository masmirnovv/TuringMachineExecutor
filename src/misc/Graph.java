package misc;

import java.util.*;

public class Graph<T> {

    private Map<T, Set<T>> graph = new HashMap<>();
    private Set<T> vertices = new HashSet<>();

    public Set<T> getVertices() {
        return vertices;
    }

    @SafeVarargs
    public final void addVertices(T... vs) {
        vertices.addAll(Arrays.asList(vs));
    }

    public void addEdge(T from, T to) {
        if (!graph.containsKey(from))
            graph.put(from, new HashSet<>());
        graph.get(from).add(to);
        vertices.add(from);
        vertices.add(to);
    }

    public Set<T> edgesFrom(T v) {
        return graph.getOrDefault(v, Set.of());
    }

    public T edgeFrom(T v) {
        assert edgesFrom(v).size() < 2;
        if (!graph.containsKey(v))
            return null;
        Iterator<T> it = graph.get(v).iterator();
        return it.hasNext()? it.next() : null;
    }

    public void joinWithAll(T to) {
        for (T from : vertices) {
            if (!graph.containsKey(from))
                graph.put(from, new HashSet<>());
            graph.get(from).add(to);
        }
        vertices.add(to);
    }

    public Set<T> bfs(T from) {
        return bfs(List.of(from));
    }

    public Set<T> bfs(Collection<T> from) {
        LinkedList<T> q = new LinkedList<>(from);
        Set<T> vis = new HashSet<>();
        while (!q.isEmpty()) {
            T cur = q.removeFirst();
            vis.add(cur);
            for (T to : edgesFrom(cur)) {
                if (!vis.contains(to))
                    q.addLast(to);
            }
        }
        return vis;
    }

    public Graph<T> inverse() {
        Graph<T> inv = new Graph<>();
        inv.vertices = new HashSet<>(this.vertices);
        for (T v : vertices) {
            for (T u : edgesFrom(v))
                inv.addEdge(u, v);
        }
        return inv;
    }

    public Graph<T> closure() {
        Graph<T> cl = new Graph<>();
        for (T v : vertices) {
            for (T u : bfs(v)) {
                if (!v.equals(u))
                    cl.addEdge(v, u);
            }
        }
        return cl;
    }

}
