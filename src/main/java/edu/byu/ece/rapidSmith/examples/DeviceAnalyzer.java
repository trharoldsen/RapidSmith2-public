package edu.byu.ece.rapidSmith.examples;

import java.io.IOException;
import java.util.Collection;

import edu.byu.ece.rapidSmith.device.*;
import org.jdom2.JDOMException;

import edu.byu.ece.rapidSmith.RSEnvironment;

public class DeviceAnalyzer {
	
	static Device device;

	public static void main(String[] args) throws IOException, JDOMException {

		msg("Starting DeviceAnalyzer...\n");

		// Load the device file
		device = RSEnvironment.defaultEnv().getDevice("xc7a100tcsg324");
		
		// Grab a few tiles and print out their wires
		printTileWires(device.getTile("CLBLL_R_X17Y181"));
		printTileWires(device.getTile("INT_R_X17Y181"));
	}
	
	/**
	 * Prints out the wires and their connections within a tile
	 * @param t A handle to the tile of interest.
	 */
	private static void printTileWires(Tile t) {

		msg("\n===========================\nSelected tile " + t.toString());
		msg("Its row and column numbers are: [" + t.getRow() + ", " + t.getColumn() + "]");
		
		
		// Build each wire and print its statistics
		Collection<Wire> wires = t.getWires();
		Collection<Node> nodes = t.getNodes();
		msg("There are " + wires.size() + " wires representing " + nodes.size() + " in this tile...");
		Collection<PIP> pips = t.getPIPs();
		msg("There are " + pips.size() + " in this tile...");
		for (PIP pip : pips) {
			msg("  " + pip.getStartWire() + " -> " + pip.getEndWire());
		}
		
		msg("Done...");
	}

	private static void msg(String s) {
		System.out.println(s);
	}

}
