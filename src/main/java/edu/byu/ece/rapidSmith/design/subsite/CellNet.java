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

import edu.byu.ece.rapidSmith.design.NetType;
import edu.byu.ece.rapidSmith.device.PIP;
import edu.byu.ece.rapidSmith.device.BelPin;
import edu.byu.ece.rapidSmith.device.PinDirection;
import edu.byu.ece.rapidSmith.device.SitePin;
import edu.byu.ece.rapidSmith.util.Exceptions;
import edu.byu.ece.rapidSmith.util.Exceptions.DesignAssemblyException;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 *  Represents a net in a cell design.  {@code CellNet}s connect the
 *  {@link CellPin}s on cells to one another.  The {@code CellNets} may optionally
 *  contain physical routing information in the form of {@link RouteTree}s.
 *
 *  Typically {@code CellNet}s contain only a single source pin.  GND and VCC nets
 *  for unplaced circuits typically contain no source pins.  In cases involving
 *  tri-state IO, {@code CellNet}s, can contain multiple source pins.  In this case,
 *  only one of the pins can be designated as an output pin and all other sources
 *  must be an inout pin.  When an output pin exists, it is designated as the source; when
 *  absent, the source is one of the inout pins.
 */
public class CellNet implements Serializable {
	
	/** Unique Serialization ID for this class*/
	private static final long serialVersionUID = 6082237548065721803L;
	/** Unique name of the net */
	private final String name;
	/**Type of net*/
	private NetType type;
	/** Design the net is attached to*/
	private CellDesign design;
	/** Sink pins of the net */
	private Set<CellPin> pins;
	/** Source pin of the net*/
	private CellPin sourcePin;
	/** Properties for the Net*/
	private final PropertyList properties;
	/** Set of CellPins that have been marked as routed in the design*/
	private Set<CellPin> routedSinks; 
	/** Set to true if this net is contained within a single site's boundaries*/
	private boolean isIntrasite;
	/** Route status of the net*/
	private RouteStatus routeStatus;
	
	// Physical route information
	/** SitePin source of the net (i.e. where the net leaves the site)*/
	private SitePin sourceSitePin;
	/** Route Tree connecting to the source pin of the net*/
	private RouteTree source;
	/** List of intersite RouteTree objects for the net*/
	private List<RouteTree> intersiteRoutes;
	/** Maps a connecting BelPin of the net, to the RouteTree connected to the BelPin*/
	private Map<BelPin, RouteTree> belPinToSinkRTMap;
	/** Maps a connecting SitePin of the net, to the RouteTree connected to the SitePin*/
	private Map<SitePin, RouteTree> sitePinToRTMap;

	/**
	 * Creates a new net with the given name and type.
	 *
	 * @param name the name for the net
	 * @param type the type for this net
	 */
	public CellNet(String name, NetType type) {
		Objects.requireNonNull(name);

		this.name = name;
		this.type = type;
		this.isIntrasite = false;
		this.properties = new PropertyList();
		init();
	}

	private void init() {
		this.pins = new HashSet<>();
	}

	/**
	 * Returns the name of the net.  The name is immutable.
	 *
	 * @return the name of the net
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the type of this net.  The {@link NetType} defines the use case of
	 * this net.
	 * @return the type of this net
	 * @see NetType
	 */
	public NetType getType() {
		return type;
	}

	/**
	 * Sets the type of this net.  The {@link NetType} defines the use case of
	 * this net.
	 * @param type the new type for this net
	 * @see NetType
	 */
	public void setType(NetType type) {
		this.type = type;
	}

	/**
	 * Returns true if this net is in a design.
	 *
	 * @return true if this net is in a design
	 */
	public boolean isInDesign() {
		return design != null;
	}

	/**
	 * Returns the design this net is a part of.
	 *
	 * @return the design the net is a part of or null if it is not in a design
	 */
	public CellDesign getDesign() {
		return design;
	}

	void setDesign(CellDesign design) {
		this.design = design;
	}

	/**
	 * Returns the properties of this net in a {@link PropertyList}.
	 * @return a {@code PropertyList} containing the properties of this net
	 */
	public final PropertyList getProperties() {
		return properties;
	}

	/**
	 * Returns the pins (source and sinks) of this net.  The returned collection is
	 * unmodifiable.
	 *
	 * @return a collection of the pins of this net
	 */
	public Collection<CellPin> getPins() {
		return Collections.unmodifiableCollection(pins);
	}

