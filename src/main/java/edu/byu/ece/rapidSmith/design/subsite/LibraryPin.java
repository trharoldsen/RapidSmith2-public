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

import edu.byu.ece.rapidSmith.device.BelId;
import edu.byu.ece.rapidSmith.device.PinDirection;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *  Pin on a {@link LibraryCell}.  {@code LibraryPin}s provide the backing for
 *  {@link BackedCellPin}s and define the directions of the pins and their possible
 *  BelPin mappings.
 */
public final class LibraryPin implements Serializable {
	
	/** Unique serialization version UID for this class*/
	private static final long serialVersionUID = 428090750543375520L;
	/** Name of the pin */
	private String name;
	/** The LibraryCell this pin is for */
	private LibraryCell libraryCell;
	/** Direction of the pin */
	private PinDirection direction;
	/** Names of the BelPins that this pin can map to for each BelIdentifier */
	private Map<BelId, List<String>> possibleBelPins;

	/** Type of the LibraryPin. See {@link CellPinType} for the possible types.*/ 
	private CellPinType pinType;

	/**
	 * Constructs an empty {@code LibraryPin}.
	 */
	public LibraryPin() {
		possibleBelPins = new HashMap<>();
	}

	/**
	 * Returns the name of this pin.
	 * @return name of this pin (ie I1, I2, O...)
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of this pin
	 * @param name name of this pin
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the {@code LibraryCell} this pin exists on.
	 * @return the {@code LibraryCell} this pin exists on
	 */
	public LibraryCell getLibraryCell() {
		return libraryCell;
	}

	/**
	 * Sets the {@code LibraryCell} this pin exists on.
	 * @param libraryCell the {@code LibraryCell} this pin exists on
	 */
	public void setLibraryCell(LibraryCell libraryCell) {
		this.libraryCell = libraryCell;
	}

	/**
	 * The direction of the pin relative to outside the cell.
	 * @return the direction of the pin relative to outside the cell
	 */
	public PinDirection getDirection() {
		return direction;
	}

	/**
	 * Sets the direction of the pin.  The direction is relative to outside the cell.
	 * @param direction the direction of this pin relative to outside the cell
	 */
	public void setDirection(PinDirection direction) {
		this.direction = direction;
	}

	/**
	 * Sets the type of {@code LibraryPin}. See {@link CellPinType} for the possible
	 * pin types. 
	 * 
	 * @param type the type of {@code LibraryPin}
	 */
	public void setPinType(CellPinType type) {
		pinType = type;
	}
	
	/**
	 * Returns the type of this LibraryPin.
	 * @return the {@link CellPinType} of this LibraryPin
	 */
	public CellPinType getPinType() {
		return pinType;
	}
	
	/**
	 * Returns a {@code Map} of {@code BelId} the {@code LibraryCell}
	 * can be placed on to a {@code List} of names of {@code BelPins} on such BELs
	 * that this pin can be placed onto.
	 * @return possible {@code BelPins} this library pin can map to for each possible
	 * {@code BelId}
	 */
	public Map<BelId, List<String>> getPossibleBelPins() {
		return possibleBelPins;
	}

	/**
	 * Returns a {@code List} of names of {@code BelPins} on BELs of type {@code belName}
	 * that this pin can be placed onto.
	 * @param belId {@code BelName} to get possible pins for
	 * @return list of possible BelPins on {@code belName}
	 */
	public List<String> getPossibleBelPins(BelId belId) {
		return possibleBelPins.get(belId);
	}

	/**
	 * Sets the possibleBelPins.  See {@link #getPossibleBelPins()}.
	 * @param possibleBelPins the possibleBelPins map
	 */
	public void setPossibleBelPins(Map<BelId, List<String>> possibleBelPins) {
		this.possibleBelPins = possibleBelPins;
	}

	/**
	 * LibraryPins are equal if they have the same name and LibraryCell.
	 * @param o the pin to compare to
	 * @return true if the pins are equivalent
	 */
	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		LibraryPin that = (LibraryPin) o;
		return Objects.equals(name, that.name) &&
				Objects.equals(libraryCell, that.libraryCell);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, libraryCell);
	}
}
