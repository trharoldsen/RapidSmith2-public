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

import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import edu.byu.ece.rapidSmith.RSEnvironment;
import edu.byu.ece.rapidSmith.device.*;
import edu.byu.ece.rapidSmith.util.ArraySet;
import edu.byu.ece.rapidSmith.util.FileTools;
import edu.byu.ece.rapidSmith.util.HashPool;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class ExtendedDeviceInfo implements Serializable {
	private static final long serialVersionUID = -459840872618980717L;
	private transient ExecutorService threadPool;

	private transient final HashPool<WireConnection<TileWireTemplate>> tileConnPool = new HashPool<>();
	private transient final HashPool<ArraySet<WireConnection<TileWireTemplate>>> tileConnSetPool = new HashPool<>();
	private transient final HashPool<WireHashMap<TileWireTemplate>> tileConnMapPool = new HashPool<>();

	private transient final HashPool<WireConnection<SiteWireTemplate>> siteConnPool = new HashPool<>();

	private Map<String, WireHashMap<TileWireTemplate>> reversedWireHashMap = new HashMap<>(); // tile names to wirehashmap
	private Map<SiteType, WireHashMap<SiteWireTemplate>> reversedSubsiteRouting = new HashMap<>();

	public void buildExtendedInfo(Device device) {
		System.out.println("started at " + new Date());
		reverseWireHashMap(device);
		reverseSubsiteWires(device);
		System.out.println("reversed done at " + new Date());
		threadPool = Executors.newFixedThreadPool(8);
		threadPool.shutdown();
		try {
			threadPool.awaitTermination(2, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		System.out.println("driving done at " + new Date());

		storeValuesIntoStructure(device);
		Path partFolderPath = getExtendedInfoPath(device);
		try {
			writeCompressedFile(this, partFolderPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("compress done at " + new Date());
	}

	public static void writeCompressedFile(ExtendedDeviceInfo info, Path path) throws IOException {
		Hessian2Output hos = null;
		try {
			hos = FileTools.getCompactWriter(path);
			hos.writeObject(info);
		} finally {
			if (hos != null) {
				hos.close();
			}
		}
	}

	private void storeValuesIntoStructure(Device device) {
		for (Tile tile : device.getTileMap().values()) {
			reversedWireHashMap.put(tile.getName(), tile.getReverseWireHashMap());
		}
		for (SiteTemplate template : device.getSiteTemplates().values()) {
			reversedSubsiteRouting.put(template.getType(), template.getReversedWireHashMap());
		}
	}

	private void reverseWireHashMap(Device device) {
		threadPool = Executors.newFixedThreadPool(8);
		for (Tile tile : device.getTiles()) {
			threadPool.execute(() -> getReverseMapForTile(device, tile));
		}
		threadPool.shutdown();
		try {
			threadPool.awaitTermination(2, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		threadPool = null;
	}

	private void getReverseMapForTile(Device device, Tile tile) {
		Map<TileWireTemplate, ArraySet<WireConnection<TileWireTemplate>>> reverseMap = new HashMap<>();
		for (Tile srcTile : device.getTiles()) {
			for (Wire srcWire : srcTile.getWires()) {
				TileWireTemplate srcEnum = ((TileWire) srcWire).getTemplate();
				ArraySet<WireConnection<TileWireTemplate>> wcs = srcTile.getWireHashMap().get(srcEnum);
				if (wcs != null) {
					for (WireConnection<TileWireTemplate> c : wcs) {
						if (c.getTile(srcTile) == tile) {
							WireConnection<TileWireTemplate> reverse = new WireConnection<>(
								srcEnum, -c.getRowOffset(),
								-c.getColumnOffset(), c.isPIP());
							WireConnection<TileWireTemplate> pooled = tileConnPool.add(reverse);
							reverseMap.computeIfAbsent(c.getSinkWire(), k -> new ArraySet<>())
								.add(pooled);
						}
					}
				}
			}
		}



		WireHashMap<TileWireTemplate> wireHashMap = new WireHashMap<>();
		for (Map.Entry<TileWireTemplate, ArraySet<WireConnection<TileWireTemplate>>> e : reverseMap.entrySet()) {
			ArraySet<WireConnection<TileWireTemplate>> v = e.getValue();
			wireHashMap.put(e.getKey(), v);
		}

		WireHashMap<TileWireTemplate> pooled = tileConnMapPool.add(wireHashMap);
		if (pooled == wireHashMap) {
			// reduce the individual components in the map
			for (TileWireTemplate key : pooled.keySet()) {
				ArraySet<WireConnection<TileWireTemplate>> v = pooled.get(key);
				ArraySet<WireConnection<TileWireTemplate>> pooledv = tileConnSetPool.add(v);
				pooledv.trimToSize();
				pooled.put(key, pooledv);
			}
		}

		tile.setReverseWireConnections(pooled);
	}

	private void reverseSubsiteWires(Device device) {
		for (SiteTemplate site : device.getSiteTemplates().values()) {
			WireHashMap<SiteWireTemplate> reversed = getReverseMapForSite(site);
			site.setReverseWireConnections(reversed);
		}
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
					WireConnection<SiteWireTemplate> pooled = siteConnPool.add(reverse);
					reverseMap.computeIfAbsent(c.getSinkWire(), k -> new ArraySet<>()).add(pooled);
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

	public static Path getExtendedInfoPath(Device device) {
		RSEnvironment env = RSEnvironment.defaultEnv();
		Path partFolderPath = env.getPartFolderPath(device.getFamily());
		partFolderPath = partFolderPath.resolve(device.getPartName() + "_info.dat");
		return partFolderPath;
	}

	public static ExtendedDeviceInfo loadCompressedFile(Path path) throws IOException {
		Hessian2Input hos = null;
		ExtendedDeviceInfo info;
		try {
			hos = FileTools.getCompactReader(path);
			info = (ExtendedDeviceInfo) hos.readObject();
		} catch (Exception e) {
			throw new IOException(e);
		} finally {
			if (hos != null) {
				hos.close();
			}
		}
		return info;
	}

	public Map<String, WireHashMap<TileWireTemplate>> getReversedWireMap() {
		return reversedWireHashMap;
	}

	public Map<SiteType, WireHashMap<SiteWireTemplate>> getReversedSubsiteRouting() {
		return reversedSubsiteRouting;
	}
}