	/**
	 * Builds and returns a {@link List} of the sink pins of the net.
	 * 
	 * @return a list of the sinks of the net
	 */
	public List<CellPin> getSinkPins() {
		return getPins().stream()
				.filter(p -> p != sourcePin)
				.collect(Collectors.toList());
	}

	/**
	 * Checks if this net has a source pin.
	 *
	 * @return true if this net has a source pin, else false
	 */
	public boolean isSourced() {
		return getSourcePin() != null;
	}

	/**
	 * Returns the source of this net.  The source is the output pin in the net or
	 * if no outpin exists, then one of the inout pins.
	 *
	 * @return the source of this net, or null if this net has no source
	 */
	public CellPin getSourcePin() {
		return sourcePin;
	}

	/**
	 * Builds and returns a list of all of the pins that source the net including
	 * the inout pins.
	 *
	 * @return all of the pins that source the net
	 */
	public List<CellPin> getAllSourcePins() {
		return getPins().stream()
				.filter(CellPin::isOutpin)
				.collect(Collectors.toList());
	}

	/**
	 * Checks if this net contains multiple source pins.  This can be true only
	 * if this net contains inout pins.
	 *
	 * @return true if this net contains multiple source pins, else false
	 */
	public boolean isMultiSourced() {
		return getAllSourcePins().size() > 1;
	}

	/**
	 * Connects this net to all pins in the given collection.
	 *
	 * @param pinsToAdd the collection of pins to add
	 * @throws NullPointerException if {@code pinsToAdd} or any of its elements is null
	 */
	public void connectToPins(Collection<CellPin> pinsToAdd) {
		Objects.requireNonNull(pinsToAdd);

		pinsToAdd.forEach(this::connectToPin);
	}
		
	/**
	 * Adds a pin to this net.  It is an error to add multiple output pins
	 * (excluding inout pins).
	 *
	 * @param pin the new pin to add
	 * @throws NullPointerException if {@code pin} is null
	 * @throws DesignAssemblyException if the pin is already connected to a net
	 * @throws DesignAssemblyException if this net already connects to an output pin
	 */
	public void connectToPin(CellPin pin) {
		Objects.requireNonNull(pin);
		if (pin.getNet() != null)
			throw new DesignAssemblyException("Pin already connected to net.");

		pins.add(pin);
		pin.setNet(this);

		if (sourcePin == null && pin.isOutpin()) {
			sourcePin = pin;
		} else if (pin.getDirection() == PinDirection.OUT) {
			assert sourcePin != null;
			if (sourcePin.getDirection() == PinDirection.OUT)
				throw new DesignAssemblyException("Cannot create multiply-sourced net.");
			sourcePin = pin;
		}
	}
	
	/**
	 * Returns the number of pseudo pins connected to this net.
	 * @return the number of pseudo pins connected to this net
	 * @see PseudoCellPin
	 */
	public int getPseudoPinCount() {
		
		int pseudoPinCount = 0;
		
		for (CellPin pin : pins) {
			if (pin.isPseudoPin()) {
				pseudoPinCount++;
			}
		}
		
		return pseudoPinCount;
	}

	/**
	 * Removes all pins from the given connection from this net.
	 * 
	 * @param pins collection of pins to remove
	 * @throws NullPointerException if {@code pins} is null
	 */
	public void disconnectFromPins(Collection<CellPin> pins) {
		pins.forEach(this::disconnectFromPin);
	}

	/**
	 * Tests if the specified pin is attached to this net.
	 * 
	 * @param pin pin to test
	 * @return true if the pin is attached to the net, else false
	 */
	public boolean isConnectedToPin(CellPin pin) {
		return pins.contains(pin);
	}
	
	/**
	 * Disconnects the net from all of its current pins
	 */
	public void detachNet() { 
		
		pins.forEach(CellPin::clearNet);
		sourcePin = null;
		pins.clear();
	}
	
	/**
	 * Removes a pin from this net.  If the pin is not connected to the net,
	 * does nothing.
	 *
	 * @param pin the pin to remove
	 * @throws NullPointerException if {@code pin} is null
	 */
	public void disconnectFromPin(CellPin pin) {
		Objects.requireNonNull(pin);

		boolean used = pins.remove(pin);
		if (!used) return; // pin is not on the net

		if (sourcePin == pin) {
			sourcePin = null;
			List<CellPin> sourcePins = getAllSourcePins();
			if (!sourcePins.isEmpty()) {
				assert sourcePins.stream()
						.map(p -> p.getDirection() != PinDirection.OUT)
						.reduce(true, Boolean::logicalAnd);
				sourcePin = sourcePins.get(0);
			}
		}

		pin.clearNet();
	}

