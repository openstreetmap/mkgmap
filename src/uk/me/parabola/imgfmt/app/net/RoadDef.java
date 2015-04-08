/*
 * Copyright (C) 2007 Steve Ratcliffe
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
 * 
 * Author: Steve Ratcliffe
 * Create date: Jan 5, 2008
 */
package uk.me.parabola.imgfmt.app.net;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import uk.me.parabola.imgfmt.MapFailedException;
import uk.me.parabola.imgfmt.app.BitWriter;
import uk.me.parabola.imgfmt.app.ImgFileWriter;
import uk.me.parabola.imgfmt.app.Label;
import uk.me.parabola.imgfmt.app.lbl.City;
import uk.me.parabola.imgfmt.app.lbl.Zip;
import uk.me.parabola.imgfmt.app.trergn.Polyline;
import uk.me.parabola.log.Logger;

/**
 * A road definition.  This ties together all segments of a single road
 * and provides street address information.
 *
 * This corresponds to an entry in NET1, which is linked with the
 * polylines making up this road in RGN. Links to RGN are written
 * via RoadIndex, while writing links from RGN to NET1 is delayed
 * via setOffsetWriter.
 *
 * If the map includes routing, the NET1 record also points to
 * a NOD2 record, written by writeNod2.
 *
 * Edges in the routing graph ("arcs") link to the corresponding
 * road via the RoadDef, storing the NET1 offset via TableA,
 * which also includes some road information.
 *
 * @author Elrond
 * @author Steve Ratcliffe
 * @author Robert Vollmert
 */

public class RoadDef {
	private static final Logger log = Logger.getLogger(RoadDef.class);

	public static final int NET_FLAG_NODINFO  = 0x40;
	public static final int NET_FLAG_ADDRINFO = 0x10;
	private static final int NET_FLAG_UNK1     = 0x04; // lock on road?
	private static final int NET_FLAG_ONEWAY   = 0x02;

	private static final int NOD2_FLAG_UNK        = 0x01;
//	private static final int NOD2_FLAG_EXTRA_DATA = 0x80; just documentation

	// first byte of Table A info in NOD 1
	private static final int TABA_FLAG_TOLL = 0x80;
//	private static final int TABA_MASK_CLASS = 0x70; just documentation
	private static final int TABA_FLAG_ONEWAY = 0x08;
//	private static final int TABA_MASK_SPEED = 0x07; just documentation

	private static final int TABAACCESS_FLAG_CARPOOL = 0x0008;
	private static final int TABAACCESS_FLAG_NOTHROUGHROUTE = 0x0080;
	
	// second byte: access flags, bits 0x08, 0x80 are set separately 
	private static final int TABAACCESS_FLAG_NO_EMERGENCY = 0x8000;
	private static final int TABAACCESS_FLAG_NO_DELIVERY  = 0x4000;
	private static final int TABAACCESS_FLAG_NO_CAR     = 0x0001;
	private static final int TABAACCESS_FLAG_NO_BUS     = 0x0002;
	private static final int TABAACCESS_FLAG_NO_TAXI    = 0x0004;
	private static final int TABAACCESS_FLAG_NO_FOOT    = 0x0010;
	private static final int TABAACCESS_FLAG_NO_BIKE    = 0x0020;
	private static final int TABAACCESS_FLAG_NO_TRUCK   = 0x0040;
	
	// true if road should not be added to NOD 
	private boolean skipAddToNOD;
	
	// the offset in Nod2 of our Nod2 record
	private int offsetNod2;

	// the offset in Net1 of our Net1 record
	private int offsetNet1;

	/*
	 * Everything that's relevant for writing to NET1.
	 */
	private int netFlags = NET_FLAG_UNK1;

	// the allowed vehicles in mkgmap internal format
	private byte mkgmapAccess; 
	
	// The road length units may be affected by other flags in the header as
	// there is doubt as to the formula.
	private int roadLength;

	// There can be up to 4 labels for the same road.
	private static final int MAX_LABELS = 4;

	private final Label[] labels = new Label[MAX_LABELS];
	private int numlabels;

	private final SortedMap<Integer,List<RoadIndex>> roadIndexes = new TreeMap<>();

	private City city;
	private Zip zip;
	private boolean paved = true;
	private boolean ferry;
	private boolean roundabout;
	private boolean linkRoad;
	private boolean synthesised;
	private boolean flareCheck;
	private Set<String> messageIssued;

	private final List<Offset> rgnOffsets = new ArrayList<>();
	// for the NOD2 bit stream   
	private BitSet nod2BitSet;

