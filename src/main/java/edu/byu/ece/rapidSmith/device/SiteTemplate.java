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

import edu.byu.ece.rapidSmith.util.ArraySet;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

/**
 *  Template to back sites that contains information common to
 *  all sites of a specific type.
 */
public final class SiteTemplate implements Serializable {
	private static final long serialVersionUID = -899254253693716120L;
	// The type of this site template
	private SiteType type;
	// Site types that can be placed on sites of this type
	private Set<SiteType> compatibleTypes;

	// The intrasite routing graph structure
	private Map<SiteWireTemplate, SiteNodeTemplate> wireNodesMap;
	private Map<String, SiteWireTemplate> siteWires;

	private List<BelTemplate> belTemplates;
	private Map<String, BelTemplate> belNamesMap;

	private List<SitePinTemplate> pinTemplates;
	private Map<String, SitePinTemplate> pinNamesMap;

	// Map containing the bel routethrough information of the site
	private Map<SiteWireTemplate, Set<SiteWireTemplate>> belRoutethroughMap;

	public SiteType getType() {
		return type;
	}

	public void setType(SiteType type) {
		this.type = type;
	}

	public List<BelTemplate> getBelTemplates() {
		return belTemplates;
	}

	public Map<String, BelTemplate> getBelNamesMap() {
		return belNamesMap;
	}

	public void setBelTemplates(List<BelTemplate> belTemplates) {
		this.belTemplates = belTemplates;
		switch (belTemplates.size()) {
			case 0:
				this.belNamesMap = emptyMap();
				break;
			case 1:
				BelTemplate first = belTemplates.get(0);
				this.belNamesMap = singletonMap(first.getId().getName(), first);
				break;
			case 2:
				this.belNamesMap = belTemplates.stream()
					.collect(Collectors.toMap(k -> k.getId().getName(), k -> k));
				break;
		}
	}

	public Set<SiteType> getCompatibleTypes() {
		return compatibleTypes;
	}

	public void setCompatibleTypes(ArraySet<SiteType> compatibleTypes) {
		this.compatibleTypes = compatibleTypes;
	}

	public Map<SiteWireTemplate, SiteNodeTemplate> getWireNodesMap() {
		return wireNodesMap;
	}

	public void setWireNodesMap(Map<SiteWireTemplate, SiteNodeTemplate> wireNodesMap) {
		this.wireNodesMap = wireNodesMap;
	}

	public List<SitePinTemplate> getPinTemplates() {
		return pinTemplates;
	}

	public Map<String, SitePinTemplate> getPinNamesMap() {
		return pinNamesMap;
	}

	public void setPinTemplates(List<SitePinTemplate> pinTemplates) {
		this.pinTemplates = pinTemplates;
		switch (pinTemplates.size()) {
			case 0:
				this.pinNamesMap = emptyMap();
				break;
			case 1:
				SitePinTemplate first = pinTemplates.get(0);
				this.pinNamesMap = singletonMap(first.getName(), first);
				break;
			case 2:
				this.pinNamesMap = pinTemplates.stream()
					.collect(Collectors.toMap(k -> k.getName(), k -> k));
				break;
		}
	}

	public Map<String, SiteWireTemplate> getSiteWires() {
		return siteWires;
	}

	public void setSiteWires(Map<String, SiteWireTemplate> siteWires) {
		this.siteWires = siteWires;
	}

	public Map<SiteWireTemplate, Set<SiteWireTemplate>> getBelRoutethroughMap() {
		return belRoutethroughMap;
	}

	public void setBelRoutethroughs(Map<SiteWireTemplate, Set<SiteWireTemplate>> belRoutethroughs) {
		this.belRoutethroughMap = belRoutethroughs;
	}

	@Override
	public String toString() {
		return "SiteTemplate{" +
				"type=" + type +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SiteTemplate other = (SiteTemplate) o;
		return type == other.type;
	}

	@Override
	public int hashCode() {
		return type.hashCode();
	}

	// for hessian compression
	private static class SiteTemplateReplace implements Serializable  {
		private static final long serialVersionUID = 5457581694407570610L;
		private SiteType type;
		private List<BelTemplate> belTemplates;
		private Set<SiteType> compatibleTypes;
		private Map<SiteWireTemplate, Set<SiteWireTemplate>> belRoutethroughMap;
		private Map<String, SiteWireTemplate> siteWires;

		public Object readResolve() {
			SiteTemplate template = new SiteTemplate();
			template.type = type;
			template.setBelTemplates(belTemplates);
			template.compatibleTypes = compatibleTypes;
			template.belRoutethroughMap = belRoutethroughMap;
			template.siteWires = siteWires;

			return template;
		}
	}

	public SiteTemplateReplace writeReplace() {
		SiteTemplateReplace repl = new SiteTemplateReplace();
		repl.type = type;
		repl.belTemplates = belTemplates;
		repl.compatibleTypes = compatibleTypes;
		repl.belRoutethroughMap = belRoutethroughMap;
		repl.siteWires = siteWires;

		return repl;
	}
}
