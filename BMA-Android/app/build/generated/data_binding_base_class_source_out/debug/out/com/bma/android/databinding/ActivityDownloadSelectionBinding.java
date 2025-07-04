// Generated by view binder compiler. Do not edit!
package com.bma.android.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.viewbinding.ViewBinding;
import androidx.viewbinding.ViewBindings;
import androidx.viewpager2.widget.ViewPager2;
import com.bma.android.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import java.lang.NullPointerException;
import java.lang.Override;
import java.lang.String;

public final class ActivityDownloadSelectionBinding implements ViewBinding {
  @NonNull
  private final LinearLayout rootView;

  @NonNull
  public final LinearLayout buttonContainer;

  @NonNull
  public final MaterialButton downloadAllButton;

  @NonNull
  public final MaterialButton downloadSelectedButton;

  @NonNull
  public final TabLayout tabLayout;

  @NonNull
  public final Toolbar toolbar;

  @NonNull
  public final ViewPager2 viewPager;

  private ActivityDownloadSelectionBinding(@NonNull LinearLayout rootView,
      @NonNull LinearLayout buttonContainer, @NonNull MaterialButton downloadAllButton,
      @NonNull MaterialButton downloadSelectedButton, @NonNull TabLayout tabLayout,
      @NonNull Toolbar toolbar, @NonNull ViewPager2 viewPager) {
    this.rootView = rootView;
    this.buttonContainer = buttonContainer;
    this.downloadAllButton = downloadAllButton;
    this.downloadSelectedButton = downloadSelectedButton;
    this.tabLayout = tabLayout;
    this.toolbar = toolbar;
    this.viewPager = viewPager;
  }

  @Override
  @NonNull
  public LinearLayout getRoot() {
    return rootView;
  }

  @NonNull
  public static ActivityDownloadSelectionBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static ActivityDownloadSelectionBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.activity_download_selection, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static ActivityDownloadSelectionBinding bind(@NonNull View rootView) {
    // The body of this method is generated in a way you would not otherwise write.
    // This is done to optimize the compiled bytecode for size and performance.
    int id;
    missingId: {
      id = R.id.button_container;
      LinearLayout buttonContainer = ViewBindings.findChildViewById(rootView, id);
      if (buttonContainer == null) {
        break missingId;
      }

      id = R.id.download_all_button;
      MaterialButton downloadAllButton = ViewBindings.findChildViewById(rootView, id);
      if (downloadAllButton == null) {
        break missingId;
      }

      id = R.id.download_selected_button;
      MaterialButton downloadSelectedButton = ViewBindings.findChildViewById(rootView, id);
      if (downloadSelectedButton == null) {
        break missingId;
      }

      id = R.id.tab_layout;
      TabLayout tabLayout = ViewBindings.findChildViewById(rootView, id);
      if (tabLayout == null) {
        break missingId;
      }

      id = R.id.toolbar;
      Toolbar toolbar = ViewBindings.findChildViewById(rootView, id);
      if (toolbar == null) {
        break missingId;
      }

      id = R.id.view_pager;
      ViewPager2 viewPager = ViewBindings.findChildViewById(rootView, id);
      if (viewPager == null) {
        break missingId;
      }

      return new ActivityDownloadSelectionBinding((LinearLayout) rootView, buttonContainer,
          downloadAllButton, downloadSelectedButton, tabLayout, toolbar, viewPager);
    }
    String missingId = rootView.getResources().getResourceName(id);
    throw new NullPointerException("Missing required view with ID: ".concat(missingId));
  }
}