	/*
	 * Everything that's relevant for writing out Nod 2.
	 */
	// This is the node associated with the road.  I'm not certain about how
	// this works, but in NOD2 each road has a reference to only one node.
	// This is that node.
	private RouteNode node;

	// the first point in the road is a node (the above routing node)
	private boolean startsWithNode = true;
	// number of nodes in the road
	private int nnodes;

	// always appears to be set
	private int nod2Flags = NOD2_FLAG_UNK;

	// The data for Table A
	private int tabAInfo;
	private int tabAAccess;

	// for diagnostic purposes
	private final long id;
	private final String name;
	private List<Numbers> numbersList;
	private int nodeCount;

	public RoadDef(long id, String name) {
		this.id = id;
		this.name = name;
	}

	/**
	 * A constructor that is used when reading a file and you know the NET1 offset. When writing
	 * the offsetNet1 field is filled in during the writing process.
	 * @param id Road id
	 * @param net1offset The offset in the road defs section of the NET file.
	 * @param name The main of the road.
	 */
	public RoadDef(long id, int net1offset, String name) {
		this.id = id;
		this.offsetNet1 = net1offset;
		this.name = name;
	}

	// for diagnostic purposes
	public String toString() {
		// assumes id is an OSM id
		String browseURL = "http://www.openstreetmap.org/browse/way/" + id;
		//if(getName() != null)
		//	return "(" + getName() + ", " + browseURL + ")";
		//else
			return "(" + browseURL + ")";
	}

	public String getName() {
		if (name != null)
			return name;
		if (labels[0] != null)
			return labels[0].toString();
		return null;
	}

	public long getId() {
		return id;
	}


	/**
	 * This is for writing to NET1.
	 * @param writer A writer that is positioned within NET1.
	 */
	void writeNet1(ImgFileWriter writer, int numCities, int numZips) {
		if (numlabels == 0)
			return;
		assert numlabels > 0;

		offsetNet1 = writer.position();

		NumberPreparer numbers = null;
		if (numbersList != null) {
			numbers = new NumberPreparer(numbersList);
			numbers.fetchBitStream();
			if (!numbers.isValid()){
				numbers = null;
				log.warn("Invalid housenumbers in",this.toString());
			}
		}

		writeLabels(writer);
		if (numbers != null && numbers.getSwapped()) {
			netFlags |= 0x20; // swapped default; left=even, right=odd
		}
		writer.put((byte) netFlags);
		writer.put3(roadLength);

		int maxlevel = writeLevelCount(writer);

		writeLevelDivs(writer, maxlevel);

		if((netFlags & NET_FLAG_ADDRINFO) != 0) {
			nodeCount--;
			if (nodeCount + 2 != nnodes){
				log.error("internal error? The nodeCount doesn't match value calculated by RoadNetWork:",this);
			}
			writer.put((byte) (nodeCount & 0xff)); // lo bits of node count

			int code = 0xe8;     // zip and city present
			code |= ((nodeCount >> 8) & 0x3); // top bits of node count
			if(city == null)
				code |= 0x10; // no city
			if(zip == null)
				code |= 0x04; // no zip
			if (numbers != null) {
				code &= ~0xc0;
				if (numbers.fetchBitStream().getLength() > 255)
					code |= 0x40;
			}
			writer.put((byte)code);
			if(zip != null) {
				char zipIndex = (char)zip.getIndex();
				if(numZips > 255)
					writer.putChar(zipIndex);
				else
					writer.put((byte)zipIndex);
			}
			if(city != null) {
				char cityIndex = (char)city.getIndex();
				if(numCities > 255)
					writer.putChar(cityIndex);
				else
					writer.put((byte)cityIndex);
			}
			if (numbers != null) {
				BitWriter bw = numbers.fetchBitStream();
				if (bw.getLength() > 255)
					writer.putChar((char) bw.getLength());
				else
					writer.put((byte) bw.getLength());
				writer.put(bw.getBytes(), 0, bw.getLength());
			}
		}

		if (hasNodInfo()) {
			// This is the offset of an entry in NOD2
			int val = offsetNod2;
			if (val < 0x7fff) {
				writer.put((byte) 1);
				writer.putChar((char) val);
			} else {
				writer.put((byte) 2);
				writer.put3(val);
			}
		}
	}

	private void writeLabels(ImgFileWriter writer) {
		for (int i = 0; i < numlabels; i++) {
			Label l = labels[i];
			int ptr = l.getOffset();
			if (i == (numlabels-1))
				ptr |= 0x800000;
			writer.put3(ptr);
		}
	}

