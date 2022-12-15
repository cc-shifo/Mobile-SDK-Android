package com.dji.sdk.sample.demo.payload;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.GeneralUtils;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import com.dji.sdk.sample.internal.utils.ViewHelper;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import dji.common.util.CommonCallbacks;
import dji.mop.common.Pipeline;
import dji.mop.common.PipelineError;
import dji.mop.common.Pipelines;
import dji.mop.common.TransmissionControlType;
import dji.sdk.payload.Payload;

/**
 * Created by Michael on 17/11/6.
 */

public class PayloadSendGetDataPipelineTCPActivity extends AppCompatActivity
        implements View.OnClickListener {
    private static final String TAG = "PayloadTCPData";
    private static final int POINT_CLOUD_PORT = 49153;
    private TextView receivedDataView;
    private TextView payloadNameView;
    private TextView sendTotal;
    private TextView receiveTotal;
    private int sendSizeTotal = 0;
    private int receiveSizeTotal = 0;
    private EditText sendDataEditView;
    private EditText periodView;
    private CheckBox repeatCheckbox;
    private Payload payload = null;
    private String payloadName = "";
    private Pipelines pipelines = null;
    private Pipeline pointCloudPipe = null;
    private ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1);
    private ScheduledFuture<?> scheduledFuture;

    private AtomicBoolean mStop = new AtomicBoolean(false);
    private Runnable repeatRunnable = new Runnable() {
        @Override
        public void run() {
            sendDataToPayload();
        }
    };

    private ScheduledFuture<?> receiveScheduledFuture;
    private Runnable receiveRunnable = new Runnable() {
        @Override
        public void run() {
            readData();
        }
    };

    private void updateTXView(final int size) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                sendSizeTotal = size + sendSizeTotal;
                sendTotal.setText(String.valueOf(sendSizeTotal));
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_payload_tcp_test_data);
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        payloadNameView = (TextView) findViewById(R.id.payload_name);
        receivedDataView = (TextView) findViewById(R.id.push_info_text);
        sendTotal = (TextView) findViewById(R.id.send_total_size);
        sendTotal.setText("0");
        receiveTotal = (TextView) findViewById(R.id.receive_total_size);
        receiveTotal.setText("0");
        receivedDataView.setMovementMethod(new ScrollingMovementMethod());
        sendDataEditView = (EditText) findViewById(R.id.sending_data);
        periodView = (EditText) findViewById(R.id.period_value);
        repeatCheckbox = (CheckBox) findViewById(R.id.repeat_send_checkbox);
        repeatCheckbox.setOnClickListener(this);

        initListener();
    }

    private void initListener() {
        View sendButton = findViewById(R.id.send_data_button);
        sendButton.setOnClickListener(this);
        View connectButton = findViewById(R.id.connect_data_button);
        connectButton.setOnClickListener(this);
        View disconnectButton = findViewById(R.id.disconnect_data_button);
        disconnectButton.setOnClickListener(this);
        payloadName = null;
        if (ModuleVerificationUtil.isPayloadAvailable()) {
            payload = DJISampleApplication.getAircraftInstance().getPayload();
            /**
             *  Gets the product name defined by the manufacturer of the payload device.
             */
            payloadName = payload.getPayloadProductName();
            listenData();

            // /**
            //  *  Set the command data callback, the command data typically sent by payload in
            //  *  UART/CAN channel, the max bandwidth of this channel is 3KBytes/s on M200.
            //  */
            // payload.setCommandDataCallback(new Payload.CommandDataCallback() {
            //     @Override
            //     public void onGetCommandData(byte[] bytes) {
            //         if (receivedDataView != null) {
            //             runOnUiThread(new Runnable() {
            //                 @Override
            //                 public void run() {
            //                     Log.e(TAG, "receiving data size:" + bytes.length);
            //                     String str = ViewHelper.getString(bytes);
            //                     receiveSizeTotal = bytes.length + receiveSizeTotal;
            //                     receiveTotal.setText(String.valueOf(receiveSizeTotal));
            //                     receivedDataView.setText(str);
            //                     receivedDataView.invalidate();
            //                 }
            //             });
            //         }
            //     }
            // });
        }
        payloadNameView.setText(
                "Payload Name:" + (TextUtils.isEmpty(payloadName) ? "N/A" : payloadName));
        payloadNameView.invalidate();
    }

    @Override
    protected void onDestroy() {
        if (ModuleVerificationUtil.isPayloadAvailable() && null != payload) {
            payload.setCommandDataCallback(null);
            payload.setStreamDataCallback(null);
            disconnectPointCloudPipe();
            mStop.set(true);
        }
        if (scheduledFuture != null && !scheduledFuture.isCancelled() &&
                !scheduledFuture.isDone()) {
            scheduledFuture.cancel(true);
        }
        if (receiveScheduledFuture != null && !receiveScheduledFuture.isCancelled() &&
                !receiveScheduledFuture.isDone()) {
            receiveScheduledFuture.cancel(true);
        }
        executorService.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.send_data_button:
                if (repeatCheckbox.isChecked()) {
                    if (GeneralUtils.isFastDoubleClick()) {
                        ToastUtils.showToast("Don't press too frequently!");
                        return;
                    }
                    if (scheduledFuture != null && !scheduledFuture.isDone()) {
                        ToastUtils.showToast("Already in sending status!");
                        return;
                    }
                    if (!TextUtils.isDigitsOnly(periodView.getText().toString())) {
                        ToastUtils.showToast("Please set the correct frequency");
                        return;
                    }
                    int frequency = Integer.valueOf(periodView.getText().toString());
                    scheduledFuture = executorService.scheduleAtFixedRate(repeatRunnable, 100,
                            1000 / frequency, TimeUnit.MILLISECONDS);
                    ToastUtils.setResultToToast("set send data repeatably");
                } else {
                    sendDataToPayload();
                }
                break;
            case R.id.repeat_send_checkbox:
                if (scheduledFuture != null && !scheduledFuture.isCancelled() &&
                        !scheduledFuture.isDone()) {
                    scheduledFuture.cancel(true);
                    ToastUtils.setResultToToast("stop sending data repeatably");
                }
                break;

            case R.id.connect_data_button:
                initPipes();
                break;
            case R.id.disconnect_data_button:
                disconnectPointCloudPipe();
                break;
            default:
        }
    }

    private void sendDataToPayload() {
        String sendingDataStr = sendDataEditView.getText().toString();
        Log.e(TAG, "sending:" + sendingDataStr);
        final byte[] data = ViewHelper.getBytes(sendingDataStr);
        if (ModuleVerificationUtil.isPayloadAvailable() && null != payload) {
            // payload.sendDataToPayload(data, new CommonCallbacks.CompletionCallback() {
            //     @Override
            //     public void onResult(DJIError djiError) {
            //         ToastUtils.setResultToToast(djiError == null ? "Send data successfully" :
            //                 djiError.getDescription());
            //     }
            // });
            writeData(data);
        }
    }

    private void initPipes() {
        if (ModuleVerificationUtil.isPayloadAvailable() && null != payload && pipelines == null) {
            pipelines = payload.getPipelines();
            connectPointCloudPipe();
        }
    }

    private void connectPointCloudPipe() {
        if (pipelines != null/* && pipelines.getPipelines().size() != 0*/) {
            pipelines.connect(POINT_CLOUD_PORT, TransmissionControlType.STABLE,
                    new CommonCallbacks.CompletionCallback<PipelineError>() {
                        @Override
                        public void onResult(PipelineError pipelineError) {
                            if (pipelineError != null) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        String error = pipelineError.getErrorCode() + ", "
                                                + pipelineError.getDescription();
                                        Log.e(TAG, "connect failed:" + error);
                                        receiveSizeTotal = 0;
                                        receiveTotal.setText(String.valueOf(receiveSizeTotal));
                                        receivedDataView.setText(error);
                                        receivedDataView.invalidate();
                                    }
                                });
                            } else {
                                synchronized (Pipelines.class) {
                                    pointCloudPipe = pipelines.getPipeline(POINT_CLOUD_PORT);
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            String error = "connected";
                                            Log.e(TAG, "connect: " + error);
                                            receiveSizeTotal = 0;
                                            receiveTotal.setText(String.valueOf(receiveSizeTotal));
                                            receivedDataView.setText(error);
                                            receivedDataView.invalidate();
                                        }
                                    });
                                }
                            }
                        }
                    });
        } else if (pipelines == null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String error = "pipelines null";
                    Log.e(TAG, "connect: " + error);
                    receiveSizeTotal = 0;
                    receiveTotal.setText(String.valueOf(receiveSizeTotal));
                    receivedDataView.setText(error);
                    receivedDataView.invalidate();
                }
            });
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String error = "pipelines size = 0";
                    Log.e(TAG, "connect: " + error);
                    receiveSizeTotal = 0;
                    receiveTotal.setText(String.valueOf(receiveSizeTotal));
                    receivedDataView.setText(error);
                    receivedDataView.invalidate();
                }
            });
        }
    }

    private void disconnectPointCloudPipe() {
        Log.d(TAG, "disconnectPointCloudPipe: ");
        if (pipelines != null) {
            pipelines.disconnect(POINT_CLOUD_PORT,
                    new CommonCallbacks.CompletionCallback<PipelineError>() {
                        @Override
                        public void onResult(PipelineError pipelineError) {
                            if (pipelineError != null) {
                                Log.e(TAG, "onDestroy disconnect: " + pipelineError.getErrorCode()
                                        + ", " + pipelineError.getDescription());
                            }
                        }
                    });
            pointCloudPipe = null;
            pipelines = null;
            Log.d(TAG, "disconnectPointCloudPipe: true");
        }
    }

    private void writeData(final byte[] data) {
        // Data to be sent. 1 KB data size is recommended.
        final int ret = pointCloudPipe.writeData(data, 0, data.length);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String str = "" + ret;
                Log.e(TAG, str);
                receiveTotal.setText(str);
                str = ViewHelper.getString(data);
                receivedDataView.setText(str);
                receivedDataView.invalidate();
                if (ret > 0) {
                    receiveSizeTotal = ret + receiveSizeTotal;
                }
            }
        });
    }

    private void listenData() {
        // receiveScheduledFuture = executorService.scheduleAtFixedRate(receiveRunnable, 100,
        //         1000, TimeUnit.MILLISECONDS);
        receiveScheduledFuture = executorService.schedule(receiveRunnable, 5,TimeUnit.MILLISECONDS);
    }

    private void readData() {
        while (!mStop.get()) {
            if (pointCloudPipe != null) {
                byte[] bytes = new byte[1024];
                int n = pointCloudPipe.readData(bytes, 0, bytes.length);
                if (n > 0) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.e(TAG, "receiving data size:" + bytes.length);
                            String str = ViewHelper.getString(bytes);
                            receiveSizeTotal = bytes.length + receiveSizeTotal;
                            receiveTotal.setText(String.valueOf(receiveSizeTotal));
                            receivedDataView.setText(str);
                            receivedDataView.invalidate();
                        }
                    });
                } else {
                    Log.e(TAG, "receiving data error:" + n);
                }
            }
        }



    }
}
