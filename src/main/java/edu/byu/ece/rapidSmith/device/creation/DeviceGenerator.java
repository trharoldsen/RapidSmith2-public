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

package edu.byu.ece.rapidSmith.device.creation;

import edu.byu.ece.rapidSmith.RSEnvironment;
import edu.byu.ece.rapidSmith.device.*;
import edu.byu.ece.rapidSmith.primitiveDefs.*;
import edu.byu.ece.rapidSmith.util.ArraySet;
import edu.byu.ece.rapidSmith.util.Exceptions;
import edu.byu.ece.rapidSmith.util.HashPool;
import edu.byu.ece.rapidSmith.util.PartNameTools;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static edu.byu.ece.rapidSmith.util.Exceptions.EnvironmentException;
import static edu.byu.ece.rapidSmith.util.Exceptions.FileFormatException;

/**
 * Generates a new device through parsing the device's XDLRC representation.
 * <p>
 * Steps
 * 1) First parse
 * a) WireTemplateListener
 * i) Extract wire names, types, and directions from XDLRC
 * ii) Enumerate each wire name in device
 * b) TileAndPrimitiveSiteListener
 * i) Extract part name from XDLRC
 * ii) Extract number of rows and columns from XDLRC
 * iii) Construct device tile array
 * iv) Extract tile names and types
 * v) Create list of sites in each tile
 * vi) Extract name and type of each primitive site
 * vii) Define alternative types for each primitive site
 * c) PrimitiveDefsListener
 * i) Construct primitive defs structure
 * 2) Construct dependent resources
 * a) Build tile and sites name map
 * 2) Second parse
 * a) Build wire connection for each tile.  Preserve all connections that
 * are either sources or sinks of a site or a PIP
 */
public final class DeviceGenerator {
	private Device device;
	private Document familyInfo;

	private static final int PIP_CAPACITY = 40000;
	private final Set<String> pipSources = new HashSet<>(PIP_CAPACITY);
	private final Set<String> pipSinks = new HashSet<>(PIP_CAPACITY);

	private HashMap<String, TileWireTemplate> tileWireTemplates;
	private HashMap<String, SiteWireTemplate> siteWireTemplates;
	private int numUniqueWireTypes;

	/** Keeps track of each unique object in the device */
	private HashPool<WireConnection<TileWireTemplate>> tileConnPool;
	private HashPool<ArraySet<WireConnection<TileWireTemplate>>> tileConnSetPool;
	private HashPool<WireHashMap<TileWireTemplate>> tileWireMapPool;

	private HashPool<PIPRouteThrough> routeThroughPool;
	private HashPool<Map<String, TileWireTemplate>> externalWiresPool;
	private HashPool<Map<SiteType, Map<String, TileWireTemplate>>> externalWiresMapPool;
	private HashPool<ArrayList<SiteType>> alternativeTypesPool;

	private Set<TileWireTemplate> siteWireSourceSet;
	private Set<TileWireTemplate> siteWireSinkSet;

	/**
	 * Generates and returns the Device created from the XDLRC at the specified
	 * path.
	 *
	 * @param xdlrcPath path to the XDLRC file for the device
	 * @return the generated Device representation
	 */
	public Device generate(Path xdlrcPath) throws IOException {
		System.out.println("Generating device for file " + xdlrcPath.getFileName());

		this.device = new Device();

		this.tileConnPool = new HashPool<>();
		this.tileConnSetPool = new HashPool<>();
		this.tileWireMapPool = new HashPool<>();

		this.routeThroughPool = new HashPool<>();
		this.externalWiresPool = new HashPool<>();
		this.externalWiresMapPool = new HashPool<>();
		this.alternativeTypesPool = new HashPool<>();

		this.tileWireTemplates = new HashMap<>(50000);
		this.siteWireTemplates = new HashMap<>(30000);
		this.numUniqueWireTypes = 0;

		// Requires a two part iteration, the first to obtain the tiles and sites,
		// and the second to gather the wires.  Two parses are required since the
		// wires need to know the source and sink tiles.
		XDLRCParser parser = new XDLRCParser();
		System.out.println("Starting first pass");
		parser.registerListener(new FamilyTypeListener());
		parser.registerListener(new WireTemplateListener());
		parser.registerListener(new TileAndSiteGeneratorListener());
		parser.registerListener(new PrimitiveDefsListener());
		parser.registerListener(new XDLRCParseProgressListener());
		try {
			parser.parse(xdlrcPath);
		} catch (IOException e) {
			throw new IOException("Error handling file " + xdlrcPath, e);
		}
		parser.clearListeners();

		device.constructTileMap();
		PrimitiveDefsCorrector.makeCorrections(device.getPrimitiveDefs(), familyInfo);
		device.setSiteTemplates(createSiteTemplates());

		System.out.println("Starting second pass");
		HashMap<Tile, WireHashMap<TileWireTemplate>> forwardWireMaps = new HashMap<>();
		HashMap<Tile, WireHashMap<TileWireTemplate>> reverseWireMaps = new HashMap<>();
		parser.registerListener(new WireConnectionGeneratorListener(forwardWireMaps));
		parser.registerListener(new ReverseWireConnectionGeneratorListener(reverseWireMaps));
		parser.registerListener(new SourceAndSinkListener());
		parser.registerListener(new TileWireListener());
		parser.registerListener(new XDLRCParseProgressListener());
		try {
			parser.parse(xdlrcPath);
		} catch (IOException e) {
			throw new IOException("Error handling file " + xdlrcPath, e);
		}

		Map<Tile, Map<TileWireTemplate, Set<WireConnection<TileWireTemplate>>>> wcsToAdd =
			getWCsToAdd(true, forwardWireMaps);
		Map<Tile, Map<TileWireTemplate, Set<WireConnection<TileWireTemplate>>>> wcsToRemove =
			getWCsToRemove(true, forwardWireMaps);
		Map<Tile, Map<TileWireTemplate, Set<WireConnection<TileWireTemplate>>>> rwcsToAdd =
			getWCsToAdd(false, reverseWireMaps);
		Map<Tile, Map<TileWireTemplate, Set<WireConnection<TileWireTemplate>>>> rwcsToRemove =
			getWCsToRemove(false, reverseWireMaps);

		// These take up a lot of memory and we're going to regenerate each of these in the
		// next step.  Clearing these will allow for better garbage collection
		tileConnPool = new HashPool<>();
		tileConnSetPool = new HashPool<>();
		tileWireMapPool = new HashPool<>();

		System.out.println("Parsing Device Info file");
		if (!parseDeviceInfo(device)) {
			System.err.println("[Warning]: The device info file for the part " + device.getPartName() + " cannot be found.");
		}

		makeWireCorrections(wcsToAdd, wcsToRemove, forwardWireMaps);
		makeWireCorrections(rwcsToAdd, rwcsToRemove, reverseWireMaps);
		forwardWireMaps.forEach((k, v) -> k.setWireHashMap(v));
		reverseWireMaps.forEach((k, v) -> k.setReverseWireConnections(v));

		device.setNumUniqueWireTypes(numUniqueWireTypes);
		device.constructDependentResources();

		System.out.println("Finishing device creation process");

		return device;
	}