	public void putSortedRoadEntry(ImgFileWriter writer, Label label) {
		for(int i = 0; i < labels.length && labels[i] != null; ++i) {
			if(labels[i].equals(label)) {
				writer.put3((i << 22) | offsetNet1);
				return;
			}
		}
	}

	private int writeLevelCount(ImgFileWriter writer) {
		int maxlevel = getMaxZoomLevel();
		for (int i = 0; i <= maxlevel; i++) {
			List<RoadIndex> l = roadIndexes.get(i);
			int b = (l == null) ? 0 : l.size();
			assert b < 0x80 : "too many polylines at level " + i;
			if (i == maxlevel)
				b |= 0x80;
			writer.put((byte) b);
		}
		return maxlevel;
	}

	private void writeLevelDivs(ImgFileWriter writer, int maxlevel) {
		for (int i = 0; i <= maxlevel; i++) {
			List<RoadIndex> l = roadIndexes.get(i);
			if (l != null) {
				for (RoadIndex ri : l)
					ri.write(writer);
			}
		}
	}

	public void addLabel(Label l) {
		int i;
		for (i = 0; i < MAX_LABELS && labels[i] != null; ++i) {
			if (l.equals(labels[i])) {
				// label already present
				return;
			}
		}

		if (i < MAX_LABELS) {
			labels[i] = l;
			++numlabels;
		}
		else
			log.warn(this.toString() + " discarding extra label (already have " + MAX_LABELS + ")");
	}

	public Label[] getLabels() {
		return labels;
	}

	/**
	 * Add a polyline to this road.
	 *
	 * References to these are written to NET. At a given zoom
	 * level, we're writing these in the order we get them,
	 * which must(!) be the order the segments have
	 * in the road.
	 */
	public void addPolylineRef(Polyline pl) {
		if(log.isDebugEnabled())
			log.debug("adding polyline ref", this, pl.getSubdiv());
		int level = pl.getSubdiv().getZoom().getLevel();
		List<RoadIndex> l = roadIndexes.get(level);
		if (l == null) {
			l = new ArrayList<>();
			roadIndexes.put(level, l);
		}
		l.add(new RoadIndex(pl));

		if (level == 0) {
			nodeCount += pl.getNodeCount(hasHouseNumbers());
		}
	}

	private int getMaxZoomLevel() {
		return roadIndexes.lastKey();
	}

	public boolean connectedTo(RoadDef other) {
		List<RoadIndex> l = roadIndexes.get(0);
		if(l == null)
			return false;

		List<RoadIndex> ol = other.roadIndexes.get(0);
		if(ol == null)
			return false;

		for(RoadIndex ri : l)
			for(RoadIndex ori : ol)
				if(ri.getLine().sharesNodeWith(ori.getLine()))
					return true;
		return false;
	}

	public boolean sameDiv(RoadDef other) {
		return getStartSubdivNumber() == other.getStartSubdivNumber();
	}

	public int getStartSubdivNumber() {
		Integer key = roadIndexes.firstKey();
		return roadIndexes.get(key).get(0).getLine().getSubdiv().getNumber();
	}

	/**
	 * Set the road length (in meters).
	 */
	public void setLength(double lenInMeter) {
		roadLength = NODHeader.metersToRaw(lenInMeter);
	}

	public boolean hasHouseNumbers() {
		return numbersList != null && !numbersList.isEmpty();
	}

	/*
	 * Everything that's relevant for writing to RGN.
	 */
	class Offset {
		final int position;
		final int flags;

		Offset(int position, int flags) {
			this.position = position;
			this.flags = flags;
		}

		int getPosition() {
			return position;
		}

		int getFlags() {
			return flags;
		}
	}


	/**
	 * Add a target location in the RGN section where we should write the
	 * offset of this road def when it is written to NET.
	 *
	 * @param position The offset in RGN.
	 * @param flags The flags that should be set.
	 */
	public void addOffsetTarget(int position, int flags) {
		rgnOffsets.add(new Offset(position, flags));
	}

	/**
	 * Write into the RGN the offset in net1 of this road.
	 * @param rgn A writer for the rgn file.
	 */
	void writeRgnOffsets(ImgFileWriter rgn) {
		if (offsetNet1 >= 0x400000)
			throw new MapFailedException("Overflow of the NET1. The tile ("
							+ log.threadTag()
							+ ") must be split so that there are fewer roads in it");

		for (Offset off : rgnOffsets) {
			rgn.position(off.getPosition());
			rgn.put3(offsetNet1 | off.getFlags());
		}
	}

