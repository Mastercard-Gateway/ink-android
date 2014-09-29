package com.simplify.ink.sample;


import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.simplify.ink.InkView;

public class MainActivity extends Activity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final InkView ink = (InkView) findViewById(R.id.ink);
//        ink.setSmoothingRatio(0.75f);

        findViewById(R.id.clear).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                ink.clear();
            }
        });

        ((CheckBox) findViewById(R.id.debug)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                ink.setMode(isChecked ? InkView.Mode.DEBUG : InkView.Mode.NORMAL);
            }
        });
    }
}
