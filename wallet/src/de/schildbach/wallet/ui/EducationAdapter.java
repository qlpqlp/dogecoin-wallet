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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.TextView;

import de.schildbach.wallet.R;

/**
 * Adapter for the Education expandable list view
 * 
 * @author Paulo Vidal - x.com/inevitable360 (Dogecoin Foundation)
 */
public class EducationAdapter extends BaseExpandableListAdapter {
    
    private Context context;
    private String[] groups;
    private String[][] children;
    
    public EducationAdapter(Context context) {
        this.context = context;
        initializeData();
    }
    
    private void initializeData() {
        // Organized in logical order for understanding Dogecoin:
        // 1. What is Dogecoin? (basics and advantages)
        // 2. What is Blockchain? (foundation)
        // 3. What is Mining? (who maintains it)
        // 4. Transaction Fees (why we pay fees)
        // 5. Addresses (how to send/receive)
        // 6. SPV vs Full Node (technical but important)
        // 7. Why This Wallet? (wallet advantages)
        // 8. Security (how to stay safe)
        // 9. Family Mode (special feature)
        // 10. RadioDoge (special feature)
        // 11. Dogecoin Philosophy (community and values)
        // 12. Tips (getting started)
        groups = new String[]{
            context.getString(R.string.education_dogecoin_advantages_title),
            context.getString(R.string.education_blockchain_title),
            context.getString(R.string.education_mining_title),
            context.getString(R.string.education_transaction_fees_title),
            context.getString(R.string.education_addresses_title),
            context.getString(R.string.education_spv_vs_full_node_title),
            context.getString(R.string.education_wallet_advantages_title),
            context.getString(R.string.education_security_title),
            context.getString(R.string.education_family_mode_title),
            context.getString(R.string.education_radiodoge_title),
            context.getString(R.string.education_dogecoin_philosophy_title),
            context.getString(R.string.education_tips_title)
        };
        
        children = new String[][]{
            {
                context.getString(R.string.education_dogecoin_advantages_decentralized),
                context.getString(R.string.education_dogecoin_advantages_fast),
                context.getString(R.string.education_dogecoin_advantages_low_fees),
                context.getString(R.string.education_dogecoin_advantages_global),
                context.getString(R.string.education_dogecoin_advantages_transparent)
            },
            {
                context.getString(R.string.education_blockchain_what_is),
                context.getString(R.string.education_blockchain_how_works),
                context.getString(R.string.education_blockchain_blocks),
                context.getString(R.string.education_blockchain_consensus),
                context.getString(R.string.education_blockchain_immutable)
            },
            {
                context.getString(R.string.education_mining_what_is),
                context.getString(R.string.education_mining_how_works),
                context.getString(R.string.education_mining_rewards),
                context.getString(R.string.education_mining_difficulty),
                context.getString(R.string.education_mining_energy)
            },
            {
                context.getString(R.string.education_transaction_fees_what_are),
                context.getString(R.string.education_transaction_fees_why_needed),
                context.getString(R.string.education_transaction_fees_how_much),
                context.getString(R.string.education_transaction_fees_examples),
                context.getString(R.string.education_transaction_fees_cheap)
            },
            {
                context.getString(R.string.education_addresses_what_is),
                context.getString(R.string.education_addresses_how_works),
                context.getString(R.string.education_addresses_sub_addresses),
                context.getString(R.string.education_addresses_new_addresses),
                context.getString(R.string.education_addresses_privacy)
            },
            {
                context.getString(R.string.education_spv_what_is),
                context.getString(R.string.education_spv_vs_full_node),
                context.getString(R.string.education_spv_advantages),
                context.getString(R.string.education_spv_how_works),
                context.getString(R.string.education_spv_security)
            },
            {
                context.getString(R.string.education_wallet_advantages_self_custody),
                context.getString(R.string.education_wallet_advantages_privacy),
                context.getString(R.string.education_wallet_advantages_family_mode),
                context.getString(R.string.education_wallet_advantages_offline),
                context.getString(R.string.education_wallet_advantages_open_source)
            },
            {
                context.getString(R.string.education_security_private_keys),
                context.getString(R.string.education_security_backup),
                context.getString(R.string.education_security_biometric),
                context.getString(R.string.education_security_encryption),
                context.getString(R.string.education_security_best_practices)
            },
            {
                context.getString(R.string.education_family_mode_what_is),
                context.getString(R.string.education_family_mode_how_works),
                context.getString(R.string.education_family_mode_benefits),
                context.getString(R.string.education_family_mode_safety),
                context.getString(R.string.education_family_mode_teaching)
            },
            {
                context.getString(R.string.education_radiodoge_what_is),
                context.getString(R.string.education_radiodoge_how_works),
                context.getString(R.string.education_radiodoge_benefits),
                context.getString(R.string.education_radiodoge_global),
                context.getString(R.string.education_radiodoge_future)
            },
            {
                context.getString(R.string.education_dogecoin_philosophy_purpose),
                context.getString(R.string.education_dogecoin_philosophy_money),
                context.getString(R.string.education_dogecoin_philosophy_unbanked),
                context.getString(R.string.education_dogecoin_philosophy_global),
                context.getString(R.string.education_dogecoin_philosophy_community)
            },
            {
                context.getString(R.string.education_tips_start_small),
                context.getString(R.string.education_tips_learn_gradually),
                context.getString(R.string.education_tips_community),
                context.getString(R.string.education_tips_stay_updated),
                context.getString(R.string.education_tips_have_fun)
            }
        };
    }
    
    @Override
    public int getGroupCount() {
        return groups.length;
    }
    
    @Override
    public int getChildrenCount(int groupPosition) {
        return children[groupPosition].length;
    }
    
    @Override
    public Object getGroup(int groupPosition) {
        return groups[groupPosition];
    }
    
    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return children[groupPosition][childPosition];
    }
    
    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }
    
    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }
    
    @Override
    public boolean hasStableIds() {
        return false;
    }
    
    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.item_education_group, parent, false);
        }
        
        TextView groupTitle = convertView.findViewById(R.id.group_title);
        groupTitle.setText(groups[groupPosition]);
        
        return convertView;
    }
    
    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.item_education_child, parent, false);
        }
        
        TextView childText = convertView.findViewById(R.id.child_text);
        childText.setText(children[groupPosition][childPosition]);
        
        return convertView;
    }
    
    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }
}