	/**
	 * Creates the templates for the primitive sites with information from the
	 * primitive defs and device information file.
	 */
	private Map<SiteType, SiteTemplate> createSiteTemplates() {
		Map<SiteType, SiteTemplate> siteTemplates = new HashMap<>();
		FamilyType family = device.getFamily();

		// Create a template for each primitive type
		for (PrimitiveDef def : device.getPrimitiveDefs()) {
			Element ptEl = getSiteTypeEl(def.getType());

			SiteTemplate template = new SiteTemplate();
			template.setType(def.getType());
			template.setBelTemplates(createBelTemplates(def, ptEl));
			createAndSetIntrasiteRouting(def, template, ptEl);
			createAndSetSitePins(def, template);

			Element compatTypesEl = ptEl.getChild("compatible_types");
			if (compatTypesEl != null) {
				ArraySet<SiteType> compatTypes = compatTypesEl.getChildren("compatible_type").stream()
					.map(compatTypeEl -> SiteType.valueOf(family, compatTypeEl.getText()))
					.collect(Collectors.toCollection(ArraySet::new));
				compatTypes.trimToSize();
				template.setCompatibleTypes(compatTypes);
			}

			template.setReverseWireConnections(getReverseMapForSite(template));

			siteTemplates.put(def.getType(), template);
		}

		return siteTemplates;
	}

	private WireHashMap<SiteWireTemplate> getReverseMapForSite(SiteTemplate site) {
		Map<SiteWireTemplate, ArraySet<WireConnection<SiteWireTemplate>>> reverseMap = new HashMap<>();
		for (SiteWireTemplate srcWire : site.getSiteWires().values()) {
			ArraySet<WireConnection<SiteWireTemplate>> wcs = site.getWireConnections(srcWire);
			if (wcs != null) {
				for (WireConnection<SiteWireTemplate> c : wcs) {
					WireConnection<SiteWireTemplate> reverse = new WireConnection<>(
						srcWire, -c.getRowOffset(),
						-c.getColumnOffset(), c.isPIP());
					reverseMap.computeIfAbsent(c.getSinkWire(), k -> new ArraySet<>()).add(reverse);
				}
			}
		}

		WireHashMap<SiteWireTemplate> wireHashMap = new WireHashMap<>();
		for (Map.Entry<SiteWireTemplate, ArraySet<WireConnection<SiteWireTemplate>>> e : reverseMap.entrySet()) {
			ArraySet<WireConnection<SiteWireTemplate>> v = e.getValue();
			v.trimToSize();
			wireHashMap.put(e.getKey(), v);
		}

		return wireHashMap;
	}

	/**
	 * Creates the templates for each BEL in the primitive site
	 *
	 * @param def       The primitive def to process
	 * @param ptElement XML element detailing the primitive type
	 * @return The templates for each BEL in the primitive type
	 */
	private Map<String, BelTemplate> createBelTemplates(PrimitiveDef def, Element ptElement) {
		Map<String, BelTemplate> templates = new HashMap<>();

		// for each BEL element
		for (PrimitiveElement el : def.getElements()) {
			if (!el.isBel())
				continue;
			
			BelId id = new BelId(def.getType(), el.getName());
			// Set the BEL type as defined in the deviceinfo file
			String belType = getTypeOfBel(el.getName(), ptElement);

			BelTemplate template = new BelTemplate(id, belType);

			// Create the BEL pin templates
			Map<String, BelPinTemplate> sinks = new HashMap<>();
			Map<String, BelPinTemplate> sources = new HashMap<>();
			for (PrimitiveDefPin pin : el.getPins()) {
				BelPinTemplate belPin = new BelPinTemplate(id, pin.getInternalName());
				belPin.setDirection(pin.getDirection());
				String wireName = getIntrasiteWireName(def.getType(), el.getName(), belPin.getName());
				belPin.setWire(siteWireTemplates.get(wireName));
				if (pin.getDirection() == PinDirection.IN || pin.getDirection() == PinDirection.INOUT)
					sinks.put(belPin.getName(), belPin);
				if (pin.getDirection() == PinDirection.OUT || pin.getDirection() == PinDirection.INOUT)
					sources.put(belPin.getName(), belPin);
			}
			template.setSources(sources);
			template.setSinks(sinks);
			templates.put(el.getName(), template);
		}

		// Find the site pins that connect to each BEL pin by traversing the routing.
		// This info is useful for directing which site pin should be targeted while
		// routing to reach the correct BEL pin.
		for (PrimitiveDefPin pin : def.getPins()) {
			PrimitiveElement el = def.getElement(pin.getInternalName());
			boolean forward = !pin.isOutput(); // traverse forward or backward?
			findAndSetSitePins(templates, def, forward, pin.getExternalName(), el);
		}

		return templates;
	}

	/**
	 * Recursively traverses through the elements to find all BEL pins reachable from the site pin.
	 *
	 * @param templates the BEL templates in the primitive type
	 * @param def       the primitive def for the current type
	 * @param forward   traverse forward or backward (forward for site sinks and
	 *                  backward for site sources)
	 * @param sitePin   Site pin we're searching from
	 * @param element   The current element we're looking at
	 */
	private void findAndSetSitePins(Map<String, BelTemplate> templates, PrimitiveDef def,
	                                boolean forward, String sitePin, PrimitiveElement element) {

		// follow each connection from the element
		for (PrimitiveConnection c : element.getConnections()) {
			PrimitiveElement destElement;

			// This connection goes the opposite of the way we want to search
			if (forward != c.isForwardConnection())
				continue;

			destElement = def.getElement(c.getElement1());

			if (destElement.isMux()) {
				// This is a routing mux.  Follow it.
				findAndSetSitePins(templates, def, forward, sitePin, destElement);
			}
		}
	}

	/**
	 * Find the XML element specifying the type for the desired BEL
	 *
	 * @param belName   name of the BEL to find the type for
	 * @param ptElement XML element detailing the primitive type
	 * @return the BEL type
	 */
	private String getTypeOfBel(String belName, Element ptElement) {
		for (Element belEl : ptElement.getChild("bels").getChildren("bel")) {
			if (belEl.getChildText("name").equals(belName))
				return belEl.getChildText("type");
		}
		assert false : "No type found for the specified BEL " + belName + " " + ptElement.getChildText("name");
		return null;
	}

