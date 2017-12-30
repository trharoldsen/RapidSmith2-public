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

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

/**
 *  A template that backs BELs of each BEL id in the device.
 */
public final class BelTemplate implements Serializable {
	private static final long serialVersionUID = 2908083429845269712L;
	private int hashCode = 0;
	private final BelId id;
	// Type of the BEL, not a part of XDLRC
	private final String type;
	// BelPinTemplates for each pin on the BEL
	private List<BelPinTemplate> pinTemplates;
	private transient Map<String, BelPinTemplate> pinNamesMap;

	public BelTemplate(BelId id, String type) {
		this.id = id;
		this.type = type;
	}

	public BelId getId() {
		return id;
	}

	public String getType() {
		return type;
	}

	public List<BelPinTemplate> getPinTemplates() {
		return pinTemplates;
	}

	public Map<String, BelPinTemplate> getPinNamesMap() {
		return pinNamesMap;
	}

	public void setPinTemplates(List<BelPinTemplate> pinTemplates) {
		this.pinTemplates = pinTemplates;
		this.pinNamesMap = pinTemplates.stream().collect(toMap(k -> k.getName(), k -> k));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		BelTemplate that = (BelTemplate) o;
		return Objects.equals(id, that.id) &&
				Objects.equals(type, that.type);
	}

	@Override
	public int hashCode() {
		if (hashCode == 0)
			hashCode = Objects.hash(id, type);
		return hashCode;
	}

	@Override
	public String toString() {
		return "BelTemplate{" +
				"id=" + id +
				'}';
	}
}
