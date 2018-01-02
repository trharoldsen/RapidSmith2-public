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

package edu.byu.ece.rapidSmith.device.creation

import edu.byu.ece.rapidSmith.RSEnvironment
import edu.byu.ece.rapidSmith.device.*
import edu.byu.ece.rapidSmith.primitiveDefs.*
import edu.byu.ece.rapidSmith.util.ArraySet
import edu.byu.ece.rapidSmith.util.Exceptions
import edu.byu.ece.rapidSmith.util.HashPool
import edu.byu.ece.rapidSmith.util.PartNameTools
import org.jdom2.Document
import org.jdom2.Element
import org.jdom2.JDOMException

import java.io.IOException
import java.nio.file.Path
import java.util.*
import java.util.stream.Collectors

import edu.byu.ece.rapidSmith.util.Exceptions.EnvironmentException
import edu.byu.ece.rapidSmith.util.Exceptions.FileFormatException

typealias SiteTypeName = String
typealias SiteName = String
typealias WireName = String

/**
 * Generates a new device through parsing the device's XDLRC representation.
 *
 *
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
class DeviceGenerator {
	private var device: Device? = null
	private var familyInfo: Document? = null

	private var tileWireTemplates: HashMap<String, TileWireTemplate>? = null
	private var siteWireTemplates: HashMap<String, SiteWireTemplate>? = null
	private val nodesMap: HashMap<TileWire, TileNodeTemplate>? = null
	private var numUniqueWireTypes: Int = 0

	/** Keeps track of each unique object in the device  */
	private var tileConnPool: HashPool<TileNodeConnection>? = null
	private var tileConnSetPool: HashPool<ArraySet<TileNodeConnection>>? = null

	private var routeThroughPool: HashPool<PIPRouteThrough>? = null
	private var externalWiresPool: HashPool<Map<String, TileWireTemplate>>? = null
	private var externalWiresMapPool: HashPool<Map<SiteType, Map<String, TileWireTemplate>>>? = null
	private var alternativeTypesPool: HashPool<ArrayList<SiteType>>? = null

	private var siteWireSourceSet: Set<TileWireTemplate>? = null
	private var siteWireSinkSet: Set<TileWireTemplate>? = null

	/**
	 * Generates and returns the Device created from the XDLRC at the specified
	 * path.
	 *
	 * @param xdlrcPath path to the XDLRC file for the device
	 * @return the generated Device representation
	 */
	@Throws(IOException::class)
	fun generate(xdlrcPath: Path): Device {
		println("Generating device for file " + xdlrcPath.fileName)

		this.device = Device()

		this.tileConnPool = HashPool()
		this.tileConnSetPool = HashPool()

		this.routeThroughPool = HashPool()
		this.externalWiresPool = HashPool()
		this.externalWiresMapPool = HashPool()
		this.alternativeTypesPool = HashPool()

		this.tileWireTemplates = HashMap(50000)
		this.siteWireTemplates = HashMap(30000)
		this.numUniqueWireTypes = 0

		// Requires a two part iteration, the first to obtain the tiles and sites,
		// and the second to gather the wires.  Two parses are required since the
		// wires need to know the source and sink tiles.
		val parser = XDLRCParser()
		println("Starting first pass")
		parser.registerListener(FamilyAndNameListener())
		parser.registerListener(TileWireTemplateListener())
		parser.registerListener(SiteWireTemplateListener())
		parser.registerListener(TileAndSiteGeneratorListener())
		val primitiveDefsListener = PrimitiveDefsListener()
		parser.registerListener(primitiveDefsListener)
		parser.registerListener(XDLRCParseProgressListener())
		try {
			parser.parse(xdlrcPath)
		} catch (e: IOException) {
			throw IOException("Error handling file " + xdlrcPath, e)
		}

		parser.clearListeners()

		device!!.constructTileMap()
		PrimitiveDefsCorrector.makeCorrections(primitiveDefsListener.defs, familyInfo)
		device!!.setSiteTemplates(createSiteTemplates(primitiveDefsListener.defs))

		println("Starting second pass")
		val forwardWireMaps = HashMap<K, V>()
		val reverseWireMaps = HashMap<K, V>()
		parser.registerListener(NodeConnectionListener(forwardWireMaps))
		parser.registerListener(ReverseWireConnectionGeneratorListener(reverseWireMaps))
		parser.registerListener(SourceAndSinkListener())
		parser.registerListener(TileWireListener())
		parser.registerListener(XDLRCParseProgressListener())
		try {
			parser.parse(xdlrcPath)
		} catch (e: IOException) {
			throw IOException("Error handling file " + xdlrcPath, e)
		}

		val wcsToAdd = getWCsToAdd(true, forwardWireMaps)
		val wcsToRemove = getWCsToRemove(true, forwardWireMaps)
		val rwcsToAdd = getWCsToAdd(false, reverseWireMaps)
		val rwcsToRemove = getWCsToRemove(false, reverseWireMaps)

		// These take up a lot of memory and we're going to regenerate each of these in the
		// next step.  Clearing these will allow for better garbage collection
		tileConnPool = HashPool()
		tileConnSetPool = HashPool()
		tileWireMapPool = HashPool<Any>()

		println("Parsing Device Info file")
		if (!parseDeviceInfo(device!!)) {
			System.err.println("[Warning]: The device info file for the part " + device!!.partName + " cannot be found.")
		}

		makeWireCorrections(wcsToAdd, wcsToRemove, forwardWireMaps)
		makeWireCorrections(rwcsToAdd, rwcsToRemove, reverseWireMaps)
		forwardWireMaps.forEach { k, v -> k.setWireHashMap(v) }
		reverseWireMaps.forEach { k, v -> k.setReverseWireConnections(v) }

		device!!.numUniqueWireTypes = numUniqueWireTypes
		device!!.constructDependentResources()

		println("Finishing device creation process")

		return device
	}

	/**
	 * Creates the templates for the primitive sites with information from the
	 * primitive defs and device information file.
	 */
	private fun createSiteTemplates(defs: PrimitiveDefList): List<SiteTemplate> {
		val siteTemplates = HashMap<K, V>()
		val family = device!!.family

		// Create a template for each primitive type
		for (def in defs) {
			val ptEl = getSiteTypeEl(def.type)

			val template = SiteTemplate()
			template.type = def.type
			template.belTemplates = createBelTemplates(def, ptEl)
			createAndSetIntrasiteRouting(def, template, ptEl)
			createAndSetSitePins(def, template)

			val compatTypesEl = ptEl.getChild("compatible_types")
			if (compatTypesEl != null) {
				val compatTypes = compatTypesEl.getChildren("compatible_type").stream()
					.map { compatTypeEl -> SiteType.valueOf(family, compatTypeEl.text) }
					.collect<ArraySet<SiteType>, Any>(Collectors.toCollection(Supplier<ArraySet<SiteType>> { ArraySet() }))
				compatTypes.trimToSize()
				template.setCompatibleTypes(compatTypes)
			}

			template.setReverseWireConnections(getReverseMapForSite(template))

			siteTemplates.add(template)
		}

		return siteTemplates
	}

	private fun getReverseMapForSite(site: SiteTemplate): WireHashMap<SiteWireTemplate> {
		val reverseMap = HashMap<SiteWireTemplate, ArraySet<WireConnection<SiteWireTemplate>>>()
		for (srcWire in site.siteWires.values) {
			val wcs = site.getWireConnections(srcWire)
			if (wcs != null) {
				for (c in wcs!!) {
					val reverse = WireConnection(
						srcWire, -c.getRowOffset(),
						-c.getColumnOffset(), c.isPIP())
					(reverseMap as java.util.Map<SiteWireTemplate, ArraySet<WireConnection<SiteWireTemplate>>>).computeIfAbsent(c.getSinkWire()) { k -> ArraySet<T>() }.add(reverse)
				}
			}
		}

		val wireHashMap = WireHashMap()
		for ((key, v) in reverseMap) {
			v.trimToSize()
			wireHashMap.put(key, v)
		}

		return wireHashMap
	}

	/**
	 * Creates the templates for each BEL in the primitive site
	 *
	 * @param def       The primitive def to process
	 * @param ptElement XML element detailing the primitive type
	 * @return The templates for each BEL in the primitive type
	 */
	private fun createBelTemplates(def: PrimitiveDef, ptElement: Element): List<BelTemplate> {
		val templates = ArrayList<BelTemplate>()

		// for each BEL element
		for (el in def.elements) {
			if (!el.isBel)
				continue

			val id = BelId(def.type, el.name)
			// Set the BEL type as defined in the deviceinfo file
			val belType = getTypeOfBel(el.name, ptElement)

			val template = BelTemplate(id, belType)

			// Create the BEL pin templates
			val pinTemplates = ArrayList<BelPinTemplate>()
			for (pin in el.pins) {
				val belPin = BelPinTemplate(id, pin.internalName)
				belPin.direction = pin.direction
				val wireName = getIntrasiteWireName(def.type, el.name, belPin.name)
				belPin.wire = siteWireTemplates!![wireName]
				pinTemplates.add(belPin)
			}
			template.pinTemplates = pinTemplates
			templates.add(template)
		}

		// Find the site pins that connect to each BEL pin by traversing the routing.
		// This info is useful for directing which site pin should be targeted while
		// routing to reach the correct BEL pin.
		for (pin in def.pins) {
			val el = def.getElement(pin.internalName)
			val forward = !pin.isOutput // traverse forward or backward?
			findAndSetSitePins(templates, def, forward, pin.externalName, el!!)
		}

		return templates
	}

	/**
	 * Recursively traverses through the elements to find all BEL pins reachable from the site pin.
	 *
	 * @param templates the BEL templates in the primitive type
	 * @param def       the primitive def for the current type
	 * @param forward   traverse forward or backward (forward for site sinks and
	 * backward for site sources)
	 * @param sitePin   Site pin we're searching from
	 * @param element   The current element we're looking at
	 */
	private fun findAndSetSitePins(templates: List<BelTemplate>, def: PrimitiveDef,
	                               forward: Boolean, sitePin: String, element: PrimitiveElement) {

		// follow each connection from the element
		for (c in element.connections) {
			val destElement: PrimitiveElement?

			// This connection goes the opposite of the way we want to search
			if (forward != c.isForwardConnection)
				continue

			destElement = def.getElement(c.element1)

			if (destElement!!.isMux) {
				// This is a routing mux.  Follow it.
				findAndSetSitePins(templates, def, forward, sitePin, destElement)
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
	private fun getTypeOfBel(belName: String, ptElement: Element): String? {
		for (belEl in ptElement.getChild("bels").getChildren("bel")) {
			if (belEl.getChildText("name") == belName)
				return belEl.getChildText("type")
		}
		assert(false) { "No type found for the specified BEL " + belName + " " + ptElement.getChildText("name") }
		return null
	}

	/**
	 * Creates the wire connections connecting the BELs and muxes in the primitive type.
	 * @param def      the primitive def for the current type
	 * @param template the template for the current type
	 */
	private fun createAndSetIntrasiteRouting(
		def: PrimitiveDef, template: SiteTemplate, siteElement: Element
	) {
		val wireMap = WireHashMap()
		val siteWires = HashMap<String, SiteWireTemplate>(1000)

		/*
		    We build the routing structure by find all of the wire sources and
		    creating a wire connection between it and its sinks.  For muxes, we
		    additionally create a wire connection from each input of the mux to
		    the output.
		 */
		for (el in def.elements) {
			val elName = el.name
			if (el.isPin && !def.getPin(elName)!!.isOutput) { // input site pin
				addWireConnectionsForElement(def, el, wireMap, siteWires)
			} else if (el.isBel) {
				addWireConnectionsForElement(def, el, wireMap, siteWires)
			} else if (el.isMux) {
				addWireConnectionsForElement(def, el, wireMap, siteWires)
				createAndAddMuxPips(def, el, wireMap, siteWires)
			}
		}

		val belRoutethroughMap = createBelRoutethroughs(template, siteElement, wireMap, siteWires)

		// reduce memory footprint of routing
		wireMap.values().forEach { s -> s.trimToSize() }

		// update the templates with the created structures
		template.setBelRoutethroughs(belRoutethroughMap)
		template.setRouting(wireMap)
		template.siteWires = siteWires
	}

	/**
	 * Creates a BEL routethrough map for the site template.
	 * @param template Site Template to generate routethroughs for
	 * @param siteElement XML document element of the site in the familyinfo.xml file
	 * @param wireMap WireHashMap of the site template
	 * @param siteWires wires in this site
	 * @return A Map of BEL routethroughs
	 */
	private fun createBelRoutethroughs(
		template: SiteTemplate, siteElement: Element,
		wireMap: WireHashMap<SiteWireTemplate>,
		siteWires: MutableMap<String, SiteWireTemplate>
	): Map<SiteWireTemplate, Set<SiteWireTemplate>>? {

		val belRoutethroughMap = HashMap<SiteWireTemplate, Set<SiteWireTemplate>>()

		for (belEl in siteElement.getChild("bels").getChildren("bel")) {
			val belName = belEl.getChildText("name")

			val routethroughs = belEl.getChild("routethroughs")

			// bel has routethroughs
			if (routethroughs != null) {

				for (routethrough in routethroughs.getChildren("routethrough")) {

					val inputPin = routethrough.getChildText("input")
					val outputPin = routethrough.getChildText("output")

					val inputWireName = getIntrasiteWireName(template.type, belName, inputPin)
					val outputWireName = getIntrasiteWireName(template.type, belName, outputPin)

					val startTemplate = siteWireTemplates!![inputWireName]
					val endTemplate = siteWireTemplates!![outputWireName]
					siteWires.put(inputWireName, startTemplate)
					siteWires.put(outputWireName, endTemplate)

					// If the wire names for the routethrough do not exist, throw a parse exception telling the user
					if (startTemplate == null) {
						throw Exceptions.ParseException(String.format("Cannot find intrasite wire \"%s\" for bel routethrough \"%s:%s:%s\". " + "Check the familyInfo.xml file for this routethrough and make sure the connections are correct.",
							inputWireName, template.type, inputPin, outputPin))
					} else if (endTemplate == null) {
						throw Exceptions.ParseException(String.format("Cannot find intrasite wire \"%s\" for bel routethrough \"%s:%s:%s\". " + "Check the familyInfo.xml file for this routethrough and make sure the connections are correct.",
							outputWireName, template.type, inputPin, outputPin))
					}

					// add the routethrough to the routethrough map;
					val sinkWires = (belRoutethroughMap as java.util.Map<SiteWireTemplate, Set<SiteWireTemplate>>).computeIfAbsent(
						startTemplate) { k -> HashSet() }
					sinkWires.add(endTemplate)
				}
			}
		}

		// create a new wire connection for each routethrough and adds them to the wire map
		for (startWire in belRoutethroughMap.keys) {

			val wireConnections = belRoutethroughMap[startWire].stream()
				.map<R> { sink -> WireConnection(sink, 0, 0, true) }
				.collect<R, A>(Collectors.toCollection<Any, Collection<Any>>(Supplier<Collection<Any>> { ArraySet() }))
			wireMap.put(startWire, wireConnections)
		}

		// return null if the belRoutethroughMap is empty
		return if (belRoutethroughMap.isEmpty()) null else belRoutethroughMap
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
	private fun createAndAddMuxPips(
		def: PrimitiveDef, el: PrimitiveElement,
		wireMap: WireHashMap<SiteWireTemplate>,
		siteWires: MutableMap<String, SiteWireTemplate>
	) {
		val elName = el.name
		val sinkName = getIntrasiteWireName(def.type, elName, getOutputPin(el))
		val sinkWire = siteWireTemplates!![sinkName]
		val wcs = ArraySet<T>(1)
		wcs.add(WireConnection(sinkWire, 0, 0, true))
		siteWires.put(sinkName, sinkWire)

		for (pin in el.pins) {
			if (pin.isOutput)
				continue
			val srcName = getIntrasiteWireName(def.type, elName, pin.internalName)
			val srcWire = siteWireTemplates!![srcName]
			wireMap.put(srcWire, wcs)
			siteWires.put(srcName, srcWire)
		}
	}

	/**
	 * Gets the wire connections for this element and adds them to the wire map
	 * @param def     the primitive def for the current type
	 * @param el      the current element from the primitive def
	 * @param wireMap the map of wire connections for the site template
	 * @param siteWires wires in the site
	 */
	private fun addWireConnectionsForElement(
		def: PrimitiveDef, el: PrimitiveElement, wireMap: WireHashMap<SiteWireTemplate>,
		siteWires: MutableMap<String, SiteWireTemplate>
	) {
		val wcsMap: Map<SiteWireTemplate, ArraySet<WireConnection<SiteWireTemplate>>>
		wcsMap = getWireConnectionsForElement(def, el)
		for ((template, value) in wcsMap) {
			wireMap.put(template, value)
			siteWires.put(template.name, template)

			for (wc in value) {
				val sink = wc.getSinkWire()
				siteWires.put(sink.getName(), sink)
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
	private fun getWireConnectionsForElement(
		def: PrimitiveDef, el: PrimitiveElement
	): Map<SiteWireTemplate, ArraySet<WireConnection<SiteWireTemplate>>> {
		val wcsMap = HashMap<SiteWireTemplate, ArraySet<WireConnection<SiteWireTemplate>>>()
		for (conn in el.connections) {
			// Only handle connections this element sources
			if (!conn.isForwardConnection)
				continue

			val source = getPinSource(def, conn)
			val sink = getPinSink(def, conn)
			val wcs = (wcsMap as java.util.Map<SiteWireTemplate, ArraySet<WireConnection<SiteWireTemplate>>>).computeIfAbsent(source) { k -> ArraySet<T>() }
			wcs.add(WireConnection(sink, 0, 0, false))
		}
		return wcsMap
	}

	private fun getPinSource(def: PrimitiveDef, conn: PrimitiveConnection): SiteWireTemplate {
		val element = conn.element0
		val pin = conn.pin0
		val wireName = getIntrasiteWireName(def.type, element, pin)
		return siteWireTemplates!![wireName]
	}

	private fun getPinSink(def: PrimitiveDef, conn: PrimitiveConnection): SiteWireTemplate {
		val element = conn.element1
		val pin = conn.pin1
		val wireName = getIntrasiteWireName(def.type, element, pin)
		return siteWireTemplates!![wireName]
	}

	/**
	 * Creates the site pin templates and adds them to the site template.
	 */
	private fun createAndSetSitePins(def: PrimitiveDef, siteTemplate: SiteTemplate) {
		val sources = HashMap<String, SitePinTemplate>()
		val sinks = HashMap<String, SitePinTemplate>()

		for (pin in def.pins) {
			val name = pin.internalName
			val template = SitePinTemplate(name, def.type)
			template.direction = pin.direction
			val wireName = getIntrasiteWireName(def.type, name, name)
			template.internalWire = siteWireTemplates!![wireName]
			if (pin.direction == PinDirection.IN)
				sinks.put(name, template)
			else
				sources.put(name, template)
		}

		siteTemplate.setSources(sources)
		siteTemplate.setSinks(sinks)
	}

	/**
	 * Add the missing wire connection and remove the unnecessary wires in a single
	 * pass.  It's easier to just recreate the wire hash maps with the corrections.
	 */
	private fun makeWireCorrections(
		wcsToAdd: Map<Tile, Map<TileWireTemplate, Set<WireConnection<TileWireTemplate>>>>,
		wcsToRemove: Map<Tile, Map<TileWireTemplate, Set<WireConnection<TileWireTemplate>>>>,
		wireMapMap: Map<Tile, WireHashMap<TileWireTemplate>>
	) {
		val tileWiresPool = HashPool<E>()
		val wireArrayPool = HashPool<ArraySet<WireConnection<TileWireTemplate>>>()

		for (e in wireMapMap.entries) {
			val tile = e.key
			val oldWireHashMap = e.value

			// create a safe wire map to modify
			val wireHashMap = WireHashMap()

			for (wire in tile.wires) {
				val wireTemplate = (wire as TileWire).template
				val wcs = oldWireHashMap.get(wireTemplate)
				val wcsCopy = if (wcs != null) HashSet(wcs!!) else HashSet<E>()
				if (wcsToRemove.containsKey(tile) && wcsToRemove[tile].containsKey(wireTemplate))
					wcsCopy.removeAll(wcsToRemove[tile][wireTemplate])
				if (wcsToAdd.containsKey(tile) && wcsToAdd[tile].containsKey(wireTemplate))
					wcsCopy.addAll(wcsToAdd[tile].get(wireTemplate))

				if (wcsCopy.size > 0) {
					wireHashMap.put(wireTemplate, ArraySet(wcsCopy))
				}
			}

			// Update the tile with the new wire map.
			val reduced = tileWiresPool.add(wireHashMap)
			if (wireHashMap === reduced) {
				for (k in reduced.keySet()) {
					val v = reduced.get(k)
					val rv = wireArrayPool.add(v)
					if (v !== rv)
						wireHashMap.put(k, rv)
				}
			}
			e.setValue(wireHashMap)
		}
	}

	/**
	 * Remove duplicate wire resources in the tile.
	 */
	private fun removeDuplicateTileResources(orig: WireHashMap<TileWireTemplate>?): WireHashMap<TileWireTemplate> {
		val retrieved = tileWireMapPool.add(orig)
		// if it's different, then another identical copy exist.  This copy should have already
		// been reduced.  Otherwise, reduce the sets of connections on the map.
		if (retrieved === orig) {
			for (key in retrieved.keySet()) {
				val conns = retrieved.get(key)
				val unique = tileConnSetPool!!.add(conns)

				// Again, if they're the same, it means this is unique,  reduce the size by trimming it
				if (conns === unique)
					conns.trimToSize()
				retrieved.put(key, unique)
			}
		}
		return retrieved
	}

	private class FamilyAndNameListener : XDLRCParserListener() {
		var family: FamilyType? = null
			private set
		var familyInfo: Document? = null
			private set
		var partname: String? = null
			private set

		override fun enterXdlResourceReport(tokens: XDLRCParserListener.pl_XdlResourceReport) {
			val family = FamilyType.valueOf(tokens.family.toUpperCase())
			this.family = family
			this.partname = PartNameTools.removeSpeedGrade(tokens.part)

			// try to load the family info xml
			try {
				familyInfo = RSEnvironment.defaultEnv().loadFamilyInfo(family)
			} catch (e: IOException) {
				throw EnvironmentException("Failed to load family information file", e)
			} catch (e: JDOMException) {
				throw EnvironmentException("Failed to load family information file", e)
			}
		}
	}

	private class TileWireTemplateListener : XDLRCParserListener() {
		var wireTemplates: Map<String, TileWireTemplate>? = null
			private set

		private val tileWireSet = TreeSet<String>()

		override fun enterWire(tokens: XDLRCParserListener.pl_Wire) {
			tileWireSet.add(tokens.name)
		}

		override fun exitXdlResourceReport(tokens: XDLRCParserListener.pl_XdlResourceReport) {
			this.wireTemplates = tileWireSet.withIndex()
				.associate { it.value to TileWireTemplate(it.value, it.index) }
		}
	}

	private inner class SiteWireTemplateListener : XDLRCParserListener() {
		var wireTemplates: Map<String, SiteWireTemplate>? = null
			private set

		private val siteWireMap = TreeMap<String, SiteType>()
		private var currType: SiteType? = null
		private var currElement: String? = null

		override fun enterPrimitiveDef(tokens: XDLRCParserListener.pl_PrimitiveDef) {
			currType = SiteType.valueOf(device!!.family, tokens.name)
		}

		override fun exitPrimitiveDef(tokens: XDLRCParserListener.pl_PrimitiveDef) {
			currType = null
		}

		override fun enterElement(tokens: XDLRCParserListener.pl_Element) {
			currElement = tokens.name
		}

		override fun exitElement(tokens: XDLRCParserListener.pl_Element) {
			currElement = null
		}

		override fun enterElementPin(tokens: XDLRCParserListener.pl_ElementPin) {
			val wireName = getIntrasiteWireName(currType!!, currElement!!, tokens.name)
			siteWireMap.put(wireName, currType!!)
		}

		override fun exitXdlResourceReport(tokens: XDLRCParserListener.pl_XdlResourceReport) {
			wireTemplates = siteWireMap.entries.withIndex()
				.associate { it.value.key to SiteWireTemplate(it.value.key, it.value.value, it.index) }
		}
	}

//	private inner class PinListener : XDLRCParserListener() {
//		private val inpinSet = HashSet<String>(PIN_SET_CAPACITY)
//		private val outpinSet = HashSet<String>(PIN_SET_CAPACITY)
//
//		private var currType: SiteType? = null
//		private var currElement: String? = null
//
//		/**
//		 * Tracks special site pin wires.
//		 */
//		override fun enterPinWire(tokens: XDLRCParserListener.pl_PinWire) {
//			val externalName = tokens.external_wire
//
//			if (tokens.direction == "input") {
//				inpinSet.add(externalName)
//			} else {
//				outpinSet.add(externalName)
//			}
//		}
//
//		override fun exitXdlResourceReport(tokens: XDLRCParserListener.pl_XdlResourceReport) {
//			val sourceWireSetLocal = HashSet<TileWireTemplate>(outpinSet.size)
//			val sinkWireSetLocal = HashSet<TileWireTemplate>(inpinSet.size)
//		}
//
//		companion object {
//			private val PIN_SET_CAPACITY = 10000
//		}
//	}

	private inner class TileAndSiteGeneratorListener : XDLRCParserListener() {
		var tileMatrix: Array<Array<Tile>>? = null
			private set

		private var tileSites: ArrayList<Site>? = null
		private var currTile: Tile? = null

		override fun enterTiles(tokens: XDLRCParserListener.pl_Tiles) {
			val rows = tokens.rows
			val columns = tokens.columns

			tileMatrix = Array(rows) { r -> Array(columns) { c -> Tile(r, c)} }
		}

		override fun enterTile(tokens: XDLRCParserListener.pl_Tile) {
			val currTile = tileMatrix!![tokens.row][tokens.column]
			currTile.name = tokens.name
			currTile.type = TileType.valueOf(device!!.family, tokens.type)

			this.currTile = currTile
			tileSites = ArrayList()
		}

		override fun enterPrimitiveSite(tokens: XDLRCParserListener.pl_PrimitiveSite) {
			val site = Site()
			site.tile = currTile
			site.name = tokens.name
			site.parseCoordinatesFromName()
			site.index = tileSites!!.size
			site.bondedType = BondedType.valueOf(tokens.bonded.toUpperCase())

			tileSites!!.add(site)
		}

		override fun exitTile(tokens: XDLRCParserListener.pl_Tile) {
			// Create an array of sites (more compact than ArrayList)
			val sites = tileSites!!
			if (sites.size > 0) {
				sites.trimToSize()
				currTile!!.sites = sites
			} else {
				currTile!!.sites = emptyList()
			}

			currTile = null
			tileSites = null
		}
	}

	private class SiteTypeListener(
		val templates: Map<SiteTypeName, SiteTemplate>,
		val sitesMap: Map<SiteName, Site>,
		val familyInfo: Document
	) : XDLRCParserListener() {
		private val alternativeTypesPool = HashPool<ArrayList<SiteTemplate>>()

		override fun enterPrimitiveSite(tokens: pl_PrimitiveSite) {
			val alternatives = ArrayList<SiteTemplate>()
			val defaultType = tokens.type
			val defaultTemplate = templates[defaultType]!!
			alternatives.add(defaultTemplate)

			val ptEl = getSiteTypeEl(familyInfo, defaultType)
			val alternativesEl = ptEl.getChild("alternatives")
			if (alternativesEl != null) {
				alternativesEl.getChildren("alternative")
					.mapTo(alternatives) { getAlternativeTemplate(it) }
			}

			alternatives.trimToSize()
			val pooled = alternativeTypesPool.add(alternatives)
			if (pooled !== alternatives)
				pooled.trimToSize()

			sitesMap[tokens.name]!!.setPossibleTypes(pooled)
		}

		private fun getAlternativeTemplate(it: Element): SiteTemplate {
			val childText = it.getChildText("name") ?:
				throw FileFormatException("family info xml missing element <name>")
			return templates[childText] ?:
				throw Exceptions.ParseException("Unrecognized site type: $")
		}
	}

	private class NodeListener(
		private val nodesMap: Array<Array<Map<WireName, TileNodeTemplate>>>
	) : XDLRCParserListener() {
		private var tileNodesMap: Map<WireName, TileNodeTemplate>? = null

		override fun enterTile(tokens: XDLRCParserListener.pl_Tile) {
			tileNodesMap = nodesMap[tokens.row][tokens.column]
		}

		override fun exitTile(tokens: XDLRCParserListener.pl_Tile) {
			tileNodesMap = null
		}

		override fun enterConn(tokens: XDLRCParserListener.pl_Conn) {
			val currWireName = tokens.name
			val currWire = tileWireTemplates!![currWireName]
			val currWireIsSiteSink = siteWireSinkSet!!.contains(currWire)
			val currWireIsPIPSource = pipSources.contains(currWireName)
			val currWireIsSink = currWireIsSiteSink || currWireIsPIPSource
			if (currTileWireIsSource || currWireIsSink) {
				val t = device!!.getTile(tokens.tile)
				val wc = WireConnection(currWire,
					currTile!!.row - t.row,
					currTile!!.column - t.column,
					false)
				wireHashMap!!.computeIfAbsent(currTileWire, { k -> ArraySet<T>() }).add(wc)
			}
		}

		override fun enterPip(tokens: XDLRCParserListener.pl_Pip) {
			val endWireName: String
			val wc: WireConnection<TileWireTemplate>

			val startWire = tileWireTemplates!![tokens.start_wire]
			endWireName = tokens.end_wire
			val endWire = tileWireTemplates!![endWireName]!!
			wc = tileConnPool!!.add(WireConnection(endWire, 0, 0, true))
			wireHashMap!!.computeIfAbsent(startWire, { k -> ArraySet<T>() }).add(wc)

			pipStartWire = startWire
			pipEndWire = endWire
		}

		override fun exitPip(tokens: XDLRCParserListener.pl_Pip) {
			pipStartWire = null
			pipEndWire = null
		}

		override fun enterRoutethrough(tokens: XDLRCParserListener.pl_Routethrough) {
			val type = SiteType.valueOf(device!!.family, tokens.site_type)

			val parts = tokens.pins.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
			val inPin = parts[1]
			val outPin = parts[2]

			var currRouteThrough = PIPRouteThrough(type, inPin, outPin)
			currRouteThrough = routeThroughPool!!.add(currRouteThrough)
//			device!!.addRouteThrough(pipStartWire, pipEndWire, currRouteThrough)
		}
	}

	private class NodeConnectionListener(
		private val nodesMap: Array<Array<Map<WireName, TileNodeTemplate>>>,
		private val wireTemplatesMap: Map<WireName, TileWireTemplate>,
		private val sitesMap: Map<SiteName, Site>,
		private val siteTemplateMap: Map<SiteType, SiteTemplate>
	) : XDLRCParserListener() {
		private val wireConnectionPool = HashPool<TileNodeConnection>()
		private val pinConnectionPool = HashPool<ExternalSitePinConnection>()
		private var tileNodesMap: Map<WireName, TileNodeTemplate>? = null
		private var currSite: Site? = null

		override fun enterTile(tokens: XDLRCParserListener.pl_Tile) {
			tileNodesMap = nodesMap[tokens.row][tokens.column]
		}

		override fun exitTile(tokens: XDLRCParserListener.pl_Tile) {
			tileNodesMap = null
		}

		override fun enterPrimitiveSite(tokens: pl_PrimitiveSite) {
			currSite = sitesMap[tokens.name]!!
		}

		override fun enterPinWire(tokens: pl_PinWire) {
			val nodeTemplate = tileNodesMap!![tokens.external_wire]!!
			for (siteType in currSite!!.possibleTypes) {
				// TODO get the pin name remapping
				val siteTemplate = siteTemplateMap[siteType]!!
				val pin = siteTemplate.pinNamesMap[tokens.name]!!
				val conn = ExternalSitePinConnection(siteType, currSite!!.index, pin.index)
				val pooled = pinConnectionPool.add(conn)

				if (pin.isInput) {
					nodeTemplate.sitePins.add(pooled)
				} else {
					nodeTemplate.rsitePins.add(pooled)
				}
			}
		}

		override fun enterPip(tokens: XDLRCParserListener.pl_Pip) {
			val startWire = wireTemplatesMap[tokens.start_wire]!!
			val endWire = wireTemplatesMap[tokens.end_wire]!!

			val conn = TileNodeConnection(startWire, endWire)
			val sourceNode = tileNodesMap!![tokens.start_wire]!!
			sourceNode.connections.add(wireConnectionPool.add(conn))

			val rconn = TileNodeConnection(endWire, startWire)
			val sinkNode = tileNodesMap!![tokens.end_wire]!!
			sinkNode.rconnections.add(wireConnectionPool.add(rconn))
		}
	}

	private inner class SourceAndSinkListener : XDLRCParserListener() {
		private var currSite: Site? = null
		private var tileSources: MutableSet<TileWireTemplate>? = null
		private var tileSinks: MutableSet<TileWireTemplate>? = null
		private var externalPinWires: MutableMap<String, TileWireTemplate>? = null

		override fun enterTile(tokens: XDLRCParserListener.pl_Tile) {
			tileSources = HashSet()
			tileSinks = HashSet()
		}

		override fun exitTile(tokens: XDLRCParserListener.pl_Tile) {
			tileSources = null
			tileSinks = null
		}

		override fun enterPrimitiveSite(tokens: XDLRCParserListener.pl_PrimitiveSite) {
			currSite = device!!.getSite(tokens.name)
			externalPinWires = HashMap()
		}

		override fun enterPinWire(tokens: XDLRCParserListener.pl_PinWire) {
			val name = tokens.name
			val direction = if (tokens.direction == "input") PinDirection.IN else PinDirection.OUT
			val externalWireName = tokens.external_wire
			val externalWire = tileWireTemplates!![externalWireName]
			externalPinWires!!.put(name, externalWire)

			if (direction == PinDirection.IN) {
				tileSinks!!.add(externalWire)
			} else {
				tileSources!!.add(externalWire)
			}
		}

		override fun exitPrimitiveSite(tokens: XDLRCParserListener.pl_PrimitiveSite) {
			var externalPinWiresMap: MutableMap<SiteType, Map<String, TileWireTemplate>> = HashMap()
			externalPinWiresMap.put(currSite!!.possibleTypes[0], externalWiresPool!!.add(externalPinWires))

			val alternativeTypes = currSite!!.possibleTypes
			for (i in 1 until alternativeTypes.size) {
				val altExternalPinWires = HashMap<String, TileWireTemplate>()
				val altType = alternativeTypes[i]
				val site = device!!.getTemplateOfSiteType(altType)
				for (sitePin in site.getSources().keySet()) {
					val wire = getExternalWireForSitePin(altType, sitePin)
					altExternalPinWires.put(sitePin, wire)
				}
				for (sitePin in site.getSinks().keySet()) {
					val wire = getExternalWireForSitePin(altType, sitePin)
					if (wire == null)
						println("There be an error here")
					altExternalPinWires.put(sitePin, wire)
				}

				externalPinWiresMap.put(altType, externalWiresPool!!.add(altExternalPinWires))
			}


			externalPinWiresMap = externalWiresMapPool!!.add(externalPinWiresMap)
			currSite!!.setExternalWires(externalPinWiresMap)

			externalPinWires = null
			currSite = null
		}

		private fun getExternalWireForSitePin(altType: SiteType, sitePin: String): TileWireTemplate? {
			val pinEl = getPinmapElement(altType, sitePin)

			var connectedPin = sitePin
			if (pinEl != null) {
				connectedPin = pinEl.getChildText("map")
			}

			return externalPinWires!![connectedPin]
		}

		private fun getPinmapElement(altType: SiteType, sitePin: String): Element? {
			val ptEl = getSiteTypeEl(currSite!!.possibleTypes[0])
			val alternativesEl = ptEl.getChild("alternatives")
			var altEl: Element? = null
			for (altTmpEl in alternativesEl.getChildren("alternative")) {
				if (altTmpEl.getChildText("name") == altType.name()) {
					altEl = altTmpEl
					break
				}
			}

			assert(altEl != null)
			val pinmapsEl = altEl!!.getChild("pinmaps")
			var pinEl: Element? = null
			if (pinmapsEl != null) {
				for (pinTmpEl in pinmapsEl.getChildren("pin")) {
					if (pinTmpEl.getChildText("name") == sitePin) {
						pinEl = pinTmpEl
						break
					}
				}
			}
			return pinEl
		}
	}

	private inner class TileWireListener : XDLRCParserListener() {
		private var currTile: Tile? = null
		private var tileWiresPool: HashMap<Set<TileWireTemplate>, Map<String, TileWireTemplate>>? = null
		private var tileWires: HashSet<TileWireTemplate>? = null

		override fun enterTiles(tokens: XDLRCParserListener.pl_Tiles) {
			tileWiresPool = HashMap(4096)
		}

		override fun enterTile(tokens: XDLRCParserListener.pl_Tile) {
			val row = tokens.row
			val col = tokens.column
			currTile = device!!.getTile(row, col)
			tileWires = HashSet()
		}

		override fun enterWire(tokens: XDLRCParserListener.pl_Wire) {
			tileWires!!.add(tileWireTemplates!![tokens.name])
		}

		override fun exitTile(tokens: XDLRCParserListener.pl_Tile) {
			currTile!!.tileWires = (tileWiresPool as java.util.Map<Set<TileWireTemplate>, Map<String, TileWireTemplate>>).computeIfAbsent(tileWires) { k ->
				if (k.isEmpty())
					return@tileWiresPool.computeIfAbsent emptyMap < String, TileWireTemplate>()
				else
				return@tileWiresPool.computeIfAbsent k . stream ().collect(Collectors.toMap<TileWireTemplate, String, TileWireTemplate>({ t -> t.name }) { t -> t })
			}

			currTile = null
			tileWires = null
		}

		override fun exitTiles(tokens: XDLRCParserListener.pl_Tiles) {
			tileWiresPool = null
		}
	}

	private inner class PrimitiveDefsListener internal constructor() : XDLRCParserListener() {
		private val defs: PrimitiveDefList
		private var currDef: PrimitiveDef? = null
		private var currElement: PrimitiveElement? = null
		private var pins: ArrayList<PrimitiveDefPin>? = null
		private var elements: ArrayList<PrimitiveElement>? = null

		init {
			defs = PrimitiveDefList()
			device!!.setPrimitiveDefs(defs)
		}

		override fun enterPrimitiveDef(tokens: XDLRCParserListener.pl_PrimitiveDef) {
			currDef = PrimitiveDef()
			val name = tokens.name.toUpperCase()
			currDef!!.type = SiteType.valueOf(device!!.family, name)

			pins = ArrayList(tokens.pin_count)
			elements = ArrayList(tokens.element_count)
		}

		override fun exitPrimitiveDef(tokens: XDLRCParserListener.pl_PrimitiveDef) {
			pins!!.trimToSize()
			elements!!.trimToSize()

			currDef!!.setPins(pins)
			currDef!!.setElements(elements)
			defs.add(currDef)
		}

		override fun enterPin(tokens: XDLRCParserListener.pl_Pin) {
			val p = PrimitiveDefPin()
			p.externalName = tokens.external_name
			p.internalName = tokens.internal_name
			p.direction = if (tokens.direction.startsWith("output")) PinDirection.OUT else PinDirection.IN
			pins!!.add(p)
		}

		override fun enterElement(tokens: XDLRCParserListener.pl_Element) {
			currElement = PrimitiveElement()
			currElement!!.name = tokens.name
			currElement!!.isBel = tokens.isBel
		}

		override fun exitElement(tokens: XDLRCParserListener.pl_Element) {
			// Determine the element type
			if (!currElement!!.isBel) {
				if (currElement!!.cfgOptions != null && !currElement!!.pins.isEmpty())
					currElement!!.isMux = true
				else if (currElement!!.cfgOptions != null)
				// && currElement.getPins() == null
					currElement!!.isConfiguration = true
				else if (currElement!!.name.startsWith("_ROUTETHROUGH"))
					currElement!!.isRouteThrough = true
				else
					currElement!!.isPin = true
			}
			elements!!.add(currElement)
		}

		override fun enterElementPin(tokens: XDLRCParserListener.pl_ElementPin) {
			val elementPin = PrimitiveDefPin()
			elementPin.externalName = tokens.name
			elementPin.internalName = tokens.name
			elementPin.direction = if (tokens.direction.startsWith("output")) PinDirection.OUT else PinDirection.IN
			currElement!!.addPin(elementPin)
		}

		override fun enterElementCfg(tokens: XDLRCParserListener.pl_ElementCfg) {
			for (cfg in tokens.cfgs) {
				currElement!!.addCfgOption(cfg)
			}
		}

		override fun enterElementConn(tokens: XDLRCParserListener.pl_ElementConn) {
			val c = PrimitiveConnection()
			c.element0 = tokens.element0
			c.pin0 = tokens.pin0
			c.isForwardConnection = tokens.direction == "==>"
			c.element1 = tokens.element1
			c.pin1 = tokens.pin1
			currElement!!.addConnection(c)
		}
	}

	companion object {

		private val PIP_CAPACITY = 40000

		private fun getOutputPin(el: PrimitiveElement): String? {
			for (pin in el.pins) {
				if (pin.isOutput)
					return pin.internalName
			}
			return null
		}

		private fun getIntrasiteWireName(
			type: SiteType, element: String?, pinName: String?): String {
			return "intrasite:" + type.name() + "/" + element + "." + pinName
		}

		/**
		 * Parses the device info XML file for the specified device, and adds the information
		 * to the [Device] object that is being created. If no device info file can be found
		 * for the part, then a warning is printed to the console.
		 *
		 * TODO: parse the clock pads and add them to the device file
		 *
		 * @param device Device object created from the XDLRC parser
		 */
		fun parseDeviceInfo(device: Device): Boolean {
			val deviceInfo = RSEnvironment.defaultEnv().loadDeviceInfo(device.family, device.partName)

			if (deviceInfo != null) {
				createPackagePins(device, deviceInfo)
				return true
			}
			return false
		}

		/**
		 * Creates a map from pad bel name -> corresponding package pin. This
		 * information is needed when generating Tincr Checkpoints from
		 * RS to be loaded into Vivado.
		 */
		private fun createPackagePins(device: Device, deviceInfo: Document) {
			val pinMapRootEl = deviceInfo.rootElement.getChild("package_pins") ?: throw Exceptions.ParseException("No package pin information found in device info file: " + deviceInfo.baseURI + ".\n"
				+ "Either add the package pin mappings, or remove the device info file and regenerate.")

// Add the package pins to the device
			pinMapRootEl.getChildren("package_pin")
				.stream()
				.map { ppEl -> PackagePin(ppEl.getChildText("name"), ppEl.getChildText("bel"), ppEl.getChild("is_clock") != null) }
				.forEach { packagePin -> device.addPackagePin(packagePin) }

			if (device.packagePins.isEmpty()) {
				throw Exceptions.ParseException("No package pin information found in device info file: " + deviceInfo.baseURI + ".\n"
					+ "Either add the package pin mappings, or remove the device info file and regenerate.")
			}
		}
	}
}

/**
 * Searches the device info file for the primitive type element of the
 * specified type.
 *
 * @param type the type of the element to retrieve
 * @return the JDOM element for the requested primitive type
 */
private fun getSiteTypeEl(familyInfo: Document, type: SiteTypeName): Element {
	val siteTypesEl = familyInfo.rootElement.getChild("site_types") ?:
		throw Exceptions.FileFormatException("family info xml is missing element <site_types>")
	return siteTypesEl.getChildren("site_type")
		.singleOrNull { it.getChild("name").text == type } ?:
		throw FileFormatException("no site type $type in familyInfo.xml")
}


