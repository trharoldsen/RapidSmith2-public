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

package edu.byu.ece.rapidSmith.util;

import edu.byu.ece.rapidSmith.device.*;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveConnection;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDef;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveDefPin;
import edu.byu.ece.rapidSmith.primitiveDefs.PrimitiveElement;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 *
 */
public class XDLRCOutputter {
	private static final Pattern INTRASITE_PATTERN =
			Pattern.compile("intrasite:(.*)/(.*)\\.(.*)");

	private String nl = System.lineSeparator();
	private boolean writeWires = true;
	private boolean forceOrdering = false;
	private String ind = "\t";

	private Writer out;

	/**
	 * Sets the line separator to use when writing.
	 * <p>
	 * Defaults to <code>System.lineSeparator()</code>.
	 * @param nl the line separator
	 */
	public void setLineSeparator(String nl) {
		this.nl = nl;
	}

	/**
	 * Sets the indent for each level to use when writing.
	 * Warning: the parser may not work if the indent is not spaces or tabs.
	 * <p>
	 * Default is single tab per level.
	 * @param indent the indent to use
	 */
	public void setIndent(String indent) {
		this.ind = indent;
	}

	/**
	 * Determines whether wires and PIPs should be written.
	 * <p>
	 * Defaults to true.
	 * @param writeWires true to write wires and PIPs, false to skip
	 */
	public void writeWires(boolean writeWires) {
		this.writeWires = writeWires;
	}

	/**
	 * Determines if objects should be ordered to ensure consistent ordering of
	 * devices.
	 * In other words, this ensures that equivalent device file representations will
	 * yield identical XDLRC files when written.  This is useful for running
	 * text-based diffing tools to compare the devices.  Run time may be
	 * significantly longer if set.
	 * <p>
	 * Defaults to false.
	 * @param orderWires true to ensure consistent ordering
	 */
	public void forceOrdering(boolean orderWires) {
		this.forceOrdering = orderWires;
	}

	/**
	 * Write the specified device to a file provided in the specified path.
	 * The path will be used to create a buffered writer.
	 * @param device the device to write
	 * @param outPath the path to the file to write to
	 * @throws IOException if any IO errors occur while writing
	 */
	public void writeDevice(Device device, Path outPath) throws IOException {
		writeDevice(device, null, outPath);
	}

	/**
	 * Write the specified device to a file provided in the specified path.
	 * The path will be used to create a buffered writer.
	 * @param device the device to write
	 * @param out the writer to write to
	 * @throws IOException if any IO errors occur while writing
	 */
	public void writeDevice(Device device, Writer out) throws IOException {
		writeDevice(device, null, out);
	}

	/**
	 * Write the specified device to a file provided in the specified path.
	 * The path will be used to create a buffered writer.
	 * @param device the device to write
	 * @param tiles set of tiles to write
	 * @param outPath the path to the file to write to
	 * @throws IOException if any IO errors occur while writing
	 */
	public void writeDevice(Device device, Set<Tile> tiles, Path outPath) throws IOException {
		try(Writer bw = Files.newBufferedWriter(outPath, Charset.defaultCharset())) {
			writeDevice(device, tiles, bw);
		}
	}

