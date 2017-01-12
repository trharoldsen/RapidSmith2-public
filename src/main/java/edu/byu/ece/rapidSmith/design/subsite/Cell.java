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

import edu.byu.ece.rapidSmith.device.*;
import edu.byu.ece.rapidSmith.util.Exceptions.DesignAssemblyException;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * <p>
 * The {@code Cell} class represents the primitive logical elements in a
 * {@link CellDesign}.  Cells connect to the nets in a design through a collection of
 * {@link CellPin CellPins} and act as the computational and memory units in a netlist.
 * </p><p>
 * Cells in a design can be placed onto {@link Bel Bels} on the device.  Most cells have
 * a one-to-one mapping between cell -&gt; BEL.  Special hierarchical <tt>macro</tt>
 * cells may occupy multiple LUTs.  Macros are currently not supported in RS.
 * </p><p>
 * Placing a cell is accomplished with the {@link CellDesign#placeCell(Cell, Bel)} method.
 * Valid locations within a device for a cell can be obtained by calling
 * {@link #getPossibleAnchors()}.
 * </p><p>
 * Each cells is backed by a {@link LibraryCell} which defines the properties and
 * operations of the cell.  The {@code LibraryCell} is provided at creation and is
 * immutable.
 * </p><p>
 * A cells connects to the rest of the netlist through a group of {@code CellPins}.
 * Most of these pins are defined by the backing {@code LibraryCell} and are created
 * upon instantiation of the cell.  Special {@link PseudoCellPin pseudo pins} may also
 * be added to the cell by the user in cases where no pin on the cell is mapped to a
 * BEL pin which needs to be connected to the outer device.
 * </p>
 */
public class Cell {
	/** Unique name of this instance */
	private final String name;
	/** The CellDesign this cell exists in */
	private CellDesign design;
	/** Type of the cell (LUT6, FF, DSP48, ...) */
	private final LibraryCell libCell;
	/** IO Bondedness for this pad cells.  Use internal for non-IO pad cells. */
	private BondedType bonded;
	/** BEL in the device this site is placed on */
	private Bel bel;
	/** Properties of the cell */		
	private final PropertyList properties;
	/** Mapping of pin names to CellPin objects of this cell */
	private final Map<String, CellPin> pinMap;
	/**	Set of pseudo pins attached to the cell */
	private Set<CellPin> pseudoPins;

	/**
	 * Creates a new cell with name {@code name} and backed by {@code libCell}.  The
	 * pins from the backing {@code LibraryCell} are also created and added to the cell
	 * at this time.
	 *
	 * @param name name of the new cell
	 * @param libCell the {@code LibraryCell} this cell is based on
	 * @throws NullPointerException if {@code name} or {@code libCell} is null
	 */
	public Cell(String name, LibraryCell libCell) {
		Objects.requireNonNull(name);
		Objects.requireNonNull(libCell);

		this.name = name;
		this.libCell = libCell;
		this.bonded = BondedType.INTERNAL;

		this.design = null;
		this.bel = null;

		this.properties = new PropertyList();
		this.pinMap = new HashMap<>();
		for (LibraryPin pin : libCell.getLibraryPins()) {
			this.pinMap.put(pin.getName(), new BackedCellPin(this, pin));
		}

		// TODO subcells for hierarchical macros
	}

	/**
	 * Returns the name of this cell.  Cell names must be unique for a design.
	 * @return the name of this cell
	 */
	public final String getName() {
		return this.name;
	}

	/**
	 * Returns {@code true} if this cell is part of a design.
	 * @return {@code true} if this cell is part of a design
	 */
	public final boolean isInDesign() {
		return design != null;
	}

	/**
	 * Returns the design this cell exists in or null if it is not in a design.
	 * @return the design this cell exists in or null if it is not in a design
	 */
	public final CellDesign getDesign() {
		return design;
	}

	void setDesign(CellDesign design) {
		assert design != null;

		this.design = design;
	}

	void clearDesign() {
		this.design = null;
	}

	/**
	 * Returns the {@link LibraryCell} this cell is backed by.  The {@code LibraryCell}
	 * defines the type of the cell, its properties and where the cell can be placed.
	 *
	 * @return the {@code LibraryCell} this cell is backed by
	 */
	public final LibraryCell getLibCell() {
		return libCell;
	}

	/**
	 * Returns {@code true} if this cell acts as a VCC source.  A VCC source contains
	 * a pin that outputs a constant logic high which can source VCC nets.
	 *
	 * @return {@code true} if this cell acts as a VCC source
	 */
	public boolean isVccSource() {
		return getLibCell().isVccSource();
	}

	/**
	 * Returns {@code true} if this cell acts as a GND source.  A GND source contains
	 * a pin that outputs a constant logic low which can source GND nets.
	 *
	 * @return {@code true} if this cell acts as a GND source
	 */
	public boolean isGndSource() {
		return getLibCell().isGndSource();
	}

	/**
	 * Returns true if this cell is a top-level port of the design.  Ports are the
	 * IO in a design.
	 * // TODO Can Thomas add more here about what is special about these.
	 * @return true if this cell is a top-level port of the design
	 */
	public boolean isPort() {
		return getLibCell().isPort();
	}

	/**
	 * <p>
	 * Returns whether this is a cell which should be placed on a bonded IO pad.  On
	 * a device, only a subset of the IO pads are bonded.  This indicates to a placer
	 * which IO cells should be placed on bonded IO.
	 * </p><p>
	 * Non-IO cells should return {@link BondedType#INTERNAL}.
	 * {@code BondedType.INTERNAL} is the default value.
	 * </p>
	 * @return the {@code BondedType} property of this cell
	 * @see BondedType
	 */
	public BondedType getBonded() {
		return bonded;
	}

	/**
	 * Sets whether this cell should be placed on a bonded IO pad.  On a device, only
	 * a subset of the IO pads are bonded.  This indicates to a placer whether this
	 * cell requires a bonded IO.  Non-IO should be set to {@link BondedType#INTERNAL}.
	 *
	 * @param bonded the new bonded parameter for this cell
	 * @throws NullPointerException if {@code bonded} is null
	 * @see #getBonded()
	 */
	public void setBonded(BondedType bonded) {
		Objects.requireNonNull(bonded);

		this.bonded = bonded;
	}

	/**
	 * Returns {@code true} if this cell is placed in the design.
	 * @return {@code true} if this cell is placed in the design
	 */
	public final boolean isPlaced() {
		return bel != null;
	}

	/**
	 * Currently unsupported.  Returns the subcells in a macro cell.
	 * @return an empty collection at this time
	 */
	public final List<Cell> getSubcells() {
		// TODO get the subcells once we have support
		return Collections.emptyList();
	}

	/**
	 * Returns the {@link Bel} this cell is placed at.  For a macro, returns the anchor
	 * of this cell.
	 * @return the {@code Bel} this cell is placed at
	 * @see CellDesign#placeCell(Cell, Bel)
	 */
	public final Bel getBel() {
		return bel;
	}

	/**
	 * Returns the possible BELs this cell can be placed at.  For leaf cells, these
	 * are the BELs the cell can be placed on; for macros, these are the anchors defining
	 * the starting point for placing the cell.
	 *
	 * @return the possible locations this cell can be placed at
	 */
	public final List<BelId> getPossibleAnchors() {
		return getLibCell().getPossibleAnchors();
	}

	/**
	 * Returns a list of all BELs which will be required to place this cell when this
	 * cells is placed at {@code anchor}.  For leaf cells, the returned list will be
	 * a singleton containing {@code anchor}.  For macros, the returned list will
	 * contain all BELs needed for the subcells.
	 *
	 * @param anchor the anchor to get the required BELs for
	 * @return all BELs which will be required to place this cell at {@code anchor}
	 * @throws IllegalArgumentException if {@code anchor} is {@code null}
	 */
	public final List<Bel> getRequiredBels(Bel anchor) {
		Objects.requireNonNull(anchor);
		return getLibCell().getRequiredBels(anchor);
	}

	/**
	 * Returns the site this cell resides at or {@code null} if the cell is not placed.
	 * This method is identical to calling {@code getBel().getSite()}
	 *
	 * @return the site this cell resides
	 * @see #getBel()
	 */
	public final Site getSite() {
		return bel == null ? null : bel.getSite();
	}

	void place(Bel anchor) {
		assert anchor != null;

		this.bel = anchor;
	}

	void unplace() {
		this.bel = null;
	}

	private void ensurePseudoPinsCreated() {
		if ( pseudoPins == null ) {
			pseudoPins = new HashSet<>(5);
		}
	}

	/**
	 * Creates and attaches a new {@link PseudoCellPin} to this cell.  Pseudo pins are
	 * special cell pins that can be added to a cell to provide a cell pin object that
	 * map to BEL pins that do not have a normal mapping.
	 * 
	 * @param pinName name of the pin to create and attach
	 * @param dir direction of the pseudo pin
	 * @return the newly created pseudo CellPin.
	 * @throws NullPointerException if {@code pinName} or {@code dir} is {@code null}
	 * @throws IllegalArgumentException if a pin with name {@code pinName} already
	 *     exists on this cell
	 * @see PseudoCellPin
	 */
	public CellPin attachPseudoPin(String pinName, PinDirection dir) {
		Objects.requireNonNull(pinName);
		Objects.requireNonNull(dir);

		CellPin pseudoPin = new PseudoCellPin(pinName, dir);
		attachPseudoPin(pseudoPin);
		return pseudoPin;
	}

	/**
	 * <p>
	 * Attaches an existing {@link PseudoCellPin} to this cell.  Pseudo pins are
	 * special cell pins that can be added to a cell to provide a cell pin object that
	 * map to BEL pins that do not have a normal mapping.  Returns {@code true} if the
	 * pin is successfully added to this cell.
	 * </p><p>
	 * The provided pin should not be connected to another cell at this point.  If the
	 * pin already exists in this cell, the method makes no changes and returns
	 * {@code false}.  The provided pin will be updated to point to this cell as its
	 * new parent, if the pin is connected to net, it will be detached, and any existing
	 * cell pin to BEL pin mappings for the pin will be cleared.
	 * </p>
	 *
	 * @param pin the pseudo pin to attach to this cell
	 * @throws NullPointerException if {@code pin} is {@code null}
	 * @throws IllegalArgumentException if {@code pin} is not a pseudo pin
	 * @throws DesignAssemblyException if another pin with the same name already exists
	 *    on this cell or is not a pseudo pin
	 * 
	 * @return {@code true} if the pin was successfully attached to this cell.  If the
	 *    pin was already on this cell, returns {@code false}.
	 * @see PseudoCellPin
	 */
	public boolean attachPseudoPin(CellPin pin) {
		Objects.requireNonNull(pin);
		if (!pin.isPseudoPin()) {
			throw new IllegalArgumentException("Expected argument \"pin\" to be a pseudo cell pin.\n"
												+ "Cell: " + getName() + " Pin: " + pin.getName()); 
		}

		if (pin.getCell() == this)
			return false;

		if (pin.getCell() != null)
			throw new DesignAssemblyException("Pin \"" + pin.getName() + "\" already exists on a cell");

		if (pinMap.containsKey(pin.getName())) {
			throw new DesignAssemblyException("Pin \"" + pin.getName() + "\" already attached to cell  \""
									+ getName() + "\". Cannot attach it again");
		}

		pin.clearPinMappings();
		pin.clearNet();

		ensurePseudoPinsCreated();
		pin.setCell(this);
		this.pinMap.put(pin.getName(), pin);
		this.pseudoPins.add(pin);
		return true;
	}
	
	/**
	 * Detaches and removes the given pseudo pin from the cell.  This method will
	 * disconnect the pin from any connected nets and clear any cell pin to BEL pin
	 * mappings.
	 * 
	 * @param pin the pin to remove
	 * @return <code>true</code> if the pin was attached to the cell 
	 * 			and was successfully removed. <code>false</code> is returned if either
	 * 			{@code pin} is not a pseudo pin, or is not attached to the cell.
	 * @see PseudoCellPin
	 */
	public boolean removePseudoPin(CellPin pin) {
		if (pseudoPins == null || !pseudoPins.contains(pin)) {
			return false; 
		}

		pin.clearNet();
		pin.clearPinMappings();
		pin.clearCell();
		pinMap.remove(pin.getName());
		pseudoPins.remove(pin);
		return true;
	}
	
	/**
	 * Detaches and removes the pseudo pin with the given name from the cell.  This
	 * method will disconnect the pin from any connected nets and clear any cell pin
	 * to BEL pin mappings.
	 * 
	 * @param pinName name of the pin to remove
	 * @return the pin object removed from the cell. If no matching cell pin is found
	 *    or the cell pin is not a pseudo pin, null is returned.
	 * @throws NullPointerException if {@code pinName} is {@code null}
	 * @see PseudoCellPin
	 */
	public CellPin removePseudoPin(String pinName) {
		Objects.requireNonNull(pinName);
		CellPin pin = pinMap.get(pinName);
		
		if (pin == null || !pin.isPseudoPin()) {
			return null;
		}
		
		pinMap.remove(pinName);
		pseudoPins.remove(pin);
		return pin;
	}
	
	/**
	 * Returns all pseudo pins currently attached to this cell.  The returned set is
	 * unmodifiable.
	 * 
	 * @return an unmodifiable set of attached pseudo pins
	 * @see PseudoCellPin
	 */
	public Set<CellPin> getPseudoPins() {
		if (pseudoPins == null) {
			return Collections.emptySet();
		}
		
		// TODO: think about creating this unmodifiable during class creation instead of on demand
		return Collections.unmodifiableSet(pseudoPins);
	}
	
	/**
	 * Returns the number of pseudo pins currently attached to this cell.
	 * 
	 * @return the number of pseudo pins attached to this cell
	 */
	public int getPseudoPinCount() {
		return pseudoPins == null ? 0 : pseudoPins.size();
	}

	/**
	 * Returns the properties of this cell in a {@link PropertyList}.  The
	 * properties describe the configuration of this cell and can be used
	 * to store user attributes for this cell.
	 *
	 * @return a {@code PropertyList} containing the properties of this cell
	 */
	public final PropertyList getProperties() {
		return properties;
	}

	/**
	 * <p>
	 * Returns the control set properties and their values for the cell based on its
	 * placement.  Control sets are properties that must be
	 * configured consistently for all cells in a site.
	 * </p><p>
	 * The cell should be placed prior to calling this method.  To get the shared site
	 * properties for an unplaced cell, use {@link #getSharedSiteProperties(BelId)}.
	 * </p>
	 *
	 * @return the control set properties and their value for this cell
	 * @throws IllegalStateException if this cell is not placed
	 */
	public Map<SiteProperty, Object> getSharedSiteProperties() {
		// TODO rename this to getControlSetProperties?
		if (!isPlaced())
			throw new IllegalStateException("Cell not placed");
		return getSharedSiteProperties(bel.getId());
	}

	/**
	 * Returns the control set properties and their values for the cell when placed
	 * on a Bel of type {@link BelId}.  Control sets are properties that must be
	 * configured consistently for all cells in a site.  The cell does not need to be
	 * placed.
	 *
	 * @param belId the type of Bel to get the control set properties for
	 * @return the control set properties and their value for this cell
	 */
	public Map<SiteProperty, Object> getSharedSiteProperties(BelId belId) {
		Map<SiteProperty, Object> returnMap = new HashMap<>();

		Map<String, SiteProperty> referenceMap =
				getLibCell().getSharedSiteProperties(belId);
		for (Map.Entry<String, SiteProperty> e : referenceMap.entrySet()) {
			if (properties.has(e.getKey()) && properties.get(e.getKey()).getType() == PropertyType.DESIGN) {
				returnMap.put(e.getValue(), properties.getValue(e.getKey()));
			}
		}
		return returnMap;
	}

	/**
	 * Returns the nets that connect to the pins of this cell.  This list is rebuilt
	 * each time this method is called.
	 *
	 * @return the nets that connect to the pins of this cell
	 */
	public final Collection<CellNet> getNetList() {
		return pinMap.values().stream()
				.filter(pin -> pin.getNet() != null)
				.map(CellPin::getNet)
				.collect(Collectors.toSet());
	}

	/**
	 * Returns the pin on this cell with the specified name or {@code null} if no
	 * pin with the name exist on the cell.
	 *
	 * @param pinName the name of the pin
	 * @return the pin on this cell with the specified name or {@code null} if none
	 *    exists
	 * @throws NullPointerException if {@code pinName} is {@code null}
	 */
	public final CellPin getPin(String pinName) {
		Objects.requireNonNull(pinName);
		return pinMap.get(pinName);
	}

	/**
	 * Returns all of the cell pins (including pseudo pins) on this net.
	 * The returned set is unmodifiable.
	 *
	 * @return a collection of pins on this cell
	 */
	public final Collection<CellPin> getPins() {
		return Collections.unmodifiableCollection(pinMap.values());
	}

	/**
	 * Returns all of the output pins on this net.  Output pins have direction of
	 * either {@link PinDirection#OUT} or {@link PinDirection#INOUT}.  The collection
	 * is created on this call.
	 *
	 * @return a collection containing the output pins on this net
	 */
	public final Collection<CellPin> getOutputPins() {
		return pinMap.values().stream()
				.filter(CellPin::isOutpin)
				.collect(Collectors.toList());
	}

	/**
	 * Returns all of the input pins on this net.  Input pins have direction of
	 * either {@link PinDirection#IN} or {@link PinDirection#INOUT}.  The collection
	 * is created on this call.
	 *
	 * @return a collection containing the input pins on this net
	 */
	public final Collection<CellPin> getInputPins() {
		return pinMap.values().stream()
				.filter(CellPin::isInpin)
				.collect(Collectors.toList());
	}

	/**
	 * Creates and returns a deep copy of this cell.  The deep copy does not belong
	 * to a design.
	 *
	 * @return a deep copy of this cell
	 */
	public Cell deepCopy() {
		return deepCopy(Collections.emptyMap());
	}

	/**
	 * Returns a deep copy of this cell except with changes specified in the
	 * {@code changes} argument map.  The changes map is a map from a property name
	 * to a new value.  The accepted changes described in the map are <br>
	 * <ul>
	 *     <li>"name" -&gt; String</li>
	 *     <li>"type" -&gt; LibraryCell</li>
	 * </ul><br>
	 * Other values in the map are ignored.
	 *
	 * @param changes map containing the changes to be made to the cell copy
	 * @return a deep copy of this cell with specified changes
	 */
	public Cell deepCopy(Map<String, Object> changes) {
		return deepCopy(Cell::new, changes);
	}

	/**
	 * Method implementing the deep copy.  This method accepts a factory method for
	 * creating a new cell of the desired type and performs the deep copy.
	 *
	 * @param cellFactory factory for creating a new cell
	 * @param changes map of the changes to make
	 * @return the newly created deep copy of this cell
	 */
	protected Cell deepCopy(
			BiFunction<String, LibraryCell, Cell> cellFactory,
			Map<String, Object> changes
	) {
		String name;
		LibraryCell libCell;

		if (changes.containsKey("name"))
			name = (String) changes.get("name");
		else
			name = getName();

		if (changes.containsKey("type"))
			libCell = (LibraryCell) changes.get("type");
		else
			libCell = getLibCell();

		Cell cellCopy = cellFactory.apply(name, libCell);
		cellCopy.setBonded(getBonded());
		getProperties().forEach(p ->
				cellCopy.properties.update(copyAttribute(getLibCell(), libCell, p))
		);
		return cellCopy;
	}

	private Property copyAttribute(LibraryCell oldType, LibraryCell newType, Property orig) {
		if (!oldType.equals(newType) && orig.getKey().equals(oldType.getName())) {
			return new Property(newType.getName(), orig.getType(), orig.getValue());
		} else {
			return orig.deepCopy();
		}
	}

	/*  Uses identity equality */

	@Override
	public int hashCode() {
		return Objects.hash(name);
	}

	@Override
	public String toString() {
		return "Cell{" + getName() + " " + (isPlaced() ? "@" + getBel().getFullName() : "") + "}";
	}
}
