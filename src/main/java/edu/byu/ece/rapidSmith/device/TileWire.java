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

import edu.byu.ece.rapidSmith.device.Connection.ReverseTileWireConnection;
import edu.byu.ece.rapidSmith.device.Connection.TileToSiteConnection;
import edu.byu.ece.rapidSmith.device.Connection.TileWireConnection;
import edu.byu.ece.rapidSmith.util.ArraySet;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;

/**
 * A wire inside a tile but outside a site.  This is part of the general
 * routing circuitry.  TileWires are composed of the tile the wire exists in
 * and the enumeration identifying the individual wire.
 */
public class TileWire implements Wire, Serializable {

	private static final long serialVersionUID = 5844788118958981887L;
	private Tile tile;
	private TileWireTemplate template;
	
	public TileWire(Tile tile, TileWireTemplate template) {
		assert tile != null;

		this.tile = tile;
		this.template = template;
	}

	@Override
	public Tile getTile() {
		return tile;
	}

	@Override
	public String getName() {
		return template.getName();
	}

	@Override
	public String getFullName() {
		return getTile().getName() + "/" + getName();
	}

	@Override
	public int ordinal() {
		return template.ordinal();
	}

	/**
	 * Always returns null.
	 */
	@Override
	public Site getSite() {
		return null;
	}

	public TileWireTemplate getTemplate() {
		return template;
	}

	/**
	 * Returns all sink connections within and between tiles.
	 */
	@Override
	public Collection<Connection> getWireConnections() {
		ArraySet<WireConnection<TileWireTemplate>> wireConnections = tile.getWireConnections(template);
		if (wireConnections == null)
			return Collections.emptyList();
		
		return wireConnections.stream()
				.map(wc -> new TileWireConnection(this, wc))
				.collect(Collectors.toList());
	}
	
	@Override
	public Collection<SitePin> getAllConnectedPins() {
		Collection<SitePin> sitePins = tile.getSitePinsOfWire(this.template);
		return sitePins.stream().filter(it -> !it.isInput())
			.collect(Collectors.toSet());
	}

	/**
	 * Returns all connections into primitive sites this wire drives.
	 */
	@Override
	@Deprecated
	public Collection<Connection> getPinConnections() {
		SitePin sitePin = getConnectedPin();
		if (sitePin == null)
			return emptyList();
		Connection c = new TileToSiteConnection(sitePin);
		return singleton(c);
	}

	/**
	 * Returns the connection into a primitive site that this wire drives.
	 */
	@Override
	public SitePin getConnectedPin() {
		SitePin sitePin = tile.getSitePinOfWire(this.template);
		if (sitePin == null || !sitePin.isInput())
			return null;
		return sitePin;
	}

	/**
	 * Always return an empty list.
	 */
	@Override
	@Deprecated
	public Collection<Connection> getTerminals() {
		return Collections.emptyList();
	}

	/**
	 * Always returns null.
	 */
	@Override
	public BelPin getTerminal() {
		return null;
	}

	/**
	 * Returns connection to all connections within or between tiles which drive
	 * this wire.
	 */
	@Override
	public Collection<Connection> getReverseWireConnections() {
		ArraySet<WireConnection<TileWireTemplate>> wireConnections = tile.getReverseConnections(template);
		if (wireConnections == null)
			return Collections.emptyList();

		return wireConnections.stream()
				.map(wc -> new ReverseTileWireConnection(this, wc))
				.collect(Collectors.toList());
	}
	
	@Override
	public Collection<SitePin> getAllReverseSitePins() {
		Collection<SitePin> sitePins = tile.getSitePinsOfWire(this.template);
		return sitePins.stream().filter(it -> !it.isOutput())
			.collect(Collectors.toSet());
	}

	/**
	 * Returns all site pin connections driving this wire.
	 */
	@Override
	@Deprecated
	public Collection<Connection> getReversePinConnections() {
		SitePin sitePin = getReverseConnectedPin();
		if (sitePin == null)
			return emptyList();
		return singleton(new TileToSiteConnection(sitePin));
	}

	/**
	 * Returns the site pin connection driving this wire.
	 */
	@Override
	public SitePin getReverseConnectedPin() {
		SitePin sitePin = tile.getSitePinOfWire(this.template);
		if (sitePin == null || !sitePin.isOutput())
			return null;
		return sitePin;
	}

	/**
	 * Always returns an empty list.
	 */
	@Override
	@Deprecated
	public Collection<Connection> getSources() {
		return Collections.emptyList();
	}

	/**
	 * Always returns null.
	 */
	@Override
	public BelPin getSource() {
		return null;
	}

	/**
	 * Tests if the object is equal to this wire.  Wires are equal if they share
	 * the same tile and wire enumeration.
	 * @return true if <i>obj</i> is the same wire as this wire
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		TileWire other = (TileWire) obj;
		return this.tile.equals(other.tile)
				&& this.template.equals(other.template);
	}

	@Override
	public int hashCode() {
		return template.hashCode() * 8191 + tile.hashCode();
	}

	@Override
	public String toString() {
		return tile.getName() + " " + template.getName();
	}
}