	/**
	 * Write the specified device to a file provided in the specified path.
	 * The path will be used to create a buffered writer.
	 * @param device the device to write
	 * @param tiles set of tiles to write
	 * @param out the writer to write to
	 * @throws IOException if any IO errors occur while writing
	 */
	public void writeDevice(Device device, Set<Tile> tiles, Writer out) throws IOException {
		this.out = out;

		SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy");

		out.append("# =======================================================").append(nl);
		out.append("# XDL REPORT MODE $Revision: 1.8 $").append(nl);
		out.append("# time: ").append(sdf.format(new Date())).append(nl);
		out.append("# =======================================================").append(nl);
		out.append("(xdl_resource_report v0.2 ");
		out.append(device.getPartName()).append(" ");
		out.append(device.getFamily().name().toLowerCase()).append(nl);
		out.append("# **************************************************************************").append(nl);
		out.append("# *                                                                        *").append(nl);
		out.append("# * Tile Resources                                                         *").append(nl);
		out.append("# *                                                                        *").append(nl);
		out.append("# **************************************************************************").append(nl);
		out.append("(tiles ").append(String.valueOf(device.getRows())).append(" ").append(String.valueOf(device.getColumns())).append(nl);

		if (tiles == null) {
			for (int row = 0; row < device.getRows(); row++) {
				for (int col = 0; col < device.getColumns(); col++) {
					writeTile(device.getTile(row, col));
				}
			}
		} else {
			for (Tile tile : tiles)
				writeTile(tile);
		}
		out.append(")").append(nl);

		out.append("(primitive_defs ").append(String.valueOf(device.getSupportedSiteTypes().size())).append(nl);
		List<PrimitiveDef> defs = device.getSupportedSiteTypes().stream()
			.map(st -> device.getTemplateOfSiteType(st))
			.map(this::createPrimitiveDef)
			.collect(Collectors.toCollection(ArrayList::new));

		if (forceOrdering)
			defs.sort(Comparator.comparing(o -> o.getType().name()));
		for (PrimitiveDef def : defs) {
			writePrimitiveDef(def);
		}

		out.append(")").append(nl);
	}

	private void writeTile(Tile tile) throws IOException {
		out.append(ind).append("(tile ").append(String.valueOf(tile.getRow())).append(" ");
		out.append(String.valueOf(tile.getColumn())).append(" ").append(tile.getName()).append(" ");
		out.append(String.valueOf(tile.getType())).append(" ");
		out.append(String.valueOf(tile.getSites() == null ? "0" : tile.getSites().size())).append(nl);
		int numPinWires = 0;
		if (tile.getSites() != null) {
			for (Site site : tile.getSites()) {
				writeSite(site, writeWires);
				numPinWires += site.getSourcePins().size() + site.getSinkPins().size();
			}
		}

		if (writeWires) {
			List<String> wireNames = tile.getWires().stream()
					.map(Wire::getName)
					.collect(Collectors.toCollection(ArrayList::new));
			if (forceOrdering)
				Collections.sort(wireNames);

			for (String wireName : wireNames) {
				Wire wire = tile.getWire(wireName);
				out.append(ind).append(ind).append("(wire ");
				out.append(wireName).append(" ");

				Node node = wire.getNode();
				List<Wire> nonPips = new ArrayList<>(node.getWires());
				if (forceOrdering)
					nonPips.sort(Comparator.comparing(k -> k.getName()));

				out.append(Integer.toString(nonPips.size()));
				if (nonPips.size() == 0) {
					out.append(")").append(nl);
				} else {
					out.append(nl);
					for (Wire sinkWire : nonPips) {
						out.append(ind).append(ind).append(ind).append("(conn ");
						out.append(sinkWire.getTile().toString()).append(" ");
						out.append(sinkWire.getName()).append(")").append(nl);
					}
					out.append(ind).append(ind).append(")").append(nl);
				}
			}

			int numPips = 0;
			for (String wireName : wireNames) {
				Wire sourceWire = tile.getWire(wireName);
				Node sourceNode = sourceWire.getNode();

				List<Connection> pips = sourceNode.getWireConnections().stream()
					.filter(it -> it.getSourceWire().equals(sourceWire))
					.collect(Collectors.toCollection(ArrayList::new));
				if (forceOrdering)
					pips.sort(new ConnectionComparator());

				for (Connection c : pips) {
					out.append(ind).append(ind).append("(pip ");
					out.append(tile.getName()).append(" ");
					out.append(wireName).append(" ");
					out.append(isBidirectionalPip(sourceWire, c.getSinkWire()) ? "=- " : "-> ");
					out.append(c.getSinkWire().getName());

					PIPRouteThrough rt = tile.getDevice().getRouteThrough(sourceWire, c.getSinkWire());
					if (rt != null) {
						out.append(" (_ROUTETHROUGH-").append(rt.getInPin()).append("-");
						out.append(rt.getOutPin()).append(" ");
						out.append(rt.getType().name()).append("))").append(nl);
					} else {
						out.append(")").append(nl);
					}
					numPips++;
				}
			}

			out.append(ind).append(ind).append("(tile_summary ");
			out.append(tile.getName()).append(" ");
			out.append(tile.getType().name()).append(" ");
			out.append(String.valueOf(numPinWires)).append(" ");
			out.append(String.valueOf(tile.getWires().size())).append(" ");
			out.append(String.valueOf(numPips)).append(")").append(nl);
		}
		out.append(ind).append(")").append(nl);
	}

