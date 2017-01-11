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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.byu.ece.rapidSmith.device.Bel;
import edu.byu.ece.rapidSmith.device.BelId;
import edu.byu.ece.rapidSmith.device.BelPin;
import edu.byu.ece.rapidSmith.device.PinDirection;

/**
 * <p>
 * A {@code CellPin} that is "backed" by a {@code LibraryPin}.  {@code BackedCellPin}
 * represents cell pins that serve as functional inputs and outputs of the cell.
 * </p><p>
 * Instances of {@code BackedCellPin} are automatically created upon the creation
 * of a new cell object.  Users do not create instances of {@code BackedCellPin}.
 * Instead, the cell pin objects are obtained through calls to {@link Cell#getPins()}.
 * </p><p>
 * As indicated by the name, instances of {@code BackedCellPin} are backed by a
 * {@code LibraryPin}.  This backing object defines the name, direction,
 * type and possible BEL pins locations for the pin.  All of this information is
 * exposed through the methods in the {@code CellPin} class and the user should not
 * need to obtain the backing {@code LibraryPin} object.
 * </p>
 * @see LibraryPin
 * @see BelPin
 * @see Cell
 */
public final class BackedCellPin extends CellPin {

	/** Unique serial number for class */
	private static final long serialVersionUID = 3866278951715319836L;
	/** LibraryPin forming the basis of this pin */
	private final LibraryPin libraryPin;
	
	/**
	 * Package Private Constructor for BackedCellPin objects.  This
	 * constructor is called by {@link Cell} while creating
	 * the pins of the cell.
	 * 
	 * @param cell cell this pin is on
	 * @param libraryPin the {@link LibraryPin} backing this pin
	 */
	BackedCellPin(Cell cell, LibraryPin libraryPin) {
		super(cell);
		
		assert cell != null;
		assert libraryPin != null;
		
		this.libraryPin = libraryPin;
	}
	
	@Override
	public String getName() {
		return libraryPin.getName();
	}

	@Override
	public String getFullName() {
		return getCell().getName() + "." + getName();
	}

	@Override
	public PinDirection getDirection() {
		return libraryPin.getDirection();
	}

	/**
	 * Returns {@code false} -- {@code BackedCellPin} objects are never pseudo pins.
	 * {@inheritDoc}
	 * @return {@code false}
	 */
	@Override
	public boolean isPseudoPin() {
		return false;
	}

	@Override
	public List<BelPin> getPossibleBelPins() {
		return getPossibleBelPins(getCell().getBel());
	}

	@Override
	public List<BelPin> getPossibleBelPins(Bel bel) {
		List<String> belPinNames = getPossibleBelPinNames(bel.getId());
		List<BelPin> belPins = new ArrayList<>(belPinNames.size());
		
		for (String pinName : belPinNames) {
			belPins.add(bel.getBelPin(pinName));
		}
		return belPins;
	}

	@Override
	public List<String> getPossibleBelPinNames() {
		Cell cell = getCell();
		return (cell.isPlaced()) ? getPossibleBelPinNames(cell.getBel().getId())
								: Collections.emptyList(); 
	}

	@Override
	public List<String> getPossibleBelPinNames(BelId belId) {
		return libraryPin.getPossibleBelPins().getOrDefault(belId, Collections.emptyList());
	}

	/**
	 * Returns the {@code LibraryPin} backing this pin.  This method is provided
	 * to give users access to the lower level RS data structures.
	 * @return the {@code LibraryPin} backing this pin
	 */
	public LibraryPin getLibraryPin() {
		return libraryPin;
	}

	@Override
	public CellPinType getType() {
		return libraryPin.getPinType();
	}
}
