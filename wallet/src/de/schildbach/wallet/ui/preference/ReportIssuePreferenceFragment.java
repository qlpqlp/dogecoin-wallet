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

import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import de.schildbach.wallet.Constants;
import de.schildbach.wallet.R;
import de.schildbach.wallet.ui.WalletActivity;

/**
 * @author Andreas Schildbach
 */
public final class ReportIssuePreferenceFragment extends PreferenceFragment {
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Launch WalletActivity with intent to show report issue dialog
        Intent intent = new Intent(getActivity(), WalletActivity.class);
        intent.putExtra(WalletActivity.INTENT_EXTRA_SHOW_REPORT_ISSUE, true);
        startActivity(intent);
        
        // Finish the activity to go back to settings
        getActivity().finish();
    }
}
