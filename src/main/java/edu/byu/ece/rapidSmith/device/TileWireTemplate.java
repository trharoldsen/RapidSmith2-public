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

import edu.byu.ece.rapidSmith.util.Ordinable;

public class TileWireTemplate implements Ordinable<TileWireTemplate> {
	private static final long serialVersionUID = -349075013690610863L;
	private final String name;
	private final Offset nodeOffset;
	private final int ordinal;

	public TileWireTemplate(String name, Offset nodeOffset, int ordinal) {
		this.name = name;
		this.nodeOffset = nodeOffset;
		this.ordinal = ordinal;
	}

	public String getName() {
		return name;
	}

	public Offset getNodeOffset() {
		return nodeOffset;
	}

	@Override
	public int getOrdinal() {
		return ordinal;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		TileWireTemplate that = (TileWireTemplate) o;
		return ordinal == that.ordinal;
	}

	@Override
	public int hashCode() {
		return ordinal;
	}
}
