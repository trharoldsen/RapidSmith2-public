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

import edu.byu.ece.rapidSmith.util.ArraySet;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Collections.emptySet;

/**
 * This class represents the sites found in a Xilinx device.  Sites are a collection
 * of closely interconnected BELs.
 * @author Chris Lavin
 */
public final class Site implements Serializable{
	private static final long serialVersionUID = -3823244747162695622L;
	/** Name of the site with X and Y coordinates (ie. SLICE_X0Y0) */
	private String name;
	/** The index in the tile's list of Sites */
	private int index;
	/** The tile where this site resides */
	private Tile tile;
	/** The X coordinate of the instance (ex: SLICE_X#Y5) */
	private int instanceX;
	/** The Y coordinate of the instance (ex: SLICE_X5Y#) */
	private int instanceY;
	/** The bondedness of the site */
	private BondedType bondedType;
	/** Stores the template of the type that has been assigned to this site. */
	private SiteTemplate template;
	/** List of possible types for this site. */
	private List<SiteTemplate> possibleTypes;

	/**
	 * Constructor unnamed, tileless site.
	 */
	public Site(){
		name = null;
		tile = null;
		instanceX = -1;
		instanceY = -1;
	}
	
	/**
	 * Returns the name of this site (ex: SLICE_X4Y6).
	 * @return the unique name of this site.
	 */
	public String getName(){
		return name;
	}
	
	/**
	 * Sets the name of this site (ex: SLICE_X5Y7).
	 * @param name the name to set.
	 */
	public void setName(String name){
		this.name = name;
	}

	/**
	 * Returns the index of this site in it tile's Site list.
	 * @return this site's index
	 */
	public int getIndex() {
		return index;
	}

	/**
	 * Sets the index of this site in its tile's Site list.
	 * @param index index of this site
	 */
	public void setIndex(int index) {
		this.index = index;
	}

	/**
	 * Returns the tile in which this site exists.
	 * @return the tile in which this site exists
	 */
	public Tile getTile(){
		return tile;
	}
	
	/**
	 * Sets the tile in which this site exists.
	 * @param location the tile location for this site
	 */
	public void setTile(Tile location){
		this.tile = location;
	}

	/**
	 * Returns the integer X value of the instance location
	 * (ex: SLICE_X5Y10, it will return 5).
	 * @return the X integer value of the site name or -1 if this instance is
	 * not placed or does not have X/Y coordinates in the site name
	 */
	public int getInstanceX(){
		return instanceX;
	}

	/**
	 * Returns the integer Y value of the instance location
	 * (ex: SLICE_X5Y10, it will return 10).
	 * @return The Y integer value of the site name or -1 if this instance is
	 * not placed or does not have X/Y coordinates in the site name
	 */
	public int getInstanceY(){
		return instanceY;
	}
	
	/**
	 * Sets the XY coordinates for this site based on the name.
	 * @param name the name of the site to infer and set the XY coordinates of.
	 */
	public boolean parseCoordinatesFromName(String name) {
		// reset the values
		this.instanceX = -1;
		this.instanceY = -1;

		// match the values
		Pattern re = Pattern.compile(".+_X(\\d+)Y(\\d+)");
		Matcher matcher = re.matcher(name);
		if (!matcher.matches())
			return false;

		// Populate the X and Y coordinates based on name
		this.instanceX = Integer.parseInt(matcher.group(1));
		this.instanceY = Integer.parseInt(matcher.group(2));
		return true;
	}

	/**
	 * Returns the current type of this site.
	 * @return the current type of this site
	 */
	public SiteType getType(){
		return getTemplate().getType();
	}

	/**
	 * Updates the type of this site to the specified type.
	 * <p/>
	 * This method obtains the site template from its device, therefore, the
	 * site must already exist in a tile which exists in a device.
	 * @param type the new type for this site
	 */
	public void setType(SiteType type) {
		template = getTemplate(type);
	}

	/**
	 * Returns the default type of this site.
	 * The default type is defined as getPossibleTypes[0].
	 *
	 * @return the default type of this site
	 */
	public SiteType getDefaultType() {
		return possibleTypes.get(0).getType();
	}

	/**
	 * Returns an array containing the valid types that this site can be
	 * treated as.
	 *
	 * @return the possible types for this site
	 */
	public List<SiteType> getPossibleTypes() {
		return possibleTypes.stream().map(k -> k.getType()).collect(Collectors.toList());
	}

