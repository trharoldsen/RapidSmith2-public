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

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.byu.ece.rapidSmith.device.Bel;
import edu.byu.ece.rapidSmith.device.BelId;
import edu.byu.ece.rapidSmith.device.BelPin;
import edu.byu.ece.rapidSmith.device.PinDirection;

/**
 * <p>
 * A pin on a {@link Cell}.  {@code CellPin}s connect cells to nets and map to BelPins.
 * CellPins are obtained through calls to {@link Cell#getPin(String)}.
 * </p><p>
 * There are two types of {@code CellPin}s -- {@link PseudoCellPin}s and
 * {@link BackedCellPin}s.  {@code BackedCellPin}s are pins that exist as part of
 * a cell, are described and back by a {@link LibraryPin}, and are created as when a
 * cell is created.  {@code PseudoCellPin}s are special pins that provide a logical
 * mapping for a {@code BelPin} that must be sourced but is not used by any of the
 * {@code BackedCellPin}s of the {@code Cell}.
 * </p>
 */
public abstract class CellPin implements Serializable {
 
	/** Unique Serial number for this class */
	private static final long serialVersionUID = 2612839140455524421L;
	/** The cell this pin resides on */
	private Cell cell;
	/** The net this pin is a member of */
	private CellNet net;
	/** Set of BelPin objects that this pin maps to*/
	private Set<BelPin> belPinMappingSet;

	/**
	 * A package private constructor to create a new CellPin
	 * 
	 * @param cell parent cell of the CellPin. Can be <code>null</code>
	 * 				if the cell is an unattached pseudo pin .
	 */
	CellPin(Cell cell) {
		this.cell = cell;
	}
	
	/**
	 * Returns the cell where this pin resides.
	 *
	 * @return the cell where the pin resides.
	 */
	public Cell getCell() {
		return cell;
	}

	/**
	 * Sets the {@link Cell} that this pin is attached to.
	 *
	 * @param inst {@link Cell} to mark as the parent of this pin
	 */
	void setCell(Cell inst) {
		this.cell = inst;
	}

	/**
	 * Detaches this pin from the {@link Cell} it is was attached to.
	 */
	void clearCell() {
		this.cell = null;
	}

	/**
	 * Returns whether this pin is connected to a net.
	 * @return true is this CellPin is attached to a net, else false
	 */
	public boolean isConnectedToNet() {
		return net != null;
	}

	/**
	 * Returns the net this pin is connected to.
	 * @return the net attached to this pin or null if this pin is not connected
	 */
	public CellNet getNet() {
		return net;
	}

	/**
	 * Sets the {@link CellNet} that this pin is connected to.
	 * 
	 * @param net {@code CellNet} to connect the pin to.
	 */
	void setNet(CellNet net) {
		this.net = net;
	}

	/**
	 * Disconnects this pin from the net is was connected to.
	 */
	void clearNet() {
		this.net = null;
	}
	
	/**
	 * Returns whether this pin is an input pin.  A pin is an input pin if its
	 * {@link PinDirection direction} is {@code IN} or {@code INOUT}.
	 * @return true if this pin is an input pin
	 */
	public boolean isInpin() {
		switch (getDirection()) {
		case IN:
		case INOUT:
			return true;
		default:
			return false;
		}
	}

	/**
	 * Returns whether this pin is an output pin.  A pin is an output pin if its
	 * {@link PinDirection direction} is {@code OUT} or {@code INOUT}.
	 * @return true if this pin is an output pin
	 */
	public boolean isOutpin(){
		switch (getDirection()) {
		case OUT:
		case INOUT:
			return true;
		default:
			return false;
		}
	}
	
	/**
	 * Maps this pin to the specified {@code BelPin}.
	 * 
	 * @param pin the BelPin to map this pin to
	 * @return true if the CellPin is not already mapped to the BelPin, else false
	 */
	public boolean mapToBelPin(BelPin pin) {
		
		if (belPinMappingSet == null) {
			belPinMappingSet = new HashSet<>();
		}
		
		return belPinMappingSet.add(pin);
	}
	
	/**
	 * Maps the CellPin to all BelPins in the specified collection.
	 *  
	 * @param pins Collection of BelPins to map BelPins to. 
	 * @return <code>true</code> if all BelPins in {@code pins} have not already been mapped to the CellPin
	 */
	public boolean mapToBelPins(Collection<BelPin> pins) {
		
		boolean allPinsMapped = true;
		
		for (BelPin pin : pins) {
			allPinsMapped &= mapToBelPin(pin);
		}
		return allPinsMapped;
	}
	
	/**
	 * Tests if this pin is mapped to the specified BelPin.
	 * 
	 * @param pin the BelPin to test against
	 * @return <code>true</code> if the mapping {@code this} -&gt; {@code pin} exists
	 */
	public boolean isMappedTo(BelPin pin) {
		return belPinMappingSet != null && belPinMappingSet.contains(pin);

	}
	
	/**
	 * Tests to see if this pin has been mapped to a BelPin.
	 * 
	 * @return true if this pin is mapped to at least one BelPin, else false
	 */
	public boolean isMapped() {
		return belPinMappingSet != null && !belPinMappingSet.isEmpty();
	}
	
