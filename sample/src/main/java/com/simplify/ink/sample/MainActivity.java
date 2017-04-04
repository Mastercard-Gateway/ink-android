package com.simplify.ink.sample;


import android.app.Activity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import com.simplify.ink.InkView;

public class MainActivity extends Activity {

    Toolbar toolbar;
    InkView inkView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        inkView = (InkView) findViewById(R.id.ink);

        initToolbar();
    }

    void initToolbar() {
        Menu menu = toolbar.getMenu();
        getMenuInflater().inflate(R.menu.options, menu);

        MenuItem item = menu.findItem(R.id.menu_interpolation);
        item.setChecked(inkView.hasFlag(InkView.FLAG_INTERPOLATION));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                item.setChecked(!item.isChecked());
                if (item.isChecked()) {
                    inkView.addFlag(InkView.FLAG_INTERPOLATION);
                } else {
                    inkView.removeFlag(InkView.FLAG_INTERPOLATION);
                }
                return true;
            }
        });

        item = menu.findItem(R.id.menu_responsive);
        item.setChecked(inkView.hasFlag(InkView.FLAG_RESPONSIVE_WIDTH));
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                item.setChecked(!item.isChecked());
                if (item.isChecked()) {
                    inkView.addFlag(InkView.FLAG_RESPONSIVE_WIDTH);
                } else {
                    inkView.removeFlag(InkView.FLAG_RESPONSIVE_WIDTH);
                }
                return true;
            }
        });

        item = menu.findItem(R.id.menu_clear);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        item.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                inkView.clear();
                return true;
            }
        });
    }
}