	/**
	 * Returns the fan-out (number of sinks) of this net.  More formally, the
	 * number of pins if the net has no source else the number of pins minus 1.
	 *
	 * @return the fan-out of this net
	 */
	public int getFanOut() {
		if (getSourcePin() == null)
			return getPins().size();
		else
			return getPins().size() - 1;
	}

	/**
	 * Checks if a net is a clk net and should use the clock routing resources.
	 * More specifically, checks if the pins connected to this net are of {@link CellPinType#CLOCK}.
	 *
	 * @return true if this net is a clock net
	 */
	public boolean isClkNet() {
		
		for (CellPin p : this.pins) {
			if (p.getType() == CellPinType.CLOCK) {
				return true;
			}
		}
		
		return false;
	}

	/**
	 * Returns true if the net is a VCC (logic high) net
	 * @return true if the net is a VCC net, else false
	 */
	public boolean isVCCNet() {

		return type.equals(NetType.VCC);
	}

	/**
	 * Returns true if the net is a GND (logic low) net
	 * @return true if the net is a GND net, else false
	 */
	public boolean isGNDNet() {

		return type.equals(NetType.GND);
	}

	/**
	 * Creates a deep copy of this net including its route trees.
	 * @return a deep copy of this net
	 */
	public CellNet deepCopy() {
		CellNet copy = new CellNet(getName(), getType());
		if (intersiteRoutes != null)
			intersiteRoutes.forEach(rt -> copy.addIntersiteRouteTree(rt.deepCopy()));
		return copy;
	}

	// Uses equality equals

	/**
	 * Returns the hashCode of the net's name.  Provides more consistency between
	 * debug and execution.
	 */
	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public String toString() {
		return "CellNet{" + getName() + "}";
	}

	/**
	 * Returns true if this net is either a VCC or GND net.  Static nets behave
	 * differently from other nets, ie static nets may not have a source pin and
	 * a design often has only a single VCC and a single GND net.
	 * 
	 * @return true if this net is VCC or GND, else false
	 */
	public boolean isStaticNet() {
		return type == NetType.VCC || type == NetType.GND;
	}
	
	/* **********************************
	 * 	    Physical Route Functions
	 * **********************************/
	
	/**
	 * Sets the {@link SitePin} source of the net. This is used to
	 * set the source of a net when loading a Tincr Checkpoint. If you are
	 * writing a intersite router, this will give you the site pin where the 
	 * route needs to start.
	 * @param sitePin the source {@code SitePin} of this net
	 */
	public void setSourceSitePin(SitePin sitePin) {
		this.sourceSitePin = sitePin;
	}
	
	/**
	 * Returns the {@link SitePin} which sources the intersite portion of this net.
	 * @return the {@code SitePin} where this net is sourced
	 */
	public SitePin getSourceSitePin() {
		return this.sourceSitePin;
	}
	
	/**
	 * Returns the {@link BelPin} which sources this net.
	 * @return the {@code BelPin} source of this net
	 */
	public BelPin getSourceBelPin() {
		return this.sourcePin.getMappedBelPin();
	}
	
	/**
	 * Builds and returns a collection of PIPs that are used in this net's physical
	 * route.
	 * @return a collection of the PIPs used in this net's physical route
	 */
	public Collection<PIP> getPips() {
		if (intersiteRoutes == null)
			return Collections.emptySet();
		Set<PIP> pipSet = new HashSet<>();
		for (RouteTree tree : intersiteRoutes) {
			pipSet.addAll(tree.getAllPips());
		}
		return pipSet;
	}

	/**
	 * Marks the net as intrasite (completely contained within a site) or not
	 * (stretches across site boundaries).
	 * 
	 * @param isIntrasite true if the net is fully contained within a site, false otherwise
	 */
	public void setIsIntrasite(boolean isIntrasite) {
		this.isIntrasite = isIntrasite;
	}
	
	/**
	 * Returns whether the net is an intrasite net (completely contained within a site)
	 * or not (stretches across site boundaries).
	 * @return true if the net is an intrasite net, false otherwise
	 */
	public boolean isIntrasite() {
		return isIntrasite;
	}
	
