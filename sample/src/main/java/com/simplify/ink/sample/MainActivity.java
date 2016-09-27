package com.simplify.ink.sample;


import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.simplify.ink.InkView;

public class MainActivity extends Activity {

    InkView inkView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inkView = (InkView) findViewById(R.id.ink);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options, menu);
        menu.findItem(R.id.menu_interpolation).setChecked(inkView.hasFlag(InkView.FLAG_INTERPOLATION));
        menu.findItem(R.id.menu_responsive).setChecked(inkView.hasFlag(InkView.FLAG_RESPONSIVE_WIDTH));

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_clear:
                inkView.clear();
                return true;

            case R.id.menu_interpolation:
                item.setChecked(!item.isChecked());
                if (item.isChecked()) {
                    inkView.addFlag(InkView.FLAG_INTERPOLATION);
                } else {
                    inkView.removeFlag(InkView.FLAG_INTERPOLATION);
                }
                return true;

            case R.id.menu_responsive:
                item.setChecked(!item.isChecked());
                if (item.isChecked()) {
                    inkView.addFlag(InkView.FLAG_RESPONSIVE_WIDTH);
                } else {
                    inkView.removeFlag(InkView.FLAG_RESPONSIVE_WIDTH);
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
