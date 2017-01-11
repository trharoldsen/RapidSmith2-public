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

package edu.byu.ece.rapidSmith.design;

import edu.byu.ece.rapidSmith.RSEnvironment;
import edu.byu.ece.rapidSmith.device.Device;
import edu.byu.ece.rapidSmith.device.FamilyType;

import java.io.Serializable;

/**
 *  Abstract base class for Design classes.  Design classes hold the logical netlists
 *  and their mappings to physical components.
 */
public abstract class AbstractDesign implements Serializable {
	private static final long serialVersionUID = 6284690406230426968L;
	/**  Name of the design */
	protected String name;
	// use partName instead of device here to allow speed grade to be specified
	/**  This is the Xilinx part, package and speed grade that this design targets */
	protected String partName;
	/**
	 * The device for this part.
	 */
	protected Device device;

	/**
	 * Constructor for an AbstractDesign with no name or partName.
	 */
	public AbstractDesign() {
		partName = null;
		name = null;
	}

	/**
	 * Constructor for an AbstractDesign with a name and partName.
	 * @param designName the name of the design
	 * @param partName the part this design targets
	 */
	public AbstractDesign(String designName, String partName) {
		setName(designName);
		setPartName(partName);
	}

	/**
	 * Returns the name of this design.
	 *
	 * @return the name of this design
	 */
	public String getName() {
		return name;
	}

	/**
	 * Sets the name of this design.
	 *
	 * @param name new name for this design
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the name with speed grade of the part associate with this
	 * design (eg xc7a100tcsg-3).
	 *
	 * @return the part name with package and speed grade information
	 */
	public String getPartName() {
		return this.partName;
	}

	/**
	 * Sets the part used for this design and loads the associated {@link Device}. The
	 * part name should include package and speed grade (eg xc7a100tcsg-3).
	 *
	 * @param partName name of the part
	 */
	public void setPartName(String partName) {
		this.partName = partName;
		this.device = RSEnvironment.defaultEnv().getDevice(partName);
	}

	/**
	 * Returns the {@link Device} associated with this design's {@code partName} or
	 * null if {@code partName} is not set.
	 *
	 * @return the device this design is targeted for
	 */
	public Device getDevice() {
		return device;
	}

	/**
	 * Returns the {@link FamilyType} for the part this design targets or null
	 * if partName is not set.
	 *
	 * @return the FamilyType of the part this design targets
	 */
	public FamilyType getFamily() {
		Device device = getDevice();
		return device != null ? device.getFamily() : null;
	}
}