	private boolean internalNodes;

	/**
	 * Does the road have any nodes besides start and end?
	 * These can be number nodes or routing nodes.
	 * This affects whether we need to write extra bits in
	 * the bitstream in RGN.
	 */
	public boolean hasInternalNodes() {
		return internalNodes;
	}

	public void setInternalNodes(boolean n) {
		internalNodes = n;
	}

	/**
	 * Set the routing node associated with this road.
	 *
	 * This implies that the road has an entry in NOD 2
	 * which will be pointed at from NET 1.
	 */
	public void setNode(RouteNode node) {
		if (skipAddToNOD)
			return;
		netFlags |= NET_FLAG_NODINFO;
		this.node = node;
	}

	public RouteNode getNode(){
		return node;
	}
	
	private boolean hasNodInfo() {
		return (netFlags & NET_FLAG_NODINFO) != 0;
	}

	public void setStartsWithNode(boolean s) {
		startsWithNode = s;
	}

	public void setNumNodes(int n) {
		nnodes = n;
	}

	public void setNumbersList(List<Numbers> numbersList) {
		if (numbersList != null && !numbersList.isEmpty()) {
			this.numbersList = numbersList;
			netFlags |= NET_FLAG_ADDRINFO;
		}
	}
	public List<Numbers> getNumbersList() {
		return numbersList;
	}


	/**
	 * Write this road's NOD2 entry.
	 *
	 * Stores the writing position to be able to link here
	 * from NET 1 later.
	 *
	 * @param writer A writer positioned in NOD2.
	 */
	public void writeNod2(ImgFileWriter writer) {
		if (!hasNodInfo())
			return;
		if (skipAddToNOD){
			// should not happen
			log.error("internal error: writeNod2 called for roaddef with skipAddToNOD=true");
			return;
		}

		log.debug("writing nod2");

		offsetNod2 = writer.position();

		writer.put((byte) nod2Flags);
		writer.put3(node.getOffsetNod1()); // offset in nod1

		// this is related to the number of nodes, but there
		// is more to it...
		// For now, shift by one if the first node is not a
		// routing node.
		// If the road has house numbers, we count also
		// the number nodes, and these get a 0 in the bit stream. 
		int nbits = nnodes;
		if (!startsWithNode)
			nbits++;
		writer.putChar((char) nbits);
		boolean[] bits = new boolean[nbits];
		
		if (hasHouseNumbers()){
			int off = startsWithNode ? 0 :1;
			for (int i = 0; i < bits.length; i++){
				if (nod2BitSet.get(i))
					bits[i+off] = true;
			}
		} else { 
			for (int i = 0; i < bits.length; i++)
				bits[i] = true;
			if (!startsWithNode)
				bits[0] = false;
		}
		for (int i = 0; i < bits.length; i += 8) {
			int b = 0;
            for (int j = 0; j < 8 && j < bits.length - i; j++)
				if (bits[i+j])
					b |= 1 << j;
			writer.put((byte) b);
		}
	}

	/*
	 * Everything that's relevant for writing out Table A.
	 *
	 * Storing this info in the RoadDef means that each
	 * arc gets the same version of the below info, which
	 * makes sense for the moment considering polish format
	 * doesn't provide for different speeds and restrictions
	 * for segments of roads.
	 */

	/**
	 * Return the offset of this road's NET1 entry. Assumes
	 * writeNet1() has been called.
	 */
	public int getOffsetNet1() {
		return offsetNet1;
	}

	/**
	 * Flag that a toll must be payed when using this road.
	 */
	public void setToll() {
		tabAInfo |= TABA_FLAG_TOLL;
	}
	
	/**
	 * Flag that the road has a carpool lane.<br>
	 * Warning: This bit does not seem to work. Maybe it does not control
	 * the carpool flag.
	 */
	public void setCarpoolLane() {
		tabAAccess |= TABAACCESS_FLAG_CARPOOL;
	}

	/**
	 * Sets the flag that routing is allowed only if the route starts or
	 * end on this road. 
	 */
	public void setNoThroughRouting() {
		tabAAccess |= TABAACCESS_FLAG_NOTHROUGHROUTE;
	}

	/**
	 * @return allowed vehicles in mkgmap format  
	 */
	public byte getAccess() {
		return mkgmapAccess;
	}

