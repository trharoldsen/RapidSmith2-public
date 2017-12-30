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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.byu.ece.rapidSmith.RSEnvironment;
import edu.byu.ece.rapidSmith.device.*;

/**
 * This class is a simple method to browse device information by tile.
 * @author Chris Lavin
 * Created on: Jul 12, 2010
 */
public class BrowseDevice{

	public static void run(Device dev){
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		Tile t;
		while(true){
			System.out.println("Commands: ");
			System.out.println(" 1: Get wire connections in tile");
			System.out.println(" 2: Check if wire is a PIP wire");
			System.out.println(" 3: List RouteThrough wires");
			System.out.println(" 4: Follow wire connections");
			System.out.println(" 5: List primitives of a tile");
			System.out.println(" 6: Get tile of a primitive site");
			System.out.println(" 7: Exit");
			try {
				Integer cmd = Integer.parseInt(br.readLine().trim());
				switch(cmd){
					case 1:
						System.out.println("Enter tile name: ");
						t = dev.getTile(br.readLine().trim());
						System.out.println("Choosen Tile: " + t.getName());

						System.out.println("Enter wire name: ");
						String wire = br.readLine().trim();
						Collection<Connection> wires = t.getWire(wire).getNode().getWireConnections();
						if (!wires.isEmpty()) {
							for (Connection w : wires) {
								System.out.println("  " + w.toString());
							}
						} else {
							System.out.println(" No Connections");
						}
						break;
					case 2:
						System.out.println("No longer supported");
//						System.out.println("Enter wire name:");
//						String wire1 = br.readLine().trim();
//						System.out.println("isPIP? " + dev.isPIPWire(wire1));
						break;
					case 3:
						System.out.println("No longer supported");
//						System.out.println("PIPRouteThroughs");
//						for(WireConnection w : dev.getRouteThroughMap().keySet()){
//							System.out.println("  " + w.toString() + " " + dev.getRouteThroughMap().get(w).toString());
//						}
						break;
					case 4:
						System.out.println("Enter start tile name: ");
						t = dev.getTile(br.readLine().trim());
						System.out.println("Choosen start tile: " + t.getName());

						System.out.println("Enter start wire name: ");
						Wire startWire = t.getWire(br.readLine().trim());
						
						while(true){
							Node startNode = startWire.getNode();
							Collection<Connection> wcs = startNode.getWireConnections();
							if(wcs.isEmpty()){
								System.out.println("This wire has no connections, it may be a sink");
								break;
							}
							List<Connection> wireConnections = new ArrayList<>(wcs);
							System.out.println(t.getName() + " " + startWire + ":");
							for (int i = 0; i < wireConnections.size(); i++) {
								Wire sinkWire = wireConnections.get(i).getSinkWire();
								System.out.println("  " + i + ". " + sinkWire.getFullName());
							}
							System.out.print("Choose a wire: ");
							int ndx;
							try{
								ndx = Integer.parseInt(br.readLine().trim());
								startWire = wireConnections.get(ndx).getSinkWire();
							}
							catch(Exception e){
								System.out.println("Did not understand, try again.");
							}
							
						}
						break;
					case 5:
						System.out.println("Enter tile name: ");
						t = dev.getTile(br.readLine().trim());
						System.out.println("Choosen Tile: " + t.getName());

						if(t.getSites() == null){
							System.out.println(t.getName() + " has no primitive sites.");
						}
						else{
							for(Site p : t.getSites()){
								System.out.println("  " + p.getName());
							}
						}
					
						break;
					case 6:
						System.out.println("Enter tile name: ");
						String siteName = br.readLine().trim();
						Site site = dev.getSite(siteName);
						if(site == null){
							System.out.println("No primitive site called \"" + siteName +  "\" exists.");
						}
						else {
							System.out.println(site.getTile());
						}
						break;
					case 7:
						System.exit(0);
						
				}
			} catch (Exception e) {
				System.out.println("Bad input, try again.");
			}
		}
	}
	public static void main(String[] args){
		MessageGenerator.printHeader(" RapidSmith Device Browser");		
		if(args.length != 1){
			System.out.println("USAGE: <device part name, ex: xc4vfx12ff668 >");
			System.exit(1);
		}
		Device dev = RSEnvironment.defaultEnv().getDevice(args[0]);

		run(dev);
	}
}
