package com.example.dipl;

public class MenuItem {
    int id;
    String name;
    boolean isSelected;
    int quantity;

    public MenuItem(int id, String name) {
        this.id = id;
        this.name = name;
        this.isSelected = false;
        this.quantity = 1;
    }


    public int getId() { return id; }
    public String getName() { return name; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity > 0 ? quantity : 1; }

    @Override
    public String toString() {
        return name;
    }
}
