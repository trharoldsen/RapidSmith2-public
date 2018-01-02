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
package edu.byu.ece.rapidSmith.device;


import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;


/**
 * This class represents the same information found and described in XDLRC files concerning
 * Xilinx FPGA tiles.  The representation given by XDLRC files is that every FPGA is described
 * as a 2D array of Tiles, each with a set of sites, and hence sources, sinks and wires
 * to connect tiles together and wires within.
 *
 * @author Chris Lavin
 *         Created on: Apr 22, 2010
 */
public class Tile implements Serializable {
	/** Unique Serialization ID */
	private static final long serialVersionUID = 4859877066322216633L;
	private Device dev;
	/** XDL Name of the tile */
	private String name;
	/** XDL Tile Type (INT,CLB,...) */
	private TileType type;
	/** This is a list of the sinks within the tile (generally in the sites)
	    Absolute tile row number - the index into the device Tiles[][] array */
	private int row;
	/** Absolute tile column number - the index into the device Tiles[][] array */
	private int column;
	/** This is the Y coordinate in the tile name (ex: 5 in INT_X0Y5) */
	private int tileYCoordinate;
	/** This is the X coordinate in the tile name (ex: 0 in INT_X0Y5) */
	private int tileXCoordinate;
	/** An array of sites located within the tile (null if none) */
	private List<Site> sites;
	/** This variable holds all the wires and their connections within the tile */
	private Map<TileWireTemplate, TileNodeTemplate> wireNodesMap;
	private Map<String, TileWireTemplate> tileWires;
	private List<Map<SiteType, List<TileWireTemplate>>> pinwires;

	/**
	 * Map of the wires to the index of the site the wire connects to.  This is
	 * needed since it is the job of the site to create the site pin, but we need
	 * to identify which site the pin exists on first.
	 */
	private Map<TileWireTemplate, Integer> wireSites;

	public Tile(int row, int column) {
		this.row = row;
		this.column = column;
	}

	/**
	 * Returns the device to which this tile belongs.
	 *
	 * @return the device to which this tile belongs
	 */
	public Device getDevice() {
		return dev;
	}

	/**
	 * Sets the device which owns this tile.
	 *
	 * @param device the device to set
	 */
	public void setDevice(Device device) {
		this.dev = device;
	}

	/**
	 * Returns the name (XDL name) of the tile (such as INT_X0Y5).
	 *
	 * @return the XDL name of the tile
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of the tile (XDL name, such as INT_X0Y5).
	 *
	 * @param name the new name of the tile
	 */
	public void setName(String name) {
		// Set the name
		this.name = name;

		if (!name.contains("_X"))
			return;

		// Populate the X and Y coordinates based on name
		int end = name.length();
		int chIndex = name.lastIndexOf('Y');
		this.tileYCoordinate = Integer.parseInt(name.substring(chIndex + 1, end));

		end = chIndex;
		chIndex = name.lastIndexOf('X');
		this.tileXCoordinate = Integer.parseInt(name.substring(chIndex + 1, end));
	}

	/**
	 * Returns the XDL tile name suffix (such as "_X0Y5").
	 *
	 * @return the tile coordinate name suffix with underscore
	 */
	public String getTileNameSuffix() {
		return name.substring(name.lastIndexOf('_'));
	}

	/**
	 * Returns the type of this tile.
	 *
	 * @return the type of this tile
	 */
	public TileType getType() {
		return type;
	}

	/**
	 * Sets the type of this tile this.
	 *
	 * @param type the new type to set
	 */
	public void setType(TileType type) {
		this.type = type;
	}

	/**
	 * The absolute row index (0 starting at top)
	 *
	 * @return the row
	 */
	public int getRow() {
		return row;
	}

	/**
	 * The absolute row index (0 starting at top)
	 *
	 * @param row the row to set
	 */
	public void setRow(int row) {
		this.row = row;
	}

	/**
	 * The absolute column index (0 starting at the left)
	 *
	 * @return the column
	 */
	public int getColumn() {
		return column;
	}

	/**
	 * The absolute column index (0 starting at the left)
	 *
	 * @param column the column to set
	 */
	public void setColumn(int column) {
		this.column = column;
	}

	/**
	 * This is the Y Coordinate in the tile name (the 5 in INT_X0Y5)
	 *
	 * @return the tileRow
	 */
	public int getTileYCoordinate() {
		return tileYCoordinate;
	}

	/**
	 * This is the X Coordinate in the tile name (the 0 in INT_X0Y5)
	 *
	 * @return the tileColumn
	 */
	public int getTileXCoordinate() {
		return tileXCoordinate;
	}

	/**
	 * Gets and returns the site array for this tile.
	 *
	 * @return An array of sites present in this tile.
	 */
	public List<Site> getSites() {
		return sites;
	}

	public Site getSite(int siteIndex) {
		return getSites().get(siteIndex);
	}

	/**
	 * Sets the sites present in this tile, should not be called during
	 * normal usage.
	 *
	 * @param sites The new sites.
	 */
	public void setSites(List<Site> sites) {
		this.sites = sites;
	}

	/* Routing description methods */

	public Map<String, TileWireTemplate> getTileWires() {
		return tileWires;
	}

	public void setTileWires(Map<String, TileWireTemplate> tileWires) {
		this.tileWires = tileWires;
	}

	/**
	 * Create Collection of TileWire objects for each wire in the tile.
	 * @return Collection of TileWire objects.
	 */
	public Collection<Wire> getWires() {
		return tileWires.values().stream()
			.map(w -> new TileWire(this, w))
			.collect(Collectors.toCollection(ArrayList::new));
	}

