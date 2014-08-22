package io.hummer.util.graph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * This class finds circular dependencies in graph models,
 * based on Robert Tarjan's strongly connected components algorithm:
 * http://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
 */
public class CircularDependencyFinder {
	
	private int index = 0;
	private ArrayList<Node> stack = new ArrayList<Node>();
	private ArrayList<ArrayList<Node>> SCC = new ArrayList<ArrayList<Node>>();
	
	public static class Node {
		public int index = -1;
		public int lowlink = -1;
		public String name;
		public Node(String name) {
			this.name = name;
		}
		public String toString() {
			return name;
		}
	}
	public static class Edge {
		public Node from;
		public Node to;
		public Edge(Node from, Node to) {
			this.from = from;
			this.to = to;
		}
	}
	public static class AdjacencyList {
		Map<Node,List<Edge>> adjacents = new HashMap<Node, List<Edge>>();
		public List<Edge> getAdjacent(Node v) {
			if(adjacents.containsKey(v))
				return adjacents.get(v);
			return new LinkedList<Edge>();
		}
	}

	public static boolean hasCircle(Node v, AdjacencyList list) {
		return hasCircle(v, list.adjacents);
	}

	public static boolean hasCircle(Map<Node,List<Edge>> adjacents) {
		for(Node n : adjacents.keySet()) {
			if(hasCircle(n, adjacents))
				return true;
		}
		return false;
	}
	
	public static boolean hasCircle(Node v, Map<Node,List<Edge>> adjacents) {
		CircularDependencyFinder t = new CircularDependencyFinder();
		init(adjacents);
		ArrayList<ArrayList<Node>> tarjan = t.getStronglyConnectedComponents(v, adjacents);
		for(ArrayList<Node> scc : tarjan) {
			if(scc.size() != 1)
				return true;
		}
		return false;
	}
	
	private static void init(Map<Node,List<Edge>> adjacents) {
		for(Node n : adjacents.keySet()) {
			n.index = -1;
			n.lowlink = -1;
			for(Edge e : adjacents.get(n)) {
				if(e.to != null) {
					e.to.index = -1;
					e.to.lowlink = -1;
				}
			}
		}
	}
	
	private ArrayList<ArrayList<Node>> getStronglyConnectedComponents(
			Node v, Map<Node,List<Edge>> adjacents) {
		
		v.index = index;
		v.lowlink = index;
		index++;
		stack.add(0, v);
		List<Edge> list = adjacents.get(v);
		if(list == null)
			list = new LinkedList<Edge>();
		for (Edge e : list) {
			Node n = e.to;
			if (n.index == -1) {
				getStronglyConnectedComponents(n, adjacents);
				v.lowlink = Math.min(v.lowlink, n.lowlink);
			} else if (stack.contains(n)) {
				v.lowlink = Math.min(v.lowlink, n.index);
			}
		}
		if (v.lowlink == v.index) {
			Node n;
			ArrayList<Node> component = new ArrayList<Node>();
			do {
				n = stack.remove(0);
				component.add(n);
			} while (n != v);
			SCC.add(component);
		}
		return SCC;
	}
	
	public static void main(String[] args) {
		Node n1 = new Node("n1");
		Node n2 = new Node("n2");
		Node n3 = new Node("n3");
		Node n4 = new Node("n4");
		Node n5 = new Node("n5");
		Edge e12 = new Edge(n1,n2);
		Edge e13 = new Edge(n1,n3);
		Edge e24 = new Edge(n2,n4);
		Edge e25 = new Edge(n2,n5);
		Edge e21 = new Edge(n2,n1);
		Edge e34 = new Edge(n3,n4);
		Edge e41 = new Edge(n4,n1);
		AdjacencyList list = new AdjacencyList();
		list.adjacents.put(n1, Arrays.asList(e12, e13));
		list.adjacents.put(n2, Arrays.asList(e24, e25, e21));
		list.adjacents.put(n3, Arrays.asList(e34));
		list.adjacents.put(n4, Arrays.asList(e41));
		
//		ArrayList<ArrayList<Node>> result = t.getStronglyConnectedComponents(n1, list.adjacents);
//		System.out.println(result);
		boolean cycle = hasCircle(n1, list);
		System.out.println(cycle);
		boolean cycle1 = hasCircle(list.adjacents);
		System.out.println(cycle1);
	}
	
}
