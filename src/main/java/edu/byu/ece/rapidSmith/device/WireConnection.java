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

import edu.byu.ece.rapidSmith.WireTemplate;

import java.io.Serializable;

/**
 * A Wire is describes simply as an integer representing the wire and
 * a row/column tile offset from the source wire. It makes little sense
 * by itself and is only understood in the context of the HashMap found in
 * Tile.HashMap&lt;Integer,Wire[]&gt; which provides all of the wire connections
 * from a wire existing to an array of Wire objects which describe wires
 * that maybe in the same tile or other tiles nearby depending on the
 * row and column offset.  These connections also may be PIP connections
 * if the boolean isPIP is true.
 * @author Chris Lavin
 *
 */
public class WireConnection<T extends WireTemplate> implements Serializable, Comparable<WireConnection<T>> {
	private static final long serialVersionUID = 8614891405695500370L;
	/** The wire enumeration value of the wire to be connected to */
	private final T template;
	/** The tile row offset from the source wire's tile */
	private final int rowOffset;
	/** The tile column offset from the source wire's tile */
	private final int columnOffset;
	/** Does the source wire connected to this wire make a PIP? */
	private final boolean isPIP;

	public WireConnection(T template, int rowOffset, int columnOffset, boolean pip){
		this.template = template;
		this.rowOffset = rowOffset;
		this.columnOffset = columnOffset;
		this.isPIP = pip;
	}

	/**
	 * @return the destination wire
	 */
	public T getSinkWire() {
		return template;
	}

	/**
	 * Returns the sink tile of this wire connection relative to the specified
	 * source tile.
	 * @param currTile the source tile of this wire connection
	 * @return the sink tile of this wire connection
	 */
	public Tile getTile(Tile currTile) {
		return currTile.getDevice().getTile(currTile.getRow()-this.rowOffset, currTile.getColumn()-this.columnOffset);
	}

	public Tile getWireCacheTile(Device dev, Tile currTile){
		String name = currTile.getName().substring(0, currTile.getName().lastIndexOf("_")+1) +
				"X" + (currTile.getTileXCoordinate()+this.columnOffset) +
				"Y" + (currTile.getTileYCoordinate()+this.rowOffset);
		return dev.getTile(name);
	}

	public int getRowOffset() {
		return rowOffset;
	}

	public int getColumnOffset() {
		return columnOffset;
	}

	/**
	 * Does the source wire connected to this wire make a PIP?
	 * @return true if this wire connection is a PIP
	 */
	public boolean isPIP() {
		return isPIP;
	}

	@Override
	public int hashCode(){
		int magicPrime = 131071;
		int hash = rowOffset;
		hash = hash * magicPrime + columnOffset;
		hash = hash * magicPrime + template.hashCode();
		return  hash;
	}

	@Override
	public int compareTo(WireConnection w){
		int compare = this.template.ordinal() - w.template.ordinal();
		if (compare != 0) return compare;
		compare = this.columnOffset - w.columnOffset;
		if (compare != 0) return compare;
		return this.rowOffset - w.rowOffset;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		WireConnection other = (WireConnection) obj;
		return template.equals(other.template) &&
			columnOffset == other.columnOffset &&
			rowOffset == other.rowOffset &&
			isPIP == other.isPIP;
	}

	@Override
	public String toString(){
		return template.getName() +"("+ rowOffset +","+ columnOffset +","+ isPIP + ")";
	}
}
