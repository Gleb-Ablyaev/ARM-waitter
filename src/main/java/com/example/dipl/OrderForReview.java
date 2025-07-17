package com.example.dipl;
public class OrderForReview {
    int id; // ID заказа
    String description;

    public OrderForReview(int id, String description) {
        this.id = id;
        this.description = description;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }


    @Override
    public String toString() {
        return description;
    }
}