	private boolean isBidirectionalPip(Wire sourceWire, Wire sinkWire) {
		Node sinkNode = sinkWire.getNode();
		return sinkNode.getReverseWireConnections().stream()
			.filter(it -> it.getSourceWire().equals(sinkWire))
			.anyMatch(it -> it.getSinkWire().equals(sourceWire));
	}

	private void writeSite(Site site, boolean writeWires) throws IOException {
		out.append(ind).append(ind).append("(primitive_site ").append(site.getName()).append(" ");
		out.append("").append(String.valueOf(site.getDefaultType()));
		out.append(" ").append(String.valueOf(site.getBondedType())).append(" ");
		int numPins = site.getSinkPins().size() + site.getSourcePins().size();
		out.append("").append(String.valueOf(numPins));
		if (!writeWires || numPins == 0) {
			out.append(")").append(nl);
		} else {
			out.append(nl);

			List<SitePin> pins = new ArrayList<>(site.getPins());
			if (forceOrdering)
				pins.sort(Comparator.comparing(k -> k.getName()));
			for (SitePin pin : pins) {
				out.append(ind).append(ind).append(ind).append("(pinwire ");
				out.append(pin.getName()).append(" ");
				out.append(pin.getDirection() == PinDirection.IN ? "input " : "output ");
				out.append(pin.getExternalWire().getName());
				out.append(")").append(nl);
			}
			out.append(ind).append(ind).append(")").append(nl);
		}
	}

	private void writePrimitiveDef(PrimitiveDef def) throws IOException {
		out.append(ind).append("(primitive_def ");
		out.append(def.getType().name()).append(" ");
		out.append(String.valueOf(def.getElements().size())).append(nl);

		List<PrimitiveDefPin> pins = new ArrayList<>(def.getPins());
		if (forceOrdering)
			pins.sort(Comparator.comparing(PrimitiveDefPin::getExternalName));
		for (PrimitiveDefPin pin : pins) {
			out.append(ind).append(ind).append("(pin ");
			out.append(pin.getInternalName()).append(" ");
			out.append(directionToString(pin.getDirection())).append(")").append(nl);
		}

		List<PrimitiveElement> els = new ArrayList<>(def.getElements());
		if (forceOrdering)
			els.sort(Comparator.comparing(PrimitiveElement::getName));
		for (PrimitiveElement el : els) {
			out.append(ind).append(ind).append("(element ");
			out.append(el.getName()).append(" ");
			out.append("").append(String.valueOf(el.getPins().size()));

			if (el.isBel()) {
				out.append(" # BEL");
			}
			out.append(nl);

			List<PrimitiveDefPin> elPins = new ArrayList<>(el.getPins());
			if (forceOrdering)
				elPins.sort(Comparator.comparing(PrimitiveDefPin::getExternalName));
			for (PrimitiveDefPin pin : el.getPins()) {
				out.append(ind).append(ind).append(ind).append("(pin ");
				out.append(pin.getExternalName()).append(" ");
				out.append(directionToString(pin.getDirection())).append(")").append(nl);
			}
			if (el.getCfgOptions() != null) {
				out.append(ind).append(ind).append(ind).append("(cfg");

				List<String> cfgs = new ArrayList<>(el.getCfgOptions());
				if (forceOrdering)
					Collections.sort(cfgs);
				for (String cfg : cfgs) {
					out.append(" ").append(cfg);
				}
				out.append(")").append(nl);
			}

			List<PrimitiveConnection> conns = new ArrayList<>(el.getConnections());
			if (forceOrdering) {
				conns.sort(Comparator.comparing(PrimitiveConnection::getElement0)
						.thenComparing(PrimitiveConnection::getPin0)
						.thenComparing(PrimitiveConnection::getElement1)
						.thenComparing(PrimitiveConnection::getPin1));
			}
			for (PrimitiveConnection c : conns) {
				out.append(ind).append(ind).append(ind).append("(conn ");
				out.append(c.getElement0()).append(" ");
				out.append(c.getPin0()).append(" ");
				out.append((c.isForwardConnection()) ? "==> " : "<== ");
				out.append(c.getElement1()).append(" ");
				out.append(c.getPin1()).append(")").append(nl);
			}

			out.append(ind).append(ind).append(")").append(nl);
		}

		out.append(ind).append(")").append(nl);
	}