	/**
	 * Creates the wire connections connecting the BELs and muxes in the primitive type.
	 *  @param def      the primitive def for the current type
	 * @param template the template for the current type
	 */
	private void createAndSetIntrasiteRouting(
		PrimitiveDef def, SiteTemplate template, Element siteElement
	) {
		WireHashMap<SiteWireTemplate> wireMap = new WireHashMap<>();
		Map<String, SiteWireTemplate> siteWires = new HashMap<>(1000);

		/*
		    We build the routing structure by find all of the wire sources and
		    creating a wire connection between it and its sinks.  For muxes, we
		    additionally create a wire connection from each input of the mux to
		    the output.
		 */
		for (PrimitiveElement el : def.getElements()) {
			String elName = el.getName();
			if (el.isPin() && !def.getPin(elName).isOutput()) { // input site pin
				addWireConnectionsForElement(def, el, wireMap, siteWires);
			} else if (el.isBel()) {
				addWireConnectionsForElement(def, el, wireMap, siteWires);
			} else if (el.isMux()) {
				addWireConnectionsForElement(def, el, wireMap, siteWires);
				createAndAddMuxPips(def, el, wireMap, siteWires);
			}
		}
		
		Map<SiteWireTemplate, Set<SiteWireTemplate>> belRoutethroughMap =
			createBelRoutethroughs(template, siteElement, wireMap, siteWires);

		// reduce memory footprint of routing
		wireMap.values().forEach(s -> s.trimToSize());

		// update the templates with the created structures
		template.setBelRoutethroughs(belRoutethroughMap);
		template.setRouting(wireMap);
		template.setSiteWires(siteWires);
	}

	/**
	 * Creates a BEL routethrough map for the site template.  
	 * @param template Site Template to generate routethroughs for
	 * @param siteElement XML document element of the site in the familyinfo.xml file
	 * @param wireMap WireHashMap of the site template
	 * @param siteWires wires in this site
	 * @return A Map of BEL routethroughs
	 */
	private Map <SiteWireTemplate, Set<SiteWireTemplate>> createBelRoutethroughs(
		SiteTemplate template, Element siteElement,
		WireHashMap<SiteWireTemplate> wireMap,
		Map<String, SiteWireTemplate> siteWires
	) {
		
		Map <SiteWireTemplate, Set<SiteWireTemplate>> belRoutethroughMap = new HashMap<>();
		
		for (Element belEl : siteElement.getChild("bels").getChildren("bel")) {
			String belName = belEl.getChildText("name");
			
			Element routethroughs = belEl.getChild("routethroughs");
			
			// bel has routethroughs
			if (routethroughs != null) {
				
				for(Element routethrough : routethroughs.getChildren("routethrough")) {
				
					String inputPin = routethrough.getChildText("input");
					String outputPin = routethrough.getChildText("output");
					
					String inputWireName = getIntrasiteWireName(template.getType(), belName, inputPin);
					String outputWireName = getIntrasiteWireName(template.getType(), belName, outputPin);

					SiteWireTemplate startTemplate = siteWireTemplates.get(inputWireName);
					SiteWireTemplate endTemplate = siteWireTemplates.get(outputWireName);
					siteWires.put(inputWireName, startTemplate);
					siteWires.put(outputWireName, endTemplate);

					// If the wire names for the routethrough do not exist, throw a parse exception telling the user 
					if (startTemplate == null) {
						throw new Exceptions.ParseException(String.format("Cannot find intrasite wire \"%s\" for bel routethrough \"%s:%s:%s\". "
								+ "Check the familyInfo.xml file for this routethrough and make sure the connections are correct.", 
								inputWireName, template.getType(), inputPin, outputPin));
					} else if (endTemplate == null) {
						throw new Exceptions.ParseException(String.format("Cannot find intrasite wire \"%s\" for bel routethrough \"%s:%s:%s\". "
								+ "Check the familyInfo.xml file for this routethrough and make sure the connections are correct.", 
								outputWireName, template.getType(), inputPin, outputPin));
					}
					
					// add the routethrough to the routethrough map; 
					Set<SiteWireTemplate> sinkWires = belRoutethroughMap.computeIfAbsent(
						startTemplate, k -> new HashSet<>());
					sinkWires.add(endTemplate);
				}
			}
		}
		
		// create a new wire connection for each routethrough and adds them to the wire map
		for (SiteWireTemplate startWire : belRoutethroughMap.keySet()) {

			ArraySet<WireConnection<SiteWireTemplate>> wireConnections = belRoutethroughMap.get(startWire).stream()
				.map(sink -> new WireConnection<>(sink, 0, 0, true))
				.collect(Collectors.toCollection(ArraySet::new));
			wireMap.put(startWire, wireConnections);
		}
		
		// return null if the belRoutethroughMap is empty
		return belRoutethroughMap.isEmpty() ? null : belRoutethroughMap;
	}
	
	/**
	 * Creates a PIP wire connection from each input of the mux to the output.
	 * Additionally creates the attribute that would represent this connection
	 * in XDL and adds it to the muxes structure.
	 * @param def     the primitive def for the current type
	 * @param el      the mux element from the primitive def
	 * @param wireMap the map of wire connections for the site template
	 * @param siteWires wires in this site
	 */
	private void createAndAddMuxPips(
		PrimitiveDef def, PrimitiveElement el,
		WireHashMap<SiteWireTemplate> wireMap,
		Map<String, SiteWireTemplate> siteWires
	) {
		String elName = el.getName();
		String sinkName = getIntrasiteWireName(def.getType(), elName, getOutputPin(el));
		SiteWireTemplate sinkWire = siteWireTemplates.get(sinkName);
		ArraySet<WireConnection<SiteWireTemplate>> wcs = new ArraySet<>(1);
		wcs.add(new WireConnection<>(sinkWire, 0, 0, true));
		siteWires.put(sinkName, sinkWire);

		for (PrimitiveDefPin pin : el.getPins()) {
			if (pin.isOutput())
				continue;
			String srcName = getIntrasiteWireName(def.getType(), elName, pin.getInternalName());
			SiteWireTemplate srcWire = siteWireTemplates.get(srcName);
			wireMap.put(srcWire, wcs);
			siteWires.put(srcName, srcWire);
		}
	}

	/**
	 * Gets the wire connections for this element and adds them to the wire map
	 *  @param def     the primitive def for the current type
	 * @param el      the current element from the primitive def
	 * @param wireMap the map of wire connections for the site template
	 * @param siteWires wires in the site
	 */
	private void addWireConnectionsForElement(
		PrimitiveDef def, PrimitiveElement el, WireHashMap<SiteWireTemplate> wireMap,
		Map<String, SiteWireTemplate> siteWires
	) {
		Map<SiteWireTemplate, ArraySet<WireConnection<SiteWireTemplate>>> wcsMap;
		wcsMap = getWireConnectionsForElement(def, el);
		for (Map.Entry<SiteWireTemplate, ArraySet<WireConnection<SiteWireTemplate>>> entry : wcsMap.entrySet()) {
			SiteWireTemplate template = entry.getKey();
			wireMap.put(template, entry.getValue());
			siteWires.put(template.getName(), template);

			for (WireConnection<SiteWireTemplate> wc : entry.getValue()) {
				SiteWireTemplate sink = wc.getSinkWire();
				siteWires.put(sink.getName(), sink);
			}
		}
	}

