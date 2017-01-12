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

import edu.byu.ece.rapidSmith.design.AbstractDesign;
import edu.byu.ece.rapidSmith.device.Bel;
import edu.byu.ece.rapidSmith.device.Site;
import edu.byu.ece.rapidSmith.util.Exceptions;
import edu.byu.ece.rapidSmith.interfaces.vivado.XdcConstraint;
import edu.byu.ece.rapidSmith.util.Exceptions.DesignAssemblyException;

import java.util.*;

/**
 * <p>
 * This class represents a cell-based logical netlist with optional placement
 * information.  Cells can be looked up in constant time by both name and location.
 * Placement information of cells in the CellDesign are updated through calls to
 * {@link #placeCell(Cell, edu.byu.ece.rapidSmith.device.Bel)}.  The
 * placement information is stored in a two-level map of sites and
 * BELs allowing quick checking of cells located at both levels of hierarchy.
 * </p>
 */
public class CellDesign extends AbstractDesign {
	private static final long serialVersionUID = -807318199842395826L;
	/** This is a list of all the cells in the design */
	private Map<String, Cell> cellMap;
	/** A map used to keep track of all used primitive sites used by the design */
	private Map<Site, Map<Bel, Cell>> placementMap;
	/** This is a list of all the nets in the design */
	private Map<String, CellNet> netMap;
	/** The properties of this design. */
	private final PropertyList properties;
	/** Map from a site to the used SitePip wires in the site*/
	private HashMap<Site, Set<Integer>> usedSitePipsMap;
	/** The VCC RapidSmith net */
	private CellNet vccNet;
	/** The GND RapidSmith net */
	private CellNet gndNet;
	/** List of Vivado constraints on the design **/
	private List<XdcConstraint> vivadoConstraints;
	
	/**
	 * Constructor which initializes all member data structures. Sets name and
	 * partName to null.
	 */
	public CellDesign() {
		super();
		init();
		properties = new PropertyList();
	}

	/**
	 * Constructs a design and populates it with the given design name and partName
	 * and loads the associated device.
	 *
	 * @param designName The name of the newly created design.
	 * @param partName   The target part name of the newly created design.
	 */
	public CellDesign(String designName, String partName) {
		super(designName, partName);
		init();
		properties = new PropertyList();
	}

	private void init() {
		cellMap = new HashMap<>();
		placementMap = new HashMap<>();
		netMap = new HashMap<>();
		usedSitePipsMap = new HashMap<>();
	}

	/**
	 * Returns the properties of this design in a {@link PropertyList}.  Properties
	 * may contain metadata about a design including user-defined metadata.
	 * @return a {@code PropertyList} containing the properties of this design
	 */
	public PropertyList getProperties() {
		return properties;
	}

	/**
	 * Returns true if this design has a cell with the specified name.
	 * @param cellName the name of the cell to test for
	 * @return true if this design ahas a cell with the specified name
	 * @throws NullPointerException if {@code cellName} is null
	 */
	public boolean hasCell(String cellName) {
		Objects.requireNonNull(cellName);

		return cellMap.containsKey(cellName);
	}

	/**
	 * Returns a unique cell name for this design based on the proposed name.  The
	 * returned name is guaranteed to not be in the design at the time this method is
	 * invoked.  If {@code proposedName} is not in the design, this method will simply
	 * return the name.  Otherwise, it will append {@code _#} to the name with # being
	 * the lowest integer that yields a unique name.
	 *
	 * @param proposedName the proposed name for the cell
	 * @return a unique cell name for this design
	 * @throws NullPointerException if {@code proposedName} is null
	 */
	public String getUniqueCellName(String proposedName) {
		Objects.requireNonNull(proposedName);

		if (!hasCell(proposedName))
			return proposedName;

		String newName;
		int i = 0;
		do {
			i++;
			newName = proposedName + "_" + i;
		} while (hasCell(newName));
		return newName;
	}

	/**
	 * Returns the cell in this design with the specified name.
	 *
	 * @param cellName name of the cell to return
	 * @return the cell, or null if it does not exist
	 * @throws NullPointerException if {@code cellName} is null
	 */
	public Cell getCell(String cellName) {
		Objects.requireNonNull(cellName);

		return cellMap.get(cellName);
	}