	private String directionToString(PinDirection pinDirection) {
		return pinDirection == PinDirection.IN ? "input" : "output";
	}

	private static class Pin {
		public final PrimitiveElement element;
		public final String pin;

		private Pin(PrimitiveElement el, String pin) {
			this.element = el;
			this.pin = pin;
		}
	}

	private PrimitiveDef createPrimitiveDef(SiteTemplate template) {
		Map<SiteWireTemplate, Pin> pinWiresMap = new HashMap<>();

		PrimitiveDef def = new PrimitiveDef();
		def.setType(template.getType());

		createPinElements(template, pinWiresMap, def);
		createBelElements(template, pinWiresMap, def);
		createPIPMuxElements(template, pinWiresMap, def);
		createSitePinConnections(template, pinWiresMap, def);
		createBelPinConnections(template, pinWiresMap, def);
		return def;
	}

	private void createPinElements(SiteTemplate template, Map<SiteWireTemplate, Pin> pinWiresMap, PrimitiveDef def) {
		for (SitePinTemplate sitePin : template.getPinTemplates()) {
			PrimitiveDefPin defSitePin = new PrimitiveDefPin();
			defSitePin.setExternalName(sitePin.getName());
			defSitePin.setInternalName(sitePin.getName());
			defSitePin.setDirection(sitePin.getDirection() == PinDirection.IN ? PinDirection.IN : PinDirection.OUT);
			def.addPin(defSitePin);

			PrimitiveElement element = new PrimitiveElement();
			element.setName(sitePin.getName());
			element.setPin(true);

			PrimitiveDefPin elPin = new PrimitiveDefPin();
			elPin.setInternalName(element.getName());
			elPin.setExternalName(element.getName());
			elPin.setDirection(sitePin.getDirection() == PinDirection.IN ? PinDirection.OUT : PinDirection.IN);
			element.addPin(elPin);

			def.addElement(element);
		}
	}

	private void createBelElements(SiteTemplate template, Map<SiteWireTemplate, Pin> pinWiresMap, PrimitiveDef def) {
		for (BelTemplate bel : template.getBelTemplates()) {
			PrimitiveElement el = new PrimitiveElement();
			el.setName(bel.getId().getName());
			el.setBel(true);
			for (BelPinTemplate belPin : bel.getPinTemplates()) {
				PrimitiveDefPin pin = new PrimitiveDefPin();
				pin.setExternalName(belPin.getName());
				pin.setDirection(pin.isOutput() ? PinDirection.OUT : PinDirection.IN);
				el.addPin(pin);

				pinWiresMap.put(belPin.getWire(), new Pin(el, belPin.getName()));
			}
			def.addElement(el);
		}
	}

