package edu.byu.ece.rapidSmith.design.subsite;

import edu.byu.ece.rapidSmith.device.*;
import edu.byu.ece.rapidSmith.util.Exceptions;

import java.util.*;

public abstract class AbstractRouteTree<T extends AbstractRouteTree<T>> implements Iterable<T> {
	private final Node node;
	private final Collection<T> children = new ArrayList<>(1);

	protected AbstractRouteTree(Node node) {
		this.node = node;
	}

	/**
	 * Returns a new instance of this class.  This method allows for the abstract class
	 * to handle the implementation details of the class while preserving the generic
	 * nature of the class.
	 * @param node the node this node represents
	 * @return a new node instance
	 */
	protected abstract T newInstance(Node node);

	/**
	 * Provides a method for a parent node to indicate to the child node that it has
	 * been added as a child to the parent.  This allows the child node to update any
	 * structures related to its location in the list.
	 * <p/>
	 * The default implementation does nothing.
	 * @param c the connection between the parent and child nodes
	 * @param parent the new parent node of the child
	 */
	protected void connectToSource(Connection c, AbstractRouteTree<T> parent) { }

	/**
	 * Provides a method for a parent node to indicate to its current child that it is
	 * breaking its connection with the child.  This allows the child node to update any
	 * structures related to its location in the list.
	 * <p/>
	 * The default implementation does nothing.
	 */
	protected void disconnectFromSource() { }

	public final Node getNode() {
		return node;
	}

	public final Collection<T> getChildren() {
		return Collections.unmodifiableCollection(children);
	}

	/**
	 * Returns true if the RouteTree object is a leaf (i.e. it has no children).
	 * For a fully routed net, a leaf tree should connect to either a SitePin
	 * or BelPin.
	 */
	public final boolean isLeaf() {
		return children.isEmpty();
	}

	/**
	 * Returns the {@link SitePin} connected to the node of the RouteTree. If no SitePin
	 * object is connected, null is returned.
	 */
	public final SitePin getConnectedSitePin() {
		return node.getConnectedPin();
	}

	/**
	 * Returns the {@link BelPin} connected to the node of the RouteTree. If no BelPin
	 * object is connected, null is returned.
	 */
	public final BelPin getConnectedBelPin() {
		return node.getTerminal();
	}

	/**
	 * Creates and adds a new child node connected to this node through {@link Connection} c.
	 * @param c the {@link Connection} connecting nodes between this node and child.
	 * @return the newly created child node
	 */
	@SuppressWarnings("unchecked")
	public final <S extends T> S connect(Connection c) {
		S i = (S) newInstance(c.getSinkNode());
		return connect(c, i);
	}

	/**
	 * Connects an existing node to this node as a child through {@link Connection} c.
	 * @param c the {@link Connection} connecting nodes between this node and child.
	 * @param child the new child node in this connection
	 * @return @{code child}
	 */
	public final <S extends T> S connect(Connection c, S child) {
		if (!c.getSinkNode().equals(child.getNode()))
			throw new Exceptions.DesignAssemblyException("Connection does not match sink tree");

		children.add(child);
		child.connectToSource(c, this);
		return child;
	}

	/**
	 * Disconnects this node from its child connected through {@link Connection} c.  If
	 * no connection exists between this node and a child, this method returns without making
	 * any changes.
	 * <p/>
	 * This method is implemented by removing the child node with the same node returned
	 * by {@link Connection#getSinkNode()} of c.
	 * @param c the connection to remove
	 */
	public final void disconnect(Connection c) {
		Node sinkNode = c.getSinkNode();
		for (Iterator<T> it = children.iterator(); it.hasNext(); ) {
			T sink = it.next();
			if (sink.getNode().equals(sinkNode)) {
				sink.disconnectFromSource();
				it.remove();
			}
		}
	}

	/**
	 * Disconnects this node from its child {@code child}.  If {@code child} is not a
	 * child of this node, this method returns without making any changes.
	 * <p/>
	 * This method uses identity equals for comparing the parent's children to child.
	 * @param child the child to disconnect from
	 */
	public final void disconnect(AbstractRouteTree<T> child) {
		for (Iterator<T> it = children.iterator(); it.hasNext(); ) {
			T sink = it.next();
			if (sink == child) {
				sink.disconnectFromSource();
				it.remove();
			}
		}
	}

	/**
	 * Disconnects this node from any child wrapping {@link Node} {@code sink}.  If no
	 * connections exist between this node and a child wrapping {@linkplain Node}
	 * {@code sink}, this method
	 * returns without making any changes.
	 * <p/>
	 * Though no two children with the same node should exist a children of this node, this
	 * method will still search the entire collection of children and remove any backed by
	 * {@linkplain Node} {@code sink}.
	 * @param sink the node of child nodes to remove.
	 */
	public final void disconnect(Node sink) {
		for (Iterator<T> it = children.iterator(); it.hasNext(); ) {
			T child = it.next();
			if (child.getNode().equals(sink)) {
				child.disconnectFromSource();
				it.remove();
			}
		}
	}

	/**
	 * Prunes all branches except the one ending in {@code toKeep}.
	 * @param toKeep the terminal ending the branch to keep
	 * @return true if the terminal was found on a branch of this tree, else false
	 */
	public final boolean prune(T toKeep) {
		Set<T> toPrune = new HashSet<>();
		toPrune.add(toKeep);
		return prune(toPrune);
	}

	/**
	 * Prunes all branches not containing a node in set {@code toKeep}.
	 * @param toKeep set of nodes to preserve in the tree
	 * @return true if any branches were preserved
	 */
	public final boolean prune(Set<? extends AbstractRouteTree<T>> toKeep) {
		return pruneChildren(toKeep);
	}

	private boolean pruneChildren(Set<? extends AbstractRouteTree<T>> terminals) {
		children.removeIf(rt -> !((AbstractRouteTree<T>) rt).pruneChildren(terminals));
		return !children.isEmpty() || terminals.contains(this);
	}

	/**
	 * Iterates over all trees in this route tree starting from this node.  Nodes
	 * in this tree before this node are not traversed.  This iterator provides no
	 * guarantee on the order of traversal, only that all nodes will be visited.
	 */
	@Override
	public Iterator<T> iterator() {
		return preorderIterator();
	}

	/**
	 * Iterates over all trees in this route tree starting from this node in a
	 * preorder traversal, ie. parent nodes are guaranteed to be visited prior to the
	 * children. Nodes in the tree prior to this node are not traversed.
	 */
	public Iterator<T> preorderIterator() {
		return new PreorderIterator();
	}

	private class PreorderIterator implements Iterator<T> {
		private final Stack<T> stack;

		@SuppressWarnings("unchecked")
		PreorderIterator() {
			this.stack = new Stack<>();
			this.stack.push((T) AbstractRouteTree.this);
		}

		@Override
		public boolean hasNext() {
			return !stack.isEmpty();
		}

		@Override
		public T next() {
			if (!hasNext())
				throw new NoSuchElementException();
			T tree = stack.pop();
			stack.addAll(tree.getChildren());
			return tree;
		}
	}

	@Override
	public boolean equals(Object obj) {
		return super.equals(obj);
	}

	@Override
	public int hashCode() {
		return Objects.hash(node);
	}
}