	/**
	 * Returns all of the cells in this design.  The returned collection is immutable.
	 *
	 * @return a collection containing the cells in this design
	 */
	public Collection<Cell> getCells() {
		return Collections.unmodifiableCollection(cellMap.values());
	}

	/**
	 * Adds a cell to this design.  The name of this added cell should be unique
	 * to this design.  The cell should not be part of another design and should
	 * not have any placement information.  Returns the added cell for convenience.
	 *
	 * @param cell the cell to add
	 * @return the added cell
	 * @throws NullPointerException if {@code cell} is null
	 * @throws DesignAssemblyException if {@code cell} is already in a design or if a
	 *     cell with the same name already exists in this design
	 */
	public Cell addCell(Cell cell) {
		Objects.requireNonNull(cell);
		if (cell.isInDesign())
			throw new DesignAssemblyException("Cell already in a design.");

		if (hasCell(cell.getName()))
			throw new DesignAssemblyException("Cell with name already exists in design.");

		cell.setDesign(this);
		cellMap.put(cell.getName(), cell);
		return cell;
	}

	/**
	 * Disconnects and removes the specified cell from this design.
	 *
	 * @param cell the cell in this design to remove
	 * @throws NullPointerException if {@code cell} is null
	 * @throws DesignAssemblyException if cell is not in this design
	 */
	public void removeCell(Cell cell) {
		Objects.requireNonNull(cell);
		if (cell.getDesign() != this)
			throw new DesignAssemblyException("Cannot remove cell not in the design.");

		removeCell_impl(cell);
	}

	private void removeCell_impl(Cell cell) {
		disconnectCell_impl(cell);
		cellMap.remove(cell.getName());
		cell.clearDesign();
	}

	/**
	 * Disconnects without removing the specified cell from this design.  This is
	 * accomplished by unplacing the cell and disconnecting all of its pins.
	 *
	 * @param cell the cell to disconnect from this design
	 * @throws NullPointerException if {@code cell} is null
	 */
	public void disconnectCell(Cell cell) {
		Objects.requireNonNull(cell);
		if (cell.getDesign() != this)
			throw new DesignAssemblyException("Cannot disconnect cell not in the design.");

		disconnectCell_impl(cell);
	}

	private void disconnectCell_impl(Cell cell) {
		if (cell.isPlaced())
			unplaceCell_impl(cell);

		// disconnect the cell's pins from their nets
		for (CellPin pin : cell.getPins()) {
			CellNet net = pin.getNet();
			if (net != null)
				net.disconnectFromPin(pin);
		}
	}

	/**
	 * Returns true if this design has a net with the specified name.
	 * @param netName the name of the net to test for
	 * @return true if this design has a net with the specified name
	 * @throws NullPointerException if {@code netName} is null
	 */
	public boolean hasNet(String netName) {
		Objects.requireNonNull(netName);

		return netMap.containsKey(netName);
	}

	/**
	 * Returns a unique net name for this design based on the proposed name.  The
	 * returned name is guaranteed to not be in the design at the time this method is
	 * invoked.  If {@code proposedName} is not in the design, this method will simply
	 * return the name.  Otherwise, it will append {@code _#} to the name with # being
	 * the lowest integer that yields a unique name.
	 *
	 * @param proposedName the proposed name for the net
	 * @return a unique net name for this design
	 * @throws NullPointerException if {@code proposedName} is null
	 */
	public String getUniqueNetName(String proposedName) {
		if (!hasNet(proposedName))
			return proposedName;

		String newName;
		int i = 0;
		do {
			i++;
			newName = proposedName + "_" + i;
		} while (hasNet(newName));
		return newName;
	}


	/**
	 * Returns the net in this design with the specified name.
	 *
	 * @param netName name of the net to return
	 * @return the net with the specified name, or null if it does not exist
	 * @throws NullPointerException if {@code netName} is null
	 */
	public CellNet getNet(String netName) {
		Objects.requireNonNull(netName);

		return netMap.get(netName);
	}

	/**
	 * Returns all of the nets in the design.  The returned collection is unmodifiable.
	 * @return a collection of all nets in the design
	 */
	public Collection<CellNet> getNets() {
		return Collections.unmodifiableCollection(netMap.values());
	}

