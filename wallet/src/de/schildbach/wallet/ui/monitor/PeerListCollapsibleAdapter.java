package de.schildbach.wallet.ui.monitor;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.wallet.R;
import de.schildbach.wallet.service.PeerManager;
import de.schildbach.wallet.ui.AbstractWalletActivity;
import com.google.common.net.HostAndPort;
import org.bitcoinj.core.Peer;

import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Collapsible adapter for displaying peer information in the Connected tab
 */
public class PeerListCollapsibleAdapter extends RecyclerView.Adapter<PeerListCollapsibleAdapter.PeerViewHolder> {
    private List<PeerManager.PeerInfo> peers;
    private List<Boolean> expandedStates; // Track which items are expanded
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    
    public PeerListCollapsibleAdapter() {
        this.peers = new ArrayList<>();
        this.expandedStates = new ArrayList<>();
    }
    
    public PeerListCollapsibleAdapter(List<PeerManager.PeerInfo> peers) {
        this.peers = new ArrayList<>(peers);
        this.expandedStates = new ArrayList<>();
        // Initialize all items as collapsed
        for (int i = 0; i < peers.size(); i++) {
            expandedStates.add(false);
        }
    }
    
    public void setSelectedPeer(HostAndPort peer) {
        // Implementation for selection if needed
    }
    
    public int positionOf(HostAndPort peer) {
        // Implementation for finding position if needed
        return RecyclerView.NO_POSITION;
    }
    
    public void submitList(List<Object> items) {
        // Convert Object list to PeerInfo list for display
        this.peers.clear();
        this.expandedStates.clear();
        
        for (Object item : items) {
            if (item instanceof PeerManager.PeerInfo) {
                this.peers.add((PeerManager.PeerInfo) item);
                expandedStates.add(false); // All items start collapsed
            }
        }
        notifyDataSetChanged();
    }
    
    public static List<Object> buildListItems(AbstractWalletActivity activity, List<Peer> peers, Map<InetAddress, String> hostnames) {
        List<Object> items = new ArrayList<>();
        if (peers != null) {
            for (Peer peer : peers) {
                String hostname = null;
                if (hostnames != null) {
                    hostname = hostnames.get(peer.getAddress().getAddr());
                }
                PeerManager.PeerInfo peerInfo = new PeerManager.PeerInfo(peer, hostname);
                items.add(peerInfo);
            }
        }
        return items;
    }
    
    public static List<Object> buildEmptyListItems(AbstractWalletActivity activity) {
        // Show a message when no peers are connected
        List<Object> emptyItems = new ArrayList<>();
        emptyItems.add("No peers connected. This could be due to:");
        emptyItems.add("• No internet connection");
        emptyItems.add("• Low device storage");
        emptyItems.add("• Blockchain sync disabled");
        emptyItems.add("• Check Total Nodes tab for discovered peers");
        return emptyItems;
    }
    
    @NonNull
    @Override
    public PeerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_peer_info_collapsible, parent, false);
        return new PeerViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull PeerViewHolder holder, int position) {
        if (position < peers.size()) {
            PeerManager.PeerInfo peer = peers.get(position);
            final boolean isExpanded = position < expandedStates.size() ? expandedStates.get(position) : false;
            holder.bind(peer, dateFormat, isExpanded);
        }
    }
    
    @Override
    public int getItemCount() {
        return peers.size();
    }
    
    class PeerViewHolder extends RecyclerView.ViewHolder {
        private TextView addressText;
        private View statusBullet;
        private ImageView expandIcon;
        private LinearLayout collapsedView;
        private LinearLayout expandedView;
        
        // Expanded view elements
        private TextView protocolText;
        private TextView subVersionText;
        private TextView servicesText;
        private TextView latencyText;
        private TextView lastSeenText;
        
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
            latencyText = itemView.findViewById(R.id.peer_latency);
            lastSeenText = itemView.findViewById(R.id.peer_last_seen);
        }
        
        public void bind(PeerManager.PeerInfo peer, SimpleDateFormat dateFormat, boolean isExpanded) {
            // Set address (IP:Port)
            String address = peer.ip + ":" + peer.port;
            addressText.setText(address);
            
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
            protocolText.setText(peer.version != null ? peer.version : "Unknown");
            subVersionText.setText(peer.subVersion != null ? peer.subVersion : "Unknown");
            servicesText.setText(peer.services != null && !peer.services.isEmpty() ? peer.services : "Unknown");
            latencyText.setText(peer.latency > 0 ? peer.latency + "ms" : "Unknown");
            lastSeenText.setText(dateFormat.format(new Date(peer.lastSeen)));
            
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
                    PeerListCollapsibleAdapter.this.toggleExpansion(position);
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
