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

import org.bitcoinj.core.Address;

/**
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
import org.bitcoinj.core.Coin;

/**
 * Data model for family members in Family Mode
 */
public class FamilyMember {
    private long id;
    private String name;
    private String derivedKey;
    private String address;
    private Coin balance;
    private long createdTime;
    private boolean isActive;

    public FamilyMember() {
        this.createdTime = System.currentTimeMillis();
        this.balance = Coin.ZERO;
        this.isActive = false;
    }

    public FamilyMember(String name, String derivedKey, String address) {
        this();
        this.name = name;
        this.derivedKey = derivedKey;
        this.address = address;
    }

    // Getters and Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDerivedKey() {
        return derivedKey;
    }

    public void setDerivedKey(String derivedKey) {
        this.derivedKey = derivedKey;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Coin getBalance() {
        return balance;
    }

    public void setBalance(Coin balance) {
        this.balance = balance;
    }

    public long getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(long createdTime) {
        this.createdTime = createdTime;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
