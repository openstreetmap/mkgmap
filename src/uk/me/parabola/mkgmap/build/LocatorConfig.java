/*
 * Copyright (C) 2006, 2011.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 or
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 */
package uk.me.parabola.mkgmap.build;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import uk.me.parabola.log.Logger;
import uk.me.parabola.mkgmap.reader.osm.Tags;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class LocatorConfig {
	private static final Logger log = Logger.getLogger(LocatorConfig.class);

	/** maps country name (in all variants) to the 3 letter ISO code */
	private final Map<String,String>  isoMap = new HashMap<String,String>();
	/** maps the ISO code to the offset of the region in the is_in tag */
	private final Map<String,Integer>  regOffsetMap = new HashMap<String,Integer>();
	/** maps the ISO code to the POI display flag */
	private final Map<String,Integer>  poiDispFlagMap = new HashMap<String,Integer>();
	/** contains the names of all continents */
	private final Set<String> continents = new HashSet<String>();

	/** maps ISO => default country name */
	private final Map<String, String> defaultCountryNames = new HashMap<String, String>();
	
	/** Maps 3 letter ISO code to all tags of a country */
	private final Map<String, Tags> countryTagMap = new HashMap<String, Tags>();
	
	private final static LocatorConfig instance = new LocatorConfig();
	
	public static LocatorConfig get() {
		return instance;
	}

	private LocatorConfig()
	{
		loadConfig("/LocatorConfig.xml");
	}

 	private void loadConfig(String fileName)
 	{
		try 
		{
			DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

			InputStream inStream;

			try
			{
				inStream = new FileInputStream("resources/" + fileName);
			}
			catch (Exception ex)
			{
				inStream = null;
			}

			if(inStream == null)	// If not loaded from disk use from jar file
				inStream = this.getClass().getResourceAsStream(fileName);

			Document document = builder.parse(inStream);
  		
			Node rootNode = document.getDocumentElement();
			
			if(rootNode.getNodeName().equals("locator"))
			{
				  Node cNode = rootNode.getFirstChild();

					while(cNode != null)
					{
						if(cNode.getNodeName().equals("continent"))
						{
							NamedNodeMap attr = cNode.getAttributes();
	
							if(attr != null)
							{
								Node nameTag = attr.getNamedItem("name");
								if(nameTag != null)
									addContinent(nameTag.getNodeValue());
							}

						}

						if(cNode.getNodeName().equals("country"))
						{
							NamedNodeMap attr = cNode.getAttributes();
							Node nameTag = null;
							Node abrTag = attr.getNamedItem("abr");
							String iso = null;
							if (abrTag != null) {
								iso = abrTag.getNodeValue().toUpperCase().trim().intern();
								if (iso.length() != 3) {
									log.error("ISO code (abr) must have three characters: "+iso);
								}
							}
							
							if(attr != null)
							{
								nameTag = attr.getNamedItem("name");
								
								if(iso != null && nameTag != null) {
									addISO(nameTag.getNodeValue(),iso);
									defaultCountryNames.put(iso, nameTag.getNodeValue().trim());
								}
								
								if (iso != null)
									addISO(iso, iso);
								
								if(iso == null && nameTag != null)					
									addISO(nameTag.getNodeValue(),"");

								
								Node regionOffsetTag = attr.getNamedItem("regionOffset");

								if(regionOffsetTag != null && iso != null)
								{
									addRegionOffset(iso,Integer.parseInt(regionOffsetTag.getNodeValue()));
								}

								Node poiDispTag = attr.getNamedItem("poiDispFlag");

								if(poiDispTag != null && iso != null)
								{
									addPoiDispTag(iso,Integer.decode(poiDispTag.getNodeValue()));
								}
							}

							if (iso != null) {
								Node cEntryNode = cNode.getFirstChild();
								while(cEntryNode != null)
								{
									if(cEntryNode.getNodeName().equals("variant"))
									{
										Node nodeText = cEntryNode.getFirstChild();
									
										if (nodeText != null)
											addISO(nodeText.getNodeValue(), iso);
									}
									cEntryNode = cEntryNode.getNextSibling();
								}
							}
						}

						cNode = cNode.getNextSibling();
					}
			}
			else
			{
				System.out.println(fileName + "contains invalid root tag " + rootNode.getNodeName());
			}
   	}
		catch (Exception ex)
		{
			ex.printStackTrace();
			//System.out.println("Something is wrong here");
		}
  	}

	private void addISO(String country, String iso)
	{
		String cStr = country.toUpperCase().trim();

		isoMap.put(cStr,iso);
	}

	private void addRegionOffset(String iso, Integer offset)
	{
		regOffsetMap.put(iso,offset);
	}

	private void addPoiDispTag(String iso, Integer flag)
	{
		poiDispFlagMap.put(iso,flag);
	}

	private void addContinent(String continent)
	{
		String cStr = continent.toUpperCase().trim();
		
		continents.add(cStr);
	}


	public synchronized void setDefaultCountry(String country, String abbr)
	{
		addISO(country, abbr);
	}

	public synchronized boolean isCountry(String country)
	{
		return isoMap.containsKey(country.toUpperCase().trim());
	}
	
	public synchronized boolean countryHasTags(String isoCode) {
		return countryTagMap.containsKey(isoCode);
	}
	
	public synchronized String addCountryWithTags(String isoCode, Tags countryTags) {
		
		if (isoCode == null) {
			// cannot find three letter iso code for this countries
			// do not use it
			log.warn("Cannot find country with tags", countryTags);
			return null;
		}
		
		if (countryHasTags(isoCode)) {
			// country is already known
			return isoCode;
		}
		
		// add it as new country to the tag map
		Tags cTagsCopy = new Tags();
		Iterator<Entry<String,String>> tagIter = countryTags.entryIterator();
		while (tagIter.hasNext()) {
			Entry<String,String> nextTag = tagIter.next();
			cTagsCopy.put(nextTag.getKey(), nextTag.getValue());
		}
		countryTagMap.put(isoCode, cTagsCopy);
		
		String name = countryTags.get("name");
		if (name != null) {
			addISO(name, isoCode);
		}
		String int_name = countryTags.get("int_name");
		if (int_name != null) {
			addISO(int_name, isoCode);
		}
		// add all variants to the abbreviation map
		for (String countryName : countryTags.getTagsWithPrefix("name:", false).values()) {
			addISO(countryName, isoCode);
		}
		return isoCode;
	}

	/**
	 * Retrieves the three letter ISO code which is used by the Garmins.
	 * @param country a country name 
	 * @return the three letter ISO code (<code>null</code> = unknown)
	 */
	public synchronized String getCountryISOCode(String country)
	{
		if (country == null) {
			return null;
		}
		return isoMap.get(country.toUpperCase().trim());
	}
	
	/**
	 * Retrieves the name of a country by its three letter iso code and the list of 
	 * name tags. The first available value of the tags in the nameTags list is returned.
	 * 
	 * @param isoCode the three letter ISO code
	 * @param nameTags the list of name tags 
	 * @return the full country name (<code>null</code> if unknown)
	 */
	public synchronized String getCountryName(String isoCode, List<String> nameTags) {
		Tags countryTags = countryTagMap.get(isoCode);
		if (countryTags==null) {
			// no tags for this country available
			// return the default country name from the LocatorConfig.xml
			return defaultCountryNames.get(isoCode);
		}
		
		// search for the first available tag of the nameTags list
		for (String nameTag : nameTags) {
			String name = countryTags.get(nameTag);
			if (name != null) {
				return name;
			}
		}
		
		// last try: just the simple "name" tag
		return countryTags.get("name");
	}

	public synchronized int getRegionOffset(String iso)
	{
		if (iso == null) {
			return 1;
		}
		
		Integer regOffset = regOffsetMap.get(iso);

		if(regOffset != null)
			return regOffset;
		else
			return 1; // Default is 1 the next string after before country
	}

	public synchronized int getPoiDispFlag(String iso)
	{
		if (iso == null) {
			return 0;
		}
		
		Integer flag = poiDispFlagMap.get(iso);

		if(flag != null)
			return flag;
		else
			return 0; // Default is 0 
	}

	public synchronized boolean isContinent(String continent)
	{
		String s = continent.toUpperCase().trim();
		return continents.contains(s);
	}		
}

