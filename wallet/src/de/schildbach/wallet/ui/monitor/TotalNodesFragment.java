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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.NodeInfo;
import de.schildbach.wallet.data.BlockchainServiceLiveData;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.service.WorldwidePeerDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment displaying detailed information about all discovered Dogecoin nodes worldwide
 * 
 * @author AI Assistant
 */
public class TotalNodesFragment extends Fragment {
    private static final Logger log = LoggerFactory.getLogger(TotalNodesFragment.class);
    
    private WalletApplication application;
    private BlockchainServiceLiveData blockchainServiceLiveData;
    private BlockchainService blockchainService;
    private WorldwidePeerDiscovery worldwideDiscovery;
    
    private TextView totalNodesText;
    private TextView lastUpdatedText;
    private Button refreshButton;
    private ProgressBar progressBar;
    private RecyclerView nodesRecyclerView;
    private NodeListAdapter nodeListAdapter;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        application = (WalletApplication) requireActivity().getApplication();
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_total_nodes, container, false);
        
        totalNodesText = view.findViewById(R.id.total_nodes_text);
        lastUpdatedText = view.findViewById(R.id.last_updated_text);
        refreshButton = view.findViewById(R.id.refresh_button);
        progressBar = view.findViewById(R.id.progress_bar);
        nodesRecyclerView = view.findViewById(R.id.nodes_recycler_view);
        
        setupRecyclerView();
        setupRefreshButton();
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Get blockchain service
        blockchainServiceLiveData = new BlockchainServiceLiveData(application);
        blockchainServiceLiveData.observe(getViewLifecycleOwner(), service -> {
            blockchainService = service;
            if (blockchainService != null) {
                worldwideDiscovery = blockchainService.getWorldwidePeerDiscovery();
                if (worldwideDiscovery != null) {
                    observeWorldwideNodes();
                } else {
                    showError("Worldwide discovery not available");
                }
            } else {
                showError("Blockchain service not available");
            }
        });
    }
    
    private void setupRecyclerView() {
        nodeListAdapter = new NodeListAdapter();
        nodesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        nodesRecyclerView.setAdapter(nodeListAdapter);
    }
    
    private void setupRefreshButton() {
        refreshButton.setOnClickListener(v -> {
            if (worldwideDiscovery != null) {
                refreshButton.setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);
                worldwideDiscovery.forceRefresh();
                
                // Re-enable button after a delay
                refreshButton.postDelayed(() -> {
                    refreshButton.setEnabled(true);
                    progressBar.setVisibility(View.GONE);
                }, 2000);
            }
        });
    }
    
    private void observeWorldwideNodes() {
        // Observe worldwide peer count
        worldwideDiscovery.worldwidePeerCount.observe(getViewLifecycleOwner(), new Observer<Integer>() {
            @Override
            public void onChanged(Integer count) {
                if (count != null) {
                    totalNodesText.setText(count + " Nodes Worldwide");
                }
            }
        });
        
        // Observe discovery progress
        worldwideDiscovery.discoveryProgress.observe(getViewLifecycleOwner(), new Observer<Integer>() {
            @Override
            public void onChanged(Integer progress) {
                if (progress != null) {
                    progressBar.setProgress(progress);
                }
            }
        });
        
        // Observe discovery status
        worldwideDiscovery.isDiscovering.observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean isDiscovering) {
                if (isDiscovering != null) {
                    if (isDiscovering) {
                        progressBar.setVisibility(View.VISIBLE);
                        refreshButton.setText("Discovering...");
                        refreshButton.setEnabled(false);
                    } else {
                        progressBar.setVisibility(View.GONE);
                        refreshButton.setText("Refresh");
                        refreshButton.setEnabled(true);
                    }
                }
            }
        });
        
        // Observe detailed node information
        worldwideDiscovery.worldwideNodes.observe(getViewLifecycleOwner(), new Observer<List<NodeInfo>>() {
            @Override
            public void onChanged(List<NodeInfo> nodes) {
                if (nodes != null) {
                    nodeListAdapter.updateNodes(nodes);
                    lastUpdatedText.setText("Last updated: " + 
                        java.text.DateFormat.getTimeInstance().format(new java.util.Date()));
                }
            }
        });
    }
    
    private void showError(String message) {
        totalNodesText.setText("Error: " + message);
        lastUpdatedText.setText("");
        progressBar.setVisibility(View.GONE);
    }
}
