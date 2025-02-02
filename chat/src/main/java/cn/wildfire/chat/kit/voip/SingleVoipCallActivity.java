/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package cn.wildfire.chat.kit.voip;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import org.webrtc.StatsReport;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import butterknife.ButterKnife;
import cn.wildfirechat.avenginekit.AVEngineKit;
import cn.wildfirechat.chat.R;
import cn.wildfirechat.client.NotInitializedExecption;

/**
 * Activity for peer connection call setup, call waiting
 * and call view.
 */

/**
 * author:xmf
 * date:2019/6/25 0025
 * description:音视频通话页面
 */
public class SingleVoipCallActivity extends FragmentActivity implements AVEngineKit.CallSessionCallback {
    private static final String TAG = "P2PVideoActivity";

    public static final String EXTRA_TARGET = "TARGET";
    public static final String EXTRA_MO = "ISMO";
    public static final String EXTRA_AUDIO_ONLY = "audioOnly";
    public static final String EXTRA_FROM_FLOATING_VIEW = "fromFloatingView";

    // List of mandatory application permissions.
    private static final String[] MANDATORY_PERMISSIONS = {"android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO", "android.permission.INTERNET"};

    private AVEngineKit gEngineKit;

    protected PowerManager.WakeLock wakeLock;


    private boolean isOutgoing;
    private String targetId;
    private boolean isAudioOnly;
    private boolean isFromFloatingView;
    private Handler handler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//    Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));

        // Set window styles for fullscreen-window size. Needs to be done before
        // adding content.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.ACQUIRE_CAUSES_WAKEUP |
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "bright");
        if (wakeLock != null) {
            wakeLock.acquire();
        }

        getWindow().addFlags(LayoutParams.FLAG_FULLSCREEN | LayoutParams.FLAG_KEEP_SCREEN_ON
                | LayoutParams.FLAG_SHOW_WHEN_LOCKED | LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());
        setContentView(R.layout.av_p2p_video_activity);

        try {
            gEngineKit = AVEngineKit.Instance();
        } catch (NotInitializedExecption notInitializedExecption) {
            notInitializedExecption.printStackTrace();
            finish();
        }
        ButterKnife.bind(this);

        // Check for mandatory permissions.
        for (String permission : MANDATORY_PERMISSIONS) {
            if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
        }

        // TODO init fragment
        final Intent intent = getIntent();

        targetId = intent.getStringExtra(EXTRA_TARGET);
        isFromFloatingView = intent.getBooleanExtra(EXTRA_FROM_FLOATING_VIEW, false);
        if (isFromFloatingView) {
            Intent serviceIntent = new Intent(this, FloatingVoipService.class);
            stopService(serviceIntent);
            initFromFloatView();
        } else {
            isOutgoing = intent.getBooleanExtra(EXTRA_MO, false);
            isAudioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false);
            init(targetId, isOutgoing, isAudioOnly);
        }
    }

    private AVEngineKit.CallSessionCallback currentCallback;

    private void initFromFloatView() {
        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session == null || AVEngineKit.CallState.Idle == session.getState()) {
            finish();
            return;
        } else {
            session.setCallback(SingleVoipCallActivity.this);
        }

        Fragment fragment;
        if (session.isAudioOnly()) {
            fragment = new AudioFragment();
        } else {
            fragment = new VideoFragment();
        }

        currentCallback = (AVEngineKit.CallSessionCallback) fragment;
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .add(android.R.id.content, fragment)
                .commit();
    }

    private void init(String targetId, boolean outgoing, boolean audioOnly) {
        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();

        Fragment fragment;
        if (audioOnly) {
            fragment = new AudioFragment();
        } else {
            fragment = new VideoFragment();
        }

        currentCallback = (AVEngineKit.CallSessionCallback) fragment;
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .add(android.R.id.content, fragment)
                .commit();

        if (outgoing) {
            gEngineKit.startCall(targetId, audioOnly, SingleVoipCallActivity.this);
            gEngineKit.startPreview();
        } else {
            if (session == null) {
                finish();
            } else {
                session.setCallback(SingleVoipCallActivity.this);
            }
        }
    }

    @TargetApi(19)
    private static int getSystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        return flags;
    }

    // Activity interfaces
    @Override
    public void onStop() {
        super.onStop();
        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session != null && session.getState() != AVEngineKit.CallState.Idle) {
//      session.stopVideoSource();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session != null && session.getState() != AVEngineKit.CallState.Idle) {
//      session.startVideoSource();
        }
    }

    @Override
    protected void onDestroy() {
        Thread.setDefaultUncaughtExceptionHandler(null);
        // TODO do not endCall
//        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
//        if (session != null && session.getState() != AVEngineKit.CallState.Idle) {
//            session.endCall();
//        }
        super.onDestroy();
        if (wakeLock != null) {
            wakeLock.release();
        }
    }

    public AVEngineKit getEngineKit() {
        return gEngineKit;
    }

    @Override
    public void didCallEndWithReason(AVEngineKit.CallEndReason reason) {
        finish();
    }

    @Override
    public void didError(String error) {
        postAction(() -> currentCallback.didError(error));
    }

    @Override
    public void didGetStats(StatsReport[] reports) {
        postAction(() -> currentCallback.didGetStats(reports));
    }

    @Override
    public void didChangeMode(boolean audioOnly) {
        postAction(() -> {
            currentCallback.didChangeMode(audioOnly);
            if (audioOnly) {
                AudioFragment fragment = new AudioFragment();
                FragmentManager fragmentManager = getSupportFragmentManager();
                fragmentManager.beginTransaction()
                        .replace(android.R.id.content, fragment)
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                        .commit();
                currentCallback = fragment;
            } else {
                // never called
            }

        });
    }

    @Override
    public void didChangeState(AVEngineKit.CallState state) {
        postAction(() -> currentCallback.didChangeState(state));
    }

    @Override
    public void didCreateLocalVideoTrack() {
        postAction(() -> currentCallback.didCreateLocalVideoTrack());
    }

    @Override
    public void didReceiveRemoteVideoTrack() {
        postAction(() -> currentCallback.didReceiveRemoteVideoTrack());
    }

    public void audioAccept() {
        AudioFragment fragment = new AudioFragment();
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
        currentCallback = fragment;

        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session != null) {
            if (session.getState() == AVEngineKit.CallState.Incoming) {
                session.answerCall(true);
            } else if (session.getState() == AVEngineKit.CallState.Connected) {
                session.setAudioOnly(true);
            }
        } else {
            finish();
        }
    }

    public void audioCall() {
        audioAccept();
    }

    public boolean isOutgoing() {
        return isOutgoing;
    }

    public String getTargetId() {
        return targetId;
    }

    public void showFloatingView() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Intent intent = new Intent(this, FloatingVoipService.class);
        intent.putExtra(EXTRA_TARGET, targetId);
        intent.putExtra(EXTRA_AUDIO_ONLY, isAudioOnly);
        intent.putExtra(EXTRA_MO, isOutgoing);
        startService(intent);
        finish();
    }

    private void postAction(Runnable action) {
        handler.post(action);
    }
}