	/**
	 * Builds and returns a list of all of the unrouted sinks of the net.
	 * @return a list of all of the unrouted sinks of a net
	 */
	public List<CellPin> getUnroutedSinks() {
		
		if (routedSinks == null || routedSinks.isEmpty()) {
			return getSinkPins();
		}
		
		return pins.stream()
					.filter(pin -> !routedSinks.contains(pin) && pin.isInpin())
					.collect(Collectors.toList());
	}
	
	/**
	 * Returns all of the routed sinks of the net.  The returned set is unmodifiable.
	 * @return a set of all routed sinks
	 */
	public Set<CellPin> getRoutedSinks() {
		
		if (routedSinks == null) {
			return Collections.emptySet();
		}
		
		return Collections.unmodifiableSet(routedSinks);
	}
	
	/**
	 * Mark a collection of pins in the net that have been routed. It is up to the user
	 * to keep the routed sinks up-to-date.
	 * 
	 * @param cellPin a collection of cell pins to be marked as routed
	 */
	public void addRoutedSinks(Collection<CellPin> cellPin) {
		cellPin.forEach(this::addRoutedSink);
	}
	
	/**
	 * Marks the specified pin as being routed. It is up to the user to keep the
	 * routed sinks up-to-date. 
	 * 
	 * @param cellPin the {@code CellPin} to mark as routed
	 */
	public void addRoutedSink(CellPin cellPin) {
		
		if (!pins.contains(cellPin)) {
			throw new IllegalArgumentException("CellPin" + cellPin.getName() + " not attached to net. "
					+ "Cannot be added to the routed sinks of the net!");
		}
		
		if (cellPin.isOutpin()) {
			throw new IllegalArgumentException(String.format("CellPin %s is an output pin. Cannout be added as a routed sink!", cellPin.getName()));
		}
		
		if(routedSinks == null) {
			routedSinks = new HashSet<>();
		}
		routedSinks.add(cellPin);
	}
	
	/**
	 * Marks a cellPin attached to the net as unrouted. 
	 * 
	 * @param cellPin the cell pin to mark as unrouted
	 * @return true if the cellPin was successfully removed or false if the cellPin was
	 * not marked as a routed pin of this net.
	 */
	public boolean removeRoutedSink(CellPin cellPin) {
		return routedSinks.remove(cellPin);
	}
	
	/**
	 * Clears all routing information of a net.
	 */
	public void unroute() {
		intersiteRoutes = null;
		routedSinks = null;
	}
	
	/**
	 * Sets the route tree starting at the source BelPin, and ending on the site
	 * pin where it leaves the site.  For intrasite nets, it will end on another
	 * BelPin within the site.
	 * @param source the source intrasite route tree
	 */
	public void setSourceRouteTree(RouteTree source) {
		
		this.source = source;
	}
	
	/**
	 * Returns the starting intrasite route of this net.  This net will start at
	 * a BelPin and end either at a BelPin in the site or where it leaves the site.
	 * @return the starting intrasite route of this net
	 */
	public RouteTree getSourceRouteTree() {
		return source;
	}
	
	/**
	 * Adds an intersite {@code RouteTree} to the net. An intersite route
	 * starts at a site pin, and ends at one or more site pins. In general,
	 * a net will have exactly one intersite route tree, but GND and VCC
	 * nets will have more than one (since they are sourced by multiple tieoff
	 * locations).
	 *  
	 * @param intersite the RouteTree to add
	 * @throws NullPointerException if {@code intersite} is null
	 */
	public void addIntersiteRouteTree(RouteTree intersite) {	
		Objects.requireNonNull(intersite);

		if (intersiteRoutes == null) {
			intersiteRoutes = new ArrayList<>();
		}
		this.intersiteRoutes.add(intersite);
	}
	
	/**
	 * Sets the list of intersite route trees.  This will clear any previously added
	 * intersite route tree.
	 * @param routes a list of the route trees to add
	 */
	public void setIntersiteRouteTrees(List<RouteTree> routes) {
		// TODO we should think about copying this list.  As it stands, we don't
		// TODO know whether the list is writeable and the user may accidentally
		// TODO modify the list with unexpected outcomes.
		this.intersiteRoutes = routes;
	}
	
