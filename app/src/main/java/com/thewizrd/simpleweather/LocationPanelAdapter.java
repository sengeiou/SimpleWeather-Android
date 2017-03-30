package com.thewizrd.simpleweather;

import android.support.v7.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;

import com.thewizrd.simpleweather.weather.weatherunderground.data.Weather;

import java.util.List;

public class LocationPanelAdapter extends RecyclerView.Adapter<LocationPanelAdapter.ViewHolder> {
    public List<LocationPanelModel> mDataset;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public LocationPanel mLocView;
        public ViewHolder(LocationPanel v) {
            super(v);
            v.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                @Override
                public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                    menu.add(Menu.NONE, getAdapterPosition(), 0, "Delete Location");
                }
            });
            mLocView = v;
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public LocationPanelAdapter(List<LocationPanelModel> myDataset) {
        mDataset = myDataset;
    }

    // Create new views (invoked by the layout manager)
    @Override
    public LocationPanelAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                              int viewType) {
        // create a new view
        LocationPanel v = new LocationPanel(parent.getContext());
        // set the view's size, margins, paddings and layout parameters
        v.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        return new ViewHolder(v);
    }

    // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        if (mDataset.get(position).Weather != null)
            holder.mLocView.setWeather((Weather) mDataset.get(position).Weather);

        holder.mLocView.setTag(mDataset.get(position).Pair);
    }

    // Return the size of your dataset (invoked by the layout manager)
    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    public void add(int position, LocationPanelModel item) {
        mDataset.add(position, item);
        notifyDataSetChanged();
    }

    public void remove(int position) {
        mDataset.remove(position);
        notifyItemRemoved(position);

        // Update pair
    }
}