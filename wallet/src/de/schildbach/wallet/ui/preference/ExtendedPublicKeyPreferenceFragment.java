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
import de.schildbach.wallet.WalletApplication;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.script.Script;
import org.bitcoinj.wallet.DeterministicKeyChain;

import java.util.Locale;

/**
 * @author Andreas Schildbach
 */
public final class ExtendedPublicKeyPreferenceFragment extends PreferenceFragment {
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Show the extended public key dialog
        final WalletApplication application = (WalletApplication) getActivity().getApplication();
        application.getWalletAsync(wallet -> {
            if (wallet != null) {
                final DeterministicKeyChain activeKeyChain = wallet.getActiveKeyChain();
                final DeterministicKey extendedKey = activeKeyChain.getWatchingKey();
                final Script.ScriptType outputScriptType = activeKeyChain.getOutputScriptType();
                final long creationTimeSeconds = extendedKey.getCreationTimeSeconds();
                final String base58 = String.format(Locale.US, "%s?c=%d&h=bip32",
                        extendedKey.serializePubB58(Constants.NETWORK_PARAMETERS, outputScriptType), creationTimeSeconds);
                
                // Create a custom dialog that will finish the activity when dismissed
                getActivity().runOnUiThread(() -> {
                    ExtendedPublicKeyFragment.show(getFragmentManager(), (CharSequence) base58);
                });
            }
        });
        
        // Don't finish immediately - let the dialog handle it
    }
}