	/**
	 * Set allowed vehicles
	 * @param mkgmapAccess bit mask in mkgmap format
	 */
	public void setAccess(byte mkgmapAccess) {
		this.mkgmapAccess = mkgmapAccess;
		// translate internal format to that used in TableA
		//clear the corresponding bits
		tabAAccess &= ~(0xc077);
		if (mkgmapAccess == (byte) 0xff)
			return; // all vehicles allowed

		if ((mkgmapAccess & AccessTagsAndBits.FOOT) == 0)
			tabAAccess |= TABAACCESS_FLAG_NO_FOOT; 
		if ((mkgmapAccess & AccessTagsAndBits.BIKE) == 0)
			tabAAccess |=TABAACCESS_FLAG_NO_BIKE;
		if ((mkgmapAccess & AccessTagsAndBits.CAR) == 0)
			tabAAccess |=TABAACCESS_FLAG_NO_CAR;
		if ((mkgmapAccess & AccessTagsAndBits.DELIVERY) == 0)
			tabAAccess |=TABAACCESS_FLAG_NO_DELIVERY;
		if ((mkgmapAccess & AccessTagsAndBits.TRUCK) == 0)
			tabAAccess |=TABAACCESS_FLAG_NO_TRUCK;
		if ((mkgmapAccess & AccessTagsAndBits.BUS) == 0)
			tabAAccess |=TABAACCESS_FLAG_NO_BUS;
		if ((mkgmapAccess & AccessTagsAndBits.TAXI) == 0)
			tabAAccess |=TABAACCESS_FLAG_NO_TAXI;
		if ((mkgmapAccess & AccessTagsAndBits.EMERGENCY) == 0)
			tabAAccess |=TABAACCESS_FLAG_NO_EMERGENCY;
	}
	
	public int getTabAInfo() {
		return tabAInfo;
	}

	public int getTabAAccess() {
		return tabAAccess;
	}

	/*
	 * These affect various parts.
	 */

	private int roadClass = -1;


	// road class that goes in various places (really?)
	public void setRoadClass(int roadClass) {
		assert roadClass < 0x08;
		
		/* for RouteArcs to get as their "destination class" */
		this.roadClass = roadClass;

		/* for Table A */
		int shifted = (roadClass << 4) & 0xff;
		tabAInfo |= shifted;

		/* for NOD 2 */
		nod2Flags |= shifted;
	}

	public int getRoadClass() {
		assert roadClass >= 0 : "roadClass not set";
		return roadClass;
	}

	public void setSpeed(int speed) {
		assert speed < 0x08;

		/* for Table A */
		tabAInfo |= speed;

		/* for NOD 2 */
		nod2Flags |= (speed << 1);
	}

	public int getRoadSpeed() {
		return tabAInfo & 7;
	}

	public void setOneway() {
		tabAInfo |= TABA_FLAG_ONEWAY;
		netFlags |= NET_FLAG_ONEWAY;
	}

	public boolean isOneway() {
		return (netFlags & NET_FLAG_ONEWAY) != 0;
	}

	public void setCity(City city) {
		this.city = city;
		netFlags |= NET_FLAG_ADDRINFO;
	}

	public void setZip(Zip zip) {
		this.zip = zip;
		netFlags |= NET_FLAG_ADDRINFO;
	}

	public City getCity() {
		return city;
	}

	public boolean paved() {
		return paved;
	}

	public void paved(boolean p) {
		paved = p;
	}

	public void ferry(boolean f) {
		ferry = f;
	}

	public boolean ferry() {
		return ferry;
	}

	public void setRoundabout(boolean r) {
		roundabout = r;
	}

	public boolean isRoundabout() {
		return roundabout;
	}

	public void setLinkRoad(boolean lr) {
		linkRoad = lr;
	}

	public boolean isLinkRoad() {
		return linkRoad;
	}

	public void setSynthesised(boolean s) {
		synthesised = s;
	}

	public boolean isSynthesised() {
		return synthesised;
	}

	public void doFlareCheck(boolean fc) {
		flareCheck = fc;
	}

	public boolean doFlareCheck() {
		return flareCheck;
	}

	public boolean messagePreviouslyIssued(String key) {
		if(messageIssued == null)
			messageIssued = new HashSet<>();
		boolean previouslyIssued = messageIssued.contains(key);
		messageIssued.add(key);
		return previouslyIssued;
	}

	public void setNod2BitSet(BitSet bs) {
		if (skipAddToNOD)
			return;
		nod2BitSet = bs;
	}

	public boolean skipAddToNOD() {
		return skipAddToNOD;
	}

	public void skipAddToNOD(boolean skip) {
		this.skipAddToNOD = skip;
	}
	
}
