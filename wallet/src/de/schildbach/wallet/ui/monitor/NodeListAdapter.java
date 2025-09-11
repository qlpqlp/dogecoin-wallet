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
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.wallet.R;
import de.schildbach.wallet.data.NodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying detailed node information in a RecyclerView
 * 
 * @author AI Assistant
 */
public class NodeListAdapter extends RecyclerView.Adapter<NodeListAdapter.NodeViewHolder> {
    private List<NodeInfo> nodes = new ArrayList<>();
    
    @NonNull
    @Override
    public NodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_node_info, parent, false);
        return new NodeViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull NodeViewHolder holder, int position) {
        final NodeInfo node = nodes.get(position);
        holder.bind(node);
    }
    
    @Override
    public int getItemCount() {
        return nodes.size();
    }
    
    public void updateNodes(List<NodeInfo> newNodes) {
        this.nodes = new ArrayList<>(newNodes);
        sortNodes();
        notifyDataSetChanged();
    }
    
    private void sortNodes() {
        // Sort nodes: Connected first, Shibetoshi second (by version), others last
        nodes.sort((node1, node2) -> {
            // First: Connected nodes
            if (node1.isConnected() && !node2.isConnected()) {
                return -1;
            } else if (!node1.isConnected() && node2.isConnected()) {
                return 1;
            }
            
            // Second: Shibetoshi nodes (by sub version)
            boolean isShibetoshi1 = node1.getSubVersion().contains("Shibetoshi");
            boolean isShibetoshi2 = node2.getSubVersion().contains("Shibetoshi");
            
            if (isShibetoshi1 && !isShibetoshi2) {
                return -1;
            } else if (!isShibetoshi1 && isShibetoshi2) {
                return 1;
            } else if (isShibetoshi1 && isShibetoshi2) {
                // Sort Shibetoshi by sub version (latest first)
                return node2.getSubVersion().compareTo(node1.getSubVersion());
            }
            
            // Third: Other nodes
            return node1.getAddress().compareTo(node2.getAddress());
        });
    }
    
    static class NodeViewHolder extends RecyclerView.ViewHolder {
        private final TextView addressText;
        private final TextView versionText;
        private final TextView subVersionText;
        private final TextView servicesText;
        private final TextView syncedBlocksText;
        private final TextView lastSeenText;
        private final TextView latencyText;
        private final TextView statusText;
        
        public NodeViewHolder(@NonNull View itemView) {
            super(itemView);
            addressText = itemView.findViewById(R.id.node_address);
            versionText = itemView.findViewById(R.id.node_version);
            subVersionText = itemView.findViewById(R.id.node_sub_version);
            servicesText = itemView.findViewById(R.id.node_services);
            syncedBlocksText = itemView.findViewById(R.id.node_synced_blocks);
            lastSeenText = itemView.findViewById(R.id.node_last_seen);
            latencyText = itemView.findViewById(R.id.node_latency);
            statusText = itemView.findViewById(R.id.node_status);
        }
        
        public void bind(NodeInfo node) {
            addressText.setText(node.getAddress());
            versionText.setText(String.valueOf(node.getVersion()));
            subVersionText.setText(node.getSubVersion());
            servicesText.setText(node.getServices());
            syncedBlocksText.setText(node.getSyncedBlocks() > 0 ? 
                String.format("%,d", node.getSyncedBlocks()) : "Unknown");
            lastSeenText.setText(node.getFormattedLastSeen());
            latencyText.setText(node.getLatencyText());
            statusText.setText(node.isConnected() ? "Connected" : "Discovered");
            statusText.setTextColor(node.isConnected() ? 
                itemView.getContext().getColor(android.R.color.holo_green_dark) :
                itemView.getContext().getColor(android.R.color.holo_blue_dark));
        }
    }
}
