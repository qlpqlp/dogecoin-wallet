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

package de.schildbach.wallet.ui;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import de.schildbach.wallet.R;
import de.schildbach.wallet.WalletApplication;

/**
 * Dialog fragment for first-time wallet setup
 * Forces users to choose between creating a new wallet, restoring a wallet, or activating a child wallet
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class FirstTimeSetupDialogFragment extends DialogFragment {
    private static final String FRAGMENT_TAG = FirstTimeSetupDialogFragment.class.getName();
    
    public interface OnSetupChoiceListener {
        void onNewWallet();
        void onRestoreWallet();
        void onActivateChildWallet();
    }
    
    private OnSetupChoiceListener listener;
    
    public static void show(final FragmentManager fm, final OnSetupChoiceListener listener) {
        final DialogFragment newFragment = new FirstTimeSetupDialogFragment();
        ((FirstTimeSetupDialogFragment) newFragment).listener = listener;
        newFragment.show(fm, FRAGMENT_TAG);
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final View view = LayoutInflater.from(getActivity()).inflate(R.layout.dialog_first_time_setup, null);
        
        // New Wallet option
        final View newWalletOption = view.findViewById(R.id.option_new_wallet);
        final ImageView newWalletIcon = view.findViewById(R.id.icon_new_wallet);
        final TextView newWalletTitle = view.findViewById(R.id.title_new_wallet);
        final TextView newWalletDescription = view.findViewById(R.id.description_new_wallet);
        
        newWalletIcon.setImageResource(R.drawable.ic_add_24dp);
        newWalletTitle.setText(R.string.first_time_setup_new_wallet_title);
        newWalletDescription.setText(R.string.first_time_setup_new_wallet_description);
        
        newWalletOption.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNewWallet();
            }
            dismiss();
        });
        
        // Restore Wallet option
        final View restoreWalletOption = view.findViewById(R.id.option_restore_wallet);
        final ImageView restoreWalletIcon = view.findViewById(R.id.icon_restore_wallet);
        final TextView restoreWalletTitle = view.findViewById(R.id.title_restore_wallet);
        final TextView restoreWalletDescription = view.findViewById(R.id.description_restore_wallet);
        
        restoreWalletIcon.setImageResource(R.drawable.ic_file_24dp);
        restoreWalletTitle.setText(R.string.first_time_setup_restore_wallet_title);
        restoreWalletDescription.setText(R.string.first_time_setup_restore_wallet_description);
        
        restoreWalletOption.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRestoreWallet();
            }
            dismiss();
        });
        
        // Activate Child Wallet option
        final View activateChildOption = view.findViewById(R.id.option_activate_child);
        final ImageView activateChildIcon = view.findViewById(R.id.icon_activate_child);
        final TextView activateChildTitle = view.findViewById(R.id.title_activate_child);
        final TextView activateChildDescription = view.findViewById(R.id.description_activate_child);
        
        activateChildIcon.setImageResource(R.drawable.ic_family_24dp);
        activateChildTitle.setText(R.string.first_time_setup_activate_child_title);
        activateChildDescription.setText(R.string.first_time_setup_activate_child_description);
        
        activateChildOption.setOnClickListener(v -> {
            if (listener != null) {
                listener.onActivateChildWallet();
            }
            dismiss();
        });
        
        final DialogBuilder builder = new DialogBuilder(getActivity());
        builder.setTitle(R.string.first_time_setup_title);
        builder.setView(view);
        builder.setCancelable(false); // Force user to make a choice
        builder.setPositiveButton(null, null); // No positive button
        builder.setNegativeButton(null, null); // No negative button
        
        return builder.create();
    }
    
    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        // Don't allow canceling - force user to make a choice
        // This will prevent the dialog from being dismissed by back button or outside touch
    }
}