	public Collection<Node> getNodes() {
		return getWires().stream()
			.map(it -> it.getNode())
			.collect(Collectors.toSet());
	}

	/**
	 * Returns the wire in this tile with the given name.  This method does not
	 * guarantee that the requested wire exists in this tile.
	 * @param wireName the name of the wire to get
	 * @return the wire in this tile with the given name
	 */
	public TileWire getWire(String wireName) {
		TileWireTemplate template = tileWires.get(wireName);
		if (template == null)
			return null;
		return new TileWire(this, template);
	}

	/**
	 * @param wireName name of wire to query for
	 * @return true if the wire with the given name exists in this tile
	 */
	public boolean hasWire(String wireName) {
		return tileWires.containsKey(wireName);
	}

	TileNodeTemplate getNodeOfWire(TileWireTemplate wire) {
		return wireNodesMap.get(wire);
	}

	/**
	 * Checks if this tile contains a pip with the same connection
	 * as that provided.
	 *
	 * @param pip The pip connection to look for.
	 * @return True if the connection exists in this tile, false otherwise.
	 */
	public boolean hasPIP(PIP pip) {
		TileWireTemplate startWire = ((TileWire) pip.getStartWire()).getTemplate();
		TileWireTemplate endWire = ((TileWire) pip.getEndWire()).getTemplate();

		TileNodeTemplate startNode = getNodeOfWire(startWire);
		return startNode.getConnections().stream().anyMatch(it ->
			it.getSourceWire().equals(startWire) && it.getSinkWire().equals(endWire));
	}

	/**
	 * This method will create a new list of PIPs from those existing
	 * in the instance of this Tile object.  Use this method with caution as
	 * it will generate a lot of new PIPs each time it is called which may (or
	 * may not) use a lot of memory.
	 *
	 * @return A list of all PIPs in this tile.
	 */
	public Collection<PIP> getPIPs() {
		return getWires().stream()
			.map(it -> it.getNode())
			.distinct()
			.flatMap(it -> it.getWireConnections().stream())
			.filter(it -> it.getSourceWire().getTile().equals(this))
			.map(it -> it.getPip())
			.collect(Collectors.toList());
	}
	
	/**
	 * Returns the neighboring tile in the specified direction of this tile.
	 *
	 * @param direction the direction to search
	 * @return the adjacent tile
	 */
	public Tile getAdjacentTile(TileDirection direction) {
		switch(direction) 
		{
			case NORTH:
				return dev.getTile(row - 1, column);
			case SOUTH:
				return dev.getTile(row + 1, column);
			case WEST:
				return dev.getTile(row, column - 1);
			case EAST:
				return dev.getTile(row, column + 1);
			default: 
				throw new AssertionError("Invalid Tile Direction"); 
		}
	}

	/**
	 * @return a list containing the wires connecting to sink site pins
	 * for this tile.
	 */
	public List<Wire> getSinks() {
		if (getSites() == null)
			return Collections.emptyList();

		return sites.stream()
			.flatMap(s -> s.getSinkPins().stream())
			.map(SitePin::getExternalWire)
			.collect(Collectors.toList());
	}

	/**
	 * Gets and returns the sources of all the sites in this tile.  This list
	 * is lazily created.
	 *
	 * @return The source wires found in this tile.
	 */
	public List<Wire> getSources() {
		if (getSites() == null)
			return Collections.emptyList();

		return getSites().stream()
			.flatMap(s -> s.getSourcePins().stream())
			.map(SitePin::getExternalWire)
			.collect(Collectors.toList());
	}

	TileWireTemplate getPinwire(SiteType type, int siteIndex, int pinIndex) {
		return pinwires.get(siteIndex).get(type).get(pinIndex);
	}

	// Used by device.constructSiteExternalConnections
	public void setWireSites(Map<TileWireTemplate, Integer> wireSites) {
		this.wireSites = wireSites;
	}

	public Map<TileWireTemplate, Integer> getWireSites() {
		return wireSites;
	}

	/**
	 * Calculates the Manhattan distance between this tile and the given tile.
	 * It calculates the distance based on tileXCoordinate and tileYCoordinate
	 * rather than the absolute indices of the tile.
	 *
	 * @param tile The tile to compare against.
	 * @return The integer Manhattan distance between this and the given tile.
	 */
	public int getManhattanDistance(Tile tile) {
		return Math.abs(tile.tileXCoordinate - tileXCoordinate) +
				Math.abs(tile.tileYCoordinate - tileYCoordinate);
	}

	@Override
	public int hashCode() {
		return column*383+row;
	}

	@Override
	public String toString() {
		return getName();
	}

	private static class TileReplace implements Serializable {
		private static final long serialVersionUID = -3588973393824445640L;
		private String name;
		private TileType type;
		private List<Site> sites;
		private Map<String, TileWireTemplate> tileWires;

		@SuppressWarnings("unused")
		private Tile readResolve() {
			Tile tile = new Tile();
			tile.setName(name);
			tile.type = type;
			tile.sites = sites;
			for (int i = 0; i < sites.size(); i++) {
				sites.get(i).setIndex(i);
				sites.get(i).setTile(tile);
			}
			tile.tileWires = tileWires;

			return tile;
		}
	}

	@SuppressWarnings("unused")
	private TileReplace writeReplace() {
		TileReplace repl = new TileReplace();
		repl.name = name;
		repl.type = type;
		repl.sites = sites;
		repl.tileWires = tileWires;

		return repl;
	}
}