	private void createPIPMuxElements(SiteTemplate template, Map<SiteWireTemplate, Pin> pinWiresMap, PrimitiveDef def) {
		List<SiteNodeTemplate> nodes = template.getWireNodesMap().values().stream()
			.distinct()
			.collect(Collectors.toList());
		for (SiteNodeTemplate node : nodes) {
			for (SiteNodeConnection c : node.getConnections()) {
				SiteWireTemplate source = c.getSourceWire();
				SiteWireTemplate sink = c.getSinkWire();

				String elName = getElementNameFromWire(sink.getName());
				PrimitiveElement el = def.getElement(elName);
				if (el == null) {
					el = new PrimitiveElement();
					el.setName(elName);
					el.setMux(true);
					PrimitiveDefPin sinkPin = new PrimitiveDefPin();
					sinkPin.setExternalName(getPinNameFromWire(sink.getName()));
					sinkPin.setDirection(PinDirection.OUT);
					el.addPin(sinkPin);
				}
				String sourcePinName = getPinNameFromWire(source.getName());
				PrimitiveDefPin sourcePin = new PrimitiveDefPin();
				sourcePin.setExternalName(sourcePinName);
				sourcePin.setDirection(PinDirection.IN);
				el.addPin(sourcePin);
				el.addCfgOption(sourcePinName);
				def.addElement(el);

				pinWiresMap.put(source, new Pin(el, sourcePinName));
			}
		}
	}

	private void createSitePinConnections(SiteTemplate template, Map<SiteWireTemplate, Pin> pinWiresMap, PrimitiveDef def) {
		for (SitePinTemplate sitePin : template.getPinTemplates()) {
			if (!sitePin.isOutput())
				continue;

			SiteWireTemplate swt = sitePin.getInternalWire();
			SiteNodeTemplate snt = template.getWireNodesMap().get(swt);
			if (snt == null)
				continue;

			PrimitiveElement el = def.getElement(sitePin.getName());
			for (SiteNodeConnection snc : snt.getConnections()) {
				SiteWireTemplate oswt = snc.getSourceWire();

				if (pinWiresMap.containsKey(oswt)) {
					Pin sink = pinWiresMap.get(oswt);

					PrimitiveConnection fc = new PrimitiveConnection();
					fc.setElement0(el.getName());
					fc.setPin0(el.getName());
					fc.setForwardConnection(true);
					fc.setElement1(sink.element.getName());
					fc.setPin1(sink.pin);
					el.addConnection(fc);

					PrimitiveConnection bc = new PrimitiveConnection();
					bc.setElement0(el.getName());
					bc.setPin0(el.getName());
					bc.setForwardConnection(false);
					bc.setElement1(sink.element.getName());
					bc.setPin1(sink.pin);
					sink.element.addConnection(bc);
				}
			}
		}
	}

	private void createBelPinConnections(SiteTemplate template, Map<SiteWireTemplate, Pin> pinWiresMap, PrimitiveDef def) {
		for (BelTemplate bel : template.getBelTemplates()) {
			PrimitiveElement el = def.getElement(bel.getId().getName());

			for (BelPinTemplate belPin : bel.getPinTemplates()) {
				if (belPin.getDirection() != PinDirection.OUT)
					continue;

				SiteWireTemplate swt = belPin.getWire();
				SiteNodeTemplate snt = template.getWireNodesMap().get(swt);
				if (snt == null)
					continue;
				for (SiteNodeConnection snc : snt.getConnections()) {
					SiteWireTemplate oswt = snc.getSourceWire();

					if (pinWiresMap.containsKey(oswt)) {
						Pin sink = pinWiresMap.get(oswt);

						PrimitiveConnection fc = new PrimitiveConnection();
						fc.setElement0(el.getName());
						fc.setPin0(el.getName());
						fc.setForwardConnection(true);
						fc.setElement1(sink.element.getName());
						fc.setPin1(sink.pin);
						el.addConnection(fc);

						PrimitiveConnection bc = new PrimitiveConnection();
						bc.setElement0(el.getName());
						bc.setPin0(el.getName());
						bc.setForwardConnection(false);
						bc.setElement1(sink.element.getName());
						bc.setPin1(sink.pin);
						sink.element.addConnection(bc);
					}
				}
			}
		}
	}

	private String getElementNameFromWire(String wireName) {
		Matcher mo = INTRASITE_PATTERN.matcher(wireName);
		//noinspection ResultOfMethodCallIgnored
		mo.find();
		return mo.group(2);
	}

	private String getPinNameFromWire(String wireName) {
		Matcher mo = INTRASITE_PATTERN.matcher(wireName);
		//noinspection ResultOfMethodCallIgnored
		mo.find();
		return mo.group(3);
	}