	/**
	 * Returns all of the wire connections coming from the element
	 *
	 * @param def the primitive def for the current type
	 * @param el  the current element from the primitive def
	 * @return all of the wire connection coming from the element
	 */
	private Map<SiteWireTemplate, ArraySet<WireConnection<SiteWireTemplate>>> getWireConnectionsForElement(
			PrimitiveDef def, PrimitiveElement el
	) {
		Map<SiteWireTemplate, ArraySet<WireConnection<SiteWireTemplate>>> wcsMap = new HashMap<>();
		for (PrimitiveConnection conn : el.getConnections()) {
			// Only handle connections this element sources
			if (!conn.isForwardConnection())
				continue;

			SiteWireTemplate source = getPinSource(def, conn);
			SiteWireTemplate sink = getPinSink(def, conn);
			ArraySet<WireConnection<SiteWireTemplate>> wcs =
				wcsMap.computeIfAbsent(source, k -> new ArraySet<>());
			wcs.add(new WireConnection<>(sink, 0, 0, false));
		}
		return wcsMap;
	}

	private static String getOutputPin(PrimitiveElement el) {
		for (PrimitiveDefPin pin : el.getPins()) {
			if (pin.isOutput())
				return pin.getInternalName();
		}
		return null;
	}

	private SiteWireTemplate getPinSource(PrimitiveDef def, PrimitiveConnection conn) {
		String element = conn.getElement0();
		String pin = conn.getPin0();
		String wireName = getIntrasiteWireName(def.getType(), element, pin);
		return siteWireTemplates.get(wireName);
	}

	private SiteWireTemplate getPinSink(PrimitiveDef def, PrimitiveConnection conn) {
		String element = conn.getElement1();
		String pin = conn.getPin1();
		String wireName = getIntrasiteWireName(def.getType(), element, pin);
		return siteWireTemplates.get(wireName);
	}

	/**
	 * Creates the site pin templates and adds them to the site template.
	 */
	private void createAndSetSitePins(PrimitiveDef def, SiteTemplate siteTemplate) {
		Map<String, SitePinTemplate> sources = new HashMap<>();
		Map<String, SitePinTemplate> sinks = new HashMap<>();

		for (PrimitiveDefPin pin : def.getPins()) {
			String name = pin.getInternalName();
			SitePinTemplate template = new SitePinTemplate(name, def.getType());
			template.setDirection(pin.getDirection());
			String wireName = getIntrasiteWireName(def.getType(), name, name);
			template.setInternalWire(siteWireTemplates.get(wireName));
			if (pin.getDirection() == PinDirection.IN)
				sinks.put(name, template);
			else
				sources.put(name, template);
		}

		siteTemplate.setSources(sources);
		siteTemplate.setSinks(sinks);
	}

	/**
	 * Searches the device info file for the primitive type element of the
	 * specified type.
	 *
	 * @param type the type of the element to retrieve
	 * @return the JDOM element for the requested primitive type
	 */
	private Element getSiteTypeEl(SiteType type) {
		Element siteTypesEl = familyInfo.getRootElement().getChild("site_types");
		for (Element siteTypeEl : siteTypesEl.getChildren("site_type")) {
			if (siteTypeEl.getChild("name").getText().equals(type.name()))
				return siteTypeEl;
		}
		throw new FileFormatException("no site type " + type.name() + " in familyInfo.xml");
	}

	private Map<Tile, Map<TileWireTemplate, Set<WireConnection<TileWireTemplate>>>> getWCsToAdd(
		boolean forward, Map<Tile, WireHashMap<TileWireTemplate>> wireMapMap
	) {
		Map<Tile, Map<TileWireTemplate, Set<WireConnection<TileWireTemplate>>>> wcsToAdd = new HashMap<>();

		for (Map.Entry<Tile, WireHashMap<TileWireTemplate>> e : wireMapMap.entrySet()) {
			Tile tile = e.getKey();
			WireHashMap<TileWireTemplate> wireHashMap = e.getValue();
			Map<TileWireTemplate, Set<WireConnection<TileWireTemplate>>> tileWCsToAdd = new HashMap<>();
			// Traverse all non-PIP wire connections starting at this source wire.  If any
			// such wire connections lead to a sink wire that is not already a connection of
			// the source wire, mark it to be added as a connection
			for (TileWireTemplate wire : tile.getTileWires().values()) {
				Set<WireConnection<TileWireTemplate>> wcToAdd = new HashSet<>();
				Set<WireConnection<TileWireTemplate>> checkedConnections = new HashSet<>();
				Queue<WireConnection<TileWireTemplate>> connectionsToFollow = new ArrayDeque<>();

				// Add the wire to prevent building a connection back to itself
				checkedConnections.add(new WireConnection<>(wire, 0, 0, false));
				ArraySet<WireConnection<TileWireTemplate>> wcs = wireHashMap.get(wire);
				if (wcs != null) {
					for (WireConnection<TileWireTemplate> wc : wcs) {
						if (!wc.isPIP()) {
							checkedConnections.add(wc);
							connectionsToFollow.add(wc);
						}
					}
				}

				while (!connectionsToFollow.isEmpty()) {
					WireConnection<TileWireTemplate> midwc = connectionsToFollow.remove();
					Tile midTile = midwc.getTile(tile);
					TileWireTemplate midWire = midwc.getSinkWire();

					// Dead end checks
					if (wireMapMap.get(midTile).get(midWire) == null)
						continue;

					for (WireConnection<TileWireTemplate> sinkwc : wireMapMap.get(midTile).get(midWire)) {
						if (sinkwc.isPIP()) continue;

						TileWireTemplate sinkWire = sinkwc.getSinkWire();
						Tile sinkTile = sinkwc.getTile(midTile);
						int colOffset = midwc.getColumnOffset() + sinkwc.getColumnOffset();
						int rowOffset = midwc.getRowOffset() + sinkwc.getRowOffset();

						// This represents the wire connection from the original source to the sink wire
						WireConnection<TileWireTemplate> source2sink =
							new WireConnection<>(sinkWire, rowOffset, colOffset, false);
						boolean wirePreviouslyChecked = !checkedConnections.add(source2sink);

						// Check if we've already processed this guy and process him if we haven't
						if (!wirePreviouslyChecked) {
							connectionsToFollow.add(source2sink);

							// Only add the connection if the wire is a sink.  Other connections are
							// useless for wire traversing.
							if (wireIsSink(wireMapMap.get(sinkTile), sinkWire, forward))
								wcToAdd.add(tileConnPool.add(source2sink));
						}
					}
				}

				// If there are wires to add, add them here by creating a new WireConnection array
				// combining the old and new wires.
				if (!wcToAdd.isEmpty()) {
					tileWCsToAdd.put(wire, wcToAdd);
				}
			}
			if (!tileWCsToAdd.isEmpty())
				wcsToAdd.put(tile, tileWCsToAdd);
		}
		return wcsToAdd;
	}

