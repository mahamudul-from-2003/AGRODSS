package com.agrodss.agrodss;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

public class Dataset extends Fragment {

    public Dataset() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dataset, container, false);

        // Initialize buttons
        LinearLayout bean = view.findViewById(R.id.bean);
        LinearLayout paddy = view.findViewById(R.id.paddy);
        LinearLayout tomato = view.findViewById(R.id.tomato);

        // Set click listeners - OPEN IN BROWSER (Most reliable)
        bean.setOnClickListener(v -> openInBrowser("https://drive.google.com/drive/folders/1uZwQPdv8StikZZK-wwX929kddssXylX3"));
        paddy.setOnClickListener(v -> openInBrowser("https://drive.google.com/drive/folders/1MN-_Vv1NgZC_87Xk-unTfDF2n7KvmZ6G"));
        tomato.setOnClickListener(v -> openInBrowser("https://drive.google.com/drive/folders/1aWZCLlVecBRdLi4-UCH6WwaZyPdR6z8k"));


        return view;
    }

    // Method 1: Open directly in browser (Most reliable)
    private void openInBrowser(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Cannot open link", Toast.LENGTH_SHORT).show();
        }
    }
}