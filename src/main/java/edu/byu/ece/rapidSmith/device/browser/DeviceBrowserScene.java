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
package edu.byu.ece.rapidSmith.device.browser;


import com.trolltech.qt.core.Qt.MouseButton;
import com.trolltech.qt.core.Qt.PenStyle;
import com.trolltech.qt.gui.*;
import edu.byu.ece.rapidSmith.device.*;
import edu.byu.ece.rapidSmith.gui.NumberedHighlightedTile;
import edu.byu.ece.rapidSmith.gui.TileScene;

import java.util.*;

/**
 * This class was written specifically for the DeviceBrowser class.  It
 * provides the scene content of the 2D tile array.
 */
public class DeviceBrowserScene extends TileScene{
	/**	 */
	Signal1<Tile> updateTile = new Signal1<>();
	/**	 */
	private QPen wirePen;
	/**	 */
	private ArrayList<QGraphicsLineItem> currLines;
	/**	 */
	private DeviceBrowser browser;
	/**	 */
	private Tile reachabilityTile;
	/**	 */
	private ArrayList<NumberedHighlightedTile> currentTiles = new ArrayList<>();


	DeviceBrowserScene(Device device, boolean hideTiles, boolean drawSites, DeviceBrowser browser){
		super(device, hideTiles, drawSites);
		currLines = new ArrayList<>();
		wirePen = new QPen(QColor.yellow, 0.25, PenStyle.SolidLine);
		this.browser = browser;
	}

	void clearCurrentLines(){
		for(QGraphicsLineItem line : currLines){
			this.removeItem(line);
			line.dispose();
		}
		currLines.clear();
	}

	void drawWire(TileWire src, TileWire dst) {
		int numWires = device.getNumUniqueWireTypes();
		int srcOrdinal = src.getTemplate().getOrdinal();
		int dstOrdinal = dst.getTemplate().getOrdinal();
		double x1 = (double) tileXMap.get(src.getTile())*tileSize  + (srcOrdinal%tileSize);
		double y1 = (double) tileYMap.get(src.getTile())*tileSize  + (srcOrdinal*tileSize)/numWires;
		double x2 = (double) tileXMap.get(dst.getTile())*tileSize  + (dstOrdinal%tileSize);
		double y2 = (double) tileYMap.get(dst.getTile())*tileSize  + (dstOrdinal*tileSize)/numWires;
		WireConnectionLine line = new WireConnectionLine(x1,y1,x2,y2, this, dst);
		line.setToolTip(src.getName() + " " + src.getName() + " -> " +
				dst.getName() + " " + dst.getName());
		line.setPen(wirePen);
		line.setAcceptHoverEvents(true);
		addItem(line);
		currLines.add(line);
	}

	void drawConnectingWires(TileWire wire){
		clearCurrentLines();
		if(wire == null) return;
		for(TileWire w : wire.getNode().getWires()) {
			drawWire(wire, w);
		}
	}

	private HashMap<Tile, Integer> findReachability(Tile t, Integer hops){
		HashMap<Node, Integer> level = new HashMap<>();
		HashMap<Tile, Integer> reachabilityMap = new HashMap<>();

		Queue<Node> queue = new LinkedList<>();
		for(Node node : t.getNodes()) {
			for(Connection c : node.getWireConnections()){
				Node w = c.getSinkNode();
				queue.add(w);
				level.put(w, 0);
			}
		}

		while(!queue.isEmpty()){
			Node currNode = queue.poll();
			Integer lev = level.get(currNode);
			if(lev < hops-1){
				for(Connection c : currNode.getWireConnections()){
					Node w = c.getSinkNode();
					if (level.putIfAbsent(w, lev+1) == null)
						queue.add(w);
				}
			}
		}

		for (Map.Entry<Node, Integer> e : level.entrySet()) {
			for (Wire wire : e.getKey().getWires()) {
				reachabilityMap.compute(wire.getTile(), (k, v) -> v == null ? 1 : v + 1);
			}
		}

		return reachabilityMap;
	}

	private void drawReachability(HashMap<Tile, Integer> map){
		menuReachabilityClear();
		for(Tile t : map.keySet()){
			int color = map.get(t)*16 > 255 ? 255 : map.get(t)*16;
			NumberedHighlightedTile tile = new NumberedHighlightedTile(t, this, map.get(t));
			tile.setBrush(new QBrush(new QColor(0, color, 0)));
			currentTiles.add(tile);
		}
	}

	@SuppressWarnings("unused")
	private void menuReachability1(){
		drawReachability(findReachability(reachabilityTile, 1));
	}

	@SuppressWarnings("unused")
	private void menuReachability2(){
		drawReachability(findReachability(reachabilityTile, 2));
	}

	@SuppressWarnings("unused")
	private void menuReachability3(){
		drawReachability(findReachability(reachabilityTile, 3));
	}

	@SuppressWarnings("unused")
	private void menuReachability4(){
		drawReachability(findReachability(reachabilityTile, 4));
	}

	@SuppressWarnings("unused")
	private void menuReachability5(){
		drawReachability(findReachability(reachabilityTile, 5));
	}

	private void menuReachabilityClear(){
		for(NumberedHighlightedTile rect : currentTiles){
			rect.remove();
		}
		currentTiles.clear();
	}


	@Override
	public void mouseDoubleClickEvent(QGraphicsSceneMouseEvent event){
		Tile t = getTile(event);
		this.updateTile.emit(t);
		super.mouseDoubleClickEvent(event);
	}

	@Override
	public void mouseReleaseEvent(QGraphicsSceneMouseEvent event){
		if(event.button().equals(MouseButton.RightButton)){
			if(browser.view.hasPanned){
				browser.view.hasPanned = false;

			}
			else{
				reachabilityTile = getTile(event);
				QMenu menu = new QMenu();
				QAction action1 = new QAction("Draw Reachability (1 Hop)", this);
				QAction action2 = new QAction("Draw Reachability (2 Hops)", this);
				QAction action3 = new QAction("Draw Reachability (3 Hops)", this);
				QAction action4 = new QAction("Draw Reachability (4 Hops)", this);
				QAction action5 = new QAction("Draw Reachability (5 Hops)", this);
				QAction actionClear = new QAction("Clear Highlighted Tiles", this);
				action1.triggered.connect(this, "menuReachability1()");
				action2.triggered.connect(this, "menuReachability2()");
				action3.triggered.connect(this, "menuReachability3()");
				action4.triggered.connect(this, "menuReachability4()");
				action5.triggered.connect(this, "menuReachability5()");
				actionClear.triggered.connect(this, "menuReachabilityClear()");
				menu.addAction(action1);
				menu.addAction(action2);
				menu.addAction(action3);
				menu.addAction(action4);
				menu.addAction(action5);
				menu.addAction(actionClear);
				menu.exec(event.screenPos());
			}
		}


		super.mouseReleaseEvent(event);
	}
}
