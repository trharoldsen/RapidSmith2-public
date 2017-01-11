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
import java.util.Objects;

/**
 * <p>
 * A programmable-interconnect-point (PIPs).  This is a connection between two wires
 * that can be turned on or off in the device.  The start and end wires in PIPs always
 * exist in the same tile.
 * </p><p>
 * PIPs objects are created by the user.  The PIPs are used primarily in the XDL
 * {@link edu.byu.ece.rapidSmith.design.xdl.XdlNet} class to describe the physical
 * route of a device.  No checking is performed that the connection between wires in
 * the PIP actually exists in the device nor that the connection is in fact a PIP.
 * </p>
 */
public final class PIP implements Serializable {
	private static final long serialVersionUID = 122367735864726588L;
	private final Wire startWire;
	private final Wire endWire;

	/**
	 * Constructs a new PIP from the given wires.  No checking is performed to ensure
	 * that {@code startWire} and {@code endWire} are part of a PIP in the device.
	 *
	 * @param startWire the start wire of this PIP
	 * @param endWire the end wire of this PIP
	 * @throws NullPointerException if startWire or endWire is null
	 * @throws IllegalArgumentException if startWire and endWire are in different tiles
	 */
	public PIP(Wire startWire, Wire endWire) {
		Objects.requireNonNull(startWire);
		Objects.requireNonNull(endWire);

		if (startWire.getTile() != endWire.getTile())
			throw new IllegalArgumentException("startWire and endWire in different tiles");

		this.startWire = startWire;
		this.endWire = endWire;
	}

	/**
	 * Returns the wire driving this PIP.
	 *
	 * @return the start wire of this PIP
	 */
	public Wire getStartWire() {
		return startWire;
	}

	/**
	 * Returns the wire driven by this PIP.
	 *
	 * @return the end wire of this PIP
	 */
	public Wire getEndWire() {
		return endWire;
	}

	/**
	 * Returns the tile this PIP is in.
	 *
	 * @return the tile this PIP is in
	 */
	public Tile getTile() {
		return startWire.getTile();
	}

	@Override
	public int hashCode() {
		return endWire.hashCode() + startWire.hashCode();
	}

	/**
	 * PIPs are equal if they have the same start and end wires.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PIP other = (PIP) obj;

		return (endWire.equals(other.endWire) && startWire.equals(other.startWire)) ||
				(endWire.equals(other.startWire) && startWire.equals(other.endWire));
	}

	/**
	 * Creates an XDL-compatible string representation of this PIP.
	 *
	 * @return an XDL-compatible string of this PIP
	 */
	public String toString() {
		return "pip " + startWire.getTile().getName() + " " + startWire.getWireName() +
				" -> " + endWire.getWireName();
	}
}