	/**
	 * Sets the possible types for this site.
	 * The type as index 0 is considered the default type for the site.
	 * This method does not update the type of the site.
	 * @param possibleTypes the possible types for this site
	 */
	public void setPossibleTypes(List<SiteTemplate> possibleTypes) {
		this.possibleTypes = possibleTypes;
	}

	/**
	 * Returns the current template backing this site.
	 * The template will change when the site's type changes.
	 * @return the current template backing this site
	 */
	SiteTemplate getTemplate() {
		return template;
	}

	private SiteTemplate getTemplate(SiteType type) {
		if (getType() == type)
			return getTemplate();

		for (SiteTemplate st : possibleTypes) {
			if (st.getType() == type)
				return st;
		}
		throw new IllegalArgumentException("Illegal type for site " + getName() + ": " + type);
	}

	/**
	 * Returns whether the site is bonded.  IO are either bonded or unbonded.  Non-IO
	 * are always <code>internal</code>.
	 * @return the bondedness of the site
	 */
	public BondedType getBondedType() {
		return bondedType;
	}

	/**
	 * Sets whether the site is bonded.  IO are either bonded or unbonded.  Non-IO
	 * are always <code>internal</code>.
	 * @param bondedType the bondedness of the site
	 */
	public void setBondedType(BondedType bondedType) {
		this.bondedType = bondedType;
	}

	/**
	 * Returns the set of all BELs in the site.
	 * Use cautiously as Bel objects are recreated on each call.
	 * @return a new set, possibly empty, of all BELs in the site
	 */
	public List<Bel> getBels() {
		return getBels(getTemplate());
	}

	public List<Bel> getBels(SiteType type) {
		return getBels(getTemplate(type));
	}

	private List<Bel> getBels(SiteTemplate template) {
		List<BelTemplate> belTemplates = template.getBelTemplates();
		return belTemplates.stream()
				.map(t -> new Bel(this, t))
				.collect(Collectors.toList());
	}

	/**
	 * Returns the BEL of the specified name for the site.
	 * The Bel object will be created upon this call.
	 * @param belName the name of the BEL to return
	 * @return the BEL of the given name or null if no BEL with the specified name
	 *   exist in the (site, type) pair.
	 */
	public Bel getBel(String belName) {
		return getBel(getTemplate(), belName);
	}

	public Bel getBel(SiteType type, String belName) {
		return getBel(getTemplate(type), belName);
	}

	public Bel getBel(BelId belId) {
		return getBel(getTemplate(belId.getSiteType()), belId.getName());
	}

	private Bel getBel(SiteTemplate template, String belName) {
		BelTemplate bt = template.getBelNamesMap().get(belName);
		if (bt == null)
			return null;
		return new Bel(this, bt);
	}

	Bel getBel(SiteType type, int belIndex) {
		SiteTemplate st = getTemplate(type);
		BelTemplate bt = st.getBelTemplates().get(belIndex);
		return new Bel(this, bt);
	}

	/**
	 * Returns SiteTypes that are compatible with the default site type.
	 * Compatible types are different that possible types. These are types
	 * that the site type may possibly not be changed to, but instances of the type be
	 * placed on them.
	 *
	 * @return SiteTypes that are compatible with the default site type
	 */
	public Set<SiteType> getCompatibleTypes() {
		return getDefaultTemplate().getCompatibleTypes();
	}

	private SiteTemplate getDefaultTemplate() {
		return possibleTypes.get(0);
	}

	/**
	 * Returns the wires in the site which source wire connections.
	 *
	 * @return the wires in the site which source wire connections
	 */
	public Collection<Wire> getWires() {
		return getWires(getTemplate());
	}

	public Collection<Wire> getWires(SiteType type) {
		return getWires(getTemplate(type));
	}

