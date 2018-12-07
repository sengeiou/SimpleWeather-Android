package com.thewizrd.simpleweather.helpers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.View;

import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simpleweather.App;
import com.thewizrd.simpleweather.R;

public class ItemTouchHelperCallback extends ItemTouchHelper.Callback {
    private boolean dragEnabled = true;
    private boolean swipeEnabled = true;

    private ItemTouchHelperAdapterInterface mAdapter;

    private Drawable deleteIcon;
    private Drawable deleteBackground;
    private int iconMargin;

    @Override
    public boolean isLongPressDragEnabled() {
        return dragEnabled;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return swipeEnabled;
    }

    public ItemTouchHelperCallback(ItemTouchHelperAdapterInterface adapter) {
        Context context = App.getInstance().getAppContext();

        mAdapter = adapter;
        deleteIcon = ContextCompat.getDrawable(context, R.drawable.ic_delete_white_24dp);
        deleteBackground = new ColorDrawable(ContextCompat.getColor(context, R.color.bg_swipe_delete));
        iconMargin = (int) context.getResources().getDimension(R.dimen.delete_icon_margin);
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        int dragFlags = 0;
        int swipeFlags = 0;

        if (dragEnabled && swipeEnabled) {
            dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
            swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
        } else if (dragEnabled)
            dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        else if (swipeEnabled)
            swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;

        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        mAdapter.onItemMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
        return true;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        mAdapter.onItemDismiss(viewHolder.getAdapterPosition());
    }

    @Override
    public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
        if (viewHolder.getAdapterPosition() == -1)
            return;

        try {
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                View itemView = viewHolder.itemView;

                int iconLeft;
                int iconRight;
                int iconTop = itemView.getTop() + (itemView.getBottom() - itemView.getTop() - deleteIcon.getIntrinsicHeight()) / 2;
                int iconBottom = iconTop + deleteIcon.getIntrinsicHeight();

                if (dX > 0) {
                    deleteBackground.setBounds(itemView.getLeft(), itemView.getTop(), itemView.getLeft() + (int) dX, itemView.getBottom());

                    iconLeft = itemView.getLeft() + iconMargin;
                    iconRight = itemView.getLeft() + iconMargin + deleteIcon.getIntrinsicWidth();
                } else {
                    deleteBackground.setBounds(itemView.getRight() + (int) dX, itemView.getTop(), itemView.getRight(), itemView.getBottom());

                    iconLeft = itemView.getRight() - iconMargin - deleteIcon.getIntrinsicHeight();
                    iconRight = itemView.getRight() - iconMargin;
                }

                deleteBackground.draw(c);

                deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                deleteIcon.draw(c);
            }
        } catch (Exception ex) {
            Logger.writeLine(Log.INFO, ex, "SimpleWeather: ItemTouchHelperCallback: object disposed error");
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    @Override
    public long getAnimationDuration(RecyclerView recyclerView, int animationType, float animateDx, float animateDy) {
        if (animationType == ItemTouchHelper.ANIMATION_TYPE_SWIPE_SUCCESS)
            return 350; // Default is 250
        else
            return super.getAnimationDuration(recyclerView, animationType, animateDx, animateDy);
    }

    public void setLongPressDragEnabled(boolean value) {
        dragEnabled = value;
    }

    public void setItemViewSwipeEnabled(boolean value) {
        swipeEnabled = value;
    }
}