/*
 * Copyright (c) 2016 Brigham Young University
 *
 * This file is part of the BYU RapidSmith Tools.
 *
 * BYU RapidSmith Tools is free software: you may redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * BYU RapidSmith Tools is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * A copy of the GNU General Public License is included with the BYU
 * RapidSmith Tools. It can be found at doc/LICENSE.GPL3.TXT. You may
 * also get a copy of the license at <http://www.gnu.org/licenses/>.
 */

package edu.byu.ece.rapidSmith.design.subsite;

import edu.byu.ece.rapidSmith.device.PIP;
import edu.byu.ece.rapidSmith.device.BelPin;
import edu.byu.ece.rapidSmith.device.Connection;
import edu.byu.ece.rapidSmith.device.SitePin;
import edu.byu.ece.rapidSmith.device.Wire;
import edu.byu.ece.rapidSmith.util.Exceptions;
import edu.byu.ece.rapidSmith.util.Exceptions.DesignAssemblyException;

import java.util.*;

/**
 * The RouteTree class describes a physical route for a net.  The physical structure
 * is described in a tree -- instances of this class are the nodes of the tree.  Each
 * wire in a route is defined in a RouteTree object, and each connection is a sink. See
 * the tech report for additional information.
 */
public final class RouteTree implements Iterable<RouteTree> {
	private RouteTree sourceTree; // Do I want bidirectional checks?
	private final Wire wire;
	private Connection connection;
	private final Collection<RouteTree> sinkTrees = new ArrayList<>(1);

	/**
	 * Creates a new RouteTree unsourced route tree.  This is typically the head
	 * of tree.
	 * @param wire the wire associated with this tree
	 */
	public RouteTree(Wire wire) {
		this.wire = wire;
	}

	private RouteTree(Wire wire, Connection c) {
		this.wire = wire;
		this.connection = c;
	}

	/**
	 * Returns the wire associated with this RouteTree node.
	 * @return the wire associated with this RouteTree
	 */
	public Wire getWire() {
		return wire;
	}

	/**
	 * Returns the {@link Connection} between the source RouteTree and this node.  A
	 * head RouteTree has no source.
	 * @return the connection between the source RouteTree and this node or null
	 *    if there is no connection
	 */
	public Connection getConnection() {
		return connection;
	}

	private void setConnection(Connection connection) {
		this.connection = connection;
	}

	/**
	 * Returns the RouteTree that sources this RouteTree.
	 * @return the RouteTree that sources this RouteTree
	 */
	public RouteTree getSourceTree() {
		return sourceTree;
	}

	/**
	 * Returns the head of the routing tree structure.
	 * @return the first source in this tree
	 */
	public RouteTree getFirstSource() {
		RouteTree parent = this;
		while (parent.isSourced())
			parent = parent.getSourceTree();
		return parent;
	}

	/**
	 * Returns whether this RouteTree is sourced by another.
	 * @return true if this RouteTree is sourced by another, else false
	 */
	public boolean isSourced() {
		return sourceTree != null;
	}

	private void setSourceTree(RouteTree sourceTree) {
		this.sourceTree = sourceTree;
	}

	/**
	 * Returns the trees that this RouteTree immediately sources.  The returned
	 * collection is unmodifiable.
	 * @return a collection of sinks of this RouteTree.
	 */
	public Collection<RouteTree> getSinkTrees() {
		return Collections.unmodifiableCollection(sinkTrees);
	}
	
	/**
	 * Returns true if this RouteTree is a leaf (i.e. it has no children).
	 * For a fully routed net, a leaf tree should connect to either a SitePin
	 * or BelPin.
	 * @return true if this RouteTree is a leaf, else false
	 */
	public boolean isLeaf() {
		return sinkTrees.size() == 0;
	}
	
	/**
	 * Returns the SitePin connected to the wire of the RouteTree. If no SitePin
	 * object is connected, null is returned.
	 * @return the SitePin connected the wire of this RouteTree or null
	 */
	public SitePin getConnectingSitePin() {
		Collection<Connection> pinConnections = wire.getPinConnections();
		return (pinConnections.isEmpty()) ? null : pinConnections.iterator().next().getSitePin(); 
	}
	
	/**
	 * Returns the BelPin connected to the wire of the RouteTree. If no BelPin
	 * object is connected, null is returned.
	 * @return the BelPin connected to the wire of this RouteTree or null
	 */
	public BelPin getConnectingBelPin() {
		Collection<Connection> terminalConnections = wire.getTerminals();
		return terminalConnections.isEmpty() ? null : terminalConnections.iterator().next().getBelPin();
	}

	/**
	 * Creates a new sink RouteTree based on this connection and adds it to this
	 * RouteTree's list of sinks.  The created RouteTree will be based on the sink
	 * wire of this connection, be sourced by this RouteTree and be backed by the
	 * provided connection.
	 *
	 * @param c the connection of the new RouteTree
	 * @return the created RouteTree
	 */
	public RouteTree addConnection(Connection c) {
		RouteTree endTree = new RouteTree(c.getSinkWire(), c);
		endTree.setSourceTree(this);
		sinkTrees.add(endTree);
		return endTree;
	}