	private Collection<Wire> getWires(SiteTemplate template) {
		return template.getSiteWires().values().stream()
				.map(i -> new SiteWire(this, i))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * Returns the wire in this site with the given name.  This method does not
	 * guarantee that the requested wire exists in this site.
	 * @param wireName the name of the wire to get
	 * @return the wire in this site with the given name
	 */
	public SiteWire getWire(String wireName) {
		return getWire(this.getType(), wireName);
	}

	/**
	 * Returns the wire in this site with the given name and type.  This method does
	 * not guarantee that the requested wire exists in this site.
	 * @param wireName the name of the wire to get
	 * @return the wire in this site with the given name
	 */
	// Included just for consistency with other methods.
	public SiteWire getWire(SiteType type, String wireName) {
		SiteTemplate template = getTemplate(type);
		SiteWireTemplate wireTemplate = template.getSiteWires().get(wireName);
		if (wireTemplate == null)
			return null;
		return new SiteWire(this, wireTemplate);
	}

	/**
	 * @param wireName name of wire to query for
	 * @return true if the wire with the given name exists in this site
	 */
	public boolean hasWire(String wireName) {
		return hasWire(getTemplate(), wireName);
	}

	/**
	 * @param wireName name of wire to query for
	 * @return true if the wire with the given name exists in this site configured
	 *   as [type]
	 */
	public boolean hasWire(SiteType type, String wireName) {
		return hasWire(getTemplate(type), wireName);
	}

	private boolean hasWire(SiteTemplate template, String wireName) {
		return template.getSiteWires().containsKey(wireName);
	}

	SiteNodeTemplate getNodeOfWire(SiteWireTemplate wire) {
		return getTemplate(wire.getSiteType()).getWireNodesMap().get(wire);
	}

	/**
	 * Creates and returns the source pins for this site.
	 * The SitePin objects are recreated upon each call.
	 * @return the source pins for this site
	 */
	public List<SitePin> getSourcePins() {
		return getSourcePins(getTemplate());
	}

	/**
	 * Creates and returns the source pins for this site when configured as type.
	 * The SitePin objects are recreated upon each call.
	 * @return the source pins for this site
	 */
	public List<SitePin> getSourcePins(SiteType type) {
		return getSourcePins(getTemplate(type));
	}
	
	private List<SitePin> getSourcePins(SiteTemplate template) {
		List<SitePinTemplate> sinkTemplates = template.getPinTemplates();
		return sinkTemplates.stream()
			.filter(it -> it.isOutput())
			.map(it -> new SitePin(this, it))
			.collect(Collectors.toList());
	}

	/**
	 * Creates and returns the source pin on this site with the specified name.
	 * @param pinName the name of the pin to create
	 * @return the source pin on this site with the specified name or
	 *   null if the pin does not exist
	 */
	public SitePin getSourcePin(String pinName) {
		return getSourcePin(getTemplate(), pinName);
	}

	/**
	 * Creates and returns the source pin on this site when configured as type with
	 * the specified name.
	 * @param pinName the name of the pin to create
	 * @return the source pin on this site with the specified name or
	 *   null if the pin does not exist
	 */
	public SitePin getSourcePin(SiteType type, String pinName) {
		return getSourcePin(getTemplate(type), pinName);
	}

	private SitePin getSourcePin(SiteTemplate template, String pinName) {
		SitePinTemplate pinTemplate = template.getPinNamesMap().get(pinName);
		if (pinTemplate == null || !pinTemplate.isOutput())
			return null;
		return new SitePin(this, pinTemplate);
	}

	/**
	 * Creates and returns all sink pins on this site.
	 * The SitePin objects are recreated upon each call.
	 * @return all sink pins on this site
	 */
	public List<SitePin> getSinkPins() {
		return getSinkPins(getTemplate());
	}

	/**
	 * Creates and returns all sink pins on this site when configured as type.
	 * The SitePin objects are recreated upon each call.
	 * @return all sink pins on this site
	 */
	public List<SitePin> getSinkPins(SiteType type) {
		return getSinkPins(getTemplate(type));
	}

	private List<SitePin> getSinkPins(SiteTemplate template) {
		List<SitePinTemplate> sinkTemplates = template.getPinTemplates();
		return sinkTemplates.stream()
			.filter(it -> it.isInput())
			.map(it -> new SitePin(this, it))
			.collect(Collectors.toList());
	}

	/**
	 * Creates and returns the sink pin on this site with the specified name.
	 * @param pinName the name of the pin to create
	 * @return the sink pin on this site with the specified name or
	 *   null if the pin does not exist
	 */
	public SitePin getSinkPin(String pinName) {
		return getSinkPin(getTemplate(), pinName);
	}

	/**
	 * Creates and returns the sink pin on this site when configured as type with
	 * the specified name.
	 * @param pinName the name of the pin to create
	 * @return the sink pin on this site with the specified name or
	 *   null if the pin does not exist
	 */
	public SitePin getSinkPin(SiteType type, String pinName) {
		return getSinkPin(getTemplate(type), pinName);
	}

	private SitePin getSinkPin(SiteTemplate template, String pinName) {
		SitePinTemplate pinTemplate = template.getPinNamesMap().get(pinName);
		if (pinTemplate == null || !pinTemplate.isInput())
			return null;
		return new SitePin(this, pinTemplate);
	}

	/**
	 * Creates and returns the pin on this site with the specified name.
	 * @param pinName the name of the pin to create
	 * @return the pin on this site with the specified name
	 */
	public SitePin getPin(String pinName) {
		return getPin(getTemplate(), pinName);
	}

	/**
	 * Creates and returns the pin on this site when configured as type with the
	 * specified name.
	 * @param pinName the name of the pin to create
	 * @return the pin on this site with the specified name
	 */
	public SitePin getPin(SiteType type, String pinName) {
		return getPin(getTemplate(type), pinName);
	}

	SitePin getPin(SiteType type, int pinIndex) {
		SiteTemplate template = getTemplate(type);
		SitePinTemplate pinTemplate = template.getPinTemplates().get(pinIndex);
		return new SitePin(this, pinTemplate);
	}

	private SitePin getPin(SiteTemplate template, String pinName) {
		SitePinTemplate pinTemplate = template.getPinNamesMap().get(pinName);
		if (pinTemplate == null) return null;
		return new SitePin(this, pinTemplate);
	}

	// Site compatibility
	/**
	 * This method will check if the SiteType otherType can be placed
	 * at this site.  Most often only if they are
	 * equal can this be true.  However there are a few special cases that require
	 * extra handling.  For example a SLICEL can reside in a SLICEM site but not 
	 * vice versa.  
	 * @param otherType The site type to try to place on this site.
	 * @return True if otherType can be placed at this site, false otherwise.
	 */
	public boolean isCompatibleSiteType(SiteType otherType){
		for (SiteType compat : getCompatibleTypes())
			if (compat == otherType)
				return true;
		return false;
	}
	
	/**
	 * This method checks to see if the specified start and <br>
	 * end wire form a bel routethrough.
	 * @param startWire Start wire enum
	 * @param endWire Sink wire enum
	 * @return True if the wires form a routethrough
	 */
	boolean isRoutethrough(SiteWireTemplate startWire, SiteWireTemplate endWire) {
		SiteTemplate template = getTemplate(startWire.getSiteType());
		Map<SiteWireTemplate, Set<SiteWireTemplate>> rtMap = template.getBelRoutethroughMap();
		Set<SiteWireTemplate> sinksOfStarts = rtMap.get(startWire);
		if (sinksOfStarts == null)
			return false;
		return sinksOfStarts.contains(endWire);
	}

	/**
	 * This method gets the type of otherSite and calls the other method
	 * public boolean isCompatibleSiteType(SiteType otherType);
	 * See that method for more information.
	 * @param otherSite The other site to see if its type is compatible with this site.
	 * @return True if compatible, false otherwise.
	 */
	public boolean isCompatibleSiteType(Site otherSite){
		return isCompatibleSiteType(otherSite.getType());
	}

	@Override
	public String toString() {
		return "{Site " + name + "}";
	}

	@Override
	public int hashCode() {
		return tile.hashCode() * 31 + index;
	}

	/*
	   Class and method for optimized Hessian serialization.
	 */
	private static class SiteReplace implements Serializable {
		private static final long serialVersionUID = 3178000777471034057L;
		/** Name of the site with X and Y coordinates (ie. SLICE_X0Y0) */
		private String name;
		private List<SiteTemplate> possibleTypes;
		private Integer instanceX;
		private Integer instancyY;
		private BondedType bondedType;

		@SuppressWarnings("unused")
		private Site readResolve() {
			Site site = new Site();
			site.setName(name);
			site.possibleTypes = possibleTypes;
			site.bondedType = bondedType;
			if (instanceX != null || instancyY != null || !site.parseCoordinatesFromName(name)) {
				site.instanceX = (instanceX != null) ? instanceX : -1;
				site.instanceY = (instanceX != null) ? instanceX : -1;
			}
			return site;
		}
	}

	@SuppressWarnings("unused")
	private SiteReplace writeReplace() {
		SiteReplace repl = new SiteReplace();
		repl.name = name;
		repl.possibleTypes = possibleTypes;
		repl.bondedType = bondedType;
		repl.instanceX = instanceX;
		repl.instancyY = instanceY;
		return repl;
	}
}
