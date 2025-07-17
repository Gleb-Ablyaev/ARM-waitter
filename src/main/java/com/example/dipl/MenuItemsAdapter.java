package com.example.dipl;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.List;

public class MenuItemsAdapter extends ArrayAdapter<MenuItem> {

    private Context context;
    private List<MenuItem> menuItems;

    public MenuItemsAdapter(@NonNull Context context, @NonNull List<MenuItem> list) {
        super(context, 0, list);
        this.context = context;
        this.menuItems = list;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View listItem = convertView;
        if (listItem == null) {
            listItem = LayoutInflater.from(context).inflate(R.layout.list_item_menu, parent, false);
        }

        MenuItem currentItem = menuItems.get(position);

        TextView nameTextView = listItem.findViewById(R.id.textViewMenuItemName);
        CheckBox checkBoxMenuItem = listItem.findViewById(R.id.checkBoxMenuItem);
        EditText editTextItemQuantity = listItem.findViewById(R.id.editTextItemQuantity);

        nameTextView.setText(currentItem.getName());
        editTextItemQuantity.setText(String.valueOf(currentItem.getQuantity()));


        checkBoxMenuItem.setOnCheckedChangeListener(null);
        checkBoxMenuItem.setChecked(currentItem.isSelected());
        checkBoxMenuItem.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                currentItem.setSelected(isChecked);
            }
        });

        // Слушатель для изменения количества
        Object tag = editTextItemQuantity.getTag();
        if (tag instanceof TextWatcher) {
            editTextItemQuantity.removeTextChangedListener((TextWatcher) tag);
        }

        TextWatcher quantityWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    int qty = Integer.parseInt(s.toString());
                    currentItem.setQuantity(qty);
                } catch (NumberFormatException e) {

                    currentItem.setQuantity(1); //
                }
            }
        };
        editTextItemQuantity.addTextChangedListener(quantityWatcher);
        editTextItemQuantity.setTag(quantityWatcher);

        return listItem;
    }
}