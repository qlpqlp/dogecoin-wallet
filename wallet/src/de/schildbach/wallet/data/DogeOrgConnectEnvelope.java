package de.schildbach.wallet.data;

import java.util.List;

/**
 * DogeOrg Connect Payment Envelope
 * Represents a detailed payment request from a vendor
 * Based on https://connect.dogecoin.org specification
 */
public class DogeOrgConnectEnvelope {
    
    // Vendor information
    private Vendor vendor;
    
    // Payment details
    private List<Item> items;
    private double subtotal;
    private double tax;
    private double fees;
    private double total;
    
    // Dogecoin amount
    private long dogecoinAmount;
    
    // Vendor signature for verification
    private String signature;
    
    // DogeConnect specific fields
    private String dcUrl;  // Payment envelope URL
    private String hash;    // Signature hash
    
    // Metadata
    private String memo;
    private String invoiceNumber;
    
    /**
     * Vendor information
     */
    public static class Vendor {
        private String name;
        private String address;
        private String website;
        private String contact;
        
        public Vendor() {}
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        
        public String getWebsite() { return website; }
        public void setWebsite(String website) { this.website = website; }
        
        public String getContact() { return contact; }
        public void setContact(String contact) { this.contact = contact; }
    }
    
    /**
     * Payment item (goods/services)
     */
    public static class Item {
        private String name;
        private String description;
        private double price;
        private int quantity;
        
        public Item() {}
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        
        public double getTotal() {
            return price * quantity;
        }
    }
    
    public DogeOrgConnectEnvelope() {}
    
    // Getters and Setters
    public Vendor getVendor() { return vendor; }
    public void setVendor(Vendor vendor) { this.vendor = vendor; }
    
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    
    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
    
    public double getTax() { return tax; }
    public void setTax(double tax) { this.tax = tax; }
    
    public double getFees() { return fees; }
    public void setFees(double fees) { this.fees = fees; }
    
    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }
    
    public long getDogecoinAmount() { return dogecoinAmount; }
    public void setDogecoinAmount(long dogecoinAmount) { this.dogecoinAmount = dogecoinAmount; }
    
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    
    public String getDcUrl() { return dcUrl; }
    public void setDcUrl(String dcUrl) { this.dcUrl = dcUrl; }
    
    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
}

