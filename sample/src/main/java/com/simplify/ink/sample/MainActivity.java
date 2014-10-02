package com.simplify.ink.sample;


import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.simplify.ink.InkView;

public class MainActivity extends Activity
{
    InkView mInkView;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mInkView = (InkView) findViewById(R.id.ink);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.options, menu);
        menu.findItem(R.id.menu_interpolation).setChecked(mInkView.hasFlags(InkView.FLAG_INTERPOLATION));
        menu.findItem(R.id.menu_responsive).setChecked(mInkView.hasFlags(InkView.FLAG_RESPONSIVE_WIDTH));
        menu.findItem(R.id.menu_debug).setChecked(mInkView.getMode() == InkView.Mode.DEBUG);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.menu_clear:
                mInkView.clear();
                return true;

            case R.id.menu_interpolation:
                item.setChecked(!item.isChecked());
                if (item.isChecked()) {
                    mInkView.addFlags(InkView.FLAG_INTERPOLATION);
                }
                else {
                    mInkView.removeFlags(InkView.FLAG_INTERPOLATION);
                }
                return true;

            case R.id.menu_responsive:
                item.setChecked(!item.isChecked());
                if (item.isChecked()) {
                    mInkView.addFlags(InkView.FLAG_RESPONSIVE_WIDTH);
                }
                else {
                    mInkView.removeFlags(InkView.FLAG_RESPONSIVE_WIDTH);
                }
                return true;

            case R.id.menu_debug:
                item.setChecked(!item.isChecked());
                mInkView.setMode(item.isChecked() ? InkView.Mode.DEBUG : InkView.Mode.NORMAL);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
