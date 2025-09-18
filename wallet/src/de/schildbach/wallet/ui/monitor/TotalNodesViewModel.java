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

package de.schildbach.wallet.ui.monitor;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * ViewModel for managing Total Nodes tab state
 * 
 * @author AI Assistant
 */
public class TotalNodesViewModel extends ViewModel {
    private final MutableLiveData<Integer> nodeCount = new MutableLiveData<>(0);
    private final MutableLiveData<String> lastUpdated = new MutableLiveData<>("Never");
    
    public LiveData<Integer> getNodeCount() {
        return nodeCount;
    }
    
    public LiveData<String> getLastUpdated() {
        return lastUpdated;
    }
    
    public void setNodeCount(int count) {
        nodeCount.setValue(count);
    }
    
    public void setLastUpdated(String time) {
        lastUpdated.setValue(time);
    }
}