	/**
	 * Returns the first intersite route associated with this net.
	 * Use this function for general nets which should only have one
	 * Route Tree.  
	 * 
	 * @return the first intersite route tree associated with this net
	 */
	public RouteTree getIntersiteRouteTree() {
		
		if (intersiteRoutes == null || intersiteRoutes.isEmpty()) {
			return null;
		}
		
		return intersiteRoutes.get(0);
	}
	
	/**
	 * Returns all intersite RouteTree objects associated with this net.  The returned
	 * list is unmodifiable.
	 * 
	 * @return a list of the intersite RouteTrees
	 */
	public List<RouteTree> getIntersiteRouteTreeList() {
	
		if (intersiteRoutes == null) {
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(intersiteRoutes);
	}
	
	/**
	 * Returns true if the net has any intersite routing defined.
	 *
	 * @return true if this net has any intersite routing, else false
	 */
	public boolean hasIntersiteRouting() {
		return intersiteRoutes != null && intersiteRoutes.size() > 0;
	}
	
	/**
	 * Sets the {@code RouteTree} which connects to the specified sink
	 * {@code BelPin}.  This RouteTree should contain only intrasite routing.
	 * 
	 * @param bp the sink BelPin connected to the route tree
	 * @param route the RouteTree leading to that BelPin
	 */
	public void addSinkRouteTree(BelPin bp, RouteTree route) {
		
		if (belPinToSinkRTMap == null) {
			belPinToSinkRTMap = new HashMap<>();
		}
		belPinToSinkRTMap.put(bp, route);
	}
	
	/**
	 * Sets the intrasite {@code RouteTree} which connects to the specified source
	 * {@code SitePin}.  This RouteTree should contain only intrasite routing.
	 *
	 * @param sp the SitePin sourcing the sink
	 * @param route the RouteTree sourced by the SitePin
	 */
	public void addSinkRouteTree(SitePin sp, RouteTree route) {
		
		if (sitePinToRTMap == null) {
			sitePinToRTMap = new HashMap<>();
		}
		sitePinToRTMap.put(sp, route);
	}

	/**
	 * Returns the {@code RouteTree} object connected to the given {@link SitePin}
	 * object.  This RouteTree contains wires INSIDE the Site, and will connect to
	 * several BelPin sinks within the Site of the SitePin.
	 * 
	 * @param sitePin the {@code SitePin} sourcing the RouteTree
	 * @return the {@code RouteTree} sourced by {@code sitePin}
	 */
	public RouteTree getSinkRouteTree(SitePin sitePin) {
				
		return sitePinToRTMap == null ? null : sitePinToRTMap.get(sitePin);
	}

	/**
	 * Returns a set of the {@link SitePin}s that the net is currently passing through.
	 * The returned set is unmodifiable.
	 * @return a set of the {@code SitePin}s that the net is currently passing through
	 * or null if no SitePins are used
	 */
	public Set<SitePin> getSitePins() {
		return sitePinToRTMap == null ? null :
				Collections.unmodifiableSet(sitePinToRTMap.keySet());
	}
	
	/**
	 * Returns the {@link SitePin} to {@code RouteTree} map of the cell net.
	 * The returned map is unmodifiable.
	 * @return the SitePin to RouteTree map of the cell net
	 */
	public Map<SitePin, RouteTree> getSitePinRouteTrees() {
		return Collections.unmodifiableMap(sitePinToRTMap);
	}
	
	/**
	 * Builds and returns a list of the {@code RouteTree}s connected to sink SitePins
	 * @return a list of RouteTrees connected to the sink SitePin
	 */
	public List<RouteTree> getSinkSitePinRouteTrees() {
		
		if (sitePinToRTMap == null) {
			return Collections.emptyList();
		}
		
		return sitePinToRTMap.keySet().stream()
									.filter(SitePin::isInput)
									.map(sp -> sitePinToRTMap.get(sp))
									.collect(Collectors.toList());
	}
	
	/**
	 * Returns a RouteTree object that is connected to the specified CellPin. If the CellPin
	 * is connected to multiple RouteTrees (because it is mapped to multiple BelPins)
	 * then only one of the RouteTrees will be returned. To return all of the route trees, call
	 * {@link #getSinkRouteTrees}. Only use this function if you know that the CellPin maps
	 * to a single BelPin.
	 * 
	 * @param cellPin the sink CellPin
	 * @return a RouteTree that is connected to the specified CellPin or null if no
	 *    RouteTree connectes to the CellPin
	 * @see #getSinkRouteTrees(CellPin)
	 */
	public RouteTree getSinkRouteTree(CellPin cellPin) {
		
		BelPin belPin = cellPin.getMappedBelPin();
		return belPinToSinkRTMap.get(belPin);
	}
	
	/**
	 * Builds and returns a set of all RouteTrees of this net that are connected
	 * to the specified CellPin.
	 *
	 * @param cellPin sink CellPin
	 * @return A Set of RouteTree objects that cellPin is connected to.
	 * @see #getSinkRouteTree(CellPin)
	 */
	public Set<RouteTree> getSinkRouteTrees(CellPin cellPin) {
		
		Set<RouteTree> connectedRouteTrees = new HashSet<>();
		
		for (BelPin belPin : cellPin.getMappedBelPins()) {
			if (belPinToSinkRTMap.containsKey(belPin)) {
				connectedRouteTrees.add(belPinToSinkRTMap.get(belPin));
			}
		}
		
		return connectedRouteTrees;
	}
	
	/**
	 * Returns the RouteTree connected to the specified BelPin of this net
	 * 
	 * @param belPin the input BelPin
	 * @return a {@code RouteTree} that connects to a {@code belPin} or null if the belPin
	 * 		does not attach this net
	 */
	public RouteTree getSinkRouteTree(BelPin belPin) {
		return belPinToSinkRTMap == null ? null : belPinToSinkRTMap.get(belPin);
	}
	
	/**
	 * Build and returns a set of BelPins that the net is currently connected to.
	 * @return a set of BelPins that the net is currently connected to
	 */
	public Set<BelPin> getBelPins() {
		// TODO: Could this be done by simply taking all the sink cell pins and getting the
		//		 corresponding BelPin?

		Set<BelPin> connectedBelPins = new HashSet<>();
		
		if (belPinToSinkRTMap != null) {
			connectedBelPins.addAll(belPinToSinkRTMap.keySet());
		}
		
		BelPin belPinSource = sourcePin.getMappedBelPin();
		if (belPinSource != null) {
			connectedBelPins.add(belPinSource);
		}
		
		return connectedBelPins;
	}
	
	/**
	 * Returns the BelPin to RouteTree map of the net.  The returned map is
	 * unmodifiable.
	 * @return a map of the BelPin to RouteTree map of this net
	 */
	public Map<BelPin, RouteTree> getBelPinRouteTrees() {
		return Collections.unmodifiableMap(belPinToSinkRTMap);
	}
		
	/**
	 * Returns the current route status of net without recomputing the status. If the routing has changed,
	 * to recompute the route status first use {@link CellNet#computeRouteStatus()}.
	 * Possible statuses in include: <br>
	 * 1.) UNROUTED - no sink cell pins have been routed <br>
	 * 2.) PARTIALLY_ROUTED - some, but not all, sink cell pins that have been mapped to bel pins have been routed<br>
	 * 3.) FULLY_ROUTED - all sink cell pins that are mapped to bel pins have been routed <br> 
	 * 
	 * @return the {@code RouteStatus} of the current net
	 */
	public RouteStatus getRouteStatus() {
		return routeStatus;
	}
	
	/**
	 * Computes and stores the route status of the net. This function should be called to recompute the status
	 * of the route if the routing structure has been modified and the . If the routing structure has not been modified,
	 * then {@link CellNet#getRouteStatus} should be used instead. Possible statuses include: <br>
	 * <br>
	 * 1.) <b>UNROUTED</b> - no sink cell pins have been routed <br>
	 * 2.) <b>PARTIALLY_ROUTED</b> - some, but not all, sink cell pins <b>that have been mapped to bel pins</b> have been routed<br>
	 * 3.) <b>FULLY_ROUTED</b> - all sink cell pins <b>that are mapped to bel pins</b> have been routed <br> 
	 * <br>
	 * The complexity of this method is O(n) where n is the number of pins connected to the net.
	 * 
	 * @return the current RouteStatus of the net
	 */
	public RouteStatus computeRouteStatus() {
		
		int subtractCount = sourcePin.isMapped() ? 1 : 0;
		
		if (routedSinks == null || routedSinks.isEmpty()) {
			routeStatus = RouteStatus.UNROUTED;
		}
		else if (routedSinks.size() == pins.stream().filter(CellPin::isMapped).count() - subtractCount) {
			routeStatus = RouteStatus.FULLY_ROUTED;
		}
		else {
			routeStatus = RouteStatus.PARTIALLY_ROUTED;
		}
		
		return routeStatus;
	}
}
