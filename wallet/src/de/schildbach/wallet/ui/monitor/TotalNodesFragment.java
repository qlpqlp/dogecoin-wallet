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

import android.app.AlertDialog;
import android.os.Bundle;
import de.schildbach.wallet.ui.DialogBuilder;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.data.DogecoinPeer;
import de.schildbach.wallet.service.PeerDiscoveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment displaying detailed information about all discovered Dogecoin nodes worldwide
 * with real-time updates using DNS discovery and Dogecoin handshakes
 * 
 * @author AI Assistant
 */
public class TotalNodesFragment extends Fragment implements PeerDiscoveryService.PeerDiscoveryCallback {
    private static final Logger log = LoggerFactory.getLogger(TotalNodesFragment.class);
    
    private WalletApplication application;
    private PeerDiscoveryService peerDiscoveryService;
    private TotalNodesViewModel viewModel;
    
    private TextView totalNodesText;
    private TextView lastUpdatedText;
    private ProgressBar progressBar;
    private Button resetButton;
    private RecyclerView nodesRecyclerView;
    private DogecoinPeerListAdapter peerListAdapter;
    private List<DogecoinPeer> peers = new ArrayList<>();
    
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        application = (WalletApplication) requireActivity().getApplication();
        
        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(TotalNodesViewModel.class);
    }
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                           @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_total_nodes, container, false);
        
        totalNodesText = view.findViewById(R.id.total_nodes_text);
        lastUpdatedText = view.findViewById(R.id.last_updated_text);
        progressBar = view.findViewById(R.id.progress_bar);
        resetButton = view.findViewById(R.id.reset_button);
        nodesRecyclerView = view.findViewById(R.id.nodes_recycler_view);
        
        setupRecyclerView();
        setupObservers();
        setupResetButton();
        
        return view;
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // Initialize peer discovery service
        try {
            log.info("Creating PeerDiscoveryService...");
            peerDiscoveryService = new PeerDiscoveryService(requireContext());
            log.info("PeerDiscoveryService created successfully");
            
            log.info("Setting callback...");
            peerDiscoveryService.setCallback(this);
            log.info("Callback set successfully");
            
            // Load existing peers immediately - this should show peers from peers.json right away
            loadExistingPeers();
            
            // Start discovery to find new peers and update existing ones
            log.info("Starting peer discovery...");
            peerDiscoveryService.startDiscovery();
            log.info("Peer discovery started successfully");
        } catch (Exception e) {
            log.error("Error initializing PeerDiscoveryService", e);
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // Refresh peers when fragment becomes visible
        loadExistingPeers();
    }
    
    /**
     * Force refresh the peers list from storage
     */
    public void refreshPeers() {
        loadExistingPeers();
    }
    
    private void setupRecyclerView() {
        peerListAdapter = new DogecoinPeerListAdapter(peers);
        nodesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        nodesRecyclerView.setAdapter(peerListAdapter);
        
        // Set up handshake click listener
        log.info("Setting up handshake click listener");
        peerListAdapter.setOnHandshakeClickListener(peer -> {
            log.info("Handshake button clicked for peer: {}", peer.getAddress());
            if (peerDiscoveryService != null) {
                log.info("Calling peerDiscoveryService.performHandshake");
                // Trigger handshake for this specific peer
                peerDiscoveryService.performHandshake(peer);
                Toast.makeText(getContext(), "Handshaking with " + peer.getAddress(), Toast.LENGTH_SHORT).show();
            } else {
                log.warn("PeerDiscoveryService is null, cannot perform handshake");
                Toast.makeText(getContext(), "Service not available", Toast.LENGTH_SHORT).show();
            }
        });
        log.info("Handshake click listener set up completed");
    }
    
    private void setupObservers() {
        // Observe node count changes
        viewModel.getNodeCount().observe(getViewLifecycleOwner(), count -> {
            totalNodesText.setText(count + " Nodes Worldwide");
        });
        
        // Observe last updated time
        viewModel.getLastUpdated().observe(getViewLifecycleOwner(), time -> {
            lastUpdatedText.setText("Last updated: " + time);
        });
    }
    
    private void loadExistingPeers() {
        // Run on main thread to ensure immediate UI update
        mainHandler.post(() -> {
            try {
                List<DogecoinPeer> existingPeers = peerDiscoveryService.getAllPeers();
                log.info("Loading existing peers: {} found", existingPeers.size());
                
                // Log details of each peer for debugging
                for (DogecoinPeer peer : existingPeers) {
                    log.debug("Existing peer: {} - {} - {}", peer.getAddress(), peer.status, peer.subVersion);
                }
                
                peers.clear();
                peers.addAll(existingPeers);
                
                // Always refresh the listing and update counts/time
                refreshPeerListing();
                updateNodeCountAndTime();
                
                // Hide progress bar if we have peers
                if (!peers.isEmpty()) {
                    progressBar.setVisibility(View.GONE);
                    log.info("Hiding progress bar, showing {} peers", peers.size());
                } else {
                    log.info("No existing peers found, keeping progress bar visible");
                }
            } catch (Exception e) {
                log.error("Error loading existing peers", e);
            }
        });
    }
    
    @Override
    public void onPeerUpdated(DogecoinPeer peer) {
        mainHandler.post(() -> {
            try {
                log.info("Peer updated: {} - {}", peer.getAddress(), peer.status);
                
                // Find and update existing peer or add new one
                boolean found = false;
                for (int i = 0; i < peers.size(); i++) {
                    if (peers.get(i).getAddress().equals(peer.getAddress())) {
                        peers.set(i, peer);
                        found = true;
                        break;
                    }
                }
                
                if (!found) {
                    peers.add(peer);
                    log.info("Added new peer: {} - Total peers: {}", peer.getAddress(), peers.size());
                }
                
                // Always refresh the entire listing to ensure UI is up to date
                refreshPeerListing();
                
                // Update counts and time with full date and time
                updateNodeCountAndTime();
                
                // Hide progress bar when we have peers
                if (peers.size() > 0 && progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                log.error("Error in onPeerUpdated: {}", e.getMessage(), e);
            }
        });
    }
    
    @Override
    public void onTotalCountChanged(int totalCount) {
        mainHandler.post(() -> {
            try {
                log.info("Total count changed to: {}", totalCount);
                viewModel.setNodeCount(totalCount);
                
                // Always refresh the listing when count changes
                refreshPeerListing();
                
                // Update counts and time
                updateNodeCountAndTime();
                
                // If count is 0, clear the local peer list and refresh UI
                if (totalCount == 0) {
                    log.info("Clearing peers list due to count = 0");
                    peers.clear();
                    if (progressBar != null) {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                    if (lastUpdatedText != null) {
                        lastUpdatedText.setText("Last updated: Never");
                    }
                }
            } catch (Exception e) {
                log.error("Error in onTotalCountChanged: {}", e.getMessage(), e);
            }
        });
    }
    
    /**
     * Setup reset button functionality
     */
    private void setupResetButton() {
        resetButton.setOnClickListener(v -> {
            log.info("Reset button clicked");
            try {
                // Show confirmation dialog
                if (getContext() != null) {
                    DialogBuilder.dialog(getContext(), 0, 
                        "Reset\n\nThis will clear all discovered peers and restart the discovery process. Continue?")
                        .setPositiveButton("Reset", (dialog, which) -> {
                            log.info("User confirmed reset");
                            resetAllPeers();
                        })
                        .setNegativeButton(R.string.common_cancel, (dialog, which) -> {
                            log.info("User cancelled reset");
                        })
                        .show();
                } else {
                    log.warn("Context is null, cannot show reset dialog");
                }
            } catch (Exception e) {
                log.error("Error showing reset dialog: {}", e.getMessage(), e);
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Error showing reset dialog: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
    }
    
    /**
     * Reset all peers and restart discovery
     */
    private void resetAllPeers() {
        log.info("Starting resetAllPeers()");
        try {
            // Clear local UI state first to prevent crashes
            mainHandler.post(() -> {
                try {
                    peers.clear();
                    if (peerListAdapter != null) {
                        peerListAdapter.notifyDataSetChanged();
                    }
                    if (progressBar != null) {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                    if (lastUpdatedText != null) {
                        lastUpdatedText.setText("Last updated: Never");
                    }
                    log.info("UI cleared for reset");
                } catch (Exception e) {
                    log.error("Error clearing UI: {}", e.getMessage(), e);
                }
            });
            
            // Reset all peers and restart discovery safely
            if (peerDiscoveryService != null) {
                log.info("Calling peerDiscoveryService.resetAllPeersAndRestart()");
                peerDiscoveryService.resetAllPeersAndRestart();
                log.info("peerDiscoveryService.resetAllPeersAndRestart() completed");
            } else {
                log.warn("peerDiscoveryService is null, cannot reset peers");
            }
            
        } catch (Exception e) {
            log.error("Error resetting peers: {}", e.getMessage(), e);
            mainHandler.post(() -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "Error resetting peers: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }
    
    /**
     * Always refresh the peer listing to ensure UI is up to date
     */
    private void refreshPeerListing() {
        try {
            if (peerListAdapter != null) {
                peerListAdapter.updatePeers(peers);
                log.debug("Refreshed peer listing with {} peers", peers.size());
            } else {
                log.warn("peerListAdapter is null, cannot refresh listing");
            }
        } catch (Exception e) {
            log.error("Error refreshing peer listing: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Update node count and last update time with full date and time
     */
    private void updateNodeCountAndTime() {
        try {
            // Update node count
            if (totalNodesText != null) {
                totalNodesText.setText(peers.size() + " Nodes Worldwide");
            }
            
            // Update last update time with full date and time
            if (lastUpdatedText != null) {
                java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault());
                String currentTime = dateFormat.format(new java.util.Date());
                lastUpdatedText.setText("Last updated: " + currentTime);
            }
            
            // Update ViewModel
            viewModel.setNodeCount(peers.size());
            viewModel.setLastUpdated(java.text.DateFormat.getDateTimeInstance().format(new java.util.Date()));
            
        } catch (Exception e) {
            log.error("Error updating node count and time: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (peerDiscoveryService != null) {
            peerDiscoveryService.stopDiscovery();
        }
        
        // Clear references to prevent memory leaks
        peers.clear();
        if (peerListAdapter != null) {
            peerListAdapter.updatePeers(new ArrayList<>());
        }
        
        // Clear UI references
        totalNodesText = null;
        lastUpdatedText = null;
        progressBar = null;
        resetButton = null;
        nodesRecyclerView = null;
        peerListAdapter = null;
    }
    
}
