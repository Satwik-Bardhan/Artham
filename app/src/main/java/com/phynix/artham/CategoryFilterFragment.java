package com.phynix.artham;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.phynix.artham.adapters.CategorySelectionAdapter;
import com.phynix.artham.models.CategoryModel;
import com.phynix.artham.utils.CategoryColorUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CategoryFilterFragment extends Fragment {

    private RecyclerView categoriesRecyclerView;
    private EditText searchEditText;
    private LinearLayout noCategoriesLayout;

    private CategorySelectionAdapter adapter;
    private List<CategoryModel> allCategories = new ArrayList<>();
    private List<CategoryModel> filteredCategories = new ArrayList<>();
    private Set<String> selectedCategories = new HashSet<>();

    private DatabaseReference userCategoriesRef;

    // Default constructor
    public CategoryFilterFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_category_selection, container, false);

        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView);
        searchEditText = view.findViewById(R.id.searchEditText);
        noCategoriesLayout = view.findViewById(R.id.noCategoriesLayout);

        // Setup RecyclerView
        categoriesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CategorySelectionAdapter(filteredCategories, selectedCategories);
        categoriesRecyclerView.setAdapter(adapter);

        setupSearch();
        loadCategories();

        return view;
    }

    private void loadCategories() {
        // Load Defaults
        String[] predefined = getResources().getStringArray(R.array.transaction_categories);
        for (String name : predefined) {
            if (!"Select Category".equals(name) && !"No Category".equals(name)) {
                int color = CategoryColorUtil.getCategoryColor(getContext(), name);
                String hex = String.format("#%06X", (0xFFFFFF & color));
                allCategories.add(new CategoryModel(name, hex, false));
            }
        }
        filterCategories(""); // Init filter list

        // Load Custom
        String uid = FirebaseAuth.getInstance().getUid();
        // Assuming you pass cashbookID via arguments, otherwise handle that logic
        if (uid != null) {
            // Note: Add logic here to get correct cashbook ID if needed
        }
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCategories(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterCategories(String query) {
        filteredCategories.clear();
        String lowerQuery = query.toLowerCase().trim();

        if (lowerQuery.isEmpty()) {
            filteredCategories.addAll(allCategories);
        } else {
            for (CategoryModel cat : allCategories) {
                if (cat.getName().toLowerCase().contains(lowerQuery)) {
                    filteredCategories.add(cat); // [FIXED] Adding Model, not String
                }
            }
        }

        if (adapter != null) adapter.notifyDataSetChanged();

        noCategoriesLayout.setVisibility(filteredCategories.isEmpty() ? View.VISIBLE : View.GONE);
        categoriesRecyclerView.setVisibility(filteredCategories.isEmpty() ? View.GONE : View.VISIBLE);
    }
}