	/**
	 * Adds a net to this design.  The name of net must be unique to the design.  If
	 * the net is VCC or GND, the VCC/GND net will be set to this net if not already
	 * set.
	 *
	 * @param net the net to add.
	 * @return the added net
	 * @throws NullPointerException if {@code net} is null
	 * @throws DesignAssemblyException if the name of the net is already used in the design
	 */
	public CellNet addNet(CellNet net) {
		Objects.requireNonNull(net);
		if (net.isInDesign())
			throw new DesignAssemblyException("Cannot add net from another design.");

		return addNet_impl(net);
	}

	private CellNet addNet_impl(CellNet net) {
		if (hasNet(net.getName()))
			throw new DesignAssemblyException("Net with name already exists in design.");

		if (net.isVCCNet()) {
			vccNet = net;
		}
		else if (net.isGNDNet()) {
			gndNet = net;
		} 

		netMap.put(net.getName(), net);
		net.setDesign(this);
		
		return net;
	}

	/**
	 * Removes a net from this design.  The net should already be fully disconnected
	 * from the design and have no nets.  This method will update the VCC/GND nets if
	 * necessary
	 *
	 * @param net the net to remove from this design
	 * @throws NullPointerException if {@code net} is null
	 * @throws DesignAssemblyException if the net is not disconnected from the design
	 */
	public void removeNet(CellNet net) {
		Objects.requireNonNull(net);
		if (net.getDesign() != this)
			return;
		if (!net.getPins().isEmpty())
			throw new DesignAssemblyException("Cannot remove connected net.");

		removeNet_impl(net);
	}

	private void removeNet_impl(CellNet net) {
		net.setDesign(null);
		
		if (net.isVCCNet()) {
			vccNet = null;
			// TODO search to see if another VCC net exists in the design
		}
		else if (net.isGNDNet()) {
			gndNet = null;
			// TODO search to see if another GND net exists in the design
		}
		else {
			netMap.remove(net.getName());
		}
	}

	/**
	 * Disconnects the specified net from this design without removing it.  This
	 * method unroutes the net and disconnects it from all of its pins.
	 *
	 * @param net the net to disconnect
	 * @throws NullPointerException if {@code net} is null
	 * @throws DesignAssemblyException if the net is not in the design
	 */
	public void disconnectNet(CellNet net) {
		Objects.requireNonNull(net);
		if (net.getDesign() != this)
			throw new DesignAssemblyException("Cannot disconnect net not in the design.");

		disconnectNet_impl(net);
	}

	private void disconnectNet_impl(CellNet net) {
		List<CellPin> pins = new ArrayList<>(net.getPins());
		pins.forEach(net::disconnectFromPin);
		net.unroute();
	}

	/**
	 * Returns the power(VCC) net of the design.  There may be more than one VCC
	 * net in the design.  This will return one of them.  If there is no VCC net in
	 * the design, this method returns null.
	 * 
	 * @return the VCC net in the design.
	 */
	public CellNet getVccNet() {
		return vccNet;
	}
	
	/**
	 * Returns the ground(GND) net of the design.  There may be more than one GND
	 * net in the design.  This will return one of them.  If there is no GND net in
	 * the design, this method returns null.
	 *
	 * @return the GND net in the design.
	 */
	public CellNet getGndNet() {
		return gndNet;
	}

	/**
	 * Returns the cell at the specified BEL in this design.
	 *
	 * @param bel the BEL of interest
	 * @return the cell at specified BEL, or null if the BEL is unoccupied
	 * @throws NullPointerException if {@code bel} is null
	 */
	public Cell getCellAtBel(Bel bel) {
		Objects.requireNonNull(bel);

		Map<Bel, Cell> sitePlacementMap = placementMap.get(bel.getSite());
		if (sitePlacementMap == null)
			return null;
		return sitePlacementMap.get(bel);
	}