	private Map<Tile, Map<TileWireTemplate, Set<WireConnection<TileWireTemplate>>>> getWCsToRemove(
		boolean forward, Map<Tile, WireHashMap<TileWireTemplate>> wireMapMap
	) {
		Map<Tile, Map<TileWireTemplate, Set<WireConnection<TileWireTemplate>>>> wcsToRemove = new HashMap<>();

		// Traverse the entire device and find which wires to remove first
		for (Map.Entry<Tile, WireHashMap<TileWireTemplate>> e : wireMapMap.entrySet()) {
			Tile tile = e.getKey();
			WireHashMap<TileWireTemplate> wireHashMap = e.getValue();

			Map<TileWireTemplate, Set<WireConnection<TileWireTemplate>>> tileWCsToRemove = new HashMap<>();

			// Create a set of wires that can be driven by other wires within the tile
			// We need this to do a fast look up later on
			Set<TileWireTemplate> sourceWires = getSourceWiresOfTile(forward, tile, wireHashMap);

			// Identify any wire connections that are not a "source" wire to "sink" wire
			// connection.
			Set<Wire> wires = new HashSet<>(tile.getWires());

			for (Wire wire : wires) {
				TileWireTemplate wireEnum = ((TileWire) wire).getTemplate();
				Set<WireConnection<TileWireTemplate>> wcToRemove = new HashSet<>();
				ArraySet<WireConnection<TileWireTemplate>> wcs = wireHashMap.get(wireEnum);
				if (wcs != null) {
					for (WireConnection<TileWireTemplate> wc : wcs) {
						// never remove PIPs.  We only are searching for different names
						// of the same wire.  A PIP connect unique wires.
						if (wc.isPIP())
							continue;
						if (!sourceWires.contains(wireEnum) ||
							!wireIsSink(wireMapMap.get(wc.getTile(tile)), wc.getSinkWire(), forward)) {
							wcToRemove.add(wc);
						}
					}
				}
				tileWCsToRemove.put(wireEnum, wcToRemove);
			}
			wcsToRemove.put(tile, tileWCsToRemove);
		}
		return wcsToRemove;
	}

	private Set<TileWireTemplate> getSourceWiresOfTile(
		boolean forward, Tile tile, WireHashMap<TileWireTemplate> whm
	) {
		Set<TileWireTemplate> sourceWires = new HashSet<>();
		for (Wire wire : tile.getWires()) {
			TileWireTemplate wireTemplate = ((TileWire) wire).getTemplate();
			if ((forward && siteWireSourceSet.contains(wireTemplate)) ||
				(!forward && siteWireSinkSet.contains(wireTemplate))
			) {
				sourceWires.add(wireTemplate);
			}
			ArraySet<WireConnection<TileWireTemplate>> wcs = whm.get(wireTemplate);
			if (wcs != null) {
				for (WireConnection<TileWireTemplate> wc : wcs) {
					if (wc.isPIP()) {
						sourceWires.add(wc.getSinkWire());
					}
				}
			}
		}
		return sourceWires;
	}

	// A wire is a sink if it is a site source (really should check in the tile sinks but
	// the wire type check is easier and should be sufficient or the wire is the source of
	// a PIP.
	private boolean wireIsSink(WireHashMap<TileWireTemplate> whm, TileWireTemplate wire, boolean forward) {
		return (forward && siteWireSinkSet.contains(wire)) ||
			(!forward && siteWireSourceSet.contains(wire)) ||
			(whm.get(wire) != null && whm.get(wire).stream().anyMatch(k -> k.isPIP()));
	}

	/**
	 * Add the missing wire connection and remove the unnecessary wires in a single
	 * pass.  It's easier to just recreate the wire hash maps with the corrections.
	 */
	private void makeWireCorrections(
			Map<Tile, Map<TileWireTemplate, Set<WireConnection<TileWireTemplate>>>> wcsToAdd,
			Map<Tile, Map<TileWireTemplate, Set<WireConnection<TileWireTemplate>>>> wcsToRemove,
			Map<Tile, WireHashMap<TileWireTemplate>> wireMapMap
	) {
		HashPool<WireHashMap<TileWireTemplate>> tileWiresPool = new HashPool<>();
		HashPool<ArraySet<WireConnection<TileWireTemplate>>> wireArrayPool = new HashPool<>();

		for (Map.Entry<Tile, WireHashMap<TileWireTemplate>> e : wireMapMap.entrySet()) {
			Tile tile = e.getKey();
			WireHashMap<TileWireTemplate> oldWireHashMap = e.getValue();

			// create a safe wire map to modify
			WireHashMap<TileWireTemplate> wireHashMap = new WireHashMap<>();

			for (Wire wire : tile.getWires()) {
				TileWireTemplate wireTemplate = ((TileWire) wire).getTemplate();
				ArraySet<WireConnection<TileWireTemplate>> wcs = oldWireHashMap.get(wireTemplate);
				Set<WireConnection<TileWireTemplate>> wcsCopy =
					(wcs != null) ? new HashSet<>(wcs) : new HashSet<>();
				if (wcsToRemove.containsKey(tile) && wcsToRemove.get(tile).containsKey(wireTemplate))
					wcsCopy.removeAll(wcsToRemove.get(tile).get(wireTemplate));
				if (wcsToAdd.containsKey(tile) && wcsToAdd.get(tile).containsKey(wireTemplate))
					wcsCopy.addAll(wcsToAdd.get(tile).get(wireTemplate));

				if (wcsCopy.size() > 0) {
					wireHashMap.put(wireTemplate, new ArraySet<>(wcsCopy));
				}
			}

			// Update the tile with the new wire map.
			WireHashMap<TileWireTemplate> reduced = tileWiresPool.add(wireHashMap);
			if (wireHashMap == reduced) {
				for (TileWireTemplate k : reduced.keySet()) {
					ArraySet<WireConnection<TileWireTemplate>> v = reduced.get(k);
					ArraySet<WireConnection<TileWireTemplate>> rv = wireArrayPool.add(v);
					if (v != rv)
						wireHashMap.put(k, rv);
				}
			}
			e.setValue(wireHashMap);
		}
	}

	/**
	 * Remove duplicate wire resources in the tile.
	 */
	private WireHashMap<TileWireTemplate> removeDuplicateTileResources(WireHashMap<TileWireTemplate> orig) {
		WireHashMap<TileWireTemplate> retrieved = tileWireMapPool.add(orig);
		// if it's different, then another identical copy exist.  This copy should have already
		// been reduced.  Otherwise, reduce the sets of connections on the map.
		if (retrieved == orig) {
			for (TileWireTemplate key : retrieved.keySet()) {
				ArraySet<WireConnection<TileWireTemplate>> conns = retrieved.get(key);
				ArraySet<WireConnection<TileWireTemplate>> unique = tileConnSetPool.add(conns);

				// Again, if they're the same, it means this is unique,  reduce the size by trimming it
				if (conns == unique)
					conns.trimToSize();
				retrieved.put(key, unique);
			}
		}
		return retrieved;
	}

	private static String getIntrasiteWireName(
			SiteType type, String element, String pinName) {
		return "intrasite:" + type.name() + "/" + element + "." + pinName;
	}

	/**
	 * Parses the device info XML file for the specified device, and adds the information
	 * to the {@link Device} object that is being created. If no device info file can be found
	 * for the part, then a warning is printed to the console.
	 * 
	 * TODO: parse the clock pads and add them to the device file
	 * 
	 * @param device Device object created from the XDLRC parser
	 */
	public static boolean parseDeviceInfo(Device device) {
		Document deviceInfo = RSEnvironment.defaultEnv().loadDeviceInfo(device.getFamily(), device.getPartName());
		
		if (deviceInfo != null) {
			createPackagePins(device, deviceInfo);
			return true;
		}
		return false;
	}
	
