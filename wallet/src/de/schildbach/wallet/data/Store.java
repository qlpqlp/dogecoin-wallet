/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package de.schildbach.wallet.data;

import java.util.List;

/**
 * Store data model for Dogecoin-accepting stores
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class Store {
    public double lat;
    public double lon;
    public String category;
    public String name;
    public String description;
    public String address;
    public String postal;
    public String location;
    public String country;
    public String email;
    public String phone;
    public String website;
    public List<String> social;
    
    // Getters and setters
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    
    public double getLon() { return lon; }
    public void setLon(double lon) { this.lon = lon; }
    
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public String getPostal() { return postal; }
    public void setPostal(String postal) { this.postal = postal; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    
    public List<String> getSocial() { return social; }
    public void setSocial(List<String> social) { this.social = social; }
}


