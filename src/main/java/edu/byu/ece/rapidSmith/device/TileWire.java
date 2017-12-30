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
	public TileNode getNode() {
		int rootRow = tile.getRow() - template.getNodeOffset().getRows();
		int rootCol = tile.getColumn() - template.getNodeOffset().getColumns();
		Tile root = tile.getDevice().getTile(rootRow, rootCol);
		return new TileNode(root, tile.getNodeOfWire(template));
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