	/**
	 * Creates a map from pad bel name -> corresponding package pin. This
	 * information is needed when generating Tincr Checkpoints from
	 * RS to be loaded into Vivado.
	 */
	private static void createPackagePins(Device device, Document deviceInfo) {
		Element pinMapRootEl = deviceInfo.getRootElement().getChild("package_pins");
		
		if (pinMapRootEl == null) {
			throw new Exceptions.ParseException("No package pin information found in device info file: " + deviceInfo.getBaseURI() + ".\n"
				+ "Either add the package pin mappings, or remove the device info file and regenerate.");
		}
		
		// Add the package pins to the device
		pinMapRootEl.getChildren("package_pin")
			.stream()
			.map(ppEl -> new PackagePin(ppEl.getChildText("name"), ppEl.getChildText("bel"), ppEl.getChild("is_clock") != null))
			.forEach(packagePin -> device.addPackagePin(packagePin));
			
		if (device.getPackagePins().isEmpty()) {
			throw new Exceptions.ParseException("No package pin information found in device info file: " + deviceInfo.getBaseURI() + ".\n"
					+ "Either add the package pin mappings, or remove the device info file and regenerate.");
		}
	}
	
	private final class FamilyTypeListener extends XDLRCParserListener {
		@Override
		protected void enterXdlResourceReport(pl_XdlResourceReport tokens) {
			FamilyType family = FamilyType.valueOf(tokens.family.toUpperCase());
			try {
				familyInfo = RSEnvironment.defaultEnv().loadFamilyInfo(family);
			} catch (IOException|JDOMException e) {
				throw new EnvironmentException("Failed to load family information file", e);
			}
			device.setFamily(family);
		}
	}

	private final class WireTemplateListener extends XDLRCParserListener {
		private static final int PIN_SET_CAPACITY = 10000;

		private final SortedSet<String> tileWireSet = new TreeSet<>();
		private final SortedMap<String, SiteType> siteWireMap = new TreeMap<>();
		private final Set<String> inpinSet = new HashSet<>(PIN_SET_CAPACITY);
		private final Set<String> outpinSet = new HashSet<>(PIN_SET_CAPACITY);

		private SiteType currType;
		private String currElement;

		/**
		 * Tracks special site pin wires.
		 */
		@Override
		protected void enterPinWire(pl_PinWire tokens) {
			String externalName = tokens.external_wire;
			
			if (tokens.direction.equals("input")) {
				inpinSet.add(externalName);
			} else {
				outpinSet.add(externalName);
			}
		}

		@Override
		protected void enterWire(pl_Wire tokens) {
			tileWireSet.add(tokens.name);
		}

		@Override
		protected void enterPip(pl_Pip tokens) {
			pipSources.add(tokens.start_wire);
			pipSinks.add(tokens.end_wire);
		}

		@Override
		protected void enterPrimitiveDef(pl_PrimitiveDef tokens) {
			currType = SiteType.valueOf(device.getFamily(), tokens.name);
		}

		@Override
		protected void exitPrimitiveDef(pl_PrimitiveDef tokens) {
			currType = null;
		}

		@Override
		protected void enterElement(pl_Element tokens) {
			currElement = tokens.name;
		}

		@Override
		protected void exitElement(pl_Element tokens) {
			currElement = null;
		}

		@Override
		protected void enterElementPin(pl_ElementPin tokens) {
			String wireName = getIntrasiteWireName(currType, currElement, tokens.name);
			siteWireMap.put(wireName, currType);
		}

		@Override
		protected void exitXdlResourceReport(pl_XdlResourceReport tokens) {
			Set<TileWireTemplate> sourceWireSetLocal = new HashSet<>(outpinSet.size());
			Set<TileWireTemplate> sinkWireSetLocal = new HashSet<>(inpinSet.size());

			int i = 0;
			for (String wire : tileWireSet) {
				TileWireTemplate template = new TileWireTemplate(wire, i++);
				tileWireTemplates.put(wire, template);

				if (inpinSet.contains(wire)) {
					sinkWireSetLocal.add(template);
				} 
				if (outpinSet.contains(wire)) {
					sourceWireSetLocal.add(template);
				} 
			}

			for (Map.Entry<String, SiteType> e : siteWireMap.entrySet()) {
				SiteWireTemplate template = new SiteWireTemplate(e.getKey(), e.getValue(), i++);
				siteWireTemplates.put(e.getKey(), template);
			}

			// create the global source and sinks wire set
			siteWireSourceSet = sourceWireSetLocal;
			siteWireSinkSet = sinkWireSetLocal;
			numUniqueWireTypes = i;
		}
	}

	private final class TileAndSiteGeneratorListener extends XDLRCParserListener {
		private ArrayList<Site> tileSites;
		private Tile currTile;

		@Override
		protected void enterXdlResourceReport(pl_XdlResourceReport tokens) {
			device.setPartName(PartNameTools.removeSpeedGrade(tokens.part));
		}

		@Override
		protected void enterTiles(pl_Tiles tokens) {
			int rows = tokens.rows;
			int columns = tokens.columns;

			device.createTileArray(rows, columns);
		}

		@Override
		protected void enterTile(pl_Tile tokens) {
			int row = tokens.row;
			int col = tokens.column;
			currTile = device.getTile(row, col);
			currTile.setName(tokens.name);
			currTile.setType(TileType.valueOf(device.getFamily(), tokens.type));

			tileSites = new ArrayList<>();
		}

		@Override
		protected void enterPrimitiveSite(pl_PrimitiveSite tokens) {
			Site site = new Site();
			site.setTile(currTile);
			site.setName(tokens.name);
			site.parseCoordinatesFromName(tokens.name);
			site.setIndex(tileSites.size());
			site.setBondedType(BondedType.valueOf(tokens.bonded.toUpperCase()));

			ArrayList<SiteType> alternatives = new ArrayList<>();
			SiteType type = SiteType.valueOf(device.getFamily(), tokens.type);
			alternatives.add(type);

			Element ptEl = getSiteTypeEl(type);
			Element alternativesEl = ptEl.getChild("alternatives");
			if (alternativesEl != null) {
				FamilyType family = device.getFamily();
				alternatives.addAll(alternativesEl.getChildren("alternative").stream()
						.map(alternativeEl -> SiteType.valueOf(family, alternativeEl.getChildText("name")))
						.collect(Collectors.toList()));
			}

			alternatives.trimToSize();
			site.setPossibleTypes(alternativeTypesPool.add(alternatives));

			tileSites.add(site);
		}

		@Override
		protected void exitTile(pl_Tile tokens) {
			// Create an array of sites (more compact than ArrayList)
			if (tileSites.size() > 0) {
				currTile.setSites(tileSites.toArray(new Site[tileSites.size()]));
			} else {
				currTile.setSites(null);
			}

			currTile = null;
			tileSites = null;
		}
	}

	private final class WireConnectionGeneratorListener extends XDLRCParserListener {
		private Tile currTile;
		private TileWireTemplate currTileWire;
		private boolean currTileWireIsSource;
		private WireHashMap<TileWireTemplate> wireHashMap;
		private TileWireTemplate pipStartWire, pipEndWire;

