package com.phynix.artham.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import com.phynix.artham.R;
import com.phynix.artham.auth.AuthManager;
import com.phynix.artham.adapters.CategorySelectionAdapter;
import com.phynix.artham.models.CategoryModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CategoryFilterFragment extends BottomSheetDialogFragment {

    private RecyclerView recyclerViewCategories;
    private EditText searchEditText;
    private LinearLayout noCategoriesLayout;

    private CategorySelectionAdapter adapter;
    private List<CategoryModel> fullCategoryList;
    private List<CategoryModel> filteredCategoryList;
    private Set<String> selectedCategories;


    private OnFilterAppliedListener filterListener;

    public interface OnFilterAppliedListener {
        void onFilterApplied(Set<String> selectedCategories);
    }

    public void setFilterListener(OnFilterAppliedListener listener) {
        this.filterListener = listener;
    }

    public void setPreselectedCategories(Set<String> preselected) {
        if (preselected != null) {
            this.selectedCategories = new HashSet<>(preselected);
        } else {
            this.selectedCategories = new HashSet<>();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Uses the theme-compatible dialog layout
        View view = inflater.inflate(R.layout.dialog_category_selection, container, false);

        String userId = AuthManager.getUserId(requireContext());

        if (selectedCategories == null) {
            selectedCategories = new HashSet<>();
        }

        initViews(view);
        setupRecyclerView();
        setupSearch();
        loadCategories();

        return view;
    }

    private void initViews(View view) {
        recyclerViewCategories = view.findViewById(R.id.recyclerViewCategories);
        searchEditText = view.findViewById(R.id.searchEditText);
        noCategoriesLayout = view.findViewById(R.id.noCategoriesLayout);
    }

    private void setupRecyclerView() {
        fullCategoryList = new ArrayList<>();
        filteredCategoryList = new ArrayList<>();

        adapter = new CategorySelectionAdapter(filteredCategoryList, selectedCategories, updatedSelection -> {
            this.selectedCategories = updatedSelection;
            if (filterListener != null) {
                // Pass the updated filter selection back to the parent Activity/Fragment
                filterListener.onFilterApplied(selectedCategories);
            }
        });

        // Use StaggeredGridLayoutManager as defined in your XML for compact, wrapping chips
        recyclerViewCategories.setLayoutManager(new StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL));
        recyclerViewCategories.setAdapter(adapter);
    }

    private void loadCategories() {
        filterList("");
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterList(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterList(String query) {
        filteredCategoryList.clear();
        if (query.trim().isEmpty()) {
            filteredCategoryList.addAll(fullCategoryList);
        } else {
            for (CategoryModel model : fullCategoryList) {
                if (model.getName().toLowerCase().contains(query.toLowerCase().trim())) {
                    filteredCategoryList.add(model);
                }
            }
        }

        adapter.updateList(filteredCategoryList);

        // Toggle Empty State UI
        if (filteredCategoryList.isEmpty()) {
            recyclerViewCategories.setVisibility(View.GONE);
            noCategoriesLayout.setVisibility(View.VISIBLE);
        } else {
            recyclerViewCategories.setVisibility(View.VISIBLE);
            noCategoriesLayout.setVisibility(View.GONE);
        }
    }
}