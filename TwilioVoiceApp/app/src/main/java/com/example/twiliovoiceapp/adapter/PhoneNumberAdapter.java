package com.example.twiliovoiceapp.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.twiliovoiceapp.R;
import com.example.twiliovoiceapp.model.TwilioPhoneNumber;
import com.google.android.material.chip.Chip;

import java.util.List;

/**
 * Adapter for displaying Twilio phone numbers in a RecyclerView.
 * This adapter handles displaying phone numbers with their nicknames,
 * showing which number is the default, and providing actions for
 * editing nicknames, setting a number as default, and deleting numbers.
 */
public class PhoneNumberAdapter extends RecyclerView.Adapter<PhoneNumberAdapter.PhoneNumberViewHolder> {

    private final Context context;
    private final List<TwilioPhoneNumber> phoneNumbers;
    private final PhoneNumberActionListener listener;

    /**
     * Interface for phone number actions
     */
    public interface PhoneNumberActionListener {
        void onEditNickname(TwilioPhoneNumber phoneNumber);
        void onSetDefault(TwilioPhoneNumber phoneNumber);
        void onDelete(TwilioPhoneNumber phoneNumber);
    }

    /**
     * Constructor for the adapter
     *
     * @param context The context
     * @param phoneNumbers The list of phone numbers to display
     */
    public PhoneNumberAdapter(Context context, List<TwilioPhoneNumber> phoneNumbers) {
        this.context = context;
        this.phoneNumbers = phoneNumbers;
        
        // The context must implement the action listener interface
        if (context instanceof PhoneNumberActionListener) {
            this.listener = (PhoneNumberActionListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement PhoneNumberActionListener");
        }
    }

    @NonNull
    @Override
    public PhoneNumberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_phone_number, parent, false);
        return new PhoneNumberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhoneNumberViewHolder holder, int position) {
        TwilioPhoneNumber phoneNumber = phoneNumbers.get(position);
        
        // Set phone number
        holder.textViewPhoneNumber.setText(phoneNumber.getPhoneNumber());
        
        // Set nickname (if available)
        if (phoneNumber.getNickname() != null && !phoneNumber.getNickname().isEmpty()) {
            holder.textViewNickname.setText(phoneNumber.getNickname());
            holder.textViewNickname.setVisibility(View.VISIBLE);
        } else {
            holder.textViewNickname.setVisibility(View.GONE);
        }
        
        // Show/hide default indicator
        holder.chipDefault.setVisibility(phoneNumber.isDefault() ? View.VISIBLE : View.GONE);
        
        // Update default button icon based on status
        holder.buttonSetDefault.setImageResource(
                phoneNumber.isDefault() ? R.drawable.ic_star : R.drawable.ic_star_outline);
        
        // Set action button click listeners
        holder.buttonEditNickname.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditNickname(phoneNumber);
            }
        });
        
        holder.buttonSetDefault.setOnClickListener(v -> {
            if (listener != null && !phoneNumber.isDefault()) {
                listener.onSetDefault(phoneNumber);
            }
        });
        
        holder.buttonDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onDelete(phoneNumber);
            }
        });
    }

    @Override
    public int getItemCount() {
        return phoneNumbers.size();
    }

    /**
     * ViewHolder for phone number items
     */
    static class PhoneNumberViewHolder extends RecyclerView.ViewHolder {
        ImageView imageViewPhoneIcon;
        TextView textViewPhoneNumber;
        TextView textViewNickname;
        Chip chipDefault;
        ImageButton buttonEditNickname;
        ImageButton buttonSetDefault;
        ImageButton buttonDelete;

        public PhoneNumberViewHolder(@NonNull View itemView) {
            super(itemView);
            
            imageViewPhoneIcon = itemView.findViewById(R.id.imageViewPhoneIcon);
            textViewPhoneNumber = itemView.findViewById(R.id.textViewPhoneNumber);
            textViewNickname = itemView.findViewById(R.id.textViewNickname);
            chipDefault = itemView.findViewById(R.id.chipDefault);
            buttonEditNickname = itemView.findViewById(R.id.buttonEditNickname);
            buttonSetDefault = itemView.findViewById(R.id.buttonSetDefault);
            buttonDelete = itemView.findViewById(R.id.buttonDelete);
        }
    }
}
