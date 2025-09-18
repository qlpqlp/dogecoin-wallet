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

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.wallet.R;
import de.schildbach.wallet.data.DogecoinPeer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying Dogecoin peer information in a collapsible RecyclerView
 * 
 * @author AI Assistant
 */
public class DogecoinPeerListAdapter extends RecyclerView.Adapter<DogecoinPeerListAdapter.PeerViewHolder> {
    private List<DogecoinPeer> peers;
    private List<Boolean> expandedStates; // Track which items are expanded
    private OnHandshakeClickListener handshakeClickListener;
    
    public DogecoinPeerListAdapter() {
        this.peers = new ArrayList<>();
        this.expandedStates = new ArrayList<>();
    }
    
    public interface OnHandshakeClickListener {
        void onHandshakeClick(DogecoinPeer peer);
    }
    
    public void setOnHandshakeClickListener(OnHandshakeClickListener listener) {
        this.handshakeClickListener = listener;
    }
    
    public DogecoinPeerListAdapter(List<DogecoinPeer> peers) {
        this.peers = new ArrayList<>(peers);
        this.expandedStates = new ArrayList<>();
        // Initialize all items as collapsed
        for (int i = 0; i < peers.size(); i++) {
            expandedStates.add(false);
        }
    }
    
    @NonNull
    @Override
    public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dogecoin_peer_collapsible, parent, false);
        return new PeerViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull PeerViewHolder holder, int position) {
        final DogecoinPeer peer = peers.get(position);
        final boolean isExpanded = position < expandedStates.size() ? expandedStates.get(position) : false;
        holder.bind(peer, isExpanded);
    }
    
    @Override
    public int getItemCount() {
        return peers.size();
    }
    
    public void updatePeers(List<DogecoinPeer> newPeers) {
        this.peers = new ArrayList<>(newPeers);
        this.expandedStates = new ArrayList<>();
        // Initialize all new items as collapsed
        for (int i = 0; i < peers.size(); i++) {
            expandedStates.add(false);
        }
        sortPeers();
        notifyDataSetChanged();
    }
    
    private void sortPeers() {
        // Sort peers: Online first, then Discovered, then Offline
        peers.sort((peer1, peer2) -> {
            int status1 = getStatusPriority(peer1.status);
            int status2 = getStatusPriority(peer2.status);
            
            if (status1 != status2) {
                return Integer.compare(status1, status2);
            }
            
            // If same status, sort by address
            return peer1.getAddress().compareTo(peer2.getAddress());
        });
    }
    
    private int getStatusPriority(String status) {
        switch (status) {
            case "Online": return 0;
            case "Discovered": return 1;
            case "Offline": return 2;
            default: return 3;
        }
    }
    
    class PeerViewHolder extends RecyclerView.ViewHolder {
        private final TextView addressText;
        private final View statusBullet;
        private final ImageView expandIcon;
        private final LinearLayout collapsedView;
        private final LinearLayout expandedView;
        
        // Expanded view elements
        private final TextView protocolText;
        private final TextView subVersionText;
        private final TextView servicesText;
        private final TextView syncedBlocksText;
        private final TextView latencyText;
        private final TextView sourceText;
        private final TextView lastSeenText;
        private final Button handshakeButton;
        
        public PeerViewHolder(@NonNull View itemView) {
            super(itemView);
            addressText = itemView.findViewById(R.id.peer_address);
            statusBullet = itemView.findViewById(R.id.peer_status_bullet);
            expandIcon = itemView.findViewById(R.id.expand_icon);
            collapsedView = itemView.findViewById(R.id.collapsed_view);
            expandedView = itemView.findViewById(R.id.expanded_view);
            
            // Expanded view elements
            protocolText = itemView.findViewById(R.id.peer_protocol);
            subVersionText = itemView.findViewById(R.id.peer_sub_version);
            servicesText = itemView.findViewById(R.id.peer_services);
            syncedBlocksText = itemView.findViewById(R.id.peer_synced_blocks);
            latencyText = itemView.findViewById(R.id.peer_latency);
            sourceText = itemView.findViewById(R.id.peer_source);
            lastSeenText = itemView.findViewById(R.id.peer_last_seen);
            handshakeButton = itemView.findViewById(R.id.handshake_button);
        }
        
        public void bind(DogecoinPeer peer, boolean isExpanded) {
            // Set address
            addressText.setText(peer.getAddress());
            
            // Set colored bullet based on status
            int bulletDrawable = R.drawable.circle_amber; // Default to amber
            switch (peer.status) {
                case "Online":
                    bulletDrawable = R.drawable.circle_green;
                    break;
                case "Discovered":
                    bulletDrawable = R.drawable.circle_amber;
                    break;
                case "Offline":
                    bulletDrawable = R.drawable.circle_red;
                    break;
            }
            statusBullet.setBackgroundResource(bulletDrawable);
            
            // Set expanded view data
            protocolText.setText(peer.version > 0 ? String.valueOf(peer.version) : "Unknown");
            subVersionText.setText(peer.subVersion != null && !peer.subVersion.isEmpty() ? peer.subVersion : "Unknown");
            servicesText.setText(peer.getServicesText());
            syncedBlocksText.setText(peer.syncedBlocks > 0 ? String.valueOf(peer.syncedBlocks) : "Unknown");
            latencyText.setText(peer.getLatencyText());
            sourceText.setText(peer.source != null && !peer.source.isEmpty() ? peer.source : "Unknown");
            lastSeenText.setText(peer.getFormattedLastSeen());
            
            // Set expand/collapse state
            if (isExpanded) {
                expandedView.setVisibility(View.VISIBLE);
                expandIcon.setImageResource(R.drawable.ic_expand_less);
            } else {
                expandedView.setVisibility(View.GONE);
                expandIcon.setImageResource(R.drawable.ic_expand_more);
            }
            
            // Set click listener for expand/collapse
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    // Call the adapter's toggleExpansion method
                    DogecoinPeerListAdapter.this.toggleExpansion(position);
                }
            });
            
            // Set click listener for handshake button
            handshakeButton.setOnClickListener(v -> {
                android.util.Log.d("DogecoinPeerListAdapter", "Handshake button clicked for peer: " + peer.getAddress());
                if (handshakeClickListener != null) {
                    android.util.Log.d("DogecoinPeerListAdapter", "Calling handshakeClickListener.onHandshakeClick");
                    handshakeClickListener.onHandshakeClick(peer);
                } else {
                    android.util.Log.w("DogecoinPeerListAdapter", "handshakeClickListener is null");
                }
            });
        }
        
    }
    
    public void toggleExpansion(int position) {
        if (position >= 0 && position < expandedStates.size()) {
            expandedStates.set(position, !expandedStates.get(position));
            notifyItemChanged(position);
        }
    }
}