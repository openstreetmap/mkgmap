package uk.me.parabola.mkgmap.osmstyle.housenumber;

import uk.me.parabola.imgfmt.app.Coord;
import uk.me.parabola.mkgmap.general.CityInfo;
import uk.me.parabola.mkgmap.general.MapRoad;
import uk.me.parabola.mkgmap.reader.osm.Element;
import uk.me.parabola.mkgmap.reader.osm.Node;
import uk.me.parabola.mkgmap.reader.osm.Way;
import uk.me.parabola.util.Locatable;

class HousenumberElem implements Locatable{
	protected final Element element;
	private boolean valid;
	private boolean processedAsPlace;
	private int housenumber;
	private String sign;
	private String place;
	private CityInfo cityInfo;
	private String zipCode;
	private String street;
	//cache for Way elements to prevent calling Way.getCofG() repeatedly
	private Coord location;
	
	public HousenumberElem(Element el, CityInfo ci) {
		this.element = el;
		this.cityInfo = ci;
	}

	public HousenumberElem(HousenumberElem he) {
		this.element = he.element;
		this.valid = he.valid;
		this.housenumber = he.housenumber;
		this.sign = he.sign;
		this.street = he.street;
		this.place = he.place;
		this.cityInfo = he.cityInfo;
		this.zipCode = he.zipCode;
		this.location = he.location;
	}

	public Element getElement() {
		return element;
	}

	public boolean isValid() {
		return valid;
	}

	public void setValid(boolean valid) {
		this.valid = valid;
	}

	public boolean processedAsPlace() {
		return processedAsPlace;
	}

	public void setProcessedAsPlace(boolean processed) {
		this.processedAsPlace = processed;
	}

	public int getHousenumber() {
		return housenumber;
	}

	public void setHousenumber(int housenumber) {
		this.housenumber = housenumber;
	}

	public String getSign() {
		return sign;
	}

	public void setSign(String sign) {
		this.sign = sign;
	}

	public String getPlace() {
		return place;
	}

	public void setPlace(String place) {
		this.place = place;
	}

	public String getStreet() {
		return street;
	}

	public void setStreet(String street) {
		this.street = street;
	}

	public MapRoad getRoad(){
		return null;
	}
	
	public CityInfo getCityInfo(){
		return cityInfo;
	}
	
	public void setZipCode(String zip){
		zipCode = zip;
	}
	
	public String getZipCode(){
		return zipCode;
	}
	
	@Override
	public Coord getLocation() {
		if (location == null){
			if (element instanceof Node)
				location = ((Node) element).getLocation();
			else 
				location = ((Way) element).getCofG();
		}
		return location;
	}
	
	@Override
	public String toString() {
		if (street != null)
			return street + " " + sign;
		if (place != null)
			return place + " " + sign;
		return "?" + " " + sign;
	}
}