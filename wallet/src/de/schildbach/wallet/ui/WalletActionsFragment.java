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

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import androidx.fragment.app.Fragment;
import de.schildbach.wallet.R;
import de.schildbach.wallet.util.CheatSheet;

/**
 * @author Andreas Schildbach
 */
public final class WalletActionsFragment extends Fragment {
    private WalletActivity activity;

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        this.activity = (WalletActivity) context;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.wallet_actions_fragment, container, false);

        final Button requestButton = view.findViewById(R.id.wallet_actions_request);
        requestButton.setOnClickListener(v -> activity.handleRequestCoins());

        final Button sendButton = view.findViewById(R.id.wallet_actions_send);
        sendButton.setOnClickListener(v -> activity.handleSendCoins());

        final View sendQrButton = view.findViewById(R.id.wallet_actions_send_qr);
        sendQrButton.setOnClickListener(v -> activity.handleScan(v));
        CheatSheet.setup(sendQrButton);

        // Check if QR code button is being pushed off screen and hide text if needed
        // Use a listener that checks on every layout to catch zoom changes
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                checkAndHideTextIfNeeded(view, requestButton, sendButton, sendQrButton);
            }
        });
        
        // Also check immediately after view is attached
        view.post(() -> checkAndHideTextIfNeeded(view, requestButton, sendButton, sendQrButton));

        return view;
    }

    private void checkAndHideTextIfNeeded(View parentView, Button requestButton, Button sendButton, View qrButton) {
        // Only check if views are laid out
        if (parentView.getWidth() == 0 || qrButton.getWidth() == 0) {
            return;
        }
        
        // Check actual layout positions to see if QR button is being pushed off screen
        int parentWidth = parentView.getWidth();
        int qrButtonRight = qrButton.getRight();
        int qrButtonLeft = qrButton.getLeft();
        
        // Check if QR button is actually visible on screen
        // QR button should be fully visible, so check if its right edge is within parent bounds
        // Use a more conservative check - only hide if QR button is actually being pushed off
        boolean qrButtonPushedOff = false;
        
        // If QR button's right edge is beyond parent width (completely off screen)
        if (qrButtonRight > parentWidth) {
            qrButtonPushedOff = true;
        }
        // Or if QR button's left edge is beyond parent width (completely off screen to the right)
        else if (qrButtonLeft >= parentWidth) {
            qrButtonPushedOff = true;
        }
        // Or if QR button is very close to the edge (within 5px of being pushed off)
        else if (qrButtonRight > parentWidth - 5) {
            qrButtonPushedOff = true;
        }
        
        // Apply text visibility based on actual layout, not font scale
        if (qrButtonPushedOff) {
            // Hide text, keep only icons
            if (requestButton.getText().length() > 0) {
                requestButton.setText("");
            }
            if (sendButton.getText().length() > 0) {
                sendButton.setText("");
            }
        } else {
            // Show text normally only if buttons don't have text already
            // This prevents flickering when text is already set
            if (requestButton.getText().length() == 0) {
                requestButton.setText(R.string.button_request_coins);
            }
            if (sendButton.getText().length() == 0) {
                sendButton.setText(R.string.button_send_coins);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        updateView();
        
        // Re-check text visibility when resuming (in case zoom changed)
        final View view = getView();
        if (view != null) {
            final Button requestButton = view.findViewById(R.id.wallet_actions_request);
            final Button sendButton = view.findViewById(R.id.wallet_actions_send);
            final View sendQrButton = view.findViewById(R.id.wallet_actions_send_qr);
            if (requestButton != null && sendButton != null && sendQrButton != null) {
                view.post(() -> checkAndHideTextIfNeeded(view, requestButton, sendButton, sendQrButton));
            }
        }
    }

    private void updateView() {
        final boolean showActions = !getResources().getBoolean(R.bool.wallet_actions_top);

        final View view = getView();
        final ViewParent parent = view.getParent();
        final View fragment = parent instanceof FrameLayout ? (FrameLayout) parent : view;
        fragment.setVisibility(showActions ? View.VISIBLE : View.GONE);
    }
}