	/**
	 * Returns the BelPins this CellPin is currently mapped to.
	 * @return a set of the {@code BelPin}s this pin is mapped to
	 */
	public Set<BelPin> getMappedBelPins() {
		return (belPinMappingSet == null) ? Collections.emptySet() : Collections.unmodifiableSet(belPinMappingSet); 
	}
	
	/**
	 * Returns the BelPin that this CellPin currently maps to. Use this function
	 * if you know that the CellPin is currently mapped to only one BelPin, otherwise
	 * use {@link #getMappedBelPins()} instead.
	 * 
	 * @return the BelPin that this CellPin is mapped to or null if the CellPin
	 * is not mapped to any BelPins
	 */
	public BelPin getMappedBelPin() {
		if (belPinMappingSet == null || belPinMappingSet.isEmpty()) {
			return null;
		}
		else {
			return belPinMappingSet.iterator().next();
		}
	}
	
	/**
	 * Returns the number of BelPins that this pin is mapped to. In most cases,
	 * CellPins are mapped to only one BelPin, but it is possible that they can
	 * be mapped to 0 or more than 1 BelPins. 
	 * 
	 *  @return the number of BelPins this pin is mapped to
	 */
	public int getMappedBelPinCount() {
		return belPinMappingSet == null ? 0 : belPinMappingSet.size();
	}
	
	/**
	 * Clears this CellPin -&gt; BelPins mappings for this pin (i.e. this
	 * pin will no longer map to any BelPins). 
	 */
	public void clearPinMappings() {
		if (getPossibleBelPins().size() > 1) {
			this.belPinMappingSet = null;
		}
	}
	
	/**
	 * Removes the pin mapping to the specified BelPin
	 * 
	 * @param belPin the BelPin to un-map
	 */
	public void clearPinMapping(BelPin belPin) {
		if (belPinMappingSet != null && belPinMappingSet.contains(belPin)) {
			belPinMappingSet.remove(belPin);
		}
	}
	
	/**
	 * Prints the CellPin object in the form: 
	 * "CellPin{ parentCellName.CellPinName }"
	 */
	@Override
	public String toString() {
		return "CellPin{" + getFullName() + "}";
	}
	
	/* **************************************************************
	 *  List of Abstract Functions to be implemented by sub-classes 
	 * **************************************************************/
	
	/**
	 * Returns the name of this pin.
	 * @return the name of this pin
	 */
	public abstract String getName();
	
	/**
	 * Returns the full name of this pin in the form "cellName/pinName"
	 * @return the full name of the CellPin in the form "cellName/pinName"
	 */
	public abstract String getFullName();

	/**
	 * Returns the {@link PinDirection direction} of this pin.  This direction is
	 * from the perspective of outside the cell.
	 * @return the direction of this pin
	 */
	public abstract PinDirection getDirection();  
	
	/**
	 * Returns true if this pin is a {@link PseudoCellPin pseudo pin}.
	 * @return true if the pin is a pseudo pin, else false
	 */
	public abstract boolean isPseudoPin(); 
	
	/**
	 * Returns the BelPins that this pin can potentially be mapped onto. The BEL that this pin's parent
	 * cell is placed on is used to determine the potential mappings. This function should 
	 * NOT be called on pseudo CellPin objects because pseudo pins are not backed
	 * by an associated {@link LibraryPin}.
	 * 
	 * @return list of possible BelPins (which might be empty). If the caller is a pseudo pin, 
	 * 			<code>null</code> is returned.
	 */
	public abstract List<BelPin> getPossibleBelPins(); 

	/**
	 * Returns the BelPins that this pin can potentially be mapped to on the specified {@code bel}. 
	 * This function should NOT be called on pseudo CellPin objects because pseudo pins are not backed
	 * by an associated {@link LibraryPin}.
	 * 
	 * @param bel The BEL to get the possible pin maps for this pin
	 * @return list of possible BelPins (which could be empty). If the caller is a pseudo pin, 
	 * 			<code>null</code> is returned
	 */
	public abstract List<BelPin> getPossibleBelPins(Bel bel); 
	
	/**
	 * Returns the names of the BelPins that this pin can potentially be mapped onto. This function
	 * should NOT be called if the CellPin is a pseudo CellPin because pseudo pins are not backed
	 * by an associated {@link LibraryPin}. 
	 * 
	 * @return list of names of possible BelPins (which could be empty). If the caller is a pseudo pin, 
	 * 			<code>null</code> is returned
	 */
	public abstract List<String> getPossibleBelPinNames();  
	
	/**
	 * Returns the names of the BelPins that this pin can potentially be mapped onto.
	 * This function should NOT be called if the CellPin is a pseudo CellPin because 
	 * pseudo pins are not backed by an associated {@link LibraryPin}.
	 *
	 * @param belId the type of BEL this pin's cell is placed on
	 * @return list of names of possible BelPins (which could be empty). If the caller is a pseudo pin, 
	 * 			<code>null</code> is returned
	 */
	public abstract List<String> getPossibleBelPinNames(BelId belId);  
	
	/**
	 * Returns the {@link CellPinType} of this pin.
	 * @return the type of this pin.
	 */
	public abstract CellPinType getType();
}
