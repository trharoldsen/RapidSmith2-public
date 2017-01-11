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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A non-macro {@link LibraryCell}.  Cells backed by this
 * {@code SimpleLibraryCell} are placed directly onto BELs.
 */
public class SimpleLibraryCell extends LibraryCell {
	private static final long serialVersionUID = 6378678352365270213L;
	/** List of types of BELs cells of this type can be placed on */
	private List<BelId> compatibleBels;
	/** List of LibraryPins of this LibraryCell */
	private List<LibraryPin> libraryPins;
	private Map<BelId, Map<String, SiteProperty>> sharedSiteProperties;

	private boolean isVccSource;
	private boolean isGndSource;
	private Integer numLutInputs = null;
	private boolean isPort;

	/**
	 * Constructs a new {@code SimpleLibraryCell}.
	 * @param name the name of the {@code LibraryCell}
	 */
	public SimpleLibraryCell(String name) {
		super(name);
	}

	@Override
	public boolean isMacro() {
		return false;
	}

	@Override
	public boolean isPort() {
		return isPort;
	}
	
	@Override
	public boolean isVccSource() {
		return isVccSource;
	}

	/**
	 * Sets whether this is a VCC source.
	 * @param isVccSource true if this is a VCC source
	 */
	public void setVccSource(boolean isVccSource) {
		this.isVccSource = isVccSource;
	}

	@Override
	public boolean isGndSource() {
		return isGndSource;
	}

	/**
	 * Sets whether this is a port.
	 * @param isPort true if this is a port
	 */
	public void setIsPort(boolean isPort) {
		this.isPort = isPort;
	}

	/**
	 * Sets whether this is a GND source.
	 * @param isGndSource true if this is a GND source
	 */
	public void setGndSource(boolean isGndSource) {
		this.isGndSource = isGndSource;
	}

	/**
	 * Sets the number of inputs for LUT cells.  Set to null for non-LUT cells.
	 * @param numInputs the number of inputs for LUT cells or null
	 */
	public void setNumLutInputs(Integer numInputs) {
		this.numLutInputs = numInputs;
	}

	@Override
	public boolean isLut() {
		return numLutInputs != null;
	}

	@Override
	public Integer getNumLutInputs() {
		return numLutInputs;
	}

	/**
	 * @return the templates of the pins that reside on cells of this type
	 */
	@Override
	public List<LibraryPin> getLibraryPins() {
		return libraryPins;
	}

	/**
	 * List containing the templates of all pins on this cell
	 * @param libraryPins list containing the templates of all pins on this cell
	 */
	public void setLibraryPins(List<LibraryPin> libraryPins) {
		this.libraryPins = libraryPins;
	}

	/**
	 * Returns the {@link BelId}s cells of this type can be placed on.
	 * @return list of the {@link BelId}s cells of this type can be placed on
	 */
	@Override
	public List<BelId> getPossibleAnchors() {
		return compatibleBels;
	}

	/**
	 * List containing the {@link BelId}s of BELs that cells of this type can be
	 * placed on.
	 * @param possibleBels the list of the possible bels for this object
	 */
	public void setPossibleBels(List<BelId> possibleBels) {
		this.compatibleBels = possibleBels;
	}

	/**
	 * As these have no hierarchy, returns a singleton with anchor.
	 * @param anchor the BEL the cell is being placed at
	 * @return {@code anchor}
	 */
	@Override
	public List<Bel> getRequiredBels(Bel anchor) {
		return Collections.singletonList(anchor);
	}

	@Override
	public Map<String, SiteProperty> getSharedSiteProperties(BelId anchor) {
		return sharedSiteProperties.get(anchor);
	}

	/**
	 * Sets the shared site properties.
	 * @param sharedSiteProperties the shared site properties for this {@code LibraryCell}
	 */
	public void setSharedSiteProperties(
			Map<BelId, Map<String, SiteProperty>> sharedSiteProperties
	) {
		this.sharedSiteProperties = sharedSiteProperties;
	}

	/**
	 * Library cells with the same name are considered equals.
	 */
	@Override
	public boolean equals(Object o) {
		// I'd prefer this to be identity equals but I think that would break
		// code somewhere.
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		LibraryCell that = (LibraryCell) o;
		return Objects.equals(getName(), that.getName());
	}

	@Override
	public int hashCode() {
		return Objects.hash(getName());
	}

	@Override
	public String toString() {
		return "LibraryCell{" +
				"name='" + getName() + '\'' +
				'}';
	}
}
