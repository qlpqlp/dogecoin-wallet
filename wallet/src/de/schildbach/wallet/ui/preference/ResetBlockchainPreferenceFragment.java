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
import android.os.Bundle;
import android.preference.PreferenceFragment;
import de.schildbach.wallet.Configuration;
import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.service.BlockchainService;
import de.schildbach.wallet.ui.DialogBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Andreas Schildbach
 */
public final class ResetBlockchainPreferenceFragment extends PreferenceFragment {
    private static final Logger log = LoggerFactory.getLogger(ResetBlockchainPreferenceFragment.class);

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Show the reset blockchain dialog
        final Activity activity = getActivity();
        final WalletApplication application = (WalletApplication) activity.getApplication();
        final Configuration config = application.getConfiguration();
        
        final DialogBuilder dialog = DialogBuilder.dialog(activity, R.string.preferences_initiate_reset_title,
                R.string.preferences_initiate_reset_dialog_message);
        dialog.setPositiveButton(R.string.preferences_initiate_reset_dialog_positive, (d, which) -> {
            log.info("manually initiated block chain reset");
            BlockchainService.resetBlockchain(activity);
            config.resetBestChainHeightEver();
            config.updateLastBlockchainResetTime();
            activity.finish(); // Go back to main menu
        });
        dialog.setNegativeButton(R.string.button_dismiss, (d, which) -> {
            activity.finish(); // Go back to main menu
        });
        dialog.show();
    }
}