		private HashMap<Tile, WireHashMap<TileWireTemplate>> wireMapsMap;

		public WireConnectionGeneratorListener(HashMap<Tile, WireHashMap<TileWireTemplate>> wireMapsMap) {
			this.wireMapsMap = wireMapsMap;
		}

		@Override
		protected void enterTile(pl_Tile tokens) {
			int row = tokens.row;
			int col = tokens.column;
			currTile = device.getTile(row, col);
			wireHashMap = new WireHashMap<>();
		}

		@Override
		protected void exitTile(pl_Tile tokens) {
			WireHashMap<TileWireTemplate> reduced = removeDuplicateTileResources(wireHashMap);
			wireMapsMap.put(currTile, reduced);
			currTile = null;
		}

		@Override
		protected void enterWire(pl_Wire tokens) {
			String wireName = tokens.name;
			currTileWire = tileWireTemplates.get(wireName);
			currTileWireIsSource = siteWireSourceSet.contains(currTileWire) || pipSinks.contains(wireName);
		}

		@Override
		protected void exitWire(pl_Wire tokens) {
			currTileWire = null;
		}

		@Override
		protected void enterConn(pl_Conn tokens) {
			String currWireName = tokens.name;
			TileWireTemplate currWire = tileWireTemplates.get(currWireName);
			boolean currWireIsSiteSink = siteWireSinkSet.contains(currWire);
			boolean currWireIsPIPSource = pipSources.contains(currWireName);
			boolean currWireIsSink = currWireIsSiteSink || currWireIsPIPSource;
			if (currTileWireIsSource || currWireIsSink) {
				Tile t = device.getTile(tokens.tile);
				WireConnection<TileWireTemplate> wc = new WireConnection<>(currWire,
						currTile.getRow() - t.getRow(),
						currTile.getColumn() - t.getColumn(),
						false);
				wireHashMap.computeIfAbsent(currTileWire, k -> new ArraySet<>()).add(wc);
			}
		}

		@Override
		protected void enterPip(pl_Pip tokens) {
			String endWireName;
			WireConnection<TileWireTemplate> wc;

			TileWireTemplate startWire = tileWireTemplates.get(tokens.start_wire);
			endWireName = tokens.end_wire;
			TileWireTemplate endWire = tileWireTemplates.get(endWireName);
			assert endWire != null;
			wc = tileConnPool.add(new WireConnection<>(endWire, 0, 0, true));
			wireHashMap.computeIfAbsent(startWire, k -> new ArraySet<>()).add(wc);

			pipStartWire = startWire;
			pipEndWire = endWire;
		}

		@Override
		protected void exitPip(pl_Pip tokens) {
			pipStartWire = null;
			pipEndWire = null;
		}

		@Override
		protected void enterRoutethrough(pl_Routethrough tokens) {
			SiteType type = SiteType.valueOf(device.getFamily(), tokens.site_type);

			String[] parts = tokens.pins.split("-");
			String inPin = parts[1];
			String outPin = parts[2];

			PIPRouteThrough currRouteThrough = new PIPRouteThrough(type, inPin, outPin);
			currRouteThrough = routeThroughPool.add(currRouteThrough);
			device.addRouteThrough(pipStartWire, pipEndWire, currRouteThrough);
		}
	}

	private final class ReverseWireConnectionGeneratorListener extends XDLRCParserListener {
		private Tile currTile;
		private TileWireTemplate currTileWire;
		private boolean currTileWireIsSink;
		private WireHashMap<TileWireTemplate> wireHashMap;
		private HashMap<Tile, WireHashMap<TileWireTemplate>> wireMapMap;

		public ReverseWireConnectionGeneratorListener(HashMap<Tile, WireHashMap<TileWireTemplate>> wireMapMap) {
			this.wireMapMap = wireMapMap;
		}

		@Override
		protected void enterTile(pl_Tile tokens) {
			int row = tokens.row;
			int col = tokens.column;
			currTile = device.getTile(row, col);
			wireHashMap = new WireHashMap<>();
		}

		@Override
		protected void exitTile(pl_Tile tokens) {
			WireHashMap<TileWireTemplate> reduced = removeDuplicateTileResources(wireHashMap);
			wireMapMap.put(currTile, reduced);
			currTile = null;
		}

		@Override
		protected void enterWire(pl_Wire tokens) {
			String wireName = tokens.name;
			currTileWire = tileWireTemplates.get(wireName);
			currTileWireIsSink = siteWireSinkSet.contains(currTileWire) || pipSources.contains(wireName);
		}

		@Override
		protected void exitWire(pl_Wire tokens) {
			currTileWire = null;
		}

		@Override
		protected void enterConn(pl_Conn tokens) {
			String currWireName = tokens.name;
			TileWireTemplate currWire = tileWireTemplates.get(currWireName);
			boolean currWireIsSiteSource = siteWireSourceSet.contains(currWire);
			boolean currWireIsPIPSink = pipSinks.contains(currWireName);
			boolean currWireIsSource = currWireIsSiteSource || currWireIsPIPSink;
			if (currTileWireIsSink || currWireIsSource) {
				Tile t = device.getTile(tokens.tile);
				WireConnection<TileWireTemplate> wc = new WireConnection<>(currWire,
					currTile.getRow() - t.getRow(),
					currTile.getColumn() - t.getColumn(),
					false);
				wireHashMap.computeIfAbsent(currTileWire, k -> new ArraySet<>()).add(wc);
			}
		}

		@Override
		protected void enterPip(pl_Pip tokens) {
			String endWireName;
			WireConnection<TileWireTemplate> wc;

			TileWireTemplate startWire = tileWireTemplates.get(tokens.start_wire);
			endWireName = tokens.end_wire;
			TileWireTemplate endWire = tileWireTemplates.get(endWireName);
			assert endWire != null;
			wc = tileConnPool.add(new WireConnection<>(startWire, 0, 0, true));
			wireHashMap.computeIfAbsent(endWire, k -> new ArraySet<>()).add(wc);
		}
	}

	private final class SourceAndSinkListener extends XDLRCParserListener {
		private Site currSite;
		private Set<TileWireTemplate> tileSources;
		private Set<TileWireTemplate> tileSinks;
		private Map<String, TileWireTemplate> externalPinWires;

		@Override
		protected void enterTile(pl_Tile tokens) {
			tileSources = new HashSet<>();
			tileSinks = new HashSet<>();
		}

		@Override
		protected void exitTile(pl_Tile tokens) {
			tileSources = null;
			tileSinks = null;
		}

		@Override
		protected void enterPrimitiveSite(pl_PrimitiveSite tokens) {
			currSite = device.getSite(tokens.name);
			externalPinWires = new HashMap<>();
		}

		@Override
		protected void enterPinWire(pl_PinWire tokens) {
			String name = tokens.name;
			PinDirection direction =
					tokens.direction.equals("input") ? PinDirection.IN : PinDirection.OUT;
			String externalWireName = tokens.external_wire;
			TileWireTemplate externalWire = tileWireTemplates.get(externalWireName);
			externalPinWires.put(name, externalWire);

			if (direction == PinDirection.IN) {
				tileSinks.add(externalWire);
			} else {
				tileSources.add(externalWire);
			}
		}

