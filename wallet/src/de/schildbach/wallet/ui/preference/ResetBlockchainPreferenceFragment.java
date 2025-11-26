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

package de.schildbach.wallet.ui.preference;

import android.app.Activity;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.ui.DialogBuilder;
import de.schildbach.wallet.util.CustomCheckpointManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Andreas Schildbach
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public final class ResetBlockchainPreferenceFragment extends PreferenceFragment {
    private static final Logger log = LoggerFactory.getLogger(ResetBlockchainPreferenceFragment.class);

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Show the reset blockchain dialog with checkpoint selection
        final Activity activity = getActivity();
        final WalletApplication application = (WalletApplication) activity.getApplication();
        final Configuration config = application.getConfiguration();
        
        // Load custom checkpoints
        final AssetManager assetManager = activity.getAssets();
        final List<CustomCheckpointManager.Checkpoint> checkpoints = CustomCheckpointManager.loadCheckpoints(assetManager);
        
        // Create checkpoint selection dialog
        final DialogBuilder checkpointDialog = DialogBuilder.dialog(activity, 
                R.string.preferences_initiate_reset_title,
                "Select a checkpoint to start from (or 'From Beginning' to start from block 0):");
        
        // Create list view for checkpoints
        final ListView listView = new ListView(activity);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, 
                android.R.layout.simple_list_item_single_choice);
        
        for (CustomCheckpointManager.Checkpoint checkpoint : checkpoints) {
            adapter.add(checkpoint.getDisplayName());
        }
        
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        listView.setItemChecked(0, true); // Select "From Beginning" by default
        
        checkpointDialog.setView(listView);
        
        checkpointDialog.setPositiveButton(R.string.preferences_initiate_reset_dialog_positive, (d, which) -> {
            int selectedIndex = listView.getCheckedItemPosition();
            if (selectedIndex >= 0 && selectedIndex < checkpoints.size()) {
                CustomCheckpointManager.Checkpoint selectedCheckpoint = checkpoints.get(selectedIndex);
                long checkpointTimestamp = selectedCheckpoint.timestamp;
                int checkpointBlockHeight = selectedCheckpoint.blockHeight;
                String checkpointBlockHash = selectedCheckpoint.blockHash;
                int checkpointVersion = selectedCheckpoint.version;
                String checkpointPrevBlockHash = selectedCheckpoint.prevBlockHash;
                String checkpointMerkleRoot = selectedCheckpoint.merkleRoot;
                long checkpointTime = selectedCheckpoint.time;
                String checkpointBits = selectedCheckpoint.bits;
                long checkpointNonce = selectedCheckpoint.nonce;
                
                log.info("manually initiated block chain reset with checkpoint timestamp: {} ({}), block height: {}, block hash: {}", 
                        checkpointTimestamp, selectedCheckpoint.getDisplayName(), checkpointBlockHeight, checkpointBlockHash);
                
                // Show confirmation dialog
                final DialogBuilder confirmDialog = DialogBuilder.dialog(activity, 
                        R.string.preferences_initiate_reset_title,
                R.string.preferences_initiate_reset_dialog_message);
                confirmDialog.setPositiveButton(R.string.preferences_initiate_reset_dialog_positive, (d2, which2) -> {
                    BlockchainService.resetBlockchain(activity, checkpointTimestamp, checkpointBlockHeight, checkpointBlockHash,
                            checkpointVersion, checkpointPrevBlockHash, checkpointMerkleRoot, checkpointTime, checkpointBits, checkpointNonce);
            config.resetBestChainHeightEver();
            config.updateLastBlockchainResetTime();
            activity.finish(); // Go back to main menu
        });
                confirmDialog.setNegativeButton(R.string.button_dismiss, (d2, which2) -> {
                    // Show checkpoint selection dialog again
                    checkpointDialog.show();
                });
                confirmDialog.show();
            } else {
                // Fallback: use default (from beginning)
                log.info("manually initiated block chain reset (default)");
                BlockchainService.resetBlockchain(activity);
                config.resetBestChainHeightEver();
                config.updateLastBlockchainResetTime();
                activity.finish();
            }
        });
        
        checkpointDialog.setNegativeButton(R.string.button_dismiss, (d, which) -> {
            activity.finish(); // Go back to main menu
        });
        
        checkpointDialog.show();
    }
}