	/**
	 * Returns a collection of cells at the specified site in this design.  This
	 * collection is unmodifiable.  If no cells exist at the site, the returned
	 * collection will be empty.
	 *
	 * @param site the site of the desired cells
	 * @return the cells at the site
	 */
	public Collection<Cell> getCellsAtSite(Site site) {
		Objects.requireNonNull(site);

		Map<Bel, Cell> sitePlacementMap = placementMap.get(site);
		if (sitePlacementMap == null)
			return Collections.emptyList();
		return Collections.unmodifiableCollection(sitePlacementMap.values());
	}

	/**
	 * Tests if the specified BEL is occupied in this design.
	 *
	 * @param bel the BEL to test
	 * @return true if a cell is placed at the BEL
	 * @throws NullPointerException if {@code bel} is null
	 */
	public boolean isBelUsed(Bel bel) {
		Objects.requireNonNull(bel);

		Map<Bel, Cell> sitePlacementMap = placementMap.get(bel.getSite());
		return sitePlacementMap != null && sitePlacementMap.containsKey(bel);
	}

	/**
	 * Tests if any BELs in the the specified site are occupied in
	 * this design.
	 *
	 * @param site the site to test
	 * @return true if this design uses any BELs in site
	 * @throws NullPointerException if {@code site} is null
	 */
	public boolean isSiteUsed(Site site) {
		Objects.requireNonNull(site);

		Map<Bel, Cell> sitePlacementMap = placementMap.get(site);
		return sitePlacementMap != null && !sitePlacementMap.isEmpty();
	}

	/**
	 * Returns all of the sites which are homes to one or more cells in the design.
	 * the returned collection is unmodifiable.
	 * @return a collection to all sites used in the design
	 */
	public Collection<Site> getUsedSites() {
		return Collections.unmodifiableCollection(placementMap.keySet());
	}

	/**
	 * Tests if the cell can be placed at the {@link Bel} anchor.  If the cell
	 * is non-hierarchical, this cell will check if {@code anchor} is used.
	 * If the cell is hierarchical, this method will check all Bels required
	 * to place the hierarchical cell.
	 *
	 * @param cell the cell to test placement for
	 * @param anchor the anchor bel to test placement on
	 * @return true if the cell can be placed at {@code anchor}, else false
	 * @throws NullPointerException if {@code cell} or {@code anchor} is null
	 */
	public boolean canPlaceCellAt(Cell cell, Bel anchor) {
		Objects.requireNonNull(cell);
		Objects.requireNonNull(anchor);
		List<Bel> requiredBels = cell.getLibCell().getRequiredBels(anchor);
		return canPlaceCellAt_impl(requiredBels);
	}

	private boolean canPlaceCellAt_impl(List<Bel> requiredBels) {
		for (Bel bel : requiredBels) {
			if (bel == null || isBelUsed(bel))
				return false;
		}
		return true;
	}

	/**
	 * Places the {@code cell} in the design anchored at {@code anchor}.
	 * The cell should not already be placed and the location should be unoccupied.
	 *
	 * @param cell the cell to place
	 * @param anchor the anchor where the cell is to be placed
	 * @throws NullPointerException if {@code cell} or {@code anchor} is null
	 * @throws DesignAssemblyException if the cell is not in the design
	 * @throws DesignAssemblyException if the cell is already placed
	 * @throws DesignAssemblyException if the cell cannot be placed at the desired location
	 */
	public void placeCell(Cell cell, Bel anchor) {
		Objects.requireNonNull(cell);
		Objects.requireNonNull(anchor);
		if (cell.getDesign() != this)
			throw new DesignAssemblyException("Cannot place cell not in the design.");
		if (cell.isPlaced())
			throw new DesignAssemblyException("Cannot re-place cell.");

		List<Bel> requiredBels = cell.getLibCell().getRequiredBels(anchor);
		if (!canPlaceCellAt_impl(requiredBels))
			throw new DesignAssemblyException("Cell already placed at location.");

		placeCell_impl(cell, anchor, requiredBels);
	}

	private void placeCell_impl(Cell cell, Bel anchor, List<Bel> requiredBels) {
		requiredBels.forEach(b -> placeCellAt(cell, b));
		cell.place(anchor);
	}