		@Override
		protected void exitPrimitiveSite(pl_PrimitiveSite tokens) {
			Map<SiteType, Map<String, TileWireTemplate>> externalPinWiresMap =
					new HashMap<>();
			externalPinWiresMap.put(currSite.getPossibleTypes().get(0), externalWiresPool.add(externalPinWires));

			List<SiteType> alternativeTypes = currSite.getPossibleTypes();
			for (int i = 1; i < alternativeTypes.size(); i++) {
				Map<String, TileWireTemplate> altExternalPinWires = new HashMap<>();
				SiteType altType = alternativeTypes.get(i);
				SiteTemplate site = device.getSiteTemplate(altType);
				for (String sitePin : site.getSources().keySet()) {
					TileWireTemplate wire = getExternalWireForSitePin(altType, sitePin);
					altExternalPinWires.put(sitePin, wire);
				}
				for (String sitePin : site.getSinks().keySet()) {
					TileWireTemplate wire = getExternalWireForSitePin(altType, sitePin);
					if (wire == null)
						System.out.println("There be an error here");
					altExternalPinWires.put(sitePin, wire);
				}

				externalPinWiresMap.put(altType, externalWiresPool.add(altExternalPinWires));
			}


			externalPinWiresMap = externalWiresMapPool.add(externalPinWiresMap);
			currSite.setExternalWires(externalPinWiresMap);

			externalPinWires = null;
			currSite = null;
		}

		private TileWireTemplate getExternalWireForSitePin(SiteType altType, String sitePin) {
			Element pinEl = getPinmapElement(altType, sitePin);

			String connectedPin = sitePin;
			if (pinEl != null) {
				connectedPin = pinEl.getChildText("map");
			}

			return externalPinWires.get(connectedPin);
		}

		private Element getPinmapElement(SiteType altType, String sitePin) {
			Element ptEl = getSiteTypeEl(currSite.getPossibleTypes().get(0));
			Element alternativesEl = ptEl.getChild("alternatives");
			Element altEl = null;
			for (Element altTmpEl : alternativesEl.getChildren("alternative")) {
				if (altTmpEl.getChildText("name").equals(altType.name())) {
					altEl = altTmpEl;
					break;
				}
			}

			assert altEl != null;
			Element pinmapsEl = altEl.getChild("pinmaps");
			Element pinEl = null;
			if (pinmapsEl != null) {
				for (Element pinTmpEl : pinmapsEl.getChildren("pin")) {
					if (pinTmpEl.getChildText("name").equals(sitePin)) {
						pinEl = pinTmpEl;
						break;
					}
				}
			}
			return pinEl;
		}
	}

	private final class TileWireListener extends XDLRCParserListener {
		private Tile currTile;
		private HashMap<Set<TileWireTemplate>, Map<String, TileWireTemplate>> tileWiresPool;
		private HashSet<TileWireTemplate> tileWires;

		@Override
		protected void enterTiles(pl_Tiles tokens) {
			tileWiresPool = new HashMap<>(4096);
		}

		@Override
		protected void enterTile(pl_Tile tokens) {
			int row = tokens.row;
			int col = tokens.column;
			currTile = device.getTile(row, col);
			tileWires = new HashSet<>();
		}

		@Override
		protected void enterWire(pl_Wire tokens) {
			tileWires.add(tileWireTemplates.get(tokens.name));
		}

		@Override
		protected void exitTile(pl_Tile tokens) {
			currTile.setTileWires(tileWiresPool.computeIfAbsent(tileWires, k ->  {
				if (k.isEmpty())
					return Collections.emptyMap();
				else
					return k.stream().collect(Collectors.toMap(t -> t.getName(), t -> t));
				}));

			currTile = null;
			tileWires = null;
		}

		@Override
		protected void exitTiles(pl_Tiles tokens) {
			tileWiresPool = null;
		}
	}

	private class PrimitiveDefsListener extends XDLRCParserListener {
		private final PrimitiveDefList defs;
		private PrimitiveDef currDef;
		private PrimitiveElement currElement;
		private ArrayList<PrimitiveDefPin> pins;
		private ArrayList<PrimitiveElement> elements;

		PrimitiveDefsListener() {
			defs = new PrimitiveDefList();
			device.setPrimitiveDefs(defs);
		}

		@Override
		protected void enterPrimitiveDef(pl_PrimitiveDef tokens) {
			currDef = new PrimitiveDef();
			String name = tokens.name.toUpperCase();
			currDef.setType(SiteType.valueOf(device.getFamily(), name));

			pins = new ArrayList<>(tokens.pin_count);
			elements = new ArrayList<>(tokens.element_count);
		}

		@Override
		protected void exitPrimitiveDef(pl_PrimitiveDef tokens) {
			pins.trimToSize();
			elements.trimToSize();

			currDef.setPins(pins);
			currDef.setElements(elements);
			defs.add(currDef);
		}

		@Override
		protected void enterPin(pl_Pin tokens) {
			PrimitiveDefPin p = new PrimitiveDefPin();
			p.setExternalName(tokens.external_name);
			p.setInternalName(tokens.internal_name);
			p.setDirection(tokens.direction.startsWith("output") ? PinDirection.OUT : PinDirection.IN);
			pins.add(p);
		}

		@Override
		protected void enterElement(pl_Element tokens) {
			currElement = new PrimitiveElement();
			currElement.setName(tokens.name);
			currElement.setBel(tokens.isBel);
		}

		@Override
		protected void exitElement(pl_Element tokens) {
			// Determine the element type
			if (!currElement.isBel()) {
				if (currElement.getCfgOptions() != null && !currElement.getPins().isEmpty())
					currElement.setMux(true);
				else if (currElement.getCfgOptions() != null) // && currElement.getPins() == null
					currElement.setConfiguration(true);
				else if (currElement.getName().startsWith("_ROUTETHROUGH"))
					currElement.setRouteThrough(true);
				else
					currElement.setPin(true);
			}
			elements.add(currElement);
		}

		@Override
		protected void enterElementPin(pl_ElementPin tokens) {
			PrimitiveDefPin elementPin = new PrimitiveDefPin();
			elementPin.setExternalName(tokens.name);
			elementPin.setInternalName(tokens.name);
			elementPin.setDirection(tokens.direction.startsWith("output") ? PinDirection.OUT : PinDirection.IN);
			currElement.addPin(elementPin);
		}

		@Override
		protected void enterElementCfg(pl_ElementCfg tokens) {
			for(String cfg : tokens.cfgs){
				currElement.addCfgOption(cfg);
			}
		}

		@Override
		protected void enterElementConn(pl_ElementConn tokens) {
			PrimitiveConnection c = new PrimitiveConnection();
			c.setElement0(tokens.element0);
			c.setPin0(tokens.pin0);
			c.setForwardConnection(tokens.direction.equals("==>"));
			c.setElement1(tokens.element1);
			c.setPin1(tokens.pin1);
			currElement.addConnection(c);
		}
	}
}
