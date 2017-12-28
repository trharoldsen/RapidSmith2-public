package edu.byu.ece.rapidSmith.examples.handRouter;
import java.io.IOException;

import edu.byu.ece.rapidSmith.RSEnvironment;
import edu.byu.ece.rapidSmith.design.subsite.RouteTree;
import edu.byu.ece.rapidSmith.device.Wire;
import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.util.RapidSmithDebug;

/**
 * Runs the {@link HandRouter} 
 *
 */
public class HandRouterExample {

	public static final String PART_NAME = "xc7a100tcsg324";
	
	public static void main(String[] args) throws IOException {
		//load the device file in to RS2
		System.out.println("Loading device " + PART_NAME + " into RapidSmith2...");
		Device device = RSEnvironment.defaultEnv().getDevice(PART_NAME);

		// Create a new hand router object
		HandRouter hr = new HandRouter();
			
		// Create a new TileWire object to be the starting wire of the route
		Wire startWire = device.getTile("CLBLL_L_X16Y152").getWire("CLBLL_L_C");
		
		// Run the hand router, which returns a RapidSmith RouteTree object
		RouteTree route = hr.route(startWire);
		
		// Print the RouteTree to the console
		System.out.println("Printing chosen route:");
		RapidSmithDebug.printRouteTree(route);
	}
}
