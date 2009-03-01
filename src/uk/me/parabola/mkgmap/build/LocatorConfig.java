/*
 * Copyright (C) 2009 Bernhard Heibler
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 *  The Locator tries to fill missing country, region, postal coude information
 *
 *  To do so we analyse the is_in information and if this doesn't helps us we
 *  try to get info from the next known city
 * 
 * Author: Bernhard Heibler
 * Create date: 02-Jan-2009
 */
package uk.me.parabola.mkgmap.build;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class LocatorConfig {

	private final Map<String,String>  variantMap = new HashMap<String,String>();
	private final Map<String,String>  abrMap = new HashMap<String,String>();
	private final Map<String,Boolean> geoDbMap = new HashMap<String,Boolean>();
	private final Map<String,Integer>  regOffsetMap = new HashMap<String,Integer>();
	private final Map<String,Integer>  poiDispFlagMap = new HashMap<String,Integer>();
	private final Map<String,Boolean> continentMap = new HashMap<String,Boolean>();


	public LocatorConfig()
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
							Node nameTag;
	
							if(attr != null)
							{
								nameTag = attr.getNamedItem("name");
								if(nameTag != null)
									addContinent(nameTag.getNodeValue());
							}

						}

						if(cNode.getNodeName().equals("country"))
						{
							NamedNodeMap attr = cNode.getAttributes();
							Node nameTag = null;

							if(attr != null)
							{
								nameTag = attr.getNamedItem("name");
								
								Node abrTag = attr.getNamedItem("abr");

								if(abrTag != null && nameTag != null)
									addAbr(nameTag.getNodeValue(),abrTag.getNodeValue());
								
								if(abrTag == null && nameTag != null)					
									addAbr(nameTag.getNodeValue(),"");

								Node geoTag = attr.getNamedItem("geodb");

								if(nameTag != null && geoTag != null)
								{
									if(geoTag.getNodeValue().equals("1"))
										addOpenGeoDb(nameTag.getNodeValue());
								}

								Node regionOffsetTag = attr.getNamedItem("regionOffset");

								if(regionOffsetTag != null && nameTag != null)
								{
									addRegionOffset(nameTag.getNodeValue(),Integer.parseInt(regionOffsetTag.getNodeValue()));
								}

								Node poiDispTag = attr.getNamedItem("poiDispFlag");

								if(poiDispTag != null && nameTag != null)
								{
									addPoiDispTag(nameTag.getNodeValue(),Integer.decode(poiDispTag.getNodeValue()));
								}
							}

							Node cEntryNode = cNode.getFirstChild();
							while(cEntryNode != null)
							{
								if(cEntryNode.getNodeName().equals("variant"))
								{
									Node nodeText = cEntryNode.getFirstChild();

									if(nodeText != null && nameTag != null)
										addVariant(nameTag.getNodeValue(), nodeText.getNodeValue());
										
								}
								cEntryNode = cEntryNode.getNextSibling();
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

	private void addVariant(String country, String variant)
	{
		String cStr = country.toUpperCase().trim();
		String vStr = variant.toUpperCase().trim();

		//System.out.println(vStr + " -> " + cStr);

		variantMap.put(vStr,cStr);
	}

	private void addAbr(String country, String abr)
	{
		String cStr = country.toUpperCase().trim();
		String aStr = abr.toUpperCase().trim();

		//System.out.println(cStr + " -> " + aStr);

		abrMap.put(cStr,aStr);
	}

	private void addRegionOffset(String country, Integer offset)
	{
		String cStr = country.toUpperCase().trim();

		//System.out.println(cStr + " -> " + offset);

		regOffsetMap.put(cStr,offset);
	}

	private void addPoiDispTag(String country, Integer flag)
	{
		String cStr = country.toUpperCase().trim();

		//System.out.println(cStr + " -> " + flag);

		poiDispFlagMap.put(cStr,flag);
	}

	private void addOpenGeoDb(String country)
	{
		String cStr = country.toUpperCase().trim();
		
		//System.out.println(cStr + " openGeoDb");
		
		geoDbMap.put(cStr,true);
		
	}

	private void addContinent(String continent)
	{
		String cStr = continent.toUpperCase().trim();
		
		//System.out.println(cStr + " continent");
		
		continentMap.put(cStr,true);
		
	}


	public void setDefaultCountry(String country, String abbr)
	{
		addAbr(country, abbr);
	}

	public String fixCountryString(String country)
	{
		String cStr = country.toUpperCase().trim();
		
		String fixedString = variantMap.get(cStr);

		if(fixedString != null)
			return fixedString;
		else
			return(cStr);
	}

	public String isCountry(String country)
	{
		String cStr = fixCountryString(country);

		if(getCountryCode(cStr) != null)
			return cStr;
		else
			return null;
	
	}

	public String getCountryCode(String country)
	{
		String cStr = country.toUpperCase().trim();
		return abrMap.get(cStr);
	}

	public int getRegionOffset(String country)
	{
		String cStr = country.toUpperCase().trim();
		
		Integer regOffset = regOffsetMap.get(cStr);

		if(regOffset != null)
			return regOffset;
		else
			return 1; // Default is 1 the next string after before country
	}

	public int getPoiDispFlag(String country)
	{
		String cStr = country.toUpperCase().trim();
		
		Integer flag = poiDispFlagMap.get(cStr);

		if(flag != null)
			return flag;
		else
			return 0; // Default is 1 the next string after before country
	}

	public boolean isOpenGeoDBCountry(String country)
	{
		// Countries that have open geo db data in osm
		// Right now this are only germany, austria and swizerland

		String cStr = country.toUpperCase().trim();

		if(geoDbMap.get(cStr) != null)
			return true;
		
		return false;
	}

	public boolean isContinent(String continent)
	{
		String s = continent.toUpperCase().trim();

		if(continentMap.get(s) != null)
			return(true);

		return false;
	}		
}

