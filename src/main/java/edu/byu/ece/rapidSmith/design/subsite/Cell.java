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
import edu.byu.ece.rapidSmith.device.BondedType;
import edu.byu.ece.rapidSmith.device.PinDirection;
import edu.byu.ece.rapidSmith.device.Site;

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
 * a one-to-one mapping between cell @code{->} BEL.  Special hierarchical <tt>macro</tt>
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
	 *
	 * @return the name of this cell
	 */
	public final String getName() {
		return this.name;
	}

	/**
	 * Returns true if this cell is part of a design.
	 * @return true if this cell is part of a design
	 */
	public final boolean isInDesign() {
		return design != null;
	}

	/**
	 * Returns the design this cell exists in or null if the Cell is an orphan.
	 * @return the design this cell exists in
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
	 * defines the type of the cell and where the cell can be placed.
	 *
	 * @return the {@code LibraryCell} this cell is backed by
	 */
	public final LibraryCell getLibCell() {
		return libCell;
	}

	/**
	 * Returns true if this cell acts as a VCC source.  When true, this cell
	 * always outputs VCC and can drive VCC nets.
	 *
	 * @return true if this cell acts as a VCC source
	 */
	public boolean isVccSource() {
		return getLibCell().isVccSource();
	}

	/**
	 * Returns true if this cell acts as a ground source.  When true, this cell
	 * always outputs ground and can drive GND nets.
	 *
	 * @return true if this cell acts as a ground source
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
	 * Returns whether this cell should be placed on a bonded IO pad.  IO cells can be
	 * either {@link BondedType#BONDED} or {@link BondedType#UNBONDED}, non-IO cells
	 * should be {@link BondedType#INTERNAL}.
	 *
	 * @return the bondedness of this cell
	 */
	public BondedType getBonded() {
		return bonded;
	}

	/**
	 * Sets whether this cell should be placed on a bonded IO pad.  Non-IO should be
	 * set to INTERNAL.  INTERNAL is the default value.
	 * @param bonded the new bonded parameter for this cell
	 * @throws NullPointerException if {@code bonded} is null
	 * @see #getBonded()
	 */
	public void setBonded(BondedType bonded) {
		Objects.requireNonNull(bonded);

		this.bonded = bonded;
	}

	/**
	 * Returns true if this cell is placed on a BEL.
	 * @return true if this cell is placed on a BEL
	 */
	public final boolean isPlaced() {
		return bel != null;
	}

	/**
	 * Currently unsupported.
	 * @return an empty collection at this time
	 */
	public final List<Cell> getSubcells() {
		// TODO get the subcells once we have support
		return Collections.emptyList();
	}

	/**
	 * Returns the {@link Bel} this cell is placed at.
	 *
	 * @return the {@code Bel} this cell is placed at
	 */
	public final Bel getBel() {
		return bel;
	}

	/**
	 * Returns the possible {@link Bel}s this cell can be placed at.  For
	 * non-hierarchical cells, these is the {@code Bel}s the cell can be placed on;
	 * for hierarchical cells, these are the anchors defining the starting point for
	 * placing the cell.
	 *
	 * TODO: Thomas -- what is the correct term for non-hierarchical cells
	 *
	 * @return the possible locations this cell can be placed at
	 */
	public final List<BelId> getPossibleAnchors() {
		return getLibCell().getPossibleAnchors();
	}

	/**
	 * Returns a list of all {@link Bel}s which will be required to place a cell if
	 * the cell is anchored at {@code Bel anchor}.  For non-hierarchical, this list will
	 * be a singleton containing {@code anchor}.  For hierarchical cells, this list will
	 * contain all {@code Bel}s needed for the subcells.
	 *
	 * @param anchor the anchor for the placement of this cell
	 * @return all {@code Bel}s which will be required to place a cell anchored at
	 * {@code anchor}
	 */
	public final List<Bel> getRequiredBels(Bel anchor) {
		return getLibCell().getRequiredBels(anchor);
	}

	/**
	 * Returns the site this cell resides at or null if the cell is not placed.
	 * This method is identical to calling {@code getBel().getSite()}
	 *
	 * @return the site this cell resides
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
	
	/**
	 * Creates a new {@link PseudoCellPin}, and attaches it to the cell.
	 * 
	 * @param pinName name of the pin to attach
	 * @param dir direction of the pseudo pin
	 * @return the newly created pseudo CellPin.
	 * @throws NullPointerException if {@code pinName} or {@code dir} is null
	 * @throws IllegalArgumentException if a pin with {@code pinName} already exists on this cell
	 * @see PseudoCellPin
	 */
	public CellPin attachPseudoPin(String pinName, PinDirection dir) {
		
		if ( pinMap.containsKey(pinName) ) {
			throw new IllegalArgumentException("Pin \"" + pinName + "\" already attached to cell  \"" 
									+ getName() + "\". Cannot attach it again");
		}
		
		if ( pseudoPins == null ) {
			pseudoPins = new HashSet<>(5);
		}
		
		CellPin pseudoPin = new PseudoCellPin(pinName, dir);
		pseudoPin.setCell(this);
		
		this.pinMap.put(pinName, pseudoPin);
		this.pseudoPins.add(pseudoPin);
		return pseudoPin;
	}
	
	/**
	 * Attaches an existing {@link PseudoCellPin} to this cell. The pin will be
	 * updated to point to this cell as its new parent. Any {@link CellPin} to
	 * {@link edu.byu.ece.rapidSmith.device.BelPin} mappings
	 * that were previously on the pin are now invalid and should be invalidated
	 * before this function is called.
	 * 
	 * @param pin the pseudo pin to attach to this cell
	 * 
	 * @throws IllegalArgumentException if {@code pin} already exists on this cell or
	 * 			is not a pseudo pin
	 * 
	 * @return <code>true</code> if the pin was successfully attached to this cell.
	 * @see PseudoCellPin
	 */
	public boolean attachPseudoPin(CellPin pin) {
		if (!pin.isPseudoPin()) {
			throw new IllegalArgumentException("Expected argument \"pin\" to be a pseudo cell pin.\n"
												+ "Cell: " + getName() + " Pin: " + pin.getName()); 
		}
		
		if (pinMap.containsKey(pin.getName())) {
			throw new IllegalArgumentException("Pin \"" + pin.getName() + "\" already attached to cell  \"" 
									+ getName() + "\". Cannot attach it again");
		}
		
		pin.setCell(this);
		this.pinMap.put(pin.getName(), pin);
		this.pseudoPins.add(pin);
		return true;
	}
	
	/**
	 * Detaches and removes a {@link PseudoCellPin} from the cell.  If you
	 * want to remove the pin from the design completely, you will need to disconnect
	 * it from all nets as well.
	 * 
	 * @param pin the pin to remove
	 * @return <code>true</code> if the pin was attached to the cell 
	 * 			and was successfully removed. <code>false</code> is returned if either
	 * 			{@code pin} is not a pseudo pin, or is not attached to the cell 
	 * @see PseudoCellPin
	 */
	public boolean removePseudoPin(CellPin pin) {
		
		if (pseudoPins == null || !pseudoPins.contains(pin)) {
			return false; 
		}
		
		pinMap.remove(pin.getName());
		pseudoPins.remove(pin);
		return true;
	}
	
	/**
	 * Removes the {@link PseudoCellPin} with the given name from the cell. If you
	 * want to remove the pin from the design completely, you will need to disconnect
	 * it from all nets as well.
	 * 
	 * @param pinName name of the pin to remove
	 * @return the pin object removed from the cell. If no matching cell pin is found
	 * 			or the cell pin is not a pseudo pin, null is returned.
	 */ 
	public CellPin removePseudoPin(String pinName) {
		
		CellPin pin = pinMap.get(pinName);
		
		if (pin == null || !pin.isPseudoPin()) {
			return null;
		}
		
		pinMap.remove(pinName);
		pseudoPins.remove(pin);
		return pin;
	}
	
	/**
	 * Returns all {@link PseudoCellPin}s currently attached to this cell.
	 * 
	 * @return an unmodifiable {@link Set} of attached pseudo pins
	 */
	public Set<CellPin> getPseudoPins() {
		
		if (pseudoPins == null) {
			return Collections.emptySet();
		}
		
		// TODO: think about creating this unmodifiable during class creation instead of on demand
		return Collections.unmodifiableSet(pseudoPins);
	}
	
	/**
	 * Returns the number of {@link PseudoCellPin}s currently attached to this cell.
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
	 * Returns the control set properties and their values for the cell based on its
	 * placement.  If this cell is not placed, the method will throw an
	 * {@link IllegalStateException}.  Control sets are properties that must be
	 * configured consistently for all cells in a site.
	 *
	 * @return the control set properties and their value for this cell.
	 * @throws IllegalStateException if this cell is not placed
	 */
	public Map<SiteProperty, Object> getSharedSiteProperties() {
		if (!isPlaced())
			throw new IllegalStateException("Cell not placed");
		return getSharedSiteProperties(bel.getId());
	}

	/**
	 * Returns the control set properties and their values for the cell when placed
	 * on a Bel with type {@link BelId}.  The cell does not need to be placed.
	 * Control sets are properties that must be configured consistently for all cells
	 * in a site.
	 *
	 * @param belId the type of Bel to get the control set properties for
	 * @return the control set properties and their value for this cell.
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
	 * Returns the pin on this cell with the specified name or null if no no pin
	 * with the name exist on the cell.
	 *
	 * @param pinName the name of the pin
	 * @return the pin on this cell with the specified name
	 */
	public final CellPin getPin(String pinName) {
		return pinMap.get(pinName);
	}

	/**
	 * Returns all of the {@link CellPin}s (including pseudo pins) on this net.
	 * The returned set is unmodifiable.
	 *
	 * @return a collection of pins on this cell
	 */
	public final Collection<CellPin> getPins() {
		return Collections.unmodifiableCollection(pinMap.values());
	}

	/**
	 * Returns all of the output pins on this net.  Output pins have direction of
	 * either OUT or INOUT.  The collection is created on this call.
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
	 * either IN or INOUT.  The collection is created on this call.
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

	@Override
	public int hashCode() {
		return Objects.hash(name);
	}

	@Override
	public String toString() {
		return "Cell{" + getName() + " " + (isPlaced() ? "@" + getBel().getFullName() : "") + "}";
	}
}
