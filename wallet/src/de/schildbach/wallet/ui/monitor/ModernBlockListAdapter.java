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

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import de.schildbach.wallet.R;
import de.schildbach.wallet.util.WalletUtils;
import org.bitcoinj.core.StoredBlock;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modern adapter for displaying block information in a RecyclerView
 * 
 * @author AI Assistant
 */
public class ModernBlockListAdapter extends RecyclerView.Adapter<ModernBlockListAdapter.BlockViewHolder> {
    private List<StoredBlock> blocks;
    private static final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.getDefault());
    
    public ModernBlockListAdapter() {
        this.blocks = new ArrayList<>();
    }
    
    public ModernBlockListAdapter(List<StoredBlock> blocks) {
        this.blocks = new ArrayList<>(blocks);
    }
    
    @NonNull
    @Override
    public BlockViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_block_modern, parent, false);
        return new BlockViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull BlockViewHolder holder, int position) {
        if (position < blocks.size()) {
            StoredBlock block = blocks.get(position);
            holder.bind(block);
        }
    }
    
    @Override
    public int getItemCount() {
        return blocks.size();
    }
    
    public void updateBlocks(List<StoredBlock> newBlocks) {
        this.blocks = new ArrayList<>(newBlocks);
        notifyDataSetChanged();
    }
    
    public static class BlockViewHolder extends RecyclerView.ViewHolder {
        private TextView blockHeight;
        private TextView blockTime;
        private TextView blockHash;
        private TextView blockTransactions;
        private TextView blockSize;
        private TextView blockDifficulty;
        
        public BlockViewHolder(@NonNull View itemView) {
            super(itemView);
            blockHeight = itemView.findViewById(R.id.block_height);
            blockTime = itemView.findViewById(R.id.block_time);
            blockHash = itemView.findViewById(R.id.block_hash);
            blockTransactions = itemView.findViewById(R.id.block_transactions);
            blockSize = itemView.findViewById(R.id.block_size);
            blockDifficulty = itemView.findViewById(R.id.block_difficulty);
        }
        
        public void bind(StoredBlock block) {
            // Block height
            blockHeight.setText("Block #" + numberFormat.format(block.getHeight()));
            
            // Block time
            long blockTimeMs = block.getHeader().getTime().getTime();
            long currentTime = System.currentTimeMillis();
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(blockTimeMs, currentTime, DateUtils.MINUTE_IN_MILLIS);
            blockTime.setText(timeAgo);
            
            // Block hash (truncated)
            String hash = block.getHeader().getHash().toString();
            String truncatedHash = hash.substring(0, 16) + "..." + hash.substring(hash.length() - 16);
            blockHash.setText("Hash: " + truncatedHash);
            
            // Block transactions (not available in header, show placeholder)
            blockTransactions.setText("Transactions: N/A");
            
            // Block size (estimated)
            int estimatedSize = block.getHeader().getMessageSize();
            String sizeText;
            if (estimatedSize > 1024 * 1024) {
                sizeText = String.format("%.1f MB", estimatedSize / (1024.0 * 1024.0));
            } else if (estimatedSize > 1024) {
                sizeText = String.format("%.1f KB", estimatedSize / 1024.0);
            } else {
                sizeText = estimatedSize + " bytes";
            }
            blockSize.setText("Header Size: " + sizeText);
            
            // Block difficulty
            long difficulty = block.getHeader().getDifficultyTarget();
            blockDifficulty.setText("Difficulty: " + numberFormat.format(difficulty));
        }
    }
}
