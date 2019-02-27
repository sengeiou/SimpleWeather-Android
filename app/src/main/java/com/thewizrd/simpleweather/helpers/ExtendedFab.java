package com.thewizrd.simpleweather.helpers;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.design.button.MaterialButton;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.util.AttributeSet;
import android.view.View;

public class ExtendedFab {
    public static class SnackBarBehavior extends CoordinatorLayout.Behavior<MaterialButton> {
        public SnackBarBehavior() {
        }

        public SnackBarBehavior(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        @Override
        public boolean layoutDependsOn(@NonNull CoordinatorLayout parent, @NonNull MaterialButton child, @NonNull View dependency) {
            return dependency instanceof Snackbar.SnackbarLayout;
        }

        @Override
        public boolean onDependentViewChanged(@NonNull CoordinatorLayout parent, @NonNull MaterialButton child, @NonNull View dependency) {
            float translationY = Math.min(0, dependency.getTranslationY() - dependency.getHeight());
            child.setTranslationY(translationY);
            return true;
        }

        @Override
        public void onDependentViewRemoved(@NonNull CoordinatorLayout parent, @NonNull MaterialButton child, @NonNull View dependency) {
            super.onDependentViewRemoved(parent, child, dependency);
            child.setTranslationY(0);
        }
    }
}