	public static void main(String[] args) {
		OptionParser parser = new OptionParser();
		parser.acceptsAll(Arrays.asList("tile", "t"), "Prints only the specified tile").withRequiredArg();
		parser.accepts("wires", "Print wires of tiles");
		parser.acceptsAll(Arrays.asList("ordered", "o"), "Ensure consistent ordering");
		parser.acceptsAll(Arrays.asList("build_defs", "b"), "Build primitive defs section from site templates");
		parser.nonOptions("<device> [<output file>]");

		Arguments arguments = new Arguments(parser, args).invoke();
		Path output = arguments.getOutput();
		Device device = arguments.getDevice();
		Set<Tile> tiles = arguments.getTiles();

		XDLRCOutputter outputter = new XDLRCOutputter();
		outputter.writeWires(arguments.writeWires());
		outputter.forceOrdering(arguments.ordered());

		Writer out = null;
		try {
			out = Files.newBufferedWriter(output, Charset.defaultCharset());
		} catch (IOException e) {
			System.err.println("Could not open file for writing " + output);
			System.exit(-3);
		}

		try {
			outputter.writeDevice(device, tiles, out);
		} catch (IOException e) {
			System.err.println("Error writing to file.");
		} finally {
			try {
				out.close();
			} catch (IOException ignored) { }
		}
	}

	private static class Arguments {
		private final OptionParser parser;
		private final String[] args;
		private Device device;
		private Set<Tile> tiles;
		private boolean writeWires;
		private boolean ordered;
		private Path output;

		public Arguments(OptionParser parser, String... args) {
			this.parser = parser;
			this.args = args;
		}

		public Device getDevice() {
			return device;
		}

		public Set<Tile> getTiles() {
			return tiles;
		}

		public boolean writeWires() {
			return writeWires;
		}

		public boolean ordered() {
			return ordered;
		}

		public Path getOutput() {
			return output;
		}

		public Arguments invoke() {
			OptionSet options = null;
			try {
				options = parser.parse(args);
			} catch (OptionException e) {
				try {
					parser.printHelpOn(System.err);
				} catch (IOException ignored) {
				}
				System.exit(-1);
			}

			if (options.nonOptionArguments().size() < 1) {
				try {
					parser.printHelpOn(System.err);
				} catch (IOException ignored) {
				}
				System.exit(-1);
			}

			try {
				device = Device.getInstance((String) options.nonOptionArguments().get(0));
			} catch (NullPointerException e) {
				device = null;
			}
			if (device == null) {
				System.err.println("Error loading part " + options.nonOptionArguments().get(0));
				System.exit(-2);
			}

			tiles = null;
			if (options.has("tile")) {
				// use a linked hash set to maintain the order tiles are added while
				// removing duplicates
				tiles = new LinkedHashSet<>();
				//noinspection unchecked,RedundantCast
				for (Object tileName : options.valuesOf("tile")) {
					Pattern pattern = null;
					try {
						pattern = Pattern.compile((String) tileName);
					} catch (PatternSyntaxException e) {
						System.err.println("Could not compile pattern " + tileName);
						System.exit(-4);
					}
					for (int row = 0; row < device.getRows(); row++) {
						for (int col = 0; col < device.getColumns(); col++) {
							Tile tile = device.getTile(0, 0);
							if (pattern.matcher(tile.getName()).matches()) {
								tiles.add(tile);
							}
						}
					}
				}
			}

			writeWires = options.has("wires");
			ordered = options.has("ordered");

			output = Paths.get(device.getPartName() + ".xdlrc");
			if (options.nonOptionArguments().size() >= 2) {
				String p = (String) options.nonOptionArguments().get(1);
				if (!p.contains("."))
					p += ".xdlrc";
				output = Paths.get(p);
			}
			return this;
		}
	}

	private class ConnectionComparator implements Comparator<Connection> {
		@Override
		public int compare(Connection o1, Connection o2) {
			return Comparator.comparing((Connection o) -> o.getSinkWire().getTile().getName())
					.thenComparing(o -> o.getSinkWire().getName())
					.compare(o1, o2);
		}
	}
}
