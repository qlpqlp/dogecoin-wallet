package de.schildbach.wallet.ui.monitor;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
 * Adapter for displaying peer information in a RecyclerView
 */
public class PeerListAdapter extends RecyclerView.Adapter<PeerListAdapter.PeerViewHolder> {
    private List<PeerManager.PeerInfo> peers;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
    
    public PeerListAdapter(List<PeerManager.PeerInfo> peers) {
        this.peers = peers;
    }
    
    public PeerListAdapter(AbstractWalletActivity activity, PeerListFragment fragment) {
        this.peers = new ArrayList<>();
    }
    
    public void setSelectedPeer(HostAndPort peer) {
        // Implementation for selection
    }
    
    public int positionOf(HostAndPort peer) {
        // Implementation for finding position
        return RecyclerView.NO_POSITION;
    }
    
    public void submitList(List<Object> items) {
        // Convert Object list to PeerInfo list for display
        this.peers.clear();
        for (Object item : items) {
            if (item instanceof PeerManager.PeerInfo) {
                this.peers.add((PeerManager.PeerInfo) item);
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
                .inflate(R.layout.item_peer_info, parent, false);
        return new PeerViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull PeerViewHolder holder, int position) {
        if (position < peers.size()) {
            PeerManager.PeerInfo peer = peers.get(position);
            holder.bind(peer, dateFormat);
        }
    }
    
    @Override
    public int getItemCount() {
        return peers.size();
    }
    
    public static class PeerViewHolder extends RecyclerView.ViewHolder {
        private TextView peerIp;
        private TextView peerPort;
        private TextView peerVersion;
        private TextView peerSubVersion;
        private TextView peerServices;
        private TextView peerLatency;
        private TextView peerStatus;
        private TextView peerLastSeen;
        
        public PeerViewHolder(@NonNull View itemView) {
            super(itemView);
            peerIp = itemView.findViewById(R.id.peer_ip);
            peerPort = itemView.findViewById(R.id.peer_port);
            peerVersion = itemView.findViewById(R.id.peer_version);
            peerSubVersion = itemView.findViewById(R.id.peer_subversion);
            peerServices = itemView.findViewById(R.id.peer_services);
            peerLatency = itemView.findViewById(R.id.peer_latency);
            peerStatus = itemView.findViewById(R.id.peer_status);
            peerLastSeen = itemView.findViewById(R.id.peer_last_seen);
        }
        
        public void bind(PeerManager.PeerInfo peer, SimpleDateFormat dateFormat) {
            peerIp.setText(peer.ip);
            peerPort.setText(":" + peer.port);
            peerVersion.setText("Version: " + (peer.version != null ? peer.version : "Unknown"));
            peerSubVersion.setText("Sub-version: " + (peer.subVersion != null ? peer.subVersion : "Unknown"));
            peerServices.setText("Services: " + formatServices(peer.services));
            peerLatency.setText("Latency: " + (peer.latency > 0 ? peer.latency + "ms" : "Unknown"));
            peerStatus.setText(peer.status);
            peerLastSeen.setText("Last seen: " + dateFormat.format(new Date(peer.lastSeen)));
            
            // Set status with colored text only (no background)
            peerStatus.setText(peer.status);
            
            // Set text color based on status
            int statusColor = Color.GRAY;
            switch (peer.status) {
                case "Online":
                    statusColor = Color.GREEN;
                    break;
                case "Discovered":
                    statusColor = Color.YELLOW;
                    break;
                case "Offline":
                    statusColor = Color.RED;
                    break;
            }
            peerStatus.setTextColor(statusColor);
        }
        
        private String formatServices(String services) {
            if (services == null || services.equals("Unknown")) return "Unknown";
            
            try {
                long servicesLong = Long.parseLong(services);
                StringBuilder result = new StringBuilder();
                if ((servicesLong & 1) != 0) result.append("NETWORK ");
                if ((servicesLong & 2) != 0) result.append("GETUTXO ");
                if ((servicesLong & 4) != 0) result.append("BLOOM ");
                if ((servicesLong & 8) != 0) result.append("WITNESS ");
                if ((servicesLong & 16) != 0) result.append("XTHIN ");
                if ((servicesLong & 32) != 0) result.append("COMPACT_FILTERS ");
                if ((servicesLong & 64) != 0) result.append("NETWORK_LIMITED ");
                
                if (result.length() == 0) {
                    return "NONE";
                }
                
                return result.toString().trim();
            } catch (NumberFormatException e) {
                return services; // Return the original string if it's not a number
            }
        }
    }
}