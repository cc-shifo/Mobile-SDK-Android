package com.dji.sdk.sample.internal.view;

import android.content.Context;
import android.content.Intent;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.demo.payload.PayloadActivity;
import com.dji.sdk.sample.demo.payload.PayloadSendGetDataActivity;
import com.dji.sdk.sample.demo.payload.PayloadSendGetDataPipelineTCPActivity;
import com.dji.sdk.sample.demo.payload.PayloadWidgetActivity;
import com.dji.sdk.sample.internal.utils.ToastUtils;

public class StartPayloadAcitivityView extends LinearLayout implements PresentableView{

    @Override
    public int getDescription() {
        return R.string.component_listview_payload;
    }
    public StartPayloadAcitivityView(Context context) {
        super(context);
        init(context);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @NonNull
    @Override
    public String getHint() {
        return this.getClass().getSimpleName() + ".java";
    }

    private void init(final Context context) {
        setOrientation(LinearLayout.VERTICAL);
        setGravity(Gravity.CENTER);
        setClickable(false);
        Button start = new Button(new ContextThemeWrapper(context, R.style.button_style));
        start.setBackground(context.getResources().getDrawable(R.drawable.round_btn));
        start.setText(R.string.start_payload);
        start.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, PayloadActivity.class);
                try {
                    context.startActivity(intent);
                } catch (Exception e) {
                    ToastUtils.showToast(context.getResources().getString(R.string.start_payload_fail));
                }
            }
        });
        Button startSendDataActivity = new Button(new ContextThemeWrapper(context, R.style.button_style));
        startSendDataActivity.setText(R.string.start_payload_send_data);
        startSendDataActivity.setBackground(context.getResources().getDrawable(R.drawable.round_btn));
        startSendDataActivity.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, PayloadSendGetDataActivity.class);
                try {
                    context.startActivity(intent);
                } catch (Exception e) {
                    ToastUtils.showToast(context.getResources().getString(R.string.start_payload_fail));
                }
            }
        });
        Button payloadWidgetActivity = new Button(new ContextThemeWrapper(context, R.style.button_style));
        payloadWidgetActivity.setText(R.string.payload_widget);
        payloadWidgetActivity.setBackground(context.getResources().getDrawable(R.drawable.round_btn));
        payloadWidgetActivity.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, PayloadWidgetActivity.class);
                try {
                    context.startActivity(intent);
                } catch (Exception e) {
                    ToastUtils.showToast(context.getResources().getString(R.string.start_payload_fail));
                }
            }
        });
        Button payloadPipeTCPActivity = new Button(new ContextThemeWrapper(context,
                R.style.button_style));
        payloadPipeTCPActivity.setText(R.string.payload_pipeline_tcp);
        payloadPipeTCPActivity.setBackground(context.getResources().getDrawable(R.drawable.round_btn));
        payloadPipeTCPActivity.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(context, PayloadSendGetDataPipelineTCPActivity.class);
                try {
                    context.startActivity(intent);
                } catch (Exception e) {
                    ToastUtils.showToast(context.getResources().getString(R.string.start_payload_fail));
                }
            }
        });
        this.addView(start);
        this.addView(startSendDataActivity);
        this.addView(payloadWidgetActivity);
        this.addView(payloadPipeTCPActivity);
    }

}
