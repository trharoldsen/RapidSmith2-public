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

import edu.byu.ece.rapidSmith.device.Bel;
import edu.byu.ece.rapidSmith.device.BelId;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *  Provides a template of possible types of cells for a design.
 */
public abstract class LibraryCell implements Serializable {
	private static final long serialVersionUID = -7850247997306342388L;
	private final String name;

	/**
	 * Creates a new LibraryCell.
	 * @param name the name/type of this {@code LibraryCell}
	 */
	protected LibraryCell(String name) {
		Objects.nonNull(name);
		this.name = name;
	}

	/**
	 * Returns the name/type of this {@code LibraryCell}.
	 * @return the name of this {@code LibraryCell}
	 */
	public final String getName() {
		return name;
	}

	/**
	 * Returns whether this cell is a macro.  Macros are hierarchical cells that
	 * contain subcells that are placed on BELs.
	 * @return true if this cell is a macro, else false
	 */
	abstract public boolean isMacro();

	/**
	 * Returns true if this {@code LibraryCell} represents a VCC source.
	 * @return true if this {@code LibraryCell} represents a VCC source, else false
	 */
	abstract public boolean isVccSource();

	/**
	 * Returns true if this {@code LibraryCell} represents a GND source.
	 * @return true if this {@code LibraryCell} represents a GND source, else false
	 */
	abstract public boolean isGndSource();

	/**
	 * Returns true if this {@code LibraryCell} represents a LUT.
	 * @return true if this {@code LibraryCell} represents a LUT, else false
	 */
	abstract public boolean isLut();

	/**
	 * Returns true if this {@code LibraryCell} represents a port.
	 * @return true if this {@code LibraryCell} represents a port, else false
	 */
	abstract public boolean isPort();

	/**
	 * For LUT {@code LibraryCell}s, returns the number of inputs to the LUT.  For
	 * non-LUT {@code LibraryCell}, returns null.
	 * @return the number of inputs for LUT {@code LibraryCell} or null for other cells
	 */
	abstract public Integer getNumLutInputs();

	/**
	 * Returns the {@code LibraryPin}s on this {@code LibraryCell}.
	 * @return the {@code LibraryPin}s on this {@code LibraryCell}
	 */
	abstract public List<LibraryPin> getLibraryPins();

	/**
	 * Returns the possible types of BELs this cell can be placed/anchored on.
	 * @return the possible types of BELs this cell can be placed/anchored on
	 */
	abstract public List<BelId> getPossibleAnchors();

	/**
	 * Returns the BELs that are required to place cells of this type when anchored
	 * at Bel {@code anchor}.
	 * @param anchor the BEL the cell is being placed at
	 * @return the BELs required to place cells of this type for the specified anchor
	 */
	abstract public List<Bel> getRequiredBels(Bel anchor);

	/**
	 * Returns the control set properties for this {@code LibraryCell}.  The properties
	 * are returned in a map of the property name to a {@link SiteProperty} description
	 * of the property.
	 *
	 * @param anchor the type of BEL this cell is placed at
	 * @return a map of the control set properties for this {@code LibraryCell}
	 */
	abstract public Map<String, SiteProperty> getSharedSiteProperties(BelId anchor);

	/**
	 * Returns the {@link LibraryPin} on this LibraryCell with the given name.
	 * Operates in O{# of pins} time.
	 *
	 * @param pinName the name of the LibraryPin to get
	 * @return the {@code LibraryPin} on this  LibraryCell with the given name
	 */
	public LibraryPin getLibraryPin(String pinName) {
		for (LibraryPin pin : getLibraryPins()) {
			if (pin.getName().equals(pinName))
				return pin;
		}
		return null;
	}
}