	/**
	 * Adds RouteTree {@code sink} as a sink of this RouteTree and updates {@code sink} to
	 * use {@code c} as its connection.
	 * @param c the connection connecting this tree to {@code sink}
	 * @param sink the tree to add as a sink of this node
	 * @return {@code sink}
	 * @throws DesignAssemblyException if sink is already sourced
	 * @throws DesignAssemblyException if the sink wire of the connection differs from
	 *    the wire of {@code sink}
	 */
	public RouteTree addConnection(Connection c, RouteTree sink) {
		if (sink.getSourceTree() != null)
			throw new DesignAssemblyException("Sink tree already sourced");
		if (!c.getSinkWire().equals(sink.getWire()))
			throw new DesignAssemblyException("Connection does not match sink tree");

		sinkTrees.add(sink);
		sink.setSourceTree(this);
		sink.setConnection(c);
		return sink;
	}

	/**
	 * Removes all sinks (a proper tree structure should only have one) that connect
	 * to this tree through connection {@code c}.  This method iterates through all of
	 * this trees sinks and removes any sink whose connection matches {@code c}.
	 * @param c the connection to search for
	 */
	public void removeConnection(Connection c) {
		for (Iterator<RouteTree> it = sinkTrees.iterator(); it.hasNext(); ) {
			RouteTree sink = it.next();
			if (sink.getConnection().equals(c)) {
				sink.setSourceTree(null);
				it.remove();
			}
		}
	}

	/**
	 * Returns all PIPs that exist in this RouteTree.  This method starts at the source
	 * of the tree structure and recursively traverses it looking for all PIP connections.
	 * @return all PIPs that exist in this RouteTree
	 */
	public List<PIP> getAllPips() {
		return getFirstSource().getAllPips(new ArrayList<>());
	}

	private List<PIP> getAllPips(List<PIP> pips) {
		for (RouteTree rt : sinkTrees) {
			if (rt.getConnection().isPip())
				pips.add(rt.getConnection().getPip());
			rt.getAllPips(pips);
		}
		return pips;
	}

	/**
	 * Returns a deep copy of this route tree.  The source for this RouteTree node is
	 * not copied.
	 * @return a deep copy of this route tree
	 */
	public RouteTree deepCopy() {
		RouteTree copy = new RouteTree(wire, connection);
		sinkTrees.forEach(rt -> copy.sinkTrees.add(rt.deepCopy()));
		copy.sinkTrees.forEach(rt -> rt.sourceTree = this);
		return copy;
	}

	/**
	 * Prunes all branches of this route tree that do not reach {@code terminal}.  Returns
	 * true if at least one branch reaches the terminal.
	 * @param terminal the terminal tree to preserve
	 * @return true if at least one branch of this tree reaches the terminal
	 */
	public boolean prune(RouteTree terminal) {
		return prune(Collections.singleton(terminal));
	}

	/**
	 * Prunes all branches of this route tree that do not reach a tree in
	 * {@code terminal}s.  Returns true if at least one branch reaches a terminal.
	 * @param terminals set of terminal trees to preserve
	 * @return true if at least one branch of this tree reaches a terminal
	 */
	public boolean prune(Set<RouteTree> terminals) {
		return pruneChildren(terminals);
	}

	private boolean pruneChildren(Set<RouteTree> terminals) {
		// TODO we should probably not prune children of a terminal

		// remove all children of this node that do not reach a terminal
		sinkTrees.removeIf(rt -> !rt.pruneChildren(terminals));

		// return true if this node is in the terminals or if a sink of this
		// tree is in the terminals
		return !sinkTrees.isEmpty() || terminals.contains(this);
	}

	/**
	 * Returns an iterator over all nodes in the tree structure stemming from
	 * this node.
	 * @return an iterator over all subtrees stemming from this node
	 */
	@Override
	public Iterator<RouteTree> iterator() {
		return prefixIterator();
	}

	/**
	 * Returns an iterator over all nodes in this tree stemming from this node in
	 * prefix order (ie the source is always visited before the sink).
	 * @return an iterator over all nodes in this tree steming from this node in
	 * prefix order
	 */
	public Iterator<RouteTree> prefixIterator() {
		return new PrefixIterator();
	}

	private class PrefixIterator implements Iterator<RouteTree> {
		private final Stack<RouteTree> stack;

		PrefixIterator() {
			this.stack = new Stack<>();
			this.stack.push(RouteTree.this);
		}

		@Override
		public boolean hasNext() {
			return !stack.isEmpty();
		}

		@Override
		public RouteTree next() {
			if (!hasNext())
				throw new NoSuchElementException();
			RouteTree tree = stack.pop();
			stack.addAll(tree.getSinkTrees());
			return tree;
		}
	}

	// Uses identity equals

	@Override
	public int hashCode() {
		return Objects.hash(connection);
	}
}