	private void placeCellAt(Cell cell, Bel bel) {
		Map<Bel, Cell> sitePlacementMap = placementMap.get(bel.getSite());
		if (sitePlacementMap == null) {
			sitePlacementMap = new HashMap<>();
			placementMap.put(bel.getSite(), sitePlacementMap);
		} else {
			assert sitePlacementMap.get(bel) == null;
		}
		sitePlacementMap.put(bel, cell);
	}

	/**
	 * Unplaces the cell in this design.  After this call, the BELs occupied by the
	 * cell will be unused.
	 *
	 * @param cell the cell to unplace.
	 * @throws NullPointerException if {@code cell} is null
	 * @throws DesignAssemblyException if {@code cell} is not in this design
	 */
	public void unplaceCell(Cell cell) {
		Objects.requireNonNull(cell);
		if (cell.getDesign() != this)
			throw new DesignAssemblyException("Cannot unplace cell not in the design.");

		unplaceCell_impl(cell);
	}

	private void unplaceCell_impl(Cell cell) {
		assert cell.getDesign() == this;
		assert cell.isPlaced();

		Site site = cell.getSite();
		Map<Bel, Cell> sitePlacementMap = placementMap.get(site);
		sitePlacementMap.remove(cell.getBel());
		if (sitePlacementMap.size() == 0)
			placementMap.remove(site);
		cell.unplace();
	}

	/**
	 * Unroutes the current design by removing all PIPs.
	 */
	public void unrouteDesign() {
		// Just remove all the PIPs
		getNets().forEach(CellNet::unroute);

		for (Cell cell : getCells()) {
			cell.getPins().forEach(CellPin::clearPinMappings);
		}
	}

	/**
	 * Map containing the PIPs used at a site.
	 * TODO I need Thomas to document this.  I don't know what this method does.
	 * @param ps the site
	 * @param usedWires the used wires at a site
	 */
	public void setUsedSitePipsAtSite(Site ps, HashSet<Integer> usedWires) {
		this.usedSitePipsMap.put(ps, usedWires);
	}

	/**
	 * Thomas needs to document this
	 * @param ps the site
	 * @return the PIPs used at the site
	 */
	public  Set<Integer> getUsedSitePipsAtSite(Site ps) {
		return this.usedSitePipsMap.getOrDefault(ps, Collections.emptySet());
	}

	/**
	 * Returns a list of XDC constraints on the design.
	 * @return the xdc constraints for this design
	 */
	public List<XdcConstraint> getVivadoConstraints() {
		return this.vivadoConstraints;
	}
	
	/**
	 * Add an XDC constraint to the design
	 * @param constraint {@link XdcConstraint} to add to the design
	 */
	public void addVivadoConstraint(XdcConstraint constraint) {
		
		if (this.vivadoConstraints == null) {
			vivadoConstraints = new ArrayList<>();
		}
		vivadoConstraints.add(constraint);
	}
	
	/**
	 * Unplaces the design.  The design is first unrouted and then all cells
	 * are unplaced.
	 */
	public void unplaceDesign() {
		unrouteDesign();

		getCells().forEach(this::unplaceCell_impl);
	}

	/**
	 * Creates and returns a deep copy of this design.
	 * @return a deep copy of this design
	 */
	public CellDesign deepCopy() {
		CellDesign designCopy = new CellDesign();
		designCopy.setName(getName());
		designCopy.setPartName(getPartName());

		for (Cell cell : getCells()) {
			Cell cellCopy = cell.deepCopy();
			designCopy.addCell(cellCopy);
			if (cell.isPlaced()) {
				designCopy.placeCell(cellCopy, cell.getBel());
				for (CellPin cellPin : cell.getPins()) {
					if (cellPin.getMappedBelPinCount() > 0) {
						CellPin copyPin = cellCopy.getPin(cellPin.getName());
						cellPin.getMappedBelPins().forEach(copyPin::mapToBelPin);
					}
				}
			}
		}

		for (CellNet net : getNets()) {
			CellNet netCopy = net.deepCopy();
			designCopy.addNet(netCopy);
			for (CellPin cellPin : net.getPins()) {
				Cell cellCopy = designCopy.getCell(cellPin.getCell().getName());
				CellPin copyPin = cellCopy.getPin(cellPin.getName());
				netCopy.connectToPin(copyPin);
			}
		}

		return designCopy;
	}